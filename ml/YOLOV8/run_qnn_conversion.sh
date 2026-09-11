#!/bin/bash
set -e

# ==============================================================================
# ExecuTorch + Qualcomm QNN INT8 (8a8w) Conversion Runner for YOLOv8n-Seg
# ==============================================================================

echo "=========================================================="
echo "⚡ Starting ExecuTorch Qualcomm QNN HTP INT8 Build Pipeline"
echo "=========================================================="

# Auto-activate virtual environment if not already activated
if [ -z "$VIRTUAL_ENV" ] && [ -f "$HOME/qnn_env/bin/activate" ]; then
    source "$HOME/qnn_env/bin/activate"
    echo "✓ Activated virtual environment: $HOME/qnn_env"
fi

# 1. Detect QNN SDK Root
DEFAULT_CACHE_QNN="$HOME/.cache/executorch/qnn/sdk-2.37.0.250724"
if [ -d "$DEFAULT_CACHE_QNN" ]; then
    export QNN_SDK_ROOT="${QNN_SDK_ROOT:-$DEFAULT_CACHE_QNN}"
else
    export QNN_SDK_ROOT="${QNN_SDK_ROOT:-/opt/qcom/qnn-sdk}"
fi
echo "Using QNN_SDK_ROOT: $QNN_SDK_ROOT"

# 2. Configure Dynamic Linker Library Paths (QNN + LibCXX)
LIBCXX_DIR="$HOME/.cache/executorch/qnn/libcxx-14.0.0/clang+llvm-14.0.0-x86_64-linux-gnu-ubuntu-18.04/lib/x86_64-unknown-linux-gnu"

if [ -d "$QNN_SDK_ROOT/lib/x86_64-linux-clang" ]; then
    export LD_LIBRARY_PATH="$QNN_SDK_ROOT/lib/x86_64-linux-clang:$LIBCXX_DIR:$LD_LIBRARY_PATH"
    echo "✓ Configured LD_LIBRARY_PATH with QNN SDK and LibCXX"
else
    echo "⚠️ Warning: $QNN_SDK_ROOT/lib/x86_64-linux-clang not found."
fi

# 3. Check calibration dataset
if [ ! -d "calibration_data/preprocessed_tensors" ]; then
    echo "Running calibration data prep..."
    python prepare_calibration.py
fi

# 4. Execute conversion
echo -e "\n[ExecuTorch] Starting Quantization & HTP Lowering..."
python convert_yolov8_seg_qnn_pte.py \
    --weights best.pt \
    --calib-dir calibration_data \
    --soc SM8650 \
    --output yolov8n_seg_htp.pte \
    --outdir qnn_android_bundle

echo "=========================================================="
echo "✅ Conversion pipeline finished successfully!"
echo "Binary and target .so libraries located in: qnn_android_bundle/"
echo "=========================================================="
