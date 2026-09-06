"""
SmartLease Edge — Vision Defect Prediction
Run inference on a single image and return structured defect results.
"""
import sys
from pathlib import Path
from dataclasses import dataclass, field
from typing import List, Optional

import numpy as np

sys.path.insert(0, str(Path(__file__).parent.parent))
from config import VISION, MODELS_DIR


@dataclass
class DefectResult:
    class_name: str
    confidence: float
    area_percent: float         # % of frame affected
    area_sqft: Optional[float]  # Physical area (requires depth)
    bbox: list                  # [x1, y1, x2, y2]
    severity: str               # "minor" | "moderate" | "severe"
    mask: Optional[np.ndarray] = field(default=None, repr=False)


def classify_severity(area_percent: float) -> str:
    """Classify defect severity based on affected area."""
    if area_percent < 2.0:
        return "minor"
    elif area_percent < 5.0:
        return "moderate"
    else:
        return "severe"


def pixel_area_to_sqft(
    active_pixels: int,
    total_pixels: int,
    depth_meters: float = 2.0,
    focal_length_px: float = 500.0,
) -> float:
    """
    Convert pixel area to physical sq.ft. using pinhole camera model.
    A_metric = (active/total) * (Z² / (fx * fy))
    A_sqft = A_metric * 10.7639
    """
    area_fraction = active_pixels / max(total_pixels, 1)
    # Approximate: assume square sensor field
    fov_area_m2 = (depth_meters ** 2) / (focal_length_px ** 2) * total_pixels
    area_m2 = area_fraction * fov_area_m2
    return area_m2 * 10.7639


class VisionPredictor:
    def __init__(self, model_path: str = None):
        from ultralytics import YOLO

        if model_path is None:
            # Look for vision_best.pt or user-placed best_multiclass / best models
            candidates = [
                MODELS_DIR / "vision_best.pt",
                MODELS_DIR / "best_multiclass (3).pt",
                MODELS_DIR / "best_multiclass.pt",
                MODELS_DIR / "best.pt",
            ]
            candidates.extend(list(MODELS_DIR.glob("best*.pt")))

            found = None
            for c in candidates:
                if c.exists():
                    found = c
                    break

            if found:
                model_path = str(found)
                print(f"✅ Loaded trained model: {model_path}")
                # Ensure vision_best.pt also exists as standard alias
                target_alias = MODELS_DIR / "vision_best.pt"
                if not target_alias.exists():
                    import shutil
                    try:
                        shutil.copy2(found, target_alias)
                    except Exception:
                        pass
            else:
                model_path = VISION["model_base"]
                print(f"⚠️  No trained model found, using pretrained: {model_path}")
                print(f"   Run 'python -m vision.train' to train on wall defects")

        self.model = YOLO(model_path)
        self.conf_threshold = VISION["conf_threshold"]
        self.iou_threshold = VISION["iou_threshold"]

    def predict(
        self,
        image,
        depth_meters: float = 2.0,
        focal_length_px: float = 500.0,
    ) -> tuple:
        """
        Run defect detection on an image.
        
        Args:
            image: Path to image, numpy array, or PIL Image
            depth_meters: Distance to wall surface (for sq.ft. calculation)
            focal_length_px: Camera focal length in pixels
        
        Returns:
            (annotated_image, list[DefectResult])
        """
        results = self.model.predict(
            source=image,
            conf=self.conf_threshold,
            iou=self.iou_threshold,
            save=False,
            verbose=False,
        )

        defects = []
        annotated = None

        for r in results:
            annotated = r.plot()  # BGR numpy array with annotations

            if r.masks is not None and len(r.masks) > 0:
                for i, mask in enumerate(r.masks.data):
                    mask_np = mask.cpu().numpy()
                    active_pixels = mask_np.sum()
                    total_pixels = mask_np.shape[0] * mask_np.shape[1]
                    area_pct = (active_pixels / total_pixels) * 100

                    area_sqft = pixel_area_to_sqft(
                        int(active_pixels), int(total_pixels),
                        depth_meters, focal_length_px
                    )

                    class_idx = int(r.boxes.cls[i])
                    class_name = r.names[class_idx]
                    confidence = float(r.boxes.conf[i])
                    bbox = r.boxes.xyxy[i].cpu().numpy().tolist()

                    defects.append(DefectResult(
                        class_name=class_name,
                        confidence=confidence,
                        area_percent=float(area_pct),
                        area_sqft=area_sqft,
                        bbox=bbox,
                        severity=classify_severity(area_pct),
                        mask=mask_np,
                    ))
            elif r.boxes is not None and len(r.boxes) > 0:
                # Object detection fallback (no segmentation masks)
                orig_h, orig_w = r.orig_shape
                total_pixels = orig_h * orig_w
                for i in range(len(r.boxes)):
                    bbox = r.boxes.xyxy[i].cpu().numpy().tolist()
                    x1, y1, x2, y2 = bbox
                    box_w = max(0.0, x2 - x1)
                    box_h = max(0.0, y2 - y1)
                    active_pixels = box_w * box_h
                    area_pct = (active_pixels / max(total_pixels, 1)) * 100

                    area_sqft = pixel_area_to_sqft(
                        int(active_pixels), int(total_pixels),
                        depth_meters, focal_length_px
                    )

                    class_idx = int(r.boxes.cls[i])
                    class_name = r.names[class_idx]
                    confidence = float(r.boxes.conf[i])

                    defects.append(DefectResult(
                        class_name=class_name,
                        confidence=confidence,
                        area_percent=float(area_pct),
                        area_sqft=area_sqft,
                        bbox=bbox,
                        severity=classify_severity(area_pct),
                        mask=None,
                    ))

        return annotated, defects


def predict_single(image_path: str, depth: float = 2.0) -> tuple:
    """Convenience function for single image prediction."""
    predictor = VisionPredictor()
    return predictor.predict(image_path, depth_meters=depth)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Run vision defect detection")
    parser.add_argument("image", type=str, help="Path to image file")
    parser.add_argument("--depth", type=float, default=2.0, help="Distance to wall (meters)")
    parser.add_argument("--save", type=str, help="Save annotated image to path")
    args = parser.parse_args()

    import cv2
    annotated, defects = predict_single(args.image, args.depth)

    if defects:
        print(f"\n{'='*60}")
        print(f"DEFECTS FOUND: {len(defects)}")
        print(f"{'='*60}")
        for d in defects:
            print(f"  {d.severity.upper()} | {d.class_name} | "
                  f"{d.area_percent:.1f}% area | {d.confidence:.0%} confidence"
                  + (f" | {d.area_sqft:.2f} sq.ft." if d.area_sqft else ""))
    else:
        print("\n✅ No defects detected — wall looks clean!")

    if args.save and annotated is not None:
        cv2.imwrite(args.save, annotated)
        print(f"\nAnnotated image saved to: {args.save}")
    elif annotated is not None:
        cv2.imwrite("output_annotated.jpg", annotated)
        print(f"\nAnnotated image saved to: output_annotated.jpg")
