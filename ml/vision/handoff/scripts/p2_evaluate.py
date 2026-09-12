# -*- coding: utf-8 -*-
"""
P2 promotion gate — per-class accuracy AND clean-image false positives.

Two numbers decide a candidate, and the second is the one this whole exercise exists to
move: the app ships SCORE_THRESHOLD 0.25 and a clean wall currently lights up, because the
model was trained and scored entirely on images that all contain a defect.

  ACCURACY  mask AP50 / AP50-95 per class on the grouped-split TEST set.
  CLEAN FP  detections per background image, at 0.25 / 0.35 / 0.45 / 0.55.

"Background" here = an image with an empty label file in the test split. Those are real
negatives, but note what they are: mostly recovered exterior/drone frames whose only
annotations were classes we dropped. They are NOT domain-matched to an Indian flat interior.
Until the clean-surface photos from SHOOT_LIST.md land, treat this column as directional.

Everything runs through ultralytics' own letterbox (grey 114, aspect preserved), which is
what the app does — not the aspect-distorting cv2.resize that prepare_calibration.py used to
apply (P2-6, fixed in P1.4).

Usage:
    python p2_evaluate.py --weights handoff/runs/A_yolov8n_640/weights/best.pt --tag A
"""
import argparse
import glob
import json
import os
import warnings

warnings.filterwarnings("ignore")

HERE = os.path.abspath(os.path.dirname(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DS = os.path.join(ROOT, "YOLOV8", "unified_v2")
DATA = os.path.join(DS, "data.yaml")
THRESHOLDS = [0.25, 0.35, 0.45, 0.55]


def background_images(split="test"):
    """Images whose label file is empty — i.e. genuine negatives."""
    out = []
    for lp in glob.glob(os.path.join(DS, split, "labels", "*.txt")):
        if os.path.getsize(lp) == 0:
            stem = os.path.splitext(os.path.basename(lp))[0]
            for ext in (".jpg", ".jpeg", ".png"):
                ip = os.path.join(DS, split, "images", stem + ext)
                if os.path.exists(ip):
                    out.append(ip)
                    break
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", required=True)
    ap.add_argument("--tag", required=True)
    ap.add_argument("--imgsz", type=int, default=640)
    ap.add_argument("--device", default="cpu")
    a = ap.parse_args()

    from ultralytics import YOLO

    res = {"tag": a.tag, "weights": a.weights, "imgsz": a.imgsz}

    # ---------- accuracy on the grouped-split test set ----------
    m = YOLO(a.weights)
    r = m.val(data=DATA, split="test", imgsz=a.imgsz, batch=8, device=a.device,
              plots=False, verbose=False, project=os.path.join(ROOT, "handoff", "runs"),
              name="eval_%s" % a.tag, exist_ok=True)
    names = m.names
    res["mask_map50"] = float(r.seg.map50)
    res["mask_map5095"] = float(r.seg.map)
    res["box_map50"] = float(r.box.map50)
    res["per_class"] = {
        names[c]: {"mask_ap50": float(r.seg.ap50[i]), "mask_ap5095": float(r.seg.ap[i]),
                   "mask_p": float(r.seg.p[i]), "mask_r": float(r.seg.r[i])}
        for i, c in enumerate(r.seg.ap_class_index)
    }

    print("\n=== %s | grouped-split TEST ===" % a.tag)
    print("  mask mAP50 %.4f   mask mAP50-95 %.4f   box mAP50 %.4f"
          % (res["mask_map50"], res["mask_map5095"], res["box_map50"]))
    for k, v in res["per_class"].items():
        print("    %-12s AP50 %.4f  AP50-95 %.4f  P %.4f  R %.4f"
              % (k, v["mask_ap50"], v["mask_ap5095"], v["mask_p"], v["mask_r"]))

    # ---------- clean-image false positives ----------
    bgs = background_images("test")
    res["n_background_images"] = len(bgs)
    res["clean_fp"] = {}
    print("\n=== %s | clean-image false positives (%d background images) ===" % (a.tag, len(bgs)))
    if not bgs:
        print("  none available")
    else:
        for t in THRESHOLDS:
            total, flagged, per_cls = 0, 0, {}
            for ip in bgs:
                p = m.predict(ip, imgsz=a.imgsz, conf=t, device=a.device, verbose=False)[0]
                n = 0 if p.boxes is None else len(p.boxes)
                total += n
                if n:
                    flagged += 1
                    for c in p.boxes.cls.tolist():
                        per_cls[names[int(c)]] = per_cls.get(names[int(c)], 0) + 1
            res["clean_fp"]["%.2f" % t] = {
                "fp_per_image": total / len(bgs),
                "pct_images_flagged": 100.0 * flagged / len(bgs),
                "per_class": per_cls,
            }
            print("  conf %.2f  FP/img %.2f  flagged %.0f%%  %s"
                  % (t, total / len(bgs), 100.0 * flagged / len(bgs), per_cls))

    out = os.path.join(ROOT, "handoff", "runs", "eval_%s.json" % a.tag)
    json.dump(res, open(out, "w", encoding="utf-8"), indent=2)
    print("\nwrote", out)


if __name__ == "__main__":
    main()
