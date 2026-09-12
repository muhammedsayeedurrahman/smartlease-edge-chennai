# -*- coding: utf-8 -*-
"""
Operating-point sweep for SCORE_THRESHOLD (YoloSegConfig.kt:47), currently 0.25f.

Two curves, and the whole point is that they trade against each other:

  RECALL  — mask recall / mAP50 on the held-out test split, per threshold.
            Uses ultralytics' own val(), which letterboxes with 114 fill exactly as the
            Android path does (YoloSegDefectSegmenter.kt:82,111 / Letterbox.kt:41-57).

  FALSE POSITIVES — detections per image on CLEAN surfaces that contain no defect at all.
            This number does not exist anywhere yet. It is the number that actually matters
            for this product: the training set has zero background images, so the model has
            never been scored on a clean wall.

Clean images are letterboxed here by hand rather than via cv2.resize, because
prepare_calibration.py:39 does a plain aspect-distorting resize and that mismatch (P2-6) is
exactly the sort of thing that makes a calibration number wrong in a way nobody notices.

Usage:
    python threshold_sweep.py                       # recall only
    python threshold_sweep.py --clean path/to/dir   # recall + false positives
"""
import argparse
import glob
import os

import numpy as np

THRESHOLDS = [0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65]
IMG = 640
PAD = 114


def letterbox(bgr, target=IMG, pad=PAD):
    """Mirror of Letterbox.forSource + the fill loop in YoloSegDefectSegmenter.toInputTensor."""
    import cv2
    h, w = bgr.shape[:2]
    scale = min(target / w, target / h)
    cw, ch = min(int(round(w * scale)), target), min(int(round(h * scale)), target)
    px, py = (target - cw) // 2, (target - ch) // 2
    canvas = np.full((target, target, 3), pad, dtype=np.uint8)
    canvas[py:py + ch, px:px + cw] = cv2.resize(bgr, (cw, ch), interpolation=cv2.INTER_LINEAR)
    return canvas


def recall_curve(weights, data):
    from ultralytics import YOLO
    rows = []
    for t in THRESHOLDS:
        m = YOLO(weights)
        r = m.val(data=data, split="test", imgsz=IMG, batch=8, device="cpu",
                  conf=t, plots=False, verbose=False,
                  project="runs_audit", name="sweep_%02d" % int(t * 100), exist_ok=True)
        rows.append({
            "conf": t,
            "mask_map50": float(r.seg.map50),
            "mask_recall": float(np.mean(r.seg.r)) if len(r.seg.r) else 0.0,
            "mask_precision": float(np.mean(r.seg.p)) if len(r.seg.p) else 0.0,
        })
        print("  conf %.2f  mask mAP50 %.4f  R %.4f  P %.4f"
              % (t, rows[-1]["mask_map50"], rows[-1]["mask_recall"], rows[-1]["mask_precision"]))
    return rows


def fp_curve(weights, clean_dir):
    import cv2
    from ultralytics import YOLO
    files = sorted(sum([glob.glob(os.path.join(clean_dir, "**", e), recursive=True)
                        for e in ("*.jpg", "*.jpeg", "*.png", "*.JPG", "*.PNG")], []))
    if not files:
        print("  no clean images found under %s" % clean_dir)
        return [], 0
    print("  %d clean images" % len(files))
    imgs = []
    for f in files:
        im = cv2.imread(f)
        if im is not None:
            imgs.append(letterbox(im))

    model = YOLO(weights)
    rows = []
    for t in THRESHOLDS:
        total, flagged = 0, 0
        per_class = {}
        for im in imgs:
            res = model.predict(im, imgsz=IMG, conf=t, device="cpu", verbose=False)[0]
            n = 0 if res.boxes is None else len(res.boxes)
            total += n
            if n:
                flagged += 1
                for c in res.boxes.cls.tolist():
                    nm = model.names[int(c)]
                    per_class[nm] = per_class.get(nm, 0) + 1
        rows.append({
            "conf": t,
            "fp_per_image": total / len(imgs),
            "pct_images_flagged": 100.0 * flagged / len(imgs),
            "per_class": per_class,
        })
        print("  conf %.2f  FP/img %.2f  images flagged %.0f%%  %s"
              % (t, rows[-1]["fp_per_image"], rows[-1]["pct_images_flagged"], per_class))
    return rows, len(imgs)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", default="best.pt")
    ap.add_argument("--data", default="unified_defects/data.yaml")
    ap.add_argument("--clean", default=None, help="folder of clean-surface photos")
    a = ap.parse_args()

    print("=" * 72)
    print("RECALL on held-out test split (inflated: source video frames span splits)")
    print("=" * 72)
    rec = recall_curve(a.weights, a.data)

    fp, n_clean = [], 0
    if a.clean:
        print()
        print("=" * 72)
        print("FALSE POSITIVES on clean surfaces")
        print("=" * 72)
        fp, n_clean = fp_curve(a.weights, a.clean)

    print()
    print("| conf | mask mAP50 | mask recall | FP/clean img | % clean flagged |")
    print("|-----:|-----------:|------------:|-------------:|----------------:|")
    fpm = {r["conf"]: r for r in fp}
    for r in rec:
        f = fpm.get(r["conf"])
        print("| %.2f | %.3f | %.3f | %s | %s |" % (
            r["conf"], r["mask_map50"], r["mask_recall"],
            ("%.2f" % f["fp_per_image"]) if f else "—",
            ("%.0f%%" % f["pct_images_flagged"]) if f else "—",
        ))
    if not fp:
        print("\nFP columns empty: rerun with --clean <dir> once clean-surface photos exist.")


if __name__ == "__main__":
    main()
