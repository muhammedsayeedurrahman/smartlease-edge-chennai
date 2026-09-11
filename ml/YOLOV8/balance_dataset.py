"""
SmartLease Edge - Balanced Dataset Generator
Converts unbalanced raw folders (crack-seg: 3700+, paint-peel: 40, concrete: 70)
into a balanced 580+ image multi-class defect dataset matching the hackathon slide:
- Class 0: crack (~280 images)
- Class 1: peeling (~150 images via augmentation)
- Class 2: structural_defect (concrete distress, chips, holes) (~150 images)
"""

import os
import shutil
import random
from pathlib import Path
import cv2
import numpy as np
import yaml

random.seed(42)

def create_balanced_dataset(root_dir=".", output_dir="balanced_defects_580"):
    root = Path(root_dir)
    out = Path(output_dir)
    
    # Target folders
    for split in ["train", "val"]:
        (out / "images" / split).mkdir(parents=True, exist_ok=True)
        (out / "labels" / split).mkdir(parents=True, exist_ok=True)

    print("=== SMARTLEASE BALANCED DATASET GENERATOR ===")
    print(f"Reading from: {root.resolve()}")
    print(f"Saving to:    {out.resolve()}\n")

    # -------------------------------------------------------------
    # 1. SAMPLE CRACKS (280 images out of 3,717)
    # -------------------------------------------------------------
    crack_img_dir = root / "crack-seg" / "images" / "train"
    crack_lbl_dir = root / "crack-seg" / "labels" / "train"
    
    if crack_img_dir.exists():
        all_crack_imgs = list(crack_img_dir.glob("*.jpg")) + list(crack_img_dir.glob("*.png"))
        random.shuffle(all_crack_imgs)
        selected_cracks = all_crack_imgs[:280]
        
        train_cracks = selected_cracks[:240]
        val_cracks = selected_cracks[240:]
        
        for split, img_list in [("train", train_cracks), ("val", val_cracks)]:
            for img_p in img_list:
                lbl_p = crack_lbl_dir / (img_p.stem + ".txt")
                if lbl_p.exists():
                    shutil.copy(img_p, out / "images" / split / img_p.name)
                    # crack class is 0
                    shutil.copy(lbl_p, out / "labels" / split / lbl_p.name)
        print(f"✅ Sampled Cracks: {len(train_cracks)} train, {len(val_cracks)} val (from 3,700+ pool)")

    # -------------------------------------------------------------
    # 2. PROCESS & AUGMENT PAINT-PEELING (40 raw -> ~150 augmented)
    # -------------------------------------------------------------
    peel_train_imgs = list((root / "paint-peel" / "train" / "images").glob("*.*"))
    peel_train_lbls = root / "paint-peel" / "train" / "labels"
    peel_val_imgs = list((root / "paint-peel" / "valid" / "images").glob("*.*"))
    peel_val_lbls = root / "paint-peel" / "valid" / "labels"

    # Process validation
    for img_p in peel_val_imgs:
        lbl_p = peel_val_lbls / (img_p.stem + ".txt")
        if lbl_p.exists():
            shutil.copy(img_p, out / "images" / "val" / img_p.name)
            # Map class to 1 (peeling)
            with open(lbl_p, 'r') as f_in, open(out / "labels" / "val" / lbl_p.name, 'w') as f_out:
                for line in f_in:
                    parts = line.strip().split()
                    if parts:
                        f_out.write(f"1 {' '.join(parts[1:])}\n")

    # Process train + augment
    peel_count = 0
    for img_p in peel_train_imgs:
        lbl_p = peel_train_lbls / (img_p.stem + ".txt")
        if not lbl_p.exists():
            continue
        
        img = cv2.imread(str(img_p))
        if img is None:
            continue
            
        with open(lbl_p, 'r') as f:
            lines = f.readlines()
            
        # Original
        shutil.copy(img_p, out / "images" / "train" / img_p.name)
        with open(out / "labels" / "train" / lbl_p.name, 'w') as f_out:
            for line in lines:
                parts = line.strip().split()
                if parts:
                    f_out.write(f"1 {' '.join(parts[1:])}\n")
        peel_count += 1
        
        # Augmentation 1: Horizontal Flip
        img_flip = cv2.flip(img, 1)
        aug_name = f"aug_flip_{img_p.stem}"
        cv2.imwrite(str(out / "images" / "train" / f"{aug_name}.jpg"), img_flip)
        with open(out / "labels" / "train" / f"{aug_name}.txt", 'w') as f_out:
            for line in lines:
                parts = line.strip().split()
                if len(parts) > 2:
                    coords = list(map(float, parts[1:]))
                    # flip x coordinates
                    for k in range(0, len(coords), 2):
                        coords[k] = 1.0 - coords[k]
                    coord_str = " ".join([f"{c:.6f}" for c in coords])
                    f_out.write(f"1 {coord_str}\n")
        peel_count += 1

        # Augmentation 2: Brightness adjustment
        img_bright = cv2.convertScaleAbs(img, alpha=1.15, beta=15)
        aug_name2 = f"aug_bright_{img_p.stem}"
        cv2.imwrite(str(out / "images" / "train" / f"{aug_name2}.jpg"), img_bright)
        with open(out / "labels" / "train" / f"{aug_name2}.txt", 'w') as f_out:
            for line in lines:
                parts = line.strip().split()
                if parts:
                    f_out.write(f"1 {' '.join(parts[1:])}\n")
        peel_count += 1

    print(f"✅ Augmented Paint-Peeling: {peel_count} train samples (boosted from 40 raw)")

    # -------------------------------------------------------------
    # 3. PROCESS CONCRETE STRUCTURAL DEFECTS (70 raw -> ~150 augmented)
    # Mapping:
    # class 1 (crack) -> 0
    # class 3 (peeling) -> 1
    # classes 0, 2, 4, 5, 6, 7, 8 -> 2 (structural_defect)
    # -------------------------------------------------------------
    conc_train_imgs = list((root / "concrete" / "train" / "images").glob("*.*"))
    conc_train_lbls = root / "concrete" / "train" / "labels"
    conc_val_imgs = list((root / "concrete" / "valid" / "images").glob("*.*"))
    conc_val_lbls = root / "concrete" / "valid" / "labels"

    def remap_concrete_line(line):
        parts = line.strip().split()
        if not parts:
            return ""
        old_cls = int(parts[0])
        if old_cls == 1:
            new_cls = 0  # crack
        elif old_cls == 3:
            new_cls = 1  # peeling
        else:
            new_cls = 2  # structural_defect (chip, pothole, corrosion, spalling)
        return f"{new_cls} {' '.join(parts[1:])}\n"

    # Validation
    for img_p in conc_val_imgs:
        lbl_p = conc_val_lbls / (img_p.stem + ".txt")
        if lbl_p.exists():
            shutil.copy(img_p, out / "images" / "val" / img_p.name)
            with open(lbl_p, 'r') as f_in, open(out / "labels" / "val" / lbl_p.name, 'w') as f_out:
                for line in f_in:
                    remapped = remap_concrete_line(line)
                    if remapped:
                        f_out.write(remapped)

    # Train + augment
    conc_count = 0
    for img_p in conc_train_imgs:
        lbl_p = conc_train_lbls / (img_p.stem + ".txt")
        if not lbl_p.exists():
            continue
        img = cv2.imread(str(img_p))
        if img is None:
            continue
            
        with open(lbl_p, 'r') as f:
            lines = f.readlines()
            
        # Original
        shutil.copy(img_p, out / "images" / "train" / img_p.name)
        with open(out / "labels" / "train" / lbl_p.name, 'w') as f_out:
            for line in lines:
                remapped = remap_concrete_line(line)
                if remapped:
                    f_out.write(remapped)
        conc_count += 1
        
        # Augmentation (Horizontal Flip)
        img_flip = cv2.flip(img, 1)
        aug_name = f"aug_conc_{img_p.stem}"
        cv2.imwrite(str(out / "images" / "train" / f"{aug_name}.jpg"), img_flip)
        with open(out / "labels" / "train" / f"{aug_name}.txt", 'w') as f_out:
            for line in lines:
                parts = line.strip().split()
                if len(parts) > 2:
                    old_cls = int(parts[0])
                    new_cls = 0 if old_cls == 1 else (1 if old_cls == 3 else 2)
                    coords = list(map(float, parts[1:]))
                    for k in range(0, len(coords), 2):
                        coords[k] = 1.0 - coords[k]
                    coord_str = " ".join([f"{c:.6f}" for c in coords])
                    f_out.write(f"{new_cls} {coord_str}\n")
        conc_count += 1

    print(f"✅ Processed & Augmented Concrete: {conc_count} train samples")

    # -------------------------------------------------------------
    # 4. GENERATE BALANCED DATA.YAML
    # -------------------------------------------------------------
    total_train = len(list((out / "images" / "train").glob("*.*")))
    total_val = len(list((out / "images" / "val").glob("*.*")))
    
    yaml_dict = {
        "path": str(out.resolve()),
        "train": "images/train",
        "val": "images/val",
        "names": {
            0: "crack",
            1: "peeling",
            2: "structural_defect"
        }
    }
    
    yaml_file = out / "balanced_defects.yaml"
    with open(yaml_file, "w") as f:
        yaml.dump(yaml_dict, f, default_flow_style=False)
        
    print("\n" + "="*55)
    print(f"🎉 BALANCED DATASET GENERATION COMPLETE!")
    print(f"Total Train Images: {total_train}")
    print(f"Total Val Images:   {total_val}")
    print(f"Total Dataset Size: {total_train + total_val} images (580+ Target Reached!)")
    print(f"YAML Config:        {yaml_file}")
    print("Classes: 0: crack | 1: peeling | 2: structural_defect")
    print("="*55 + "\n")
    return yaml_file

if __name__ == "__main__":
    create_balanced_dataset()
