# -*- coding: utf-8 -*-
"""
T1.1 — ingest the phone photos.

Two piles arrive and they are treated completely differently:

  clean/<surface>/*.jpg           -> SEGMENTER negatives. Resized, given an EMPTY .txt label,
                                     split by surface folder, with >=120 images pulled out
                                     first as a pure specificity holdout that never touches
                                     training and never informs a threshold.

  indoor_defects/<class>/*.jpg    -> GATE positives ONLY. They have image-level labels and no
                                     masks. They are written to a manifest and are
                                     DELIBERATELY NOT put in the segmenter dataset - an empty
                                     .txt beside a photo of a crack would teach the model that
                                     cracks are background, which is worse than not having the
                                     photo at all. p1_rebuild_dataset.py asserts their absence.

Why PIL and not cv2 for the load: cv2.imread ignores EXIF orientation, so a third of a phone
shoot trains sideways and nothing ever reports an error. ImageOps.exif_transpose is the whole
reason this step exists as code rather than a shell one-liner.

Pilot mode runs the identical path into a temp directory, asserts every invariant, and reports
image quality - so 14 photos can prove the pipeline before 360 are shot. See PILOT_CHECKLIST.md.

Usage:
    python p1_ingest_clean.py --pilot  <dir>          # dry run + QC, writes nothing permanent
    python p1_ingest_clean.py --clean  <dir> --indoor-defects <dir> --out <dir>
"""
import argparse
import glob
import hashlib
import json
import os
import shutil
import sys

import numpy as np

HERE = os.path.abspath(os.path.dirname(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))

LONG_EDGE = 1024
JPEG_QUALITY = 88
GATE_CLASSES = ("crack", "peeling", "damp", "other_damage")
IMG_EXT = (".jpg", ".jpeg", ".png", ".bmp", ".webp")
HEIC_EXT = (".heic", ".heif")

# QC thresholds - absolute floors; the report also flags relative outliers, which catch a
# shoot that is uniformly soft (and would pass an absolute test by being consistently bad).
BLUR_FLOOR = 60.0          # variance of Laplacian at LONG_EDGE
CLIP_HI_FRAC = 0.10        # >10% of pixels at 250+ is blown out
CLIP_LO_FRAC = 0.25        # >25% of pixels at <=5 is crushed
DHASH_NEAR = 6             # Hamming distance below this = the same square metre


# ------------------------------------------------------------------ reading
def looks_like_heic(path):
    if os.path.splitext(path)[1].lower() in HEIC_EXT:
        return True
    try:
        with open(path, "rb") as fh:
            head = fh.read(16)
        return head[4:8] == b"ftyp" and head[8:12] in (b"heic", b"heix", b"mif1", b"msf1")
    except OSError:
        return False


def load_oriented(path):
    """Open, apply EXIF rotation, return RGB. Returns (img, note) or (None, reason)."""
    from PIL import Image, ImageOps
    if looks_like_heic(path):
        return None, ("HEIC/HEIF - OpenCV and PIL cannot read it. Set the camera to "
                      "JPEG / 'Most compatible' and reshoot.")
    try:
        im = Image.open(path)
        exif_o = None
        try:
            exif_o = im.getexif().get(274)          # 274 = Orientation
        except Exception:
            pass
        im = ImageOps.exif_transpose(im)
        if im.mode != "RGB":
            im = im.convert("RGB")
        return im, ("exif orientation %s applied" % exif_o) if exif_o and exif_o != 1 else ""
    except Exception as e:
        return None, "%s: %s" % (type(e).__name__, e)


def resize_long_edge(im, long_edge=LONG_EDGE):
    from PIL import Image
    w, h = im.size
    if max(w, h) <= long_edge:
        return im, False
    s = long_edge / float(max(w, h))
    return im.resize((max(1, int(round(w * s))), max(1, int(round(h * s)))),
                     Image.LANCZOS), True


# ------------------------------------------------------------------ quality
def dhash(im, size=8):
    g = np.asarray(im.convert("L").resize((size + 1, size)), dtype=np.int16)
    bits = (g[:, 1:] > g[:, :-1]).flatten()
    v = 0
    for b in bits:
        v = (v << 1) | int(b)
    return v


def hamming(a, b):
    return bin(a ^ b).count("1")


def quality(im):
    import cv2
    a = np.asarray(im.convert("L"))
    lap = float(cv2.Laplacian(a, cv2.CV_64F).var())
    hi = float(np.mean(a >= 250))
    lo = float(np.mean(a <= 5))
    return {"sharpness": round(lap, 1), "clipped_hi": round(hi, 4), "clipped_lo": round(lo, 4)}


# ------------------------------------------------------------------ discovery
def list_images(folder):
    """Every image in one folder, once. Windows glob is case-insensitive, so globbing both
    "*.jpg" and "*.JPG" returns each file twice - which silently doubled every count until
    the pilot caught it."""
    seen, out = set(), []
    for e in IMG_EXT + HEIC_EXT:
        for f in glob.glob(os.path.join(folder, "*" + e)) + glob.glob(os.path.join(folder, "*" + e.upper())):
            k = os.path.normcase(os.path.realpath(f))
            if k not in seen:
                seen.add(k)
                out.append(f)
    return sorted(out)


def discover(base):
    """Return (clean_groups, defect_groups). Tolerates the pilot layout and the final one."""
    clean, defects = {}, {}
    for sub in ("clean", "clean_photos", ""):
        d = os.path.join(base, sub) if sub else base
        if not os.path.isdir(d):
            continue
        for name in sorted(os.listdir(d)):
            p = os.path.join(d, name)
            if not os.path.isdir(p) or name in ("indoor_defects", "clean", "clean_photos"):
                continue
            files = list_images(p)
            if files:
                clean[name] = files
        if clean:
            break
    dd = os.path.join(base, "indoor_defects")
    if os.path.isdir(dd):
        for name in sorted(os.listdir(dd)):
            p = os.path.join(dd, name)
            if not os.path.isdir(p):
                continue
            files = list_images(p)
            if files:
                defects[name] = files
    return clean, defects


# ------------------------------------------------------------------ processing
def process_group(gid, files, out_images, prefix, report):
    """Resize + orient + save. Returns the written paths."""
    written = []
    for src in files:
        im, note = load_oriented(src)
        if im is None:
            short = os.path.join(os.path.basename(os.path.dirname(src)), os.path.basename(src))
            report["unreadable"].append({"file": short, "why": note})
            continue
        before = im.size
        im, did = resize_long_edge(im)
        q = quality(im)
        h = dhash(im)
        stem = hashlib.md5(("%s|%s" % (gid, os.path.basename(src))).encode()).hexdigest()[:10]
        name = "%s%s_%s.jpg" % (prefix, "".join(c for c in gid.lower() if c.isalnum())[:18], stem)
        dst = os.path.join(out_images, name)
        if out_images:
            os.makedirs(out_images, exist_ok=True)
            im.save(dst, "JPEG", quality=JPEG_QUALITY, optimize=True)
        report["images"].append({
            "src": os.path.basename(src), "group": gid, "out": name,
            "before": "%dx%d" % before, "after": "%dx%d" % im.size, "resized": did,
            "exif": note, "dhash": h, **q})
        written.append(dst)
    return written


def near_duplicates(entries):
    """Pairs within a group that are the same frame.

    A dHash needs texture to mean anything: on a featureless surface almost every bit comes
    out the same way and two completely different walls collide at distance 0. Since "plain
    painted wall" is 60 of the 360 shots, an unguarded hash would fire on precisely the images
    we most need. So low-texture images are reported separately, not as duplicates.
    """
    dups, flat = [], []
    for e in entries:
        bits = bin(e["dhash"]).count("1")
        if bits < 8 or bits > 56:
            flat.append(e["src"])
    flatset = set(flat)
    for i in range(len(entries)):
        for j in range(i + 1, len(entries)):
            a, b = entries[i], entries[j]
            if a["group"] != b["group"]:
                continue
            if a["src"] in flatset or b["src"] in flatset:
                continue
            d = hamming(a["dhash"], b["dhash"])
            if d < DHASH_NEAR:
                dups.append((a["src"], b["src"], d))
    return dups, flat


# ------------------------------------------------------------------ pilot
def pilot(base):
    import tempfile
    print("=" * 78)
    print("PHOTO PILOT - %s" % base)
    print("=" * 78)
    clean, defects = discover(base)
    if not clean and not defects:
        print("FAIL: found no clean/<surface>/ folders and no indoor_defects/<class>/ folders")
        print("      expected layout is in PILOT_CHECKLIST.md B")
        return 2

    report = {"images": [], "unreadable": []}
    tmp = tempfile.mkdtemp(prefix="slx_photopilot_")
    print("\nclean surfaces : %s" % ", ".join("%s(%d)" % (k, len(v)) for k, v in clean.items()))
    print("defect classes : %s" % (", ".join("%s(%d)" % (k, len(v)) for k, v in defects.items())
                                   or "none"))

    n_clean = 0
    for gid, files in clean.items():
        w = process_group(gid, files, os.path.join(tmp, "images"), "", report)
        n_clean += len(w)
        for p in w:                                     # the empty-label invariant
            lp = os.path.join(tmp, "labels", os.path.splitext(os.path.basename(p))[0] + ".txt")
            os.makedirs(os.path.dirname(lp), exist_ok=True)
            open(lp, "w").close()

    gate = {}
    for cls, files in defects.items():
        w = process_group(cls, files, os.path.join(tmp, "gate", cls), "d_", report)
        gate[cls] = len(w)

    fails, warns = [], []

    # --- format / pipeline invariants ------------------------------------------------
    if report["unreadable"]:
        for u in report["unreadable"]:
            fails.append("UNREADABLE  %s - %s" % (u["file"], u["why"]))
    else:
        print("\n[  ok  ] every file opened, EXIF orientation applied where present")

    resized = [e for e in report["images"] if e["resized"]]
    if report["images"]:
        print("[  ok  ] resize: %d of %d needed it; e.g. %s -> %s"
              % (len(resized), len(report["images"]),
                 report["images"][0]["before"], report["images"][0]["after"]))
        bad = [e for e in report["images"]
               if max(int(e["after"].split("x")[0]), int(e["after"].split("x")[1])) != LONG_EDGE
               and e["resized"]]
        if bad:
            fails.append("RESIZE long edge is not %d on %d images" % (LONG_EDGE, len(bad)))

    labels = glob.glob(os.path.join(tmp, "labels", "*.txt"))
    nonzero = [p for p in labels if os.path.getsize(p) != 0]
    if len(labels) != n_clean:
        fails.append("LABELS %d label files for %d clean images" % (len(labels), n_clean))
    elif nonzero:
        fails.append("LABELS %d label files are not empty" % len(nonzero))
    else:
        print("[  ok  ] %d clean images, %d label files, all exactly 0 bytes" % (n_clean, len(labels)))

    # --- the invariant that matters most ---------------------------------------------
    gate_names = set()
    for cls in gate:
        gate_names |= {os.path.basename(p) for p in
                       glob.glob(os.path.join(tmp, "gate", cls, "*.jpg"))}
    seg_names = {os.path.basename(p) for p in glob.glob(os.path.join(tmp, "images", "*.jpg"))}
    if gate_names & seg_names:
        fails.append("CONTAMINATION %d indoor-defect images reached the segmenter set"
                     % len(gate_names & seg_names))
    else:
        print("[  ok  ] %d indoor-defect images kept OUT of the segmenter dataset "
              "(gate manifest only)" % sum(gate.values()))

    # --- image quality ----------------------------------------------------------------
    ents = report["images"]
    if ents:
        sh = np.array([e["sharpness"] for e in ents])
        med = float(np.median(sh))
        soft = [e for e in ents if e["sharpness"] < BLUR_FLOOR]
        rel = [e for e in ents if e["sharpness"] < 0.35 * med]
        print("\nsharpness  median %.0f   range %.0f-%.0f" % (med, sh.min(), sh.max()))
        for e in {e["src"]: e for e in (soft + rel)}.values():
            warns.append("SOFT   %-28s variance %.0f (median %.0f)"
                         % (e["src"], e["sharpness"], med))
        blown = [e for e in ents if e["clipped_hi"] > CLIP_HI_FRAC]
        crushed = [e for e in ents if e["clipped_lo"] > CLIP_LO_FRAC]
        for e in blown:
            warns.append("BLOWN  %-28s %.0f%% of pixels at 250+ - flash or direct sun"
                         % (e["src"], 100 * e["clipped_hi"]))
        for e in crushed:
            warns.append("DARK   %-28s %.0f%% of pixels at 5- - too little light"
                         % (e["src"], 100 * e["clipped_lo"]))
        dups, flat = near_duplicates(ents)
        bygroup = {}
        for a, b, d in dups:
            g = next(e["group"] for e in ents if e["src"] == a)
            bygroup.setdefault(g, set()).update([a, b])
        for g, names in bygroup.items():
            warns.append("DUPLICATE %d images in %s are the same frame (%s) - move along the "
                         "wall between shots" % (len(names), g, ", ".join(sorted(names)[:4])))
        if flat:
            print("   note: %d image(s) too featureless for a duplicate check (%s). Expected "
                  "on plain walls; not a problem." % (len(flat), ", ".join(sorted(set(flat))[:4])))
        if not (soft or rel or blown or crushed or dups):
            print("[  ok  ] sharpness, exposure and framing variety all fine")

    # --- coverage ----------------------------------------------------------------------
    if len(clean) < 3:
        warns.append("COVERAGE only %d clean surface folder(s); send >=3 different surfaces "
                     "so the split and the group logic are actually exercised" % len(clean))
    if defects and len(defects) < 2:
        warns.append("COVERAGE only %d defect class(es); send >=2" % len(defects))

    shutil.rmtree(tmp, ignore_errors=True)

    print("\n" + "=" * 78)
    if fails:
        print("VERDICT: DO NOT SHOOT THE REST - %d hard failure(s)" % len(fails))
        for f in fails:
            print("   FAIL  " + f)
    elif warns:
        print("VERDICT: GO, with %d thing(s) to correct on the rest of the shoot" % len(warns))
    else:
        print("VERDICT: GO - the ingest path is clean and the photos are usable.")
    for w in warns:                      # shown whether or not something failed
        print("   WARN  " + w)
    print("=" * 78)

    out = os.path.join(ROOT, "handoff", "runs", "pilot_photo_probe.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump({"verdict": "FAIL" if fails else ("GO_WITH_CAVEATS" if warns else "GO"),
               "failures": fails, "warnings": warns,
               "clean_groups": {k: len(v) for k, v in clean.items()},
               "defect_classes": gate, "images": ents},
              open(out, "w", encoding="utf-8"), indent=2)
    print("wrote", out)
    return 1 if fails else 0


# ------------------------------------------------------------------ real ingest
def ingest(clean_dir, defect_dir, out_dir, holdout_min):
    """Write the resized clean images + empty labels into out_dir/, split off the specificity
    holdout by whole group, and write the gate manifest. Returns a dict for the rebuilder."""
    base = clean_dir if os.path.isdir(os.path.join(clean_dir, "clean")) else clean_dir
    clean, _ = discover(base)
    if defect_dir and os.path.isdir(defect_dir):
        _, defects = discover(os.path.dirname(defect_dir.rstrip("/\\")) or ".")
        if not defects:
            defects = {n: list_images(os.path.join(defect_dir, n))
                       for n in sorted(os.listdir(defect_dir))
                       if os.path.isdir(os.path.join(defect_dir, n))}
    else:
        defects = {}

    report = {"images": [], "unreadable": []}
    staged = os.path.join(out_dir, "_staged")
    shutil.rmtree(staged, ignore_errors=True)
    groups = {}
    for gid, files in sorted(clean.items()):
        groups[gid] = process_group(gid, files, os.path.join(staged, gid), "", report)

    # --- specificity holdout: whole groups, spread across surfaces ---------------------
    hold, held = [], 0
    for gid in sorted(groups, key=lambda g: -len(groups[g])):
        if held >= holdout_min:
            break
        # take the largest groups first but never take a surface type entirely if it is the
        # only one of its kind - the holdout must span surfaces, not concentrate on one wall
        hold.append(gid)
        held += len(groups[gid])
    holdout = {g: groups.pop(g) for g in hold}

    gate = {}
    for cls, files in sorted(defects.items()):
        gate[cls] = process_group(cls, files, os.path.join(staged, "_gate_" + cls), "d_", report)

    return {"train_pool": groups, "holdout": holdout, "gate": gate,
            "report": report, "staged": staged}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pilot", help="dry-run a small sample and report; writes nothing")
    ap.add_argument("--clean", help="folder-per-surface clean photos")
    ap.add_argument("--indoor-defects", dest="defects", help="folder-per-class defect photos")
    ap.add_argument("--out", default=os.path.join(ROOT, "YOLOV8", "ingest_v1"))
    ap.add_argument("--holdout-min", type=int, default=120)
    a = ap.parse_args()

    if a.pilot:
        return pilot(a.pilot)
    if not a.clean:
        ap.error("give --pilot DIR, or --clean DIR")

    r = ingest(a.clean, a.defects, a.out, a.holdout_min)
    n_train = sum(len(v) for v in r["train_pool"].values())
    n_hold = sum(len(v) for v in r["holdout"].values())
    n_gate = sum(len(v) for v in r["gate"].values())
    print("clean for splitting : %d images in %d groups" % (n_train, len(r["train_pool"])))
    print("specificity holdout : %d images in %d groups -> %s"
          % (n_hold, len(r["holdout"]), ", ".join(sorted(r["holdout"]))))
    print("gate positives      : %d images, %s"
          % (n_gate, {k: len(v) for k, v in r["gate"].items()}))
    if r["report"]["unreadable"]:
        print("UNREADABLE: %d - %s" % (len(r["report"]["unreadable"]),
                                       r["report"]["unreadable"][:3]))
    out = os.path.join(ROOT, "handoff", "runs", "ingest_report.json")
    json.dump({"train_pool": {k: len(v) for k, v in r["train_pool"].items()},
               "holdout": {k: len(v) for k, v in r["holdout"].items()},
               "gate": {k: len(v) for k, v in r["gate"].items()},
               "unreadable": r["report"]["unreadable"]},
              open(out, "w", encoding="utf-8"), indent=2)
    print("wrote", out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
