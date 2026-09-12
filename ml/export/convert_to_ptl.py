"""
SmartLease Edge — PyTorch Lite Interpreter (.ptl) Exporter
Exports trained vision (YOLOv8) and acoustic (TapClassifierCNN) models
to PyTorch Mobile Lite Interpreter format (.ptl) for on-device mobile inference.
"""
import sys
from pathlib import Path
import torch
import torch.nn as nn

REPO_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(REPO_ROOT))

from config import MODELS_DIR
from acoustic.train import TapClassifierCNN


def convert_acoustic_cnn(model_path: Path = None, output_path: Path = None) -> bool:
    """Convert acoustic CNN PyTorch model to .ptl."""
    weights_path = model_path or (MODELS_DIR / "acoustic_cnn.pt")
    out_path = output_path or (MODELS_DIR / "acoustic_cnn.ptl")

    print(f"--- Converting acoustic model: {weights_path.name} -> {out_path.name} ---")
    if not weights_path.exists():
        print(f"❌ Error: {weights_path} not found")
        return False

    model = TapClassifierCNN(num_classes=2)
    state_dict = torch.load(weights_path, map_location="cpu")
    model.load_state_dict(state_dict)
    model.eval()

    dummy_input = torch.randn(1, 1, 64, 19)
    traced_model = torch.jit.trace(model, dummy_input)
    traced_model._save_for_lite_interpreter(str(out_path))

    print(f"✅ Acoustic model exported: {out_path} ({out_path.stat().st_size:,} bytes)")
    return True


def convert_vision_model(model_path: Path = None, output_path: Path = None, imgsz: int = 640) -> bool:
    """Convert YOLOv8 defect detection PyTorch model to .ptl."""
    from ultralytics import YOLO

    weights_path = model_path or (MODELS_DIR / "vision_best.pt")
    out_path = output_path or (MODELS_DIR / "vision_best.ptl")

    print(f"\n--- Converting vision model: {weights_path.name} -> {out_path.name} ---")
    if not weights_path.exists():
        print(f"❌ Error: {weights_path} not found")
        return False

    model = YOLO(str(weights_path))

    print("1. Exporting YOLO to TorchScript base graph...")
    ts_path = model.export(format="torchscript", imgsz=imgsz, optimize=False)
    
    print("2. Converting TorchScript to PyTorch Lite Interpreter (.ptl)...")
    ts_model = torch.jit.load(ts_path, map_location="cpu")
    ts_model.eval()
    ts_model._save_for_lite_interpreter(str(out_path))

    print(f"✅ Vision model exported: {out_path} ({out_path.stat().st_size:,} bytes)")
    return True


if __name__ == "__main__":
    acc_ok = convert_acoustic_cnn()
    vis_ok = convert_vision_model()
    print("\n" + "=" * 45)
    print(f" Acoustic CNN (.ptl):  {'SUCCESS' if acc_ok else 'FAILED'}")
    print(f" Vision YOLO (.ptl):   {'SUCCESS' if vis_ok else 'FAILED'}")
    print("=" * 45)
