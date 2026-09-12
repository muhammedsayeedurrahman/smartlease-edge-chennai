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

# Backgrounds are sent to test at a higher rate than positives. They carry no labels, so
# over-representing them in test costs no training signal, and the clean-image specificity
# column is the one measurement that is underpowered - 14 backgrounds in test gave a 95%
# interval ~35 points wide and three claims built on that sample were later retracted.
BG_SPLITS = {"train": 0.55, "valid": 0.20, "test": 0.25}

# Relative weights in the allocation cost. Class balance is weighted above raw image count
# because getting image counts right while leaving test with 46 cracks is exactly the failure
# this replaces.
W_IMAGES, W_CLASS, W_BG = 1.0, 6.0, 2.0
QUOTA_SLACK = 1.05          # a split may run 5% over its image quota, no further
# The refinement's image bound is expressed in SHARE POINTS, not as a percentage of each
# split's own quota. Expressed the latter way, test (a 10% quota) could only move by ~11
# images, so a 65-image group could never enter it - and the one group holding 177 crack
# instances is 65 images. That single constraint was why test kept 2% of the crack.
SHARE_BAND = 0.06           # a split's image share may sit +/- 6 points from its target
REFINE_PASSES = 12

# A per-class floor for the test split. Below this, per-class AP is not a measurement.
# See TARGETS.md M5: crack AP50 read 0.05 on 46 test instances and 0.23 on 640 pooled ones.
TEST_CLASS_FLOOR = 150


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
    Split BY GROUP, STRATIFIED BY SOURCE DATASET **AND BY CLASS**.

    Two bugs have been fixed here, in that order, and the second was caused by fixing the
    first without noticing what it left behind.

    1. The original allocated groups largest-first into whichever split was most under quota.
       That produced a test set that measured the wrong thing: train crack came 63% from
       dataset 2 (close-up crack photos) while test crack came 80% from dataset 1 (drone
       exteriors), and test was 3% d2 against train's 55%. It was a cross-dataset transfer
       test wearing a held-out test set's name, and it is why `crack` first scored AP50 0.030.
       Fixed by stratifying per source dataset.

    2. That fix balanced IMAGE COUNTS within each source stratum and nothing else, so class
       composition was left to chance - and chance gave the test split **46 crack instances
       and 52% spalling**. Per-class AP on 46 instances is not a measurement: the same
       weights scored crack 0.0516 on that test split and 0.2343 on the 640 crack instances
       in the val+test pool. Two published claims were built on the 46 and both were
       retracted (METRICS_MODELS.md).

    So allocation is now a greedy look-ahead over a cost that sees every axis at once: for
    each group, try it in each split, and take the split that minimises the resulting squared
    relative deviation from target across image count, EVERY CLASS's instance count, and the
    background count. Groups are ordered most-instances-first so the big indivisible lumps
    are placed while there is still freedom to compensate.

    Groups are still never split, so the leak fix from (1) is untouched, and the source
    stratification from (1) is preserved by construction because the allocation runs inside
    each source stratum.
    """
    rng = random.Random(seed)
    by_src = defaultdict(dict)
    for gid, imgs in groups.items():
        by_src[gid.split(":", 1)[0]][gid] = imgs

    out, have = {}, {k: 0 for k in SPLITS}
    for src in sorted(by_src):
        sub = by_src[src]

        # what each group is made of
        gstat = {}
        for gid, items in sub.items():
            cls = Counter()
            bg = 0
            for _ip, remapped in items:
                if not remapped:
                    bg += 1
                for line in remapped:
                    cls[int(line.split()[0])] += 1
            gstat[gid] = (len(items), cls, bg)

        tot_img = sum(v[0] for v in gstat.values()) or 1
        tot_cls = Counter()
        for _n, c, _b in gstat.values():
            tot_cls.update(c)
        tot_bg = sum(v[2] for v in gstat.values())

        got_img = {s: 0 for s in SPLITS}
        got_cls = {s: Counter() for s in SPLITS}
        got_bg = {s: 0 for s in SPLITS}
        assign = {}

        def fills(split, gid):
            """How much of what this split still NEEDS does this group supply?

            Deliberately not "which placement minimises deviation afterwards" - that scores
            the smallest quota best while every split is empty, so it fills test, then valid,
            then train, and the first version of this produced a 835/672/694 split out of a
            70/20/10 target. Scoring the deficit filled keeps the original allocator's
            behaviour (biggest hole first) and widens it from image count to every class.
            """
            n, cls, bg = gstat[gid]
            want = SPLITS[split] * tot_img
            e = W_IMAGES * ((want - got_img[split]) / (want + 1e-9)) * (n / tot_img)
            for k, total_k in tot_cls.items():
                want_k = SPLITS[split] * total_k
                e += W_CLASS * ((want_k - got_cls[split][k]) / (want_k + 1e-9))                      * (cls.get(k, 0) / max(total_k, 1))
            if tot_bg:
                want_b = BG_SPLITS[split] * tot_bg
                e += W_BG * ((want_b - got_bg[split]) / (want_b + 1e-9)) * (bg / tot_bg)
            return e

        order = sorted(sub, key=lambda g: (-sum(gstat[g][1].values()), -gstat[g][0], g))
        for gid in order:
            # Pass 1 - shape. Hard image quota, class balance only among splits that can
            # still take the group. This alone is not enough: the groups are large and nearly
            # class-pure (a DJI video group is 50 frames of mostly spalling), so by the time
            # the quota guard has had its say the eligible set is usually a single split and
            # class balance has no vote left. Pass 2 fixes that.
            n_g = gstat[gid][0]
            eligible = [s_ for s_ in SPLITS
                        if got_img[s_] + n_g <= QUOTA_SLACK * SPLITS[s_] * tot_img]
            if eligible:
                pick = max(eligible, key=lambda s: (fills(s, gid), rng.random()))
            else:
                pick = max(SPLITS, key=lambda s: (SPLITS[s] * tot_img - got_img[s],
                                                  rng.random()))
            assign[gid] = pick
            got_img[pick] += n_g
            got_cls[pick].update(gstat[gid][1])
            got_bg[pick] += gstat[gid][2]

        # ---- Pass 2: refinement -----------------------------------------------------
        # Greedy allocation cannot see a move it should have made three groups ago. With 226
        # indivisible, class-lumpy groups that matters: the first version of this left test
        # at 65% spalling and 7% crack, which is the very defect it was written to remove.
        # So: hill-climb. Score the whole split, try every single move and every pairwise
        # swap, keep what lowers the score, stop when nothing does. Deterministic given seed.
        def objective():
            e = 0.0
            for s_ in SPLITS:
                want = SPLITS[s_] * tot_img
                e += W_IMAGES * ((got_img[s_] - want) / (want + 1e-9)) ** 2
                for k, total_k in tot_cls.items():
                    want_k = SPLITS[s_] * total_k
                    e += W_CLASS * ((got_cls[s_][k] - want_k) / (want_k + 1e-9)) ** 2
                if tot_bg:
                    want_b = BG_SPLITS[s_] * tot_bg
                    e += W_BG * ((got_bg[s_] - want_b) / (want_b + 1e-9)) ** 2
            return e

        def move(gid, frm, to):
            n, cls, bg = gstat[gid]
            got_img[frm] -= n; got_img[to] += n
            got_cls[frm].subtract(cls); got_cls[to].update(cls)
            got_bg[frm] -= bg; got_bg[to] += bg
            assign[gid] = to

        def within_quota():
            return all(abs(got_img[s_] / tot_img - SPLITS[s_]) <= SHARE_BAND for s_ in SPLITS)

        gids = sorted(sub)
        best = objective()
        for _ in range(REFINE_PASSES):
            improved = False
            for gid in gids:
                frm = assign[gid]
                for to in SPLITS:
                    if to == frm:
                        continue
                    move(gid, frm, to)
                    j = objective()
                    if j < best - 1e-12 and within_quota():
                        best, improved = j, True
                        frm = to
                    else:
                        move(gid, to, frm)
            for i in range(len(gids)):
                for j2 in range(i + 1, len(gids)):
                    a, b = gids[i], gids[j2]
                    sa, sb = assign[a], assign[b]
                    if sa == sb:
                        continue
                    move(a, sa, sb); move(b, sb, sa)
                    j = objective()
                    if j < best - 1e-12 and within_quota():
                        best, improved = j, True
                    else:
                        move(a, sb, sa); move(b, sa, sb)
            if not improved:
                break

        for gid, s_ in assign.items():
            out[gid] = s_
            have[s_] += gstat[gid][0]

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
