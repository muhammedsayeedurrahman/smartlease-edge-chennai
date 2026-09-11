"""
SmartLease Edge — Dataset Download & Preparation
Downloads the Wall Defects segmentation dataset from Roboflow
and prepares it for YOLOv8-Seg training.
"""
import os
import sys
import shutil
import zipfile
import requests
import yaml
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
from config import DATASETS_DIR, DATA_DIR, VISION


ROBOFLOW_DATASETS = [
    {
        "name": "Wall Defects (Peumalab)",
        "url_template": "https://universe.roboflow.com/ds/{key}?key={api_key}",
        "instructions": (
            "1. Go to https://universe.roboflow.com/peumalab/wall-defects\n"
            "2. Click 'Download Dataset'\n"
            "3. Select format: 'YOLOv8'\n"
            "4. Select 'download zip to computer'\n"
            "5. Extract to: datasets/wall-defects/\n"
        ),
    },
]


def setup_dataset_from_zip(zip_path: str, dataset_name: str = "wall-defects") -> Path:
    """Extract a downloaded Roboflow zip and validate the structure."""
    dest = DATASETS_DIR / dataset_name
    dest.mkdir(parents=True, exist_ok=True)

    print(f"Extracting {zip_path} → {dest}")
    with zipfile.ZipFile(zip_path, 'r') as zf:
        zf.extractall(dest)

    # Check if this is a COCO format dataset (_annotations.coco.json)
    coco_files = list(dest.rglob("_annotations.coco.json"))
    if coco_files:
        print(f"📦 Detected COCO Segmentation dataset with {len(coco_files)} split(s). Converting to YOLOv8-Seg format...")
        _convert_coco_to_yolo_seg(dest)

    # Find data.yaml
    yaml_path = dest / "data.yaml"
    if not yaml_path.exists():
        # Sometimes it's nested one level
        for p in dest.rglob("data.yaml"):
            yaml_path = p
            break

    if yaml_path.exists():
        print(f"✅ Found data.yaml at {yaml_path}")
        with open(yaml_path) as f:
            cfg = yaml.safe_load(f)
        print(f"   Classes: {cfg.get('names', 'unknown')}")
        print(f"   Train path: {cfg.get('train', 'N/A')}")
        print(f"   Val path: {cfg.get('val', 'N/A')}")

        # Fix relative paths to be absolute
        for key in ['train', 'val', 'test']:
            if key in cfg and not os.path.isabs(cfg[key]):
                cfg[key] = str((yaml_path.parent / cfg[key]).resolve())

        with open(yaml_path, 'w') as f:
            yaml.dump(cfg, f, default_flow_style=False)
        print("   ✅ Paths updated to absolute")
    else:
        print("⚠️  data.yaml not found — check extraction structure")

    # Count images
    for split in ['train', 'valid', 'val', 'test']:
        img_dir = dest / split / "images"
        if img_dir.exists():
            count = len(list(img_dir.iterdir()))
            print(f"   {split}: {count} images")

    return dest


def _convert_coco_to_yolo_seg(dest: Path):
    """Convert COCO instance segmentation JSON annotations to YOLOv8-Seg polygon format."""
    import json
    all_categories = {}

    for split_dir in [dest / "train", dest / "valid", dest / "val", dest / "test"]:
        if not split_dir.exists():
            continue

        coco_file = split_dir / "_annotations.coco.json"
        if not coco_file.exists():
            # Check nested
            found = list(split_dir.rglob("_annotations.coco.json"))
            if found:
                coco_file = found[0]
            else:
                continue

        with open(coco_file, "r", encoding="utf-8") as f:
            coco_data = json.load(f)

        # Collect categories
        cat_id_to_idx = {}
        for idx, cat in enumerate(coco_data.get("categories", [])):
            cat_id_to_idx[cat["id"]] = idx
            all_categories[idx] = cat["name"]

        images_dir = split_dir / "images"
        labels_dir = split_dir / "labels"
        images_dir.mkdir(parents=True, exist_ok=True)
        labels_dir.mkdir(parents=True, exist_ok=True)

        # Move image files into images_dir if they are in split_dir root
        image_meta = {img["id"]: img for img in coco_data.get("images", [])}
        for img_id, img_info in image_meta.items():
            fname = img_info["file_name"]
            src_img = coco_file.parent / fname
            if src_img.exists() and src_img != (images_dir / fname):
                shutil.move(str(src_img), str(images_dir / fname))

        # Group annotations by image
        img_annos = {}
        for ann in coco_data.get("annotations", []):
            img_annos.setdefault(ann["image_id"], []).append(ann)

        # Write YOLO segmentation labels
        for img_id, img_info in image_meta.items():
            w, h = max(1, img_info.get("width", 640)), max(1, img_info.get("height", 640))
            fname = img_info["file_name"]
            txt_name = Path(fname).stem + ".txt"
            label_lines = []

            for ann in img_annos.get(img_id, []):
                cat_idx = cat_id_to_idx.get(ann.get("category_id", 0), 0)
                seg = ann.get("segmentation", [])

                has_poly = False
                if isinstance(seg, list) and len(seg) > 0:
                    for poly in seg:
                        if isinstance(poly, list) and len(poly) >= 6:
                            norm_pts = []
                            for i in range(0, len(poly), 2):
                                px = min(1.0, max(0.0, poly[i] / w))
                                py = min(1.0, max(0.0, poly[i + 1] / h))
                                norm_pts.extend([f"{px:.6f}", f"{py:.6f}"])
                            label_lines.append(f"{cat_idx} " + " ".join(norm_pts))
                            has_poly = True

                # Fallback to bbox if no segmentation polygon
                if not has_poly and "bbox" in ann:
                    bx, by, bw, bh = ann["bbox"]
                    x1, y1 = max(0.0, bx) / w, max(0.0, by) / h
                    x2, y2 = min(float(w), bx + bw) / w, max(0.0, by) / h
                    x3, y3 = min(float(w), bx + bw) / w, min(float(h), by + bh) / h
                    x4, y4 = max(0.0, bx) / w, min(float(h), by + bh) / h
                    label_lines.append(f"{cat_idx} {x1:.6f} {y1:.6f} {x2:.6f} {y2:.6f} {x3:.6f} {y3:.6f} {x4:.6f} {y4:.6f}")

            with open(labels_dir / txt_name, "w", encoding="utf-8") as f:
                f.write("\n".join(label_lines) + "\n")

    # Generate data.yaml
    names_list = [all_categories[i] for i in sorted(all_categories.keys())] if all_categories else ["crack"]
    val_split = "valid" if (dest / "valid").exists() else ("val" if (dest / "val").exists() else "train")

    yaml_data = {
        "train": str((dest / "train" / "images").resolve()),
        "val": str((dest / val_split / "images").resolve()),
        "nc": len(names_list),
        "names": names_list,
    }
    test_dir = dest / "test" / "images"
    if test_dir.exists():
        yaml_data["test"] = str(test_dir.resolve())

    with open(dest / "data.yaml", "w", encoding="utf-8") as f:
        yaml.dump(yaml_data, f, default_flow_style=False)

    print(f"✨ Successfully converted to YOLOv8-Seg! Generated: {dest / 'data.yaml'}")
    print(f"   Classes: {names_list}")


def process_extracted_dataset(src_path: str = None) -> Path:
    """Process an already-extracted dataset directory and convert to YOLOv8-Seg."""
    if src_path is None:
        candidates = [
            DATA_DIR / "dataset" / "wall-defects",
            DATASETS_DIR / "wall-defects",
            DATA_DIR / "wall-defects",
        ]
        for c in candidates:
            if c.exists() and any(c.iterdir()):
                src_path = str(c)
                break

    if src_path is None or not Path(src_path).exists():
        print("❌ Could not find extracted dataset directory.")
        return None

    src = Path(src_path).resolve()
    dest = DATASETS_DIR / "wall-defects"
    dest.mkdir(parents=True, exist_ok=True)

    print(f"Processing extracted dataset from: {src}")
    if src != dest:
        for item in src.iterdir():
            target = dest / item.name
            if not target.exists():
                print(f"  Copying {item.name} → {target}")
                if item.is_dir():
                    shutil.copytree(str(item), str(target), dirs_exist_ok=True)
                else:
                    shutil.copy2(str(item), str(target))

    # Convert if COCO format
    coco_files = list(dest.rglob("_annotations.coco.json"))
    if coco_files:
        print(f"📦 Detected COCO annotations. Converting to YOLOv8-Seg format...")
        _convert_coco_to_yolo_seg(dest)

    # Count images
    for split in ['train', 'valid', 'val', 'test']:
        img_dir = dest / split / "images"
        if img_dir.exists():
            count = len(list(img_dir.iterdir()))
            lbl_dir = dest / split / "labels"
            lbl_count = len(list(lbl_dir.iterdir())) if lbl_dir.exists() else 0
            print(f"   {split}: {count} images, {lbl_count} label files")

    print(f"\n✅ Dataset is ready for training at: {dest / 'data.yaml'}")
    return dest




def create_sample_dataset() -> Path:
    """
    Create a minimal sample dataset for testing the pipeline.
    Uses placeholder images — replace with real data before real training.
    """
    import numpy as np
    from PIL import Image

    dest = DATASETS_DIR / "sample-defects"
    for split in ['train', 'valid']:
        (dest / split / "images").mkdir(parents=True, exist_ok=True)
        (dest / split / "labels").mkdir(parents=True, exist_ok=True)

    # Generate 20 dummy images with simple synthetic "cracks"
    print("Generating sample dataset (20 train + 5 val)...")
    for split, count in [('train', 20), ('valid', 5)]:
        for i in range(count):
            # Random wall-like background
            img = np.random.randint(180, 220, (640, 640, 3), dtype=np.uint8)

            # Draw random dark lines as "cracks"
            num_cracks = np.random.randint(0, 4)
            label_lines = []

            for _ in range(num_cracks):
                # Random crack line
                points = []
                x, y = np.random.randint(100, 540), np.random.randint(100, 540)
                for _ in range(np.random.randint(4, 8)):
                    x += np.random.randint(-30, 30)
                    y += np.random.randint(-30, 30)
                    x = max(0, min(639, x))
                    y = max(0, min(639, y))
                    points.append((x, y))
                    # Draw on image
                    img[max(0, y-1):min(639, y+1), max(0, x-1):min(639, x+1)] = [40, 40, 40]

                # Create YOLO segmentation label (class x1 y1 x2 y2 ...)
                if len(points) >= 3:
                    normalized = []
                    for px, py in points:
                        normalized.extend([px / 640.0, py / 640.0])
                    label_lines.append(f"0 " + " ".join(f"{v:.6f}" for v in normalized))

            # Save image
            Image.fromarray(img).save(dest / split / "images" / f"sample_{i:04d}.jpg")

            # Save label
            with open(dest / split / "labels" / f"sample_{i:04d}.txt", 'w') as f:
                f.write("\n".join(label_lines))

    # Create data.yaml
    data_yaml = {
        "train": str((dest / "train" / "images").resolve()),
        "val": str((dest / "valid" / "images").resolve()),
        "nc": 1,
        "names": ["crack"],
    }
    yaml_path = dest / "data.yaml"
    with open(yaml_path, 'w') as f:
        yaml.dump(data_yaml, f, default_flow_style=False)

    print(f"✅ Sample dataset created at {dest}")
    print(f"   data.yaml: {yaml_path}")
    print(f"   ⚠️  This is synthetic data — replace with real images for actual training!")
    return dest


def verify_dataset(dataset_path: str) -> bool:
    """Verify that a dataset is properly formatted for YOLOv8-Seg training."""
    path = Path(dataset_path)

    checks = {
        "data.yaml exists": (path / "data.yaml").exists(),
        "train/images exists": (path / "train" / "images").exists(),
    }

    # Check for val or valid
    val_exists = (path / "val" / "images").exists() or (path / "valid" / "images").exists()
    checks["val/images exists"] = val_exists

    if (path / "data.yaml").exists():
        with open(path / "data.yaml") as f:
            cfg = yaml.safe_load(f)
        checks["has class names"] = "names" in cfg
        checks["has nc (num classes)"] = "nc" in cfg

    print("\nDataset verification:")
    all_ok = True
    for check, passed in checks.items():
        icon = "✅" if passed else "❌"
        print(f"  {icon} {check}")
        if not passed:
            all_ok = False

    return all_ok


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="SmartLease Edge Dataset Manager")
    parser.add_argument("--extract", type=str, help="Path to downloaded Roboflow zip file")
    parser.add_argument("--process", type=str, nargs="?", const="auto", help="Process already extracted dataset folder")
    parser.add_argument("--sample", action="store_true", help="Generate a sample dataset for pipeline testing")
    parser.add_argument("--verify", type=str, help="Verify a dataset directory")
    args = parser.parse_args()

    if args.extract:
        setup_dataset_from_zip(args.extract)
    elif args.process:
        src = None if args.process == "auto" else args.process
        process_extracted_dataset(src)
    elif args.sample:
        create_sample_dataset()
    elif args.verify:
        verify_dataset(args.verify)
    else:
        # Check if user already extracted into data/dataset/wall-defects
        auto_candidate = DATA_DIR / "dataset" / "wall-defects"
        if auto_candidate.exists() and any(auto_candidate.iterdir()):
            print("🔍 Found extracted dataset in data/dataset/wall-defects!")
            process_extracted_dataset(str(auto_candidate))
        else:
            print("SmartLease Edge — Dataset Manager")
            print("=" * 50)
            print("\nTo download the Wall Defects dataset:")
            for ds in ROBOFLOW_DATASETS:
                print(f"\n  {ds['name']}:")
                print(f"  {ds['instructions']}")
            print("\nAfter downloading, run:")
            print("  python data/download_dataset.py --extract path/to/download.zip")
            print("\nOr process extracted folder:")
            print("  python data/download_dataset.py --process")
            print("\nOr generate a sample dataset for testing:")
            print("  python data/download_dataset.py --sample")

        print("  python data/download_dataset.py --sample")
