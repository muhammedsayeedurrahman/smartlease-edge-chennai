import os
import shutil
import random
from pathlib import Path
import cv2
import numpy as np

def prepare_calibration_dataset(num_samples=50):
    random.seed(42)
    workspace = Path(".")
    
    # Look for validation or test images across sub-datasets
    candidates = list(workspace.glob("**/valid/images/*.jpg")) + \
                 list(workspace.glob("**/valid/images/*.png")) + \
                 list(workspace.glob("**/test/images/*.jpg")) + \
                 list(workspace.glob("**/test/images/*.png"))
                 
    if not candidates:
        candidates = list(workspace.glob("**/images/*.jpg"))
        
    selected = random.sample(candidates, min(num_samples, len(candidates)))
    
    calib_raw_dir = workspace / "calibration_data" / "raw_images"
    calib_tensors_dir = workspace / "calibration_data" / "preprocessed_tensors"
    calib_raw_dir.mkdir(parents=True, exist_ok=True)
    calib_tensors_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"Sampling {len(selected)} representative images for INT8 QNN calibration...")
    for idx, img_path in enumerate(selected):
        # 1. Save raw image
        raw_target = calib_raw_dir / f"calib_{idx:02d}.jpg"
        shutil.copy(img_path, raw_target)
        
        # 2. Preprocess: RGB, 640x640, normalized to [0.0, 1.0], shape (1, 3, 640, 640)
        img = cv2.imread(str(img_path))
        if img is None:
            continue
        img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        img_resized = cv2.resize(img_rgb, (640, 640), interpolation=cv2.INTER_LINEAR)
        img_norm = (img_resized.astype(np.float32) / 255.0).transpose(2, 0, 1) # CHW
        tensor_data = np.expand_dims(img_norm, axis=0) # 1x3x640x640
        
        # Save as npy for fast zero-overhead loading in quantization loop
        np.save(calib_tensors_dir / f"calib_{idx:02d}.npy", tensor_data)
        
    print(f"Calibration data successfully prepared:")
    print(f"  - Raw images: {calib_raw_dir}")
    print(f"  - Preprocessed tensors (1x3x640x640 float32 [0.0, 1.0]): {calib_tensors_dir}")

if __name__ == "__main__":
    prepare_calibration_dataset(50)
