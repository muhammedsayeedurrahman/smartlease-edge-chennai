"""
Convert fine-tuned YOLOv8n-Seg (best.pt) to INT8 (8a8w) Hexagon HTP binary (.pte)
via ExecuTorch PT2E and Qualcomm QNN Backend.

Target: Snapdragon 8-series Hexagon NPU (e.g., SM8650 / SM8750)
Input shape:  [1, 3, 640, 640] (RGB, float32, normalized [0.0, 1.0])
Output shapes:
  1. Box + Cls + Mask-Coeffs: [1, 40, 8400] (4 bbox + 4 classes + 32 proto coefs)
  2. Prototype Masks:        [1, 32, 160, 160] (32 prototype mask channels)
"""

import os
import sys
import glob
import shutil
import argparse
from pathlib import Path
import numpy as np
import torch
import torch.nn as nn
from ultralytics import YOLO

class StaticYOLOv8SegWrapper(nn.Module):
    """
    Wraps YOLOv8 segmentation model to guarantee strict static shapes:
    Returns pure tensor tuple: (preds, protos)
    - preds: (1, 40, 8400)
    - protos: (1, 32, 160, 160)
    """
    def __init__(self, model):
        super().__init__()
        self.model = model
        # Set export mode on Segment head to bypass internal non-tensor logic
        for m in self.model.modules():
            if 'Segment' in m.__class__.__name__ or 'Detect' in m.__class__.__name__:
                m.export = True

    def forward(self, x: torch.Tensor):
        out = self.model(x)
        # In export mode, Segment head outputs [preds, protos]
        if isinstance(out, (tuple, list)):
            preds, protos = out[0], out[1]
            return preds, protos
        return out


def load_calibration_batches(calib_dir: Path, num_samples: int = 50):
    """
    Loads preprocessed calibration tensors (1, 3, 640, 640)
    """
    npy_files = sorted(list(calib_dir.glob("preprocessed_tensors/*.npy")))
    batches = []
    
    if npy_files:
        print(f"Loading {min(num_samples, len(npy_files))} preprocessed calibration tensors (.npy)...")
        for f in npy_files[:num_samples]:
            arr = np.load(f)
            t = torch.from_numpy(arr).float()
            batches.append(t)
    else:
        # Fallback to loading raw images if npy files not yet generated
        import cv2
        img_files = sorted(list(calib_dir.glob("raw_images/*.*"))) or sorted(list(calib_dir.glob("*.*")))
        print(f"Processing {min(num_samples, len(img_files))} raw calibration images...")
        for f in img_files[:num_samples]:
            img = cv2.imread(str(f))
            if img is None: continue
            img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
            img_resized = cv2.resize(img_rgb, (640, 640), interpolation=cv2.INTER_LINEAR)
            img_norm = (img_resized.astype(np.float32) / 255.0).transpose(2, 0, 1)
            tensor_data = torch.from_numpy(img_norm).unsqueeze(0).float()
            batches.append(tensor_data)
            
    if not batches:
        print("Warning: No calibration images found in calibration_data/. Generating 30 synthetic samples.")
        for _ in range(30):
            batches.append(torch.rand(1, 3, 640, 640, dtype=torch.float32))
            
    return batches


def main():
    parser = argparse.ArgumentParser(description="Convert YOLOv8n-Seg to Qualcomm HTP INT8 .pte via ExecuTorch")
    parser.add_argument("--weights", type=str, default="best.pt", help="Path to best.pt weights")
    parser.add_argument("--calib-dir", type=str, default="calibration_data", help="Calibration images directory")
    parser.add_argument("--soc", type=str, default="SM8650", help="Target Qualcomm SoC (e.g. SM8650, SM8750)")
    parser.add_argument("--output", type=str, default="yolov8n_seg_htp.pte", help="Output .pte filename")
    parser.add_argument("--outdir", type=str, default="qnn_android_bundle", help="Target output bundle directory")
    args = parser.parse_args()

    print("=" * 65)
    print("🚀 YOLOv8n-Seg to Qualcomm Hexagon HTP INT8 (8a8w) Converter")
    print(f"• Weights:        {args.weights}")
    print(f"• Target SoC:     {args.soc}")
    print(f"• Output Binary:  {args.output}")
    print("=" * 65)

    # 1. Environment & SDK Verification
    qnn_sdk_root = os.environ.get("QNN_SDK_ROOT")
    if not qnn_sdk_root:
        print("⚠️ Warning: QNN_SDK_ROOT environment variable is not set.")
        print("   If lowering to HTP, make sure to export QNN_SDK_ROOT=/path/to/qnn/sdk")
    else:
        print(f"✓ Found QNN_SDK_ROOT: {qnn_sdk_root}")

    # 2. Model Loading & Static Graph Preparation
    print("\n[Step 1/6] Loading YOLOv8n-Seg model...")
    yolo = YOLO(args.weights)
    wrapped_model = StaticYOLOv8SegWrapper(yolo.model).eval()

    example_inputs = (torch.randn(1, 3, 640, 640, dtype=torch.float32),)

    # Warmup and shape check
    with torch.no_grad():
        preds, protos = wrapped_model(*example_inputs)
        print(f"✓ Verification forward pass:")
        print(f"  - Preds (Box + Cls + Coefs): {preds.shape} (Expected: 1, 40, 8400)")
        print(f"  - Protos (Mask Prototypes):  {protos.shape} (Expected: 1, 32, 160, 160)")

    # 3. Graph Capture with torch.export
    print("\n[Step 2/6] Capturing TorchDynamo FX Graph with torch.export...")
    # strict=False is required for dynamic anchor initialization
    exported_prog = torch.export.export(wrapped_model, example_inputs, strict=False)
    graph_module = exported_prog.module()
    print("✓ FX Graph successfully captured.")

    # 4. Calibration Data Loading
    print("\n[Step 3/6] Loading calibration dataset...")
    calib_dir = Path(args.calib_dir)
    calib_batches = load_calibration_batches(calib_dir, num_samples=50)
    print(f"✓ Prepared {len(calib_batches)} calibration samples.")

    # 5. PT2E Quantization & Calibration (QnnQuantizer 8a8w)
    print("\n[Step 4/6] Initializing QnnQuantizer for INT8 (8a8w)...")
    try:
        from executorch.backends.qualcomm.quantizer.quantizer import QnnQuantizer
        from torchao.quantization.pt2e.quantize_pt2e import convert_pt2e, prepare_pt2e

        quantizer = QnnQuantizer()
        prepared_model = prepare_pt2e(graph_module, quantizer)

        print("  Running activation calibration across calibration dataset...")
        with torch.no_grad():
            for i, batch in enumerate(calib_batches):
                prepared_model(batch)
                if (i + 1) % 10 == 0 or (i + 1) == len(calib_batches):
                    print(f"   Processed {i + 1}/{len(calib_batches)} batches")

        print("  Locking quantization scales (convert_pt2e)...")
        quantized_model = convert_pt2e(prepared_model)
        print("✓ INT8 Graph quantization complete.")

    except ImportError as e:
        print(f"\n❌ Error importing ExecuTorch/QnnQuantizer: {e}")
        print("   Please run this step in an environment with ExecuTorch & torchao installed.")
        print("   (See deployment instructions below)")
        return

    # 6. Lowering to ExecuTorch & Qualcomm QNN HTP Backend
    print(f"\n[Step 5/6] Lowering to Qualcomm QNN Backend targeting {args.soc}...")
    try:
        from executorch.backends.qualcomm.serialization.qc_schema import QcomChipset
        from executorch.backends.qualcomm.utils.utils import (
            generate_htp_compiler_spec,
            generate_qnn_executorch_compiler_spec,
            to_edge_transform_and_lower_to_qnn,
        )

        backend_options = generate_htp_compiler_spec(use_fp16=False)  # Strict INT8 mode
        soc_enum = getattr(QcomChipset, args.soc, None)
        if soc_enum is None:
            # Fallback to SM8650
            soc_enum = QcomChipset.SM8650

        compile_specs = generate_qnn_executorch_compiler_spec(
            soc_model=soc_enum,
            backend_options=backend_options,
        )

        print("  Transforming to Edge dialect and lowering to QNN HTP delegate...")
        delegated_program = to_edge_transform_and_lower_to_qnn(
            quantized_model, example_inputs, compile_specs
        )

        print("  Serializing to ExecuTorch program...")
        executorch_program = delegated_program.to_executorch()
        print("✓ QNN HTP lowering successful.")

    except Exception as e:
        import traceback
        traceback.print_exc()
        print(f"\n❌ Lowering failed: {e}")
        print("   Ensure QNN SDK is properly configured in LD_LIBRARY_PATH.")
        return

    # 7. Serialization & Packaging
    print("\n[Step 6/6] Packaging and saving compiled .pte binary...")
    out_dir = Path(args.outdir)
    out_dir.mkdir(parents=True, exist_ok=True)
    pte_path = out_dir / args.output

    with open(pte_path, "wb") as f:
        f.write(executorch_program.buffer)

    file_size_mb = pte_path.stat().st_size / (1024 * 1024)
    print(f"\n🎉 SUCCESS! Compiled HTP binary generated:")
    print(f"  • Binary Path: {pte_path.resolve()}")
    print(f"  • Binary Size: {file_size_mb:.2f} MB (Expected: ~4-6 MB)")

    # Copy target android QNN libraries if QNN_SDK_ROOT is present
    if qnn_sdk_root:
        aarch64_lib = Path(qnn_sdk_root) / "lib" / "aarch64-android"
        if aarch64_lib.exists():
            print(f"\nBundling target Qualcomm Android libraries for APK integration:")
            needed_libs = [
                "libQnnHtp.so",
                "libQnnHtpV75Skel.so",
                "libQnnHtpV73Skel.so",
                "libqnn_executorch_backend.so"
            ]
            for lib_name in needed_libs:
                src_lib = aarch64_lib / lib_name
                if src_lib.exists():
                    shutil.copy(src_lib, out_dir / lib_name)
                    print(f"  ✓ Copied {lib_name}")

    print("\n" + "=" * 65)
    print(f"All deployment artifacts packaged in: {out_dir.resolve()}")
    print("=" * 65)


if __name__ == "__main__":
    main()
