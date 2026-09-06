"""
Multi-Class Wall Defect Dataset Preparation
=============================================
Downloads the Roboflow "building defect" dataset (5 classes: crack, mold,
peeling_paint, stairstep_crack, water_seepage) and merges it with the
existing crack detection dataset for a stronger combined model.

Trains YOLOv8n (detection) — NOT segmentation.

Usage:
  # Option 1: Download via Roboflow API (needs free API key)
  python data/prepare_multiclass.py --api-key YOUR_KEY

  # Option 2: If you manually downloaded the ZIP from Roboflow
  python data/prepare_multiclass.py --zip-path path/to/download.zip

  # Option 3: Just merge existing datasets (if already downloaded)
  python data/prepare_multiclass.py --skip-download
"""

import os
import sys
import shutil
import random
import yaml
from pathlib import Path
from collections import defaultdict

sys.path.insert(0, str(Path(__file__).parent.parent))

# ─── Configuration ───────────────────────────────────
PROJECT_ROOT = Path(__file__).parent.parent
DATASETS_DIR = PROJECT_ROOT / "datasets"
ROBOFLOW_DIR = DATASETS_DIR / "building-defect-roboflow"
EXISTING_CRACK_DIR = DATASETS_DIR / "wall-defects"
MERGED_DIR = DATASETS_DIR / "wall-defects-multiclass"

# Unified class list — ORDER MATTERS (index = class_id)
UNIFIED_CLASSES = ["crack", "mold", "peeling_paint", "stairstep_crack", "water_seepage"]

# Roboflow dataset info
ROBOFLOW_WORKSPACE = "builddef2"
ROBOFLOW_PROJECT = "building-defect-mmjsi"
ROBOFLOW_VERSION = 3  # v3 has 702 augmented images

VALID_IMG_EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def download_from_roboflow(api_key: str):
    """Download dataset using Roboflow API."""
    try:
        from roboflow import Roboflow
    except ImportError:
        print("Installing roboflow package...")
        os.system(f"{sys.executable} -m pip install -q roboflow")
        from roboflow import Roboflow

    print(f"📥 Downloading from Roboflow: {ROBOFLOW_WORKSPACE}/{ROBOFLOW_PROJECT} v{ROBOFLOW_VERSION}")
    rf = Roboflow(api_key=api_key)
    project = rf.workspace(ROBOFLOW_WORKSPACE).project(ROBOFLOW_PROJECT)
    version = project.version(ROBOFLOW_VERSION)
    dataset = version.download("yolov8", location=str(ROBOFLOW_DIR))
    print(f"✅ Downloaded to: {ROBOFLOW_DIR}")
    return Path(dataset.location)


def extract_from_zip(zip_path: str):
    """Extract a manually downloaded Roboflow ZIP."""
    import zipfile
    zip_path = Path(zip_path)
    if not zip_path.exists():
        print(f"❌ ZIP file not found: {zip_path}")
        sys.exit(1)

    if ROBOFLOW_DIR.exists():
        shutil.rmtree(ROBOFLOW_DIR)
    ROBOFLOW_DIR.mkdir(parents=True, exist_ok=True)

    print(f"📦 Extracting {zip_path}...")
    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(str(ROBOFLOW_DIR))
    print(f"✅ Extracted to: {ROBOFLOW_DIR}")


def find_data_yaml(base_dir: Path) -> Path:
    """Find data.yaml in the dataset directory."""
    for candidate in [
        base_dir / "data.yaml",
        base_dir / "dataset.yaml",
    ]:
        if candidate.exists():
            return candidate
    # Search recursively
    for match in base_dir.rglob("data.yaml"):
        return match
    for match in base_dir.rglob("*.yaml"):
        return match
    return None


def read_roboflow_classes(data_yaml_path: Path) -> list:
    """Read class names from Roboflow's data.yaml."""
    with open(data_yaml_path, "r") as f:
        data = yaml.safe_load(f)
    names = data.get("names", [])
    if isinstance(names, dict):
        names = [names[k] for k in sorted(names.keys())]
    print(f"📋 Roboflow classes: {names}")
    return names


def build_class_remap(roboflow_classes: list) -> dict:
    """
    Build mapping from Roboflow class_id → unified class_id.
    If a Roboflow class matches our unified list, map it. Otherwise skip.
    """
    remap = {}
    for rf_id, rf_name in enumerate(roboflow_classes):
        rf_name_lower = rf_name.lower().strip()
        for unified_id, unified_name in enumerate(UNIFIED_CLASSES):
            if rf_name_lower == unified_name.lower():
                remap[rf_id] = unified_id
                break
        else:
            # Try fuzzy matching
            for unified_id, unified_name in enumerate(UNIFIED_CLASSES):
                if rf_name_lower in unified_name.lower() or unified_name.lower() in rf_name_lower:
                    remap[rf_id] = unified_id
                    break
            else:
                print(f"  ⚠️  No match for Roboflow class '{rf_name}' (id={rf_id}) — will keep as-is")
                # Add it to unified classes
                UNIFIED_CLASSES.append(rf_name)
                remap[rf_id] = len(UNIFIED_CLASSES) - 1

    print(f"📋 Class remapping: {remap}")
    return remap


def remap_labels(src_dir: Path, dst_dir: Path, class_remap: dict):
    """Copy images and remap label class IDs."""
    img_dir = src_dir / "images"
    lbl_dir = src_dir / "labels"

    dst_img = dst_dir / "images"
    dst_lbl = dst_dir / "labels"
    dst_img.mkdir(parents=True, exist_ok=True)
    dst_lbl.mkdir(parents=True, exist_ok=True)

    if not img_dir.exists():
        return 0

    count = 0
    for img_file in img_dir.iterdir():
        if img_file.suffix.lower() not in VALID_IMG_EXT:
            continue

        # Copy image
        shutil.copy2(img_file, dst_img / img_file.name)
        count += 1

        # Remap label
        lbl_file = lbl_dir / (img_file.stem + ".txt")
        dst_lbl_file = dst_lbl / (img_file.stem + ".txt")

        if lbl_file.exists():
            with open(lbl_file, "r") as f:
                lines = f.readlines()

            remapped = []
            for line in lines:
                parts = line.strip().split()
                if len(parts) >= 5:
                    old_id = int(parts[0])
                    if old_id in class_remap:
                        parts[0] = str(class_remap[old_id])
                    # Keep only first 5 values (class cx cy w h) for detection
                    remapped.append(" ".join(parts[:5]) + "\n")

            with open(dst_lbl_file, "w") as f:
                f.writelines(remapped)
        else:
            dst_lbl_file.touch()

    return count


def convert_seg_labels_to_det(src_dir: Path, dst_dir: Path, class_id: int = 0):
    """
    Convert YOLOv8-Seg polygon labels to YOLOv8 detection bbox labels.
    Extracts bounding box from polygon vertices.
    """
    img_dir = src_dir / "images"
    lbl_dir = src_dir / "labels"

    dst_img = dst_dir / "images"
    dst_lbl = dst_dir / "labels"
    dst_img.mkdir(parents=True, exist_ok=True)
    dst_lbl.mkdir(parents=True, exist_ok=True)

    if not img_dir.exists():
        return 0

    count = 0
    for img_file in img_dir.iterdir():
        if img_file.suffix.lower() not in VALID_IMG_EXT:
            continue

        # Copy image with prefix to avoid name collision
        new_name = f"crack_{img_file.name}"
        shutil.copy2(img_file, dst_img / new_name)
        count += 1

        # Convert segmentation label to detection label
        lbl_file = lbl_dir / (img_file.stem + ".txt")
        dst_lbl_file = dst_lbl / f"crack_{img_file.stem}.txt"

        if lbl_file.exists():
            with open(lbl_file, "r") as f:
                lines = f.readlines()

            det_lines = []
            for line in lines:
                parts = line.strip().split()
                if len(parts) < 5:
                    continue

                # parts[0] = class_id, rest are polygon coordinates (x1 y1 x2 y2 ...)
                coords = [float(p) for p in parts[1:]]

                if len(coords) == 4:
                    # Already bbox format: cx cy w h
                    cx, cy, w, h = coords
                else:
                    # Polygon format: x1 y1 x2 y2 x3 y3 ...
                    xs = coords[0::2]
                    ys = coords[1::2]
                    if not xs or not ys:
                        continue
                    x_min, x_max = min(xs), max(xs)
                    y_min, y_max = min(ys), max(ys)
                    cx = (x_min + x_max) / 2
                    cy = (y_min + y_max) / 2
                    w = x_max - x_min
                    h = y_max - y_min

                det_lines.append(f"{class_id} {cx:.6f} {cy:.6f} {w:.6f} {h:.6f}\n")

            with open(dst_lbl_file, "w") as f:
                f.writelines(det_lines)
        else:
            dst_lbl_file.touch()

    return count


def create_val_split(merged_dir: Path, val_ratio: float = 0.2):
    """
    If valid set is empty or too small, move some train images to valid.
    The Roboflow dataset has 100% train / 0% valid, so we need to fix this.
    """
    train_img = merged_dir / "train" / "images"
    train_lbl = merged_dir / "train" / "labels"
    valid_img = merged_dir / "valid" / "images"
    valid_lbl = merged_dir / "valid" / "labels"

    valid_img.mkdir(parents=True, exist_ok=True)
    valid_lbl.mkdir(parents=True, exist_ok=True)

    # Count existing valid images
    existing_valid = len([f for f in valid_img.iterdir() if f.suffix.lower() in VALID_IMG_EXT]) if valid_img.exists() else 0
    train_images = [f for f in train_img.iterdir() if f.suffix.lower() in VALID_IMG_EXT]
    total = len(train_images) + existing_valid

    needed_valid = int(total * val_ratio) - existing_valid
    if needed_valid <= 0:
        print(f"✅ Validation set already has {existing_valid} images — no split needed")
        return

    print(f"🔀 Moving {needed_valid} images from train → valid (creating {val_ratio:.0%} split)")

    random.seed(42)
    random.shuffle(train_images)
    to_move = train_images[:needed_valid]

    for img_file in to_move:
        # Move image
        shutil.move(str(img_file), str(valid_img / img_file.name))
        # Move label
        lbl_file = train_lbl / (img_file.stem + ".txt")
        if lbl_file.exists():
            shutil.move(str(lbl_file), str(valid_lbl / lbl_file.name))

    print(f"   Train: {len(train_images) - needed_valid} | Valid: {existing_valid + needed_valid}")


def merge_all():
    """Merge Roboflow + existing crack dataset into one unified dataset."""
    print(f"\n{'='*55}")
    print(f"🔀 MERGING DATASETS → {MERGED_DIR}")
    print(f"{'='*55}")

    if MERGED_DIR.exists():
        shutil.rmtree(MERGED_DIR)

    # --- Step 1: Process Roboflow dataset ---
    rf_yaml = find_data_yaml(ROBOFLOW_DIR)
    if rf_yaml:
        rf_classes = read_roboflow_classes(rf_yaml)
        class_remap = build_class_remap(rf_classes)

        for split in ["train", "valid", "test"]:
            src = ROBOFLOW_DIR / split
            if src.exists():
                dst = MERGED_DIR / split
                count = remap_labels(src, dst, class_remap)
                print(f"   Roboflow {split}: {count} images")
    else:
        print("⚠️  No Roboflow data.yaml found — skipping Roboflow data")

    # --- Step 2: Merge existing crack dataset ---
    if EXISTING_CRACK_DIR.exists():
        print(f"\n📋 Merging existing crack segmentation dataset...")
        print(f"   Converting seg labels → detection bboxes...")
        for split in ["train", "valid", "test"]:
            src = EXISTING_CRACK_DIR / split
            if src.exists():
                dst = MERGED_DIR / split
                count = convert_seg_labels_to_det(src, dst, class_id=0)
                print(f"   Crack {split}: {count} images (converted to bbox)")
    else:
        print(f"ℹ️  No existing crack dataset at {EXISTING_CRACK_DIR}")

    # --- Step 3: Create proper train/valid split ---
    create_val_split(MERGED_DIR, val_ratio=0.2)

    # --- Step 4: Generate data.yaml ---
    data_yaml_path = MERGED_DIR / "data.yaml"
    yaml_content = {
        "train": str((MERGED_DIR / "train" / "images").resolve()),
        "val": str((MERGED_DIR / "valid" / "images").resolve()),
        "test": str((MERGED_DIR / "test" / "images").resolve()),
        "nc": len(UNIFIED_CLASSES),
        "names": UNIFIED_CLASSES,
    }

    with open(data_yaml_path, "w") as f:
        yaml.dump(yaml_content, f, default_flow_style=False, sort_keys=False)

    # --- Step 5: Print final summary ---
    print(f"\n{'='*55}")
    print(f"✅ MERGED MULTI-CLASS DATASET READY")
    print(f"{'='*55}")

    total = 0
    for split in ["train", "valid", "test"]:
        img_dir = MERGED_DIR / split / "images"
        if img_dir.exists():
            n = len([f for f in img_dir.iterdir() if f.suffix.lower() in VALID_IMG_EXT])
            total += n
            print(f"   {split:6s}: {n:4d} images")
    print(f"   {'TOTAL':6s}: {total:4d} images")

    print(f"\n🏷️  Classes ({len(UNIFIED_CLASSES)}):")
    for i, name in enumerate(UNIFIED_CLASSES):
        print(f"   {i}: {name}")

    # Count labels per class
    class_counts = defaultdict(int)
    for split in ["train", "valid", "test"]:
        lbl_dir = MERGED_DIR / split / "labels"
        if not lbl_dir.exists():
            continue
        for lbl_file in lbl_dir.iterdir():
            if lbl_file.suffix != ".txt":
                continue
            with open(lbl_file, "r") as f:
                for line in f:
                    parts = line.strip().split()
                    if parts:
                        cid = int(parts[0])
                        if cid < len(UNIFIED_CLASSES):
                            class_counts[UNIFIED_CLASSES[cid]] += 1

    if class_counts:
        print(f"\n📊 Label distribution:")
        for name in UNIFIED_CLASSES:
            count = class_counts.get(name, 0)
            bar = "█" * min(40, count // 10)
            print(f"   {name:18s}: {count:5d} {bar}")

    print(f"\n📄 data.yaml: {data_yaml_path}")
    print(f"\n🚀 To train:")
    print(f"   python -m vision.train --data {data_yaml_path}")
    return data_yaml_path


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Multi-class wall defect dataset preparation")
    parser.add_argument("--api-key", type=str, help="Roboflow API key")
    parser.add_argument("--zip-path", type=str, help="Path to manually downloaded Roboflow ZIP")
    parser.add_argument("--skip-download", action="store_true", help="Skip download, only merge")
    args = parser.parse_args()

    print("=" * 55)
    print("🏗️  Multi-Class Wall Defect Dataset Pipeline")
    print("=" * 55)

    # Step 1: Get Roboflow data
    if not args.skip_download:
        if args.api_key:
            download_from_roboflow(args.api_key)
        elif args.zip_path:
            extract_from_zip(args.zip_path)
        else:
            # Check if already downloaded
            if ROBOFLOW_DIR.exists() and find_data_yaml(ROBOFLOW_DIR):
                print(f"✅ Roboflow data already exists at {ROBOFLOW_DIR}")
            else:
                print("❌ No download method specified!")
                print()
                print("Options:")
                print("  1. python data/prepare_multiclass.py --api-key YOUR_ROBOFLOW_KEY")
                print("  2. python data/prepare_multiclass.py --zip-path path/to/download.zip")
                print()
                print("To get a free API key:")
                print("  → Go to https://app.roboflow.com/settings/api")
                print("  → Sign up with Google/GitHub (free)")
                print("  → Copy your Private API Key")
                sys.exit(1)

    # Step 2: Merge everything
    merge_all()


if __name__ == "__main__":
    main()
