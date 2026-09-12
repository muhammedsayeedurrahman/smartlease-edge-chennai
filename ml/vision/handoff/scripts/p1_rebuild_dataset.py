# -*- coding: utf-8 -*-
"""
P1 — rebuild the vision dataset. Replaces merge_and_zip_datasets.py.

Three defects in the original merge, fixed here:

  1.3 TAXONOMY
      `stain_mould` (1285 instances, AP50 0.065) is three unrelated things: blackening +
      moss + dampness (832, 65%), corrosion/rust (398, 31%), efflorescence (55, 4%). It is
      redefined as `damp_stain` — the coherent 832 — and corrosion and efflorescence are
      dropped. nc STAYS 4, so no structural contract break; only two display strings in
      YoloSegConfig.kt change (see INTEGRATION.md).

      `exposed brickwork -> spalling` (:24 of the original, 323 instances) is removed. That
      mapping is why the best-performing class is the one most likely to report a deliberate
      feature wall as "spalling (surface breaking away)".

  1.2 GROUPED SPLIT
      The original split is inherited from the source datasets and leaks: DJI_0017 has 50
      train / 5 valid / 3 test frames. Here the source video / capture session is parsed out
      of the filename and the split is by GROUP, so val and test stop being near-duplicates
      of train. Expect mAP to drop. That drop is the first honest number this project has.

  1.1 BACKGROUNDS
      The original only copied an image `if remapped_lines:` — so it deleted every
      background. Two sources are recovered here:
        - 78 images whose source label files were ALREADY empty
        - 76 images whose only annotations were classes we no longer keep
      plus whatever clean-surface photos are passed via --clean.

  Exposed brickwork: the LABEL is dropped, the IMAGE is kept.
      My first pass excluded those images entirely, on the theory that deliberate brick and
      spalling-with-exposed-brick are confusable. Measuring it killed that idea: all 89
      brick images also contain a kept class, and NONE is brick-only — so exclusion cost 931
      kept instances (640 of them spalling, 24% of the class) and prevented no contradiction,
      because there were no brick-only images to label background in the first place.
      Dropping just the brick annotation leaves the brick region unlabelled inside an
      otherwise-positive image, which is ordinary and consistent. The explicit negative
      signal for tidy feature walls comes from the clean brick photos in SHOOT_LIST.md.

Usage:
    python p1_rebuild_dataset.py [--clean DIR] [--out DIR] [--seed 0]
"""
import argparse
import glob
import hashlib
import json
import os
import random
import re
import shutil
from collections import Counter, defaultdict

import yaml

HERE = os.path.abspath(os.path.dirname(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SRC = os.path.join(ROOT, "YOLOV8")

# new unified taxonomy — nc stays 4
CLASSES = {0: "crack", 1: "peeling", 2: "spalling", 3: "damp_stain"}

# source class id -> new unified id
KEEP = {
    "1": {3: 0, 7: 1, 8: 1, 9: 2, 5: 2, 6: 3, 11: 3},
    "2": {0: 0, 1: 0, 2: 2},
    "3": {0: 1, 1: 2, 3: 0, 4: 3},
}
# label dropped, image kept (see module docstring). Only a brick-ONLY image would be
# excluded, and measurement showed there are none.
CONFUSABLE = {"1": {10}}                       # exposed brickwork ~ spalling
# dropped and visually distinct -> image may serve as a background
DROP_SAFE = {"1": {0, 1, 2, 4}, "3": {2, 5}}   # broken pane, corrosion, corrosion stain,
                                               # graffiti, corrosion, efflorescence

SPLITS = {"train": 0.70, "valid": 0.20, "test": 0.10}


def group_of(dataset, stem):
    """
    Group identity = the physical capture that produced this frame.

    Frames from one video or one HDR burst are near-duplicates; they must not straddle a
    split. Roboflow appends `_jpg.rf.<32 hex>` which is per-image and must be stripped first
    or every image becomes its own group and the leak survives.
    """
    s = re.sub(r"_jpg\.rf\.[0-9a-f]+$", "", stem, flags=re.I)
    s = re.sub(r"^d\d+_", "", s)

    # video frames: DJI_0017_MP4-0023, VID_20241228_130232250_mp4-0014
    m = re.match(r"(?i)^(DJI_\d+|VID_[\d_]+?)[-_]?(?:MP4|mp4)[-_]\d+", s)
    if m:
        return "%s:vid:%s" % (dataset, m.group(1).upper())

    # phone bursts: IMG_20241228_124308505_HDR -> group by capture DAY
    m = re.match(r"(?i)^IMG_(\d{8})_\d+", s)
    if m:
        return "%s:day:%s" % (dataset, m.group(1))

    # sequential sets: mildcrack114 -> group by prefix + hundreds bucket
    m = re.match(r"(?i)^([a-z_]+?)(\d+)$", s)
    if m:
        return "%s:seq:%s:%d" % (dataset, m.group(1).lower(), int(m.group(2)) // 100)

    return "%s:single:%s" % (dataset, s.lower())


def assign_splits(groups, seed):
    """
    Split BY GROUP, STRATIFIED BY SOURCE DATASET.

    The stratification is not cosmetic. A first version allocated groups largest-first into
    whichever split was most under quota, and the result was a test set that measured the
    wrong thing entirely: train crack came 63% from dataset 2 (close-up crack photos) while
    test crack came 80% from dataset 1 (drone exteriors), and test was 3% d2 against train's
    55%. d2's big sequential buckets landed in train because they are large and get placed
    first; d3's many single-image groups got scattered into test to fill the remaining quota.

    That turns the evaluation into a cross-dataset transfer test rather than a held-out test,
    and it is why `crack` scored AP50 0.030 — the model was asked to generalise from close-ups
    to aerial imagery. A real weakness, but a different question from the one being asked, and
    a number nobody can act on.

    Stratifying per source dataset isolates the variable we actually set out to fix —
    near-duplicate leakage between train and test — without confounding it with domain
    transfer. Groups are still never split, so the leak stays fixed.
    """
    rng = random.Random(seed)
    by_src = defaultdict(dict)
    for gid, imgs in groups.items():
        by_src[gid.split(":", 1)[0]][gid] = imgs

    out, have = {}, {k: 0 for k in SPLITS}
    for src in sorted(by_src):
        sub = by_src[src]
        total = sum(len(v) for v in sub.values())
        want = {k: v * total for k, v in SPLITS.items()}
        got = {k: 0 for k in SPLITS}
        # largest-first within the stratum keeps the proportions close
        for gid, imgs in sorted(sub.items(), key=lambda kv: (-len(kv[1]), kv[0])):
            pick = max(SPLITS, key=lambda s: (want[s] - got[s], rng.random()))
            out[gid] = pick
            got[pick] += len(imgs)
            have[pick] += len(imgs)
    return out, have


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--clean", default=None, help="folder-per-surface clean photos (SHOOT_LIST.md)")
    ap.add_argument("--out", default=os.path.join(SRC, "unified_v2"))
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()

    stats = Counter()
    groups = defaultdict(list)      # gid -> [(img_path, [remapped lines] or [])]
    per_class = Counter()

    for d in ("1", "2", "3"):
        keep = KEEP.get(d, {})
        conf = CONFUSABLE.get(d, set())
        drop = DROP_SAFE.get(d, set())
        for split in ("train", "valid", "test"):
            idir = os.path.join(SRC, d, split, "images")
            ldir = os.path.join(SRC, d, split, "labels")
            if not os.path.isdir(idir):
                continue
            for ip in sorted(glob.glob(os.path.join(idir, "*.*"))):
                stem = os.path.splitext(os.path.basename(ip))[0]
                lp = os.path.join(ldir, stem + ".txt")
                lines = []
                if os.path.exists(lp):
                    with open(lp, encoding="utf-8", errors="ignore") as f:
                        lines = [l.strip() for l in f if l.strip()]
                cids = [int(l.split()[0]) for l in lines]

                # Exclude ONLY if the image is nothing but confusable content: keeping it
                # as a background would contradict spalling examples elsewhere.
                if cids and all(c in conf for c in cids):
                    stats["excluded_confusable_only"] += 1
                    continue
                if any(c in conf for c in cids):
                    stats["brick_label_dropped_image_kept"] += 1

                remapped = []
                for l in lines:
                    p = l.split()
                    c = int(p[0])
                    if c in keep:
                        remapped.append("%d %s" % (keep[c], " ".join(p[1:])))
                        per_class[keep[c]] += 1

                if remapped:
                    stats["positive"] += 1
                elif not cids:
                    stats["background_already_empty"] += 1
                elif all(c in drop for c in cids):
                    stats["background_recovered"] += 1
                else:
                    stats["dropped_other"] += 1
                    continue

                groups[group_of(d, stem)].append((ip, remapped))

    # ---- clean photos from SHOOT_LIST.md: folder name IS the group ------------------
    if a.clean and os.path.isdir(a.clean):
        for sub in sorted(os.listdir(a.clean)):
            sd = os.path.join(a.clean, sub)
            if not os.path.isdir(sd):
                continue
            for ip in sorted(sum([glob.glob(os.path.join(sd, e))
                                  for e in ("*.jpg", "*.jpeg", "*.png", "*.JPG", "*.PNG")], [])):
                groups["clean:%s" % sub].append((ip, []))
                stats["background_shot"] += 1

    split_of, have = assign_splits(groups, a.seed)

    # Leak check against the authoritative assignment, not re-parsed filenames.
    spanning = [g for g in groups if len({split_of[g]}) > 1]
    assert not spanning, "a group was assigned more than one split: %s" % spanning[:3]

    out = a.out
    if os.path.isdir(out):
        shutil.rmtree(out)
    for s in SPLITS:
        os.makedirs(os.path.join(out, s, "images"), exist_ok=True)
        os.makedirs(os.path.join(out, s, "labels"), exist_ok=True)

    written = Counter()
    bg_per_split = Counter()
    for gid, items in groups.items():
        s = split_of[gid]
        for ip, remapped in items:
            base = hashlib.md5(("%s|%s" % (gid, os.path.basename(ip))).encode()).hexdigest()[:10]
            name = "%s_%s%s" % (re.sub(r"[^a-z0-9]+", "", gid.lower())[:18], base,
                                os.path.splitext(ip)[1].lower())
            shutil.copyfile(ip, os.path.join(out, s, "images", name))
            # EMPTY label file for a background - ultralytics reads that as "nothing here"
            with open(os.path.join(out, s, "labels", os.path.splitext(name)[0] + ".txt"),
                      "w", encoding="utf-8") as f:
                f.write("\n".join(remapped) + ("\n" if remapped else ""))
            written[s] += 1
            if not remapped:
                bg_per_split[s] += 1

    with open(os.path.join(out, "data.yaml"), "w", encoding="utf-8") as f:
        yaml.dump({"names": CLASSES, "train": "train/images",
                   "val": "valid/images", "test": "test/images"},
                  f, default_flow_style=False, sort_keys=False)

    report = {
        "classes": CLASSES,
        "image_stats": dict(stats),
        "instances_per_class": {CLASSES[k]: v for k, v in sorted(per_class.items())},
        "images_per_split": dict(written),
        "backgrounds_per_split": dict(bg_per_split),
        "background_pct_per_split": {s: round(100.0 * bg_per_split[s] / max(written[s], 1), 1)
                                     for s in written},
        "n_groups": len(groups),
        "seed": a.seed,
    }
    json.dump(report, open(os.path.join(out, "rebuild_report.json"), "w", encoding="utf-8"), indent=2)

    print("=" * 68)
    print("REBUILT ->", out)
    print("=" * 68)
    for k, v in stats.most_common():
        print("  %-28s %d" % (k, v))
    print("\n  groups: %d" % len(groups))
    print("  instances per class:")
    for k, v in sorted(per_class.items()):
        print("     %-12s %d" % (CLASSES[k], v))
    print("  images per split:      ", dict(written))
    print("  backgrounds per split: ", dict(bg_per_split),
          report["background_pct_per_split"])
    print("  leak check: every one of %d groups sits in exactly one split" % len(groups))
    print("  source mix per split (must be similar, or the test set measures transfer):")
    for s_ in ("train", "valid", "test"):
        mix = Counter()
        for gid, items in groups.items():
            if split_of[gid] == s_:
                mix[gid.split(":", 1)[0]] += len(items)
        tot = sum(mix.values()) or 1
        print("     %-6s " % s_ + "  ".join("d%s=%.0f%%" % (k, 100.0 * v / tot)
                                            for k, v in sorted(mix.items())))
    if not bg_per_split.get("valid"):
        print("\n  WARNING: no backgrounds in val - the FP improvement will be invisible to the metric")


if __name__ == "__main__":
    main()
