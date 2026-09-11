"""
BD3 Dataset Downloader & Converter
===================================
Downloads the BD3 (Building Defects Detection Dataset) from GitHub,
converts classification-format images to YOLO-Seg format,
and merges with the existing crack segmentation dataset.

BD3 Classes → Our Classes:
  Algae       → mold      (2)
  Major Crack → crack     (0)  ← merges with existing crack dataset
  Minor Crack → crack     (0)
  Peeling     → peeling   (3)
  Spalling    → spalling  (4)
  Stain       → stain     (1)
  Normal      → SKIPPED   (no defect, not used)

Usage:
  python data/prepare_bd3.py                    # Download + convert + merge
  python data/prepare_bd3.py --skip-download    # Only convert + merge (if already cloned)
  python data/prepare_bd3.py --bd3-only         # Only prepare BD3, don't merge
"""

import os
import sys
import shutil
import random
import zipfile
import urllib.request
from pathlib import Path
from collections import defaultdict

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent.parent))

# ─── Configuration ───────────────────────────────────────────
PROJECT_ROOT = Path(__file__).parent.parent
DATASETS_DIR = PROJECT_ROOT / "datasets"

BD3_REPO_URL = "https://github.com/Praveenkottari/BD3-Dataset/archive/refs/heads/main.zip"
BD3_RAW_DIR = DATASETS_DIR / "bd3-raw"
BD3_YOLO_DIR = DATASETS_DIR / "bd3-yolo"

EXISTING_CRACK_DIR = DATASETS_DIR / "wall-defects"
MERGED_DIR = DATASETS_DIR / "wall-defects-merged"

# Class mapping: BD3 folder name → (our_class_id, our_class_name)
BD3_CLASS_MAP = {
    "Algae":       (2, "mold"),
    "Major Crack": (0, "crack"),
    "Minor Crack": (0, "crack"),
    "Peeling":     (3, "peeling"),
    "Spalling":    (4, "spalling"),
    "Stain":       (1, "stain"),
    "Normal":      None,  # Skip normal images
}

# Final unified class list (order matters — index = class_id)
UNIFIED_CLASSES = ["crack", "stain", "mold", "peeling", "spalling"]

# Train/Val/Test split ratios for BD3
SPLIT_RATIOS = {"train": 0.7, "valid": 0.2, "test": 0.1}

VALID_IMG_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def download_bd3():
    """Download and extract BD3 dataset from GitHub."""
    zip_path = DATASETS_DIR / "bd3-dataset.zip"
    
    if BD3_RAW_DIR.exists() and any(BD3_RAW_DIR.rglob("*.jpg")):
        print(f"✅ BD3 raw data already exists at {BD3_RAW_DIR}")
        return
    
    print(f"📥 Downloading BD3 dataset from GitHub...")
    DATASETS_DIR.mkdir(parents=True, exist_ok=True)
    
    # Download with progress
    def _progress(block_num, block_size, total_size):
        downloaded = block_num * block_size
        if total_size > 0:
            pct = min(100, downloaded * 100 / total_size)
            bar = "█" * int(pct // 2) + "░" * (50 - int(pct // 2))
            print(f"\r  [{bar}] {pct:.0f}% ({downloaded // 1024 // 1024}MB)", end="", flush=True)
    
    urllib.request.urlretrieve(BD3_REPO_URL, str(zip_path), reporthook=_progress)
    print()  # newline after progress bar
    
    print(f"📦 Extracting...")
    with zipfile.ZipFile(zip_path, 'r') as zf:
        zf.extractall(str(DATASETS_DIR))
    
    # Find the extracted folder (usually BD3-Dataset-main)
    extracted = DATASETS_DIR / "BD3-Dataset-main"
    if extracted.exists():
        if BD3_RAW_DIR.exists():
            shutil.rmtree(BD3_RAW_DIR)
        shutil.move(str(extracted), str(BD3_RAW_DIR))
    
    # Clean up zip
    zip_path.unlink(missing_ok=True)
    print(f"✅ BD3 extracted to {BD3_RAW_DIR}")


def find_bd3_class_folders():
    """Locate the class folders in the BD3 dataset."""
    # BD3 structure: sample images/class_images/<ClassName>/
    candidates = [
        BD3_RAW_DIR / "sample images" / "class_images",
        BD3_RAW_DIR / "sample_images" / "class_images",
        BD3_RAW_DIR / "class_images",
        BD3_RAW_DIR / "sample images",
        BD3_RAW_DIR,
    ]
    
    for base in candidates:
        if not base.exists():
            continue
        # Check if this directory has subdirectories matching our class names
        subdirs = [d.name for d in base.iterdir() if d.is_dir()]
        matched = sum(1 for s in subdirs if s in BD3_CLASS_MAP)
        if matched >= 3:  # At least 3 class folders found
            print(f"📁 Found BD3 class folders at: {base}")
            print(f"   Classes found: {[s for s in subdirs if s in BD3_CLASS_MAP]}")
            return base
    
    # Fallback: search recursively for any folder named "Algae" or "Stain"
    for marker in ["Algae", "Stain", "Peeling", "Spalling"]:
        for match in BD3_RAW_DIR.rglob(marker):
            if match.is_dir():
                parent = match.parent
                subdirs = [d.name for d in parent.iterdir() if d.is_dir()]
                matched = sum(1 for s in subdirs if s in BD3_CLASS_MAP)
                if matched >= 3:
                    print(f"📁 Found BD3 class folders at: {parent}")
                    return parent
    
    print("❌ Could not find BD3 class folders!")
    print(f"   Searched in: {BD3_RAW_DIR}")
    print(f"   Expected folders named: {list(BD3_CLASS_MAP.keys())}")
    sys.exit(1)


def convert_bd3_to_yolo():
    """Convert BD3 classification images to YOLO-Seg format."""
    class_base = find_bd3_class_folders()
    
    # Clean output directory
    if BD3_YOLO_DIR.exists():
        shutil.rmtree(BD3_YOLO_DIR)
    
    # Collect all images per class
    all_images = defaultdict(list)  # class_id → [(src_path, class_id)]
    class_counts = defaultdict(int)
    
    for bd3_folder_name, mapping in BD3_CLASS_MAP.items():
        if mapping is None:
            continue  # Skip "Normal"
        
        class_id, class_name = mapping
        folder = class_base / bd3_folder_name
        
        if not folder.exists():
            print(f"  ⚠️  Folder not found: {folder} — skipping {bd3_folder_name}")
            continue
        
        images = [
            f for f in folder.iterdir()
            if f.is_file() and f.suffix.lower() in VALID_IMG_EXTENSIONS
        ]
        
        for img_path in images:
            all_images[class_id].append((img_path, class_id, class_name))
            class_counts[class_name] += 1
    
    total = sum(class_counts.values())
    print(f"\n📊 BD3 Image counts per class:")
    for name, count in sorted(class_counts.items()):
        print(f"   {name}: {count}")
    print(f"   TOTAL: {total}")
    
    if total == 0:
        print("❌ No images found! Check BD3 dataset structure.")
        sys.exit(1)
    
    # Split into train/val/test
    splits = {"train": [], "valid": [], "test": []}
    
    for class_id, items in all_images.items():
        random.seed(42)
        random.shuffle(items)
        n = len(items)
        n_train = int(n * SPLIT_RATIOS["train"])
        n_valid = int(n * SPLIT_RATIOS["valid"])
        
        splits["train"].extend(items[:n_train])
        splits["valid"].extend(items[n_train:n_train + n_valid])
        splits["test"].extend(items[n_train + n_valid:])
    
    # Write YOLO format
    for split_name, items in splits.items():
        img_dir = BD3_YOLO_DIR / split_name / "images"
        lbl_dir = BD3_YOLO_DIR / split_name / "labels"
        img_dir.mkdir(parents=True, exist_ok=True)
        lbl_dir.mkdir(parents=True, exist_ok=True)
        
        for i, (src_path, class_id, class_name) in enumerate(items):
            # Copy image with unique name to avoid collisions
            new_name = f"bd3_{class_name}_{i:04d}{src_path.suffix.lower()}"
            dst_img = img_dir / new_name
            shutil.copy2(src_path, dst_img)
            
            # Create YOLO-Seg label (full-image polygon)
            # Format: class_id x1 y1 x2 y2 x3 y3 x4 y4
            # Full image rectangle: top-left, top-right, bottom-right, bottom-left
            # But we use a small margin (5%) to avoid edge artifacts
            m = 0.02  # 2% margin
            label_line = f"{class_id} {m:.4f} {m:.4f} {1-m:.4f} {m:.4f} {1-m:.4f} {1-m:.4f} {m:.4f} {1-m:.4f}\n"
            
            lbl_path = lbl_dir / f"{new_name.rsplit('.', 1)[0]}.txt"
            with open(lbl_path, "w") as f:
                f.write(label_line)
    
    print(f"\n✅ BD3 converted to YOLO-Seg format:")
    print(f"   Train: {len(splits['train'])} | Valid: {len(splits['valid'])} | Test: {len(splits['test'])}")
    print(f"   Output: {BD3_YOLO_DIR}")


def remap_existing_crack_labels(src_dir, dst_dir, old_classes, new_class_id=0):
    """
    Copy existing crack dataset and remap all class IDs to 0 (crack).
    The existing dataset has classes ['crack', 'crack'] with nc=2,
    which is a bug — both class 0 and 1 should map to our class 0.
    """
    for split in ["train", "valid", "test"]:
        src_img_dir = src_dir / split / "images"
        src_lbl_dir = src_dir / split / "labels"
        dst_img_dir = dst_dir / split / "images"
        dst_lbl_dir = dst_dir / split / "labels"
        
        if not src_img_dir.exists():
            continue
        
        dst_img_dir.mkdir(parents=True, exist_ok=True)
        dst_lbl_dir.mkdir(parents=True, exist_ok=True)
        
        # Copy images
        img_count = 0
        for img_file in src_img_dir.iterdir():
            if img_file.suffix.lower() in VALID_IMG_EXTENSIONS:
                shutil.copy2(img_file, dst_img_dir / img_file.name)
                img_count += 1
                
                # Copy and remap labels
                lbl_name = img_file.stem + ".txt"
                src_lbl = src_lbl_dir / lbl_name
                dst_lbl = dst_lbl_dir / lbl_name
                
                if src_lbl.exists():
                    with open(src_lbl, "r") as f:
                        lines = f.readlines()
                    
                    remapped = []
                    for line in lines:
                        parts = line.strip().split()
                        if len(parts) >= 5:
                            # Replace class ID with 0 (crack)
                            parts[0] = str(new_class_id)
                            remapped.append(" ".join(parts) + "\n")
                    
                    with open(dst_lbl, "w") as f:
                        f.writelines(remapped)
                else:
                    # Create empty label file (no detections)
                    dst_lbl.touch()
        
        print(f"   {split}: copied {img_count} crack images")


def merge_datasets():
    """Merge existing crack dataset with BD3 YOLO dataset."""
    print(f"\n🔀 Merging datasets...")
    
    # Clean merged directory
    if MERGED_DIR.exists():
        shutil.rmtree(MERGED_DIR)
    
    # Step 1: Copy and remap existing crack dataset
    if EXISTING_CRACK_DIR.exists():
        print(f"\n📋 Copying existing crack dataset (remapping class IDs to 0)...")
        remap_existing_crack_labels(EXISTING_CRACK_DIR, MERGED_DIR, old_classes=["crack", "crack"])
    else:
        print(f"⚠️  No existing crack dataset at {EXISTING_CRACK_DIR} — using BD3 only")
    
    # Step 2: Copy BD3 YOLO data into merged directory
    if BD3_YOLO_DIR.exists():
        print(f"\n📋 Merging BD3 data...")
        for split in ["train", "valid", "test"]:
            bd3_img_dir = BD3_YOLO_DIR / split / "images"
            bd3_lbl_dir = BD3_YOLO_DIR / split / "labels"
            dst_img_dir = MERGED_DIR / split / "images"
            dst_lbl_dir = MERGED_DIR / split / "labels"
            
            if not bd3_img_dir.exists():
                continue
            
            dst_img_dir.mkdir(parents=True, exist_ok=True)
            dst_lbl_dir.mkdir(parents=True, exist_ok=True)
            
            img_count = 0
            for img_file in bd3_img_dir.iterdir():
                if img_file.suffix.lower() in VALID_IMG_EXTENSIONS:
                    shutil.copy2(img_file, dst_img_dir / img_file.name)
                    img_count += 1
            
            for lbl_file in bd3_lbl_dir.iterdir():
                if lbl_file.suffix == ".txt":
                    shutil.copy2(lbl_file, dst_lbl_dir / lbl_file.name)
            
            print(f"   {split}: added {img_count} BD3 images")
    
    # Step 3: Generate data.yaml
    data_yaml = MERGED_DIR / "data.yaml"
    yaml_content = f"""# SmartLease Edge — Multi-Class Wall Defect Dataset
# Merged: Roboflow Crack Segmentation + BD3 Building Defects
# Total classes: {len(UNIFIED_CLASSES)}

train: {str(MERGED_DIR / 'train' / 'images')}
val: {str(MERGED_DIR / 'valid' / 'images')}
test: {str(MERGED_DIR / 'test' / 'images')}

nc: {len(UNIFIED_CLASSES)}
names:
"""
    for cls_name in UNIFIED_CLASSES:
        yaml_content += f"  - {cls_name}\n"
    
    with open(data_yaml, "w") as f:
        f.write(yaml_content)
    
    # Step 4: Print final stats
    print(f"\n{'='*50}")
    print(f"✅ MERGED DATASET READY")
    print(f"{'='*50}")
    print(f"📁 Location: {MERGED_DIR}")
    print(f"📄 Config:   {data_yaml}")
    print(f"\n📊 Final stats:")
    
    total_all = 0
    for split in ["train", "valid", "test"]:
        img_dir = MERGED_DIR / split / "images"
        if img_dir.exists():
            count = len([f for f in img_dir.iterdir() if f.suffix.lower() in VALID_IMG_EXTENSIONS])
            total_all += count
            print(f"   {split}: {count} images")
    print(f"   TOTAL: {total_all} images")
    
    print(f"\n🏷️  Classes ({len(UNIFIED_CLASSES)}):")
    for i, name in enumerate(UNIFIED_CLASSES):
        print(f"   {i}: {name}")
    
    print(f"\n🚀 To train, update your notebook/script to use:")
    print(f"   data='{data_yaml}'")
    
    return data_yaml


def main():
    import argparse
    parser = argparse.ArgumentParser(description="BD3 Dataset Preparation")
    parser.add_argument("--skip-download", action="store_true", help="Skip downloading BD3")
    parser.add_argument("--bd3-only", action="store_true", help="Only prepare BD3, don't merge")
    args = parser.parse_args()
    
    print("=" * 50)
    print("🏗️  BD3 Dataset Preparation Pipeline")
    print("=" * 50)
    
    # Step 1: Download
    if not args.skip_download:
        download_bd3()
    
    # Step 2: Convert BD3 to YOLO format
    convert_bd3_to_yolo()
    
    # Step 3: Merge (optional)
    if not args.bd3_only:
        merge_datasets()
    else:
        # Generate standalone data.yaml for BD3
        data_yaml = BD3_YOLO_DIR / "data.yaml"
        yaml_content = f"""train: {str(BD3_YOLO_DIR / 'train' / 'images')}
val: {str(BD3_YOLO_DIR / 'valid' / 'images')}
test: {str(BD3_YOLO_DIR / 'test' / 'images')}
nc: {len(UNIFIED_CLASSES)}
names:
"""
        for cls_name in UNIFIED_CLASSES:
            yaml_content += f"  - {cls_name}\n"
        with open(data_yaml, "w") as f:
            f.write(yaml_content)
        print(f"\n✅ BD3-only dataset ready at: {BD3_YOLO_DIR}")


if __name__ == "__main__":
    main()
