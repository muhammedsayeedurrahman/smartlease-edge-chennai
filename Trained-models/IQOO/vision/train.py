"""
SmartLease Edge — YOLOv8-Seg Training Pipeline
Fine-tunes YOLOv8n-seg on wall defect segmentation data.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
from config import VISION, MODELS_DIR, DATASETS_DIR
from ultralytics import YOLO


def train(
    data_yaml: str = None,
    epochs: int = None,
    device: str = "cpu",
    resume: bool = False,
) -> str:
    """
    Fine-tune YOLOv8n-seg on wall defect data.
    
    Args:
        data_yaml: Path to data.yaml (auto-detected if None)
        epochs: Override epoch count
        device: "cpu" or "0" for GPU
        resume: Resume from last checkpoint
    
    Returns:
        Path to best weights
    """
    # Auto-detect dataset
    if data_yaml is None:
        candidates = [
            DATASETS_DIR / "wall-defects-multiclass" / "data.yaml",
            DATASETS_DIR / "wall-defects" / "data.yaml",
            DATASETS_DIR / "sample-defects" / "data.yaml",
        ]
        for c in candidates:
            if c.exists():
                data_yaml = str(c)
                break
        if data_yaml is None:
            print("❌ No dataset found. Run one of:")
            print("   python data/prepare_multiclass.py --api-key <key>  (5-class Roboflow dataset)")
            print("   python data/download_dataset.py --sample     (synthetic test data)")
            print("   python data/download_dataset.py --extract <zip>  (real data)")
            return None

    print(f"Dataset: {data_yaml}")
    print(f"Device: {device}")

    # Determine base model: if dataset is multiclass detection, use yolov8n.pt; otherwise seg
    is_detection = "multiclass" in str(data_yaml).lower() or not str(VISION["model_base"]).endswith("-seg.pt")
    base_model = "yolov8n.pt" if is_detection else VISION["model_base"]
    print(f"Base model: {base_model}")

    # Load base model
    model = YOLO(base_model)

    # Train
    aug = VISION["augmentation"]
    results = model.train(
        data=data_yaml,
        epochs=epochs or VISION["epochs"],
        imgsz=VISION["imgsz"],
        batch=VISION["batch_size"],
        patience=VISION["patience"],
        augment=True,
        lr0=0.01,
        lrf=0.01,
        mosaic=aug["mosaic"],
        flipud=aug["flipud"],
        fliplr=aug["fliplr"],
        degrees=aug["degrees"],
        translate=aug["translate"],
        scale=aug["scale"],
        hsv_h=aug["hsv_h"],
        hsv_s=aug["hsv_s"],
        hsv_v=aug["hsv_v"],
        name="smartlease",
        project=str(MODELS_DIR / "vision_runs"),
        device=device,
        resume=resume,
        verbose=True,
    )

    # Copy best weights
    best_path = Path(results.save_dir) / "weights" / "best.pt"
    dest = MODELS_DIR / "vision_best.pt"
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    if best_path.exists():
        import shutil
        shutil.copy2(best_path, dest)
        print(f"\n✅ Best weights saved to: {dest}")
        print(f"   mAP@50: {results.results_dict.get('metrics/mAP50(M)', 'N/A')}")
        return str(dest)
    else:
        print("⚠️  Training completed but best.pt not found at expected path")
        return str(best_path)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Train YOLOv8-Seg on wall defects")
    parser.add_argument("--data", type=str, help="Path to data.yaml")
    parser.add_argument("--epochs", type=int, help="Number of epochs")
    parser.add_argument("--device", type=str, default="cpu", help="cpu or 0 for GPU")
    parser.add_argument("--resume", action="store_true", help="Resume training")
    args = parser.parse_args()

    train(data_yaml=args.data, epochs=args.epochs, device=args.device, resume=args.resume)
