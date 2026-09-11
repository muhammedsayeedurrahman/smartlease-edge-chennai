"""
SmartLease Edge - Dataset Merger & Zipping Tool
Merges datasets 1, 2, and 3 into a single unified 4-class instance segmentation dataset:
  0: crack
  1: peeling
  2: spalling (concrete damage / holes)
  3: stain_mould (dampness / moisture / efflorescence)
And packages it into dataset.zip ready for Google Colab.
"""

import os
import shutil
import zipfile
from pathlib import Path
import yaml
from collections import Counter

# Class taxonomy mapping
MAPPING_1 = {
    3: 0,   # 'Crack' -> crack
    7: 1,   # 'Paint Peeling' -> peeling
    8: 1,   # 'Scaling' -> peeling
    9: 2,   # 'Spalling' -> spalling
    10: 2,  # 'exposed brickwork' -> spalling
    5: 2,   # 'Incomplete Plaster Work' -> spalling
    2: 3,   # 'Corrosion stain' -> stain_mould
    6: 3,   # 'Moss growth due to damping' -> stain_mould
    11: 3,  # 'pollution -moisture induced blackening' -> stain_mould
    1: 3,   # 'Corrosion' -> stain_mould
}

MAPPING_2 = {
    0: 0,   # 'mildcrack' -> crack
    1: 0,   # 'severecrack' -> crack
    2: 2,   # 'spall' -> spalling
}

MAPPING_3 = {
    0: 1,   # 'Paint peeling' -> peeling
    1: 2,   # 'Spalling' -> spalling
    3: 0,   # 'crack' -> crack
    4: 3,   # 'dampness' -> stain_mould
    5: 3,   # 'efflorescence' -> stain_mould
    2: 3,   # 'corrosion' -> stain_mould
}

def merge_datasets(root_dir=".", output_dir="unified_defects"):
    root = Path(root_dir)
    out = Path(output_dir)
    
    for split in ["train", "valid", "test"]:
        (out / split / "images").mkdir(parents=True, exist_ok=True)
        (out / split / "labels").mkdir(parents=True, exist_ok=True)
        
    print("=" * 60)
    print("MERGING DATASETS 1, 2, 3 INTO UNIFIED SEGMENTATION DATASET")
    print("=" * 60)
    
    datasets = [
        ("1", root / "1", MAPPING_1),
        ("2", root / "2", MAPPING_2),
        ("3", root / "3", MAPPING_3)
    ]
    
    stats = Counter()
    total_copied = 0
    
    for d_name, d_path, mapping in datasets:
        if not d_path.exists():
            print(f"Warning: {d_path} not found, skipping.")
            continue
            
        print(f"\nProcessing Dataset {d_name} ({d_path})...")
        d_copied = 0
        
        for split in ["train", "valid", "test"]:
            img_dir = d_path / split / "images"
            lbl_dir = d_path / split / "labels"
            
            if not img_dir.exists():
                continue
                
            for img_p in list(img_dir.glob("*.jpg")) + list(img_dir.glob("*.jpeg")) + list(img_dir.glob("*.png")):
                lbl_p = lbl_dir / (img_p.stem + ".txt")
                if not lbl_p.exists():
                    continue
                    
                # Read and remap label
                remapped_lines = []
                with open(lbl_p, "r", encoding="utf-8", errors="ignore") as f:
                    for line in f:
                        parts = line.strip().split()
                        if parts:
                            old_cid = int(parts[0])
                            if old_cid in mapping:
                                new_cid = mapping[old_cid]
                                remapped_lines.append(f"{new_cid} {' '.join(parts[1:])}\n")
                                stats[new_cid] += 1
                                
                if remapped_lines:
                    # Prefix filename with dataset name to avoid collisions
                    target_img_name = f"d{d_name}_{img_p.name}"
                    target_lbl_name = f"d{d_name}_{img_p.stem}.txt"
                    
                    shutil.copy(img_p, out / split / "images" / target_img_name)
                    with open(out / split / "labels" / target_lbl_name, "w", encoding="utf-8") as f_out:
                        f_out.writelines(remapped_lines)
                        
                    d_copied += 1
                    total_copied += 1
                    
        print(f"  -> Converted {d_copied} images from Dataset {d_name}")
        
    # Write unified data.yaml
    class_names = {
        0: "crack",
        1: "peeling",
        2: "spalling",
        3: "stain_mould"
    }
    
    yaml_data = {
        "path": str(out.resolve()),
        "train": "train/images",
        "val": "valid/images",
        "test": "test/images",
        "names": class_names
    }
    
    yaml_path = out / "data.yaml"
    with open(yaml_path, "w", encoding="utf-8") as f:
        yaml.dump(yaml_data, f, default_flow_style=False)
        
    print("\n" + "=" * 60)
    print("UNIFIED DATASET STATS")
    print("=" * 60)
    print(f"Total Images: {total_copied}")
    total_instances = sum(stats.values())
    for cid, name in class_names.items():
        cnt = stats[cid]
        pct = (cnt / total_instances * 100) if total_instances > 0 else 0
        print(f"  Class {cid} [{name:<12}]: {cnt:,} instances ({pct:.1f}%)")
    print(f"Total Defect Instances: {total_instances:,}")
    print(f"Config generated: {yaml_path}")
    print("=" * 60)
    
    return out

def zip_dataset(source_dir="unified_defects", zip_name="dataset.zip"):
    print(f"\nCompressing {source_dir} into {zip_name} for Google Colab...")
    with zipfile.ZipFile(zip_name, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, _, files in os.walk(source_dir):
            for file in files:
                file_path = os.path.join(root, file)
                arcname = os.path.relpath(file_path, start=source_dir)
                zipf.write(file_path, arcname)
    size_mb = os.path.getsize(zip_name) / (1024 * 1024)
    print(f"[SUCCESS] Created {zip_name} ({size_mb:.1f} MB)! Ready to upload to Colab or Google Drive.")

if __name__ == "__main__":
    out_folder = merge_datasets()
    zip_dataset(out_folder, "dataset.zip")
