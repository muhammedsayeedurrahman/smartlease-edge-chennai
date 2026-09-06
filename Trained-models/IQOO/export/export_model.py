"""
SmartLease Edge — Model Export Pipeline
Exports trained PyTorch weights to edge formats:
- ONNX (Static shape for Qualcomm AI Engine / QNN)
- TorchScript / ExecuTorch-ready graph
"""
import sys
import argparse
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
from config import MODELS_DIR, HARDWARE
from ultralytics import YOLO


def export_vision_model(
    weights_path: str = None,
    output_format: str = "onnx",
    imgsz: int = 640,
    simplify: bool = True,
    dynamic: bool = False,
) -> str:
    """
    Export YOLOv8-Seg to ONNX / TorchScript for edge deployment.
    
    Args:
        weights_path: Path to best.pt (defaults to models/vision_best.pt)
        output_format: 'onnx', 'torchscript', or 'tflite'
        imgsz: Static image dimension (640 recommended for NPU)
        simplify: Run onnxslim / onnx-simplifier
        dynamic: Dynamic axes (MUST be False for Qualcomm Hexagon HTP / QNN)
    """
    if weights_path is None:
        weights_path = MODELS_DIR / "vision_best.pt"
        if not weights_path.exists():
            print(f"❌ Weights not found at {weights_path}.")
            print("   Train the model first using: python vision/train.py")
            return None

    weights_path = Path(weights_path)
    print(f"Loading weights from: {weights_path}")
    model = YOLO(str(weights_path))

    print(f"Exporting to format: {output_format.upper()}...")
    print(f"Static shape: ({HARDWARE['batch_size']}, 3, {imgsz}, {imgsz})")
    print(f"Target Accelerator: {HARDWARE['target_npu']} ({HARDWARE['quantization']})")

    exported_file = model.export(
        format=output_format,
        imgsz=imgsz,
        batch=HARDWARE["batch_size"],
        simplify=simplify,
        dynamic=dynamic,
    )

    print(f"\n✅ Export successful: {exported_file}")
    return exported_file


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export trained models for Edge / NPU")
    parser.add_argument("--model", type=str, help="Path to weights file (default: models/vision_best.pt)")
    parser.add_argument("--format", type=str, default="onnx", choices=["onnx", "torchscript", "tflite"])
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--dynamic", action="store_true", help="Enable dynamic axes (CPU only, avoid for NPU)")
    args = parser.parse_args()

    export_vision_model(
        weights_path=args.model,
        output_format=args.format,
        imgsz=args.imgsz,
        dynamic=args.dynamic,
    )
