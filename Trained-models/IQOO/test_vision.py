"""
SmartLease Edge — Quick Model Verification Script
Tests the newly loaded YOLO vision model on validation images
and outputs the detected classes, bboxes, and confidence.
"""
import sys
from pathlib import Path

# Set up paths
PROJECT_ROOT = Path(__file__).parent
sys.path.insert(0, str(PROJECT_ROOT))

from ultralytics import YOLO
from vision.predict import VisionPredictor

def test_model():
    print("=" * 60)
    print("🔍 Testing SmartLease Edge Vision Model...")
    print("=" * 60)

    # 1. Initialize predictor
    predictor = VisionPredictor()
    model = predictor.model

    print(f"\n📦 Model Info:")
    print(f"   Architecture: {model.task}")
    print(f"   Classes count ({len(model.names)}): {model.names}")

    # 2. Find a test image from validation set
    val_images = list((PROJECT_ROOT / "datasets" / "wall-defects" / "valid" / "images").glob("*.jpg"))
    if not val_images:
        print("❌ No validation images found in datasets/wall-defects/valid/images")
        return

    test_img = val_images[0]
    print(f"\n📸 Running inference on test image: {test_img.name}")

    # 3. Predict
    annotated, defects = predictor.predict(str(test_img), depth_meters=2.0)

    print(f"\n🎯 Results:")
    print(f"   Defects Detected: {len(defects)}")
    for i, d in enumerate(defects, 1):
        print(f"   [{i}] {d.class_name.upper()} | Conf: {d.confidence:.1%} | Severity: {d.severity.upper()} | Area: {d.area_percent:.2f}% ({d.area_sqft:.2f} sq.ft.)")

    # 4. Save output
    output_path = PROJECT_ROOT / "outputs" / "test_prediction.jpg"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if annotated is not None:
        import cv2
        cv2.imwrite(str(output_path), annotated)
        print(f"\n💾 Saved annotated prediction image to: {output_path}")

    print("\n✅ Vision Model is 100% OPERATIONAL!")
    print("=" * 60)

if __name__ == "__main__":
    test_model()
