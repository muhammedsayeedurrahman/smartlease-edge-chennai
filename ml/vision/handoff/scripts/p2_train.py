# -*- coding: utf-8 -*-
"""
P2 — vision bake-off on the rebuilt, group-split dataset.

  A  yolov8n-seg @ 640   the control; everything is measured against it
  B  yolo11n-seg @ 640   contract verified BEFORE training: same Segment head, same
                         (1, 4+nc+32, 8400) + (1, 32, 160, 160) output shapes
  C  yolov8n-seg @ 800   resolution is the cheapest fix for hairline cracks, but input size
                         is part of the contract, so promoting C costs a Kotlin diff AND
                         phone latency the app owner has to accept

Deliberately NOT trained: any 's' or 'm' scale. The app runs PyTorch Lite on a phone CPU
with no delegate. yolov8n-seg is 11.3 GFLOPs and was measured at 21.8 ms on a desktop Ryzen 9;
yolov8s-seg is ~3.4x the FLOPs and an ARM core is several times slower again, which puts a
single capture into seconds. That is a UX failure, not a metric tradeoff.

Augmentation is aimed at indoor phone photos rather than the drone/outdoor imagery the
source datasets are dominated by: HSV shifts wide enough to cover tubelight vs daylight
colour temperature, perspective and rotation for handheld framing, and scale for distance.

Usage:
    python p2_train.py --run A [--epochs 100] [--batch 24]
"""
import argparse
import os
import sys

HERE = os.path.abspath(os.path.dirname(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(ROOT, "YOLOV8", "unified_v2", "data.yaml")
PROJECT = os.path.join(ROOT, "handoff", "runs")

RUNS = {
    "A": dict(model="yolov8n-seg.pt", imgsz=640, name="A_yolov8n_640"),
    "B": dict(model="yolo11n-seg.pt", imgsz=640, name="B_yolo11n_640"),
    "C": dict(model="yolov8n-seg.pt", imgsz=800, name="C_yolov8n_800"),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True, choices=sorted(RUNS))
    ap.add_argument("--epochs", type=int, default=100)
    ap.add_argument("--batch", type=int, default=24)
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()

    import torch
    if not torch.cuda.is_available():
        print("FATAL: no CUDA. Refusing to train on CPU.")
        return 1
    print("device:", torch.cuda.get_device_name(0),
          "| capability", torch.cuda.get_device_capability(),
          "| torch", torch.__version__, "cu", torch.version.cuda)

    from ultralytics import YOLO
    cfg = RUNS[a.run]
    # 800px needs a smaller batch to stay inside 11.9 GB
    batch = a.batch if cfg["imgsz"] <= 640 else max(8, a.batch // 2)

    m = YOLO(cfg["model"])
    m.train(
        data=DATA,
        epochs=a.epochs,
        imgsz=cfg["imgsz"],
        batch=batch,
        workers=8,
        seed=a.seed,
        deterministic=True,
        project=PROJECT,
        name=cfg["name"],
        exist_ok=True,
        pretrained=True,
        optimizer="AdamW",
        lr0=0.002,
        lrf=0.01,
        weight_decay=0.0005,
        close_mosaic=10,
        # --- indoor phone photo augmentation ---
        hsv_h=0.020,      # tubelight/daylight colour temperature
        hsv_s=0.60,
        hsv_v=0.45,       # indoor exposure swings
        degrees=8.0,      # handheld tilt
        translate=0.10,
        scale=0.45,       # distance to the surface varies a lot
        shear=2.0,
        perspective=0.0005,
        fliplr=0.5,
        flipud=0.15,      # floors get photographed from any orientation
        mosaic=1.0,
        mixup=0.10,
        copy_paste=0.20,  # helps the rarer classes without duplicating whole images
        erasing=0.2,
        # --- loss ---
        box=7.5,
        cls=0.8,
        dfl=1.5,
        plots=True,
        val=True,
        save=True,
        amp=True,
    )
    print("\nDONE:", os.path.join(PROJECT, cfg["name"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
