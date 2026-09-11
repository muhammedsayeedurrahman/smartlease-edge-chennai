"""
Export the fine-tuned YOLOv8n-Seg (best.pt) to a PyTorch Lite .ptl for the Android app.

This is the CPU/mobile-interpreter path. It exists alongside convert_yolov8_seg_qnn_pte.py
(the Hexagon NPU path) because the QNN bundle cannot execute without the ExecuTorch Android
runtime plus the HTP skel libraries, and the app needs a model that runs today.

The output contract is deliberately identical to the .pte export so the Kotlin decoder
(app/src/main/java/com/smartlease/edge/vision/YoloSegDecoder.kt) works against either:
    input  : [1, 3, 640, 640] float32 RGB in [0, 1]
    output : ( preds [1, 40, 8400], protos [1, 32, 160, 160] )
"""

import argparse
from pathlib import Path

import torch
import torch.nn as nn
from ultralytics import YOLO


class StaticYOLOv8SegWrapper(nn.Module):
    """Force the Segment head into export mode so it emits plain tensors."""

    def __init__(self, model):
        super().__init__()
        self.model = model
        for m in self.model.modules():
            if "Segment" in m.__class__.__name__ or "Detect" in m.__class__.__name__:
                m.export = True
                m.format = "torchscript"

    def forward(self, x):
        out = self.model(x)
        if isinstance(out, (tuple, list)):
            return out[0], out[1]
        return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", default="best.pt")
    ap.add_argument("--output", default="yolov8n_seg.ptl")
    args = ap.parse_args()

    print(f"Loading {args.weights} ...")
    yolo = YOLO(args.weights)
    names = yolo.names
    print(f"Classes ({len(names)}): {names}")

    model = StaticYOLOv8SegWrapper(yolo.model).eval().float()
    example = torch.randn(1, 3, 640, 640, dtype=torch.float32)

    with torch.no_grad():
        preds, protos = model(example)
    print(f"  preds  {tuple(preds.shape)}   (expected (1, 40, 8400))")
    print(f"  protos {tuple(protos.shape)}  (expected (1, 32, 160, 160))")

    assert preds.shape[1] == 4 + len(names) + 32, (
        f"Channel count {preds.shape[1]} does not match 4 box + {len(names)} cls + 32 coef"
    )

    print("Tracing ...")
    with torch.no_grad():
        traced = torch.jit.trace(model, example, strict=False)
    # NOTE: torch.jit.freeze + optimize_for_inference segfaults _save_for_lite_interpreter
    # on torch 2.11 (Lite Interpreter is deprecated there). Tracing alone is sufficient --
    # the wrapper already puts the Segment head in export mode, so there is no training-only
    # branch left to fold away.

    out = Path(args.output)
    traced._save_for_lite_interpreter(str(out))
    print(f"\nSaved {out.resolve()}  ({out.stat().st_size / 1048576:.2f} MB)")
    print("Copy to app/src/main/assets/ to ship it.")


if __name__ == "__main__":
    main()
