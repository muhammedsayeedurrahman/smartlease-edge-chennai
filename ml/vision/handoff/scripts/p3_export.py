# -*- coding: utf-8 -*-
"""
P3 — export the promoted model as a drop-in replacement for app/src/main/assets/yolov8n_seg.ptl

Same path that produced the working artifact, found in
ml/YOLOV8/convert_yolov8_seg_qnn_pte.py:23-44 (StaticYOLOv8SegWrapper) and
ml/YOLOV8/export_torchscript_lite.py:

    YOLO(weights) -> set Segment/Detect .export = True   (bypasses non-tensor head logic)
      -> StaticYOLOv8SegWrapper                          (guarantees the Tuple[Tensor,Tensor])
      -> torch.jit.trace
      -> _save_for_lite_interpreter          (NO optimize_for_mobile - see below)
      -> backport to N if needed                         (N=8; torch 2.12 already emits 8)

**optimize_for_mobile is deliberately NOT called.** I added it on the first pass and it
silently corrupted the output: traced-vs-optimized max |diff| was 524 on box coordinates in
640-pixel space, while trace-vs-eager and optimized-vs-saved were both bit-exact 0.000000. So
the one call that is supposed to be a free speedup was the only lossy step. The repo's own
export path (ml/YOLOV8/export_torchscript_lite.py:63-72) does not call it either, and carries
a comment that torch.jit.freeze + optimize_for_inference segfaults _save_for_lite_interpreter
on torch 2.11. Trace and save, nothing else.

Then it is verified where it counts: shapes asserted against CONTRACTS.md, and the file
loaded under torch 1.13.1 in the export/ venv, which is the same source tree the Android
native library is built from.

Windows: every torch file op runs in a space-free temp dir — PytorchStreamReader fails on a
path containing a space and reports it as a corrupt file. See CONTRACTS.md §0.

Usage:
    python p3_export.py --weights handoff/runs/A_yolov8n_640/weights/best.pt
"""
import argparse
import json
import os
import shutil
import sys
import tempfile

HERE = os.path.abspath(os.path.dirname(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT_DIR = os.path.join(ROOT, "handoff", "models", "vision")
WORK = os.path.join(tempfile.gettempdir(), "slx_export")

# from CONTRACTS.md §1-2
N_BYTECODE = 8
INPUT_SIZE = 640
NUM_CLASSES = 4
MASK_COEFFS = 32
ANCHORS = 8400
PROTO_SIZE = 160


def main():
    import torch
    from torch.jit.mobile import (_backport_for_mobile, _get_model_bytecode_version,
                                  _load_for_lite_interpreter)
    from ultralytics import YOLO
    import torch.nn as nn

    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", required=True)
    ap.add_argument("--imgsz", type=int, default=INPUT_SIZE)
    ap.add_argument("--name", default="yolov8n_seg.ptl")
    a = ap.parse_args()

    os.makedirs(WORK, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    tmp_ptl = os.path.join(WORK, "out.ptl")
    report = {"weights": a.weights, "imgsz": a.imgsz, "torch": torch.__version__}

    class StaticYOLOv8SegWrapper(nn.Module):
        """Verbatim contract from convert_yolov8_seg_qnn_pte.py:23-44."""

        def __init__(self, model):
            super().__init__()
            self.model = model
            for m in self.model.modules():
                if "Segment" in type(m).__name__ or "Detect" in type(m).__name__:
                    m.export = True
                    m.format = "torchscript"   # matches export_torchscript_lite.py:31

        def forward(self, x):
            out = self.model(x)
            if isinstance(out, (tuple, list)):
                return out[0], out[1]
            return out

    yolo = YOLO(a.weights)
    nc = yolo.model.model[-1].nc
    report["nc"] = int(nc)
    print("nc =", nc, "| names =", yolo.names)
    if nc != NUM_CLASSES:
        print("FATAL: nc=%d breaks the contract (expected %d)" % (nc, NUM_CLASSES))
        return 2

    wrapped = StaticYOLOv8SegWrapper(yolo.model.float().eval()).eval()
    ex = torch.randn(1, 3, a.imgsz, a.imgsz)

    with torch.no_grad():
        preds, protos = wrapped(ex)
    print("eager   preds", tuple(preds.shape), "protos", tuple(protos.shape))

    exp_preds = (1, 4 + nc + MASK_COEFFS, ANCHORS if a.imgsz == 640 else preds.shape[2])
    exp_protos = (1, MASK_COEFFS, PROTO_SIZE if a.imgsz == 640 else protos.shape[2],
                  PROTO_SIZE if a.imgsz == 640 else protos.shape[3])
    assert tuple(preds.shape) == exp_preds, "preds %s != contract %s" % (tuple(preds.shape), exp_preds)
    assert tuple(protos.shape) == exp_protos, "protos %s != contract %s" % (tuple(protos.shape), exp_protos)

    with torch.no_grad():
        traced = torch.jit.trace(wrapped, ex, strict=False)
    traced._save_for_lite_interpreter(tmp_ptl)

    with open(tmp_ptl, "rb") as fh:
        v = _get_model_bytecode_version(fh)
    report["bytecode_emitted"] = v
    print("bytecode emitted:", v)
    if v > N_BYTECODE:
        bp = os.path.join(WORK, "out_bp.ptl")
        _backport_for_mobile(tmp_ptl, bp, N_BYTECODE)
        shutil.move(bp, tmp_ptl)
        with open(tmp_ptl, "rb") as fh:
            v = _get_model_bytecode_version(fh)
        print("backported to:", v)
    report["bytecode_final"] = v
    assert v <= N_BYTECODE, "bytecode %d > N=%d, pytorch_android_lite 1.13.0 cannot load it" % (v, N_BYTECODE)

    with open(tmp_ptl, "rb") as fh:
        lite = _load_for_lite_interpreter(fh)
    with torch.no_grad():
        lp, lpr = lite(ex)
    report["lite_preds"] = list(lp.shape)
    report["lite_protos"] = list(lpr.shape)
    assert tuple(lp.shape) == exp_preds and tuple(lpr.shape) == exp_protos, "lite shapes drifted"

    # numeric agreement between eager and the lite artifact
    d = (lp - preds).abs().max().item()
    report["max_abs_diff_eager_vs_lite"] = d
    print("max |eager - lite| =", d)
    assert d < 1e-3, (
        "exported model does not match the trained model (max diff %g). "
        "Do NOT ship this - the app would compute different detections." % d
    )

    dst = os.path.join(OUT_DIR, a.name)
    shutil.copyfile(tmp_ptl, dst)
    report["output"] = dst
    report["bytes"] = os.path.getsize(dst)
    print("\nwrote", dst, "(%.2f MB)" % (report["bytes"] / 1e6))

    for extra in ("best.pt",):
        src = os.path.join(os.path.dirname(a.weights), extra)
        if os.path.exists(src):
            shutil.copyfile(src, os.path.join(OUT_DIR, extra))

    json.dump(report, open(os.path.join(OUT_DIR, "export_report.json"), "w"), indent=2)
    print("\nNow verify under torch 1.13.1:")
    print("  handoff/env/export/Scripts/python.exe handoff/scripts/p0_1_verify_on_1131.py \"%s\"" % dst)
    return 0


if __name__ == "__main__":
    sys.exit(main())
