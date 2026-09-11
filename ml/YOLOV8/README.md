# 🧱 SmartLease Edge: YOLOv8-Seg Training & On-Device Defect Segmentation

> **iQOO Hackathon 2026 — Chennai City Battle**  
> *"Cracks become square feet, not opinions."*

---

## 📌 Overview

This repository contains the dataset analysis and the end-to-end Google Colab training notebook (**[`smartlease_edge_yolov8_seg.ipynb`](./smartlease_edge_yolov8_seg.ipynb)**) to fine-tune **YOLOv8n-Seg** for wall and concrete defect instance segmentation.

It directly implements the **SmartLease Edge** vision pipeline:
1. **Defect Instance Segmentation**: Sub-centimeter polygon boundaries for cracks, peeling paint, and structural concrete distress.
2. **Metric Pinhole Estimation**:
   $$\text{Area}_{\text{sq ft}} = \left( \frac{\text{Mask Pixels} \times Z^2}{f_x \times f_y} \right) \times 10.7639$$
   Converting active segmentation mask pixels directly into real-world physical area ($\text{sq ft}$) using ARCore depth ($Z$) or manual/laser distance.
3. **Severity & Decision Banding**:
   - `< 0.30` confidence: Filtered as noise.
   - `0.30 - 0.50` confidence: Flagged for human *Review*.
   - `> 0.50` confidence: Confirmed defect with automatic severity rating (*Minor*, *Moderate*, *Severe*) and repair cost estimation (Chennai plaster & paint rates).
4. **On-Device Edge Deployment**: Export to **ONNX**, **TFLite**, and **Snapdragon Hexagon NPU (ExecuTorch / PT2E QNN Quantizer 8a8w)** targeting $< 30\text{ ms}$ latency and $\sim 6\text{ MB}$ INT8 footprint.

---

## 📊 Dataset Analysis & Breakdown

The local workspace (`d:\hackathon\YOLOV8`) contains three specialized segmentation datasets formatted with YOLOv8 polygon masks:

| Dataset | Splits (Train / Val / Test) | Classes | Format | Description |
| :--- | :--- | :--- | :--- | :--- |
| **`crack-seg`** | 3,717 / 112 / 200 images | `crack` (1 class) | YOLOv8-Seg Polygons | High-resolution micro & macro surface cracks on walls and pavements. |
| **`concrete`** | 70 / 20 / 10 images | `chip`, `crack`, `leakage`, `peeling`, `potholes`, `protective layer`, `rebar corrosion`, `rebar exposure`, `rebar gap` (9 classes) | YOLOv8-Seg Polygons | Roboflow dataset for structural defects and concrete spalling. |
| **`paint-peel`** | 40 / 10 / 0 images | `paint-peeling` (1 class) | YOLOv8-Seg Polygons | Real smartphone photos of peeled wall paint and flaking coatings on interior/exterior walls. |

---

## 🚀 How to Run in Google Colab

### Step 1: Open the Notebook in Colab
1. Navigate to [Google Colab](https://colab.research.google.com/).
2. Click **File -> Upload notebook**.
3. Select and upload [`smartlease_edge_yolov8_seg.ipynb`](./smartlease_edge_yolov8_seg.ipynb).
4. In the Colab menu, ensure GPU acceleration is enabled:
   - Go to **Runtime -> Change runtime type**.
   - Select **T4 GPU** (or any available GPU).

### Step 2: Providing the Dataset
The notebook gives you two options:
- **Option A (Instant Demo / High Accuracy Crack Model)**:
  Run the notebook without uploading anything! It will automatically download the complete Ultralytics `crack-seg` dataset (4,000+ images) directly inside Colab.
- **Option B (Your Local Custom Dataset)**:
  If you want to train on your local `concrete` or `paint-peel` datasets:
  1. Compress your folder into `dataset.zip`:
     - Select `concrete`, `crack-seg`, or `paint-peel` and zip it as `dataset.zip`.
  2. In Google Colab, click the **Folder icon (Files)** on the left sidebar.
  3. Drag and drop `dataset.zip` into `/content/` (or place it in your Google Drive at `My Drive/dataset.zip`).
  4. The notebook will automatically extract and configure it.

### Step 3: Run the Cells
Run each cell sequentially:
1. **Hardware Setup**: Installs `ultralytics`, `onnx`, `opencv-python`, etc.
2. **Dataset Setup**: Verifies paths and generates `colab_data.yaml`.
3. **EDA & Visualizer**: Displays ground-truth segmentation masks overlaid on sample wall photos.
4. **Training**: Trains `yolov8n-seg.pt` for 50 epochs with real-world wall augmentations.
5. **Evaluation**: Displays mAP@50, mAP@50-95, loss curves, and confusion matrix.
6. **Inference**: Visualizes segmentation masks on test images.
7. **Pinhole Metric Engine**: Computes defect area ($\text{sq ft}$), severity, and estimated repair cost.
8. **Export**: Generates `best.onnx` and `best.tflite`.
9. **Download Bundle**: Automatically zips and downloads `smartlease_edge_trained_bundle.zip`.

---

## 📐 Pinhole Camera Metric Calculation

The physical surface area is projected using the pinhole model:
```python
def compute_defect_area(mask_pixels, distance_meters, fx=580.0, fy=580.0):
    # Metric area in square meters
    area_m2 = (mask_pixels * (distance_meters ** 2)) / (fx * fy)
    # Convert to square feet (1 m² = 10.7639 sq ft)
    area_sqft = area_m2 * 10.7639
    return area_sqft
```

### Decision Rules:
| Confidence Band | Action | UI Display |
| :--- | :--- | :--- |
| `< 0.30` | Suppressed | Hidden (Noise) |
| `0.30 - 0.50` | Review | Yellow / Orange badge: "Review Required" |
| `> 0.50` | Confirmed | Automatic Defect Card with Severity & Cost |

---

## ⚡ Snapdragon Hexagon NPU Lowering Path

For deployment on Qualcomm Snapdragon 8-series (iQOO flagship target):
```
PyTorch (best.pt) 
  └─> torch.export 
        └─> PT2E Quantizer (QnnQuantizer 8a8w) 
              └─> ExecuTorch Lowering 
                    └─> smartlease_vision_htp.pte (~6 MB INT8, <30 ms on Hexagon HTP)
```
*(A companion script `snapdragon_htp_quantize_guide.py` is included directly in the export section of the notebook).*

## Source datasets (not tracked in git)

The image sets are excluded by `.gitignore` -- 9,043 files and a 118 MB archive do not
belong in the repository. Re-download them from Roboflow in **YOLOv8 format** and unzip
into the directories below.

| Dir | Dataset | Link | License |
|-----|---------|------|---------|
| `1/` | Infrastructure Monitoring (12 cls) | https://universe.roboflow.com/pothole-detection-h9muz/infrastructure-monitoring/dataset/1 | CC BY 4.0 |
| `2/` | Structural Defects (3 cls) | https://universe.roboflow.com/kamar/structural-defects/dataset/1 | CC BY 4.0 |
| `3/` | Building Anomalies (6 cls) | https://universe.roboflow.com/image-classification-jf9vm/building-anomalies/dataset/1 | CC BY 4.0 |
| `concrete/` | Concrete Defects (9 cls) | https://universe.roboflow.com/ammlworkspace/concrete-defects-gn6lu/dataset/1 | CC BY 4.0 |
| `paint-peel/` | Paint Peel Detection (1 cls) | https://universe.roboflow.com/image-classification-jf9vm/detection-ghu6i/dataset/1 | Public Domain |

`unified_defects/` is **derived, not downloaded**: it is the merge of the five above remapped
onto the four classes the shipped model uses -- `crack, peeling, spalling, stain_mould`.
Regenerate it rather than looking for a source:

```bash
py -3 ml/YOLOV8/merge_and_zip_datasets.py     # -> unified_defects/
py -3 ml/YOLOV8/balance_dataset.py            # class balancing
```

The wall-finishing dataset used by the older pipeline lives elsewhere:
`ml/data/dataset/internal-defect/` <- https://universe.roboflow.com/chew-poh-yee/internal-wall-finishing-defects/dataset/5

## Retraining and export

```bash
# after training, export for the app (PyTorch Lite, runs today on CPU)
py -3 ml/YOLOV8/export_torchscript_lite.py --weights best.pt
cp ml/YOLOV8/yolov8n_seg.ptl app/src/main/assets/

# NPU path (needs QNN SDK + ExecuTorch; see the caveats in the repo history)
py -3 ml/YOLOV8/convert_yolov8_seg_qnn_pte.py --soc SM8650
```
