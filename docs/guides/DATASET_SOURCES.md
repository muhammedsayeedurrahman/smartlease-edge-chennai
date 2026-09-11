# Wall Damage Dataset Sources — Quick Reference

**Goal:** 200–500 annotated images of wall defects for YOLOv8-Seg training
**Strategy:** Download pre-annotated public datasets (fastest path for Day 2)

---

## Roboflow Universe Datasets (Recommended — Pre-Annotated)

Visit: https://universe.roboflow.com

### Search Terms to Try

1. **"wall damage"** → Filter: Segmentation datasets only
2. **"surface crack"** → Often has polygon masks
3. **"concrete defect"** → Good for crack/spalling/stain classes
4. **"building facade"** → Architectural damage datasets

### Specific Datasets to Check (Search by Name)

| Dataset Name | Classes | Images | License | Notes |
|--------------|---------|--------|---------|-------|
| "Wall Crack Detection Dataset" | crack, no-crack | 300–500 | Check license | Common on Roboflow |
| "Surface Defect Segmentation" | crack, stain, peeling | 200+ | Varies | Multiple versions exist |
| "Concrete Surface Inspection" | crack, spall, stain | 400+ | CC BY 4.0 | Industrial/construction |

**How to Download:**
1. Search for dataset on Roboflow Universe
2. Click dataset → "Download Dataset"
3. Format: Select **"YOLOv8"** or **"YOLO Segmentation"** (NOT YOLOv5)
4. Download → Save to `training_data/vision/datasets/[dataset_name]/`

**License Check:** Only use CC BY 4.0, Public Domain, or MIT licensed datasets. Avoid NC (non-commercial) licenses for a product you might monetize.

---

## Kaggle Datasets

Visit: https://www.kaggle.com/datasets

### Search: `wall crack segmentation`

**Recommended datasets:**

1. **"Concrete Crack Images for Classification"**
   - URL: kaggle.com/datasets/arunrk7/surface-crack-detection
   - 40,000 images (positive/negative)
   - **Limitation:** Classification only (no segmentation masks)
   - **Use case:** Good for negative samples (clean walls)

2. **"Surface Crack Detection"** (search for segmentation variants)
   - Multiple authors have similar datasets
   - Look for ones with "mask" or "segmentation" in description
   - Download format: Usually images + mask images (convert to YOLO polygon format)

**How to Download:**
1. Log in to Kaggle
2. Navigate to dataset page
3. Click "Download" (requires Kaggle account)
4. Extract to `training_data/vision/datasets/[dataset_name]/`

**Format Conversion (If Needed):**
- If dataset has mask images (PNG masks), not YOLO txt files:
  - Use Roboflow's converter: roboflow.com/formats
  - Or write a Python script to convert masks → YOLO polygon format

---

## GitHub Public Datasets

### Search: `github.com/search` → "wall damage dataset"

**Example repos to check:**

1. **khanhha/crack_segmentation**
   - Crack segmentation dataset with masks
   - Usually MIT licensed

2. **Various construction/infrastructure repos**
   - Search: "crack detection dataset", "concrete defect dataset"
   - Check README for license and download instructions

---

## Google Dataset Search

Visit: https://datasetsearch.research.google.com

**Search queries:**
- "wall damage segmentation dataset"
- "surface crack detection"
- "building defect images"

**Filter by:**
- License: Free to use
- Format: Images (JPEG, PNG)
- With annotations (bounding boxes, masks)

---

## Your Own Photos (Supplemental — 30% of total)

**If you have time after downloading 2–3 public datasets:**

Take 50–100 photos with iQOO 15:
- **Nail holes:** 15 photos (close-up, 30cm distance)
- **Paint peeling:** 10 photos (bathroom corners, humid areas)
- **Water stains:** 10 photos (ceilings, AC vents)
- **Small cracks:** 15 photos (wall corners, door frames)

**Camera settings:**
- Mode: Auto or Photo mode (not Pro mode)
- Resolution: 12MP processed (not 50MP raw — too large)
- Lighting: Natural daylight preferred
- Distance: 30cm–1m from wall

**Annotation (Only if you have 4+ hours):**
- Tool: Roboflow Annotate (roboflow.com → Create Project → Upload → Annotate)
- Format: Export as **YOLOv8 Segmentation**
- Classes: crack, hole, stain, peeling_paint
- Time: ~5–10 min per image

---

## Realistic Day 2 Timeline (Total: 4–6 hours)

| Task | Time | Output |
|------|------|--------|
| Search & download 2–3 Roboflow datasets | 1–2 hrs | 400–800 images |
| Download 1 Kaggle dataset | 30 min | 200+ images |
| Take your own photos (optional) | 1 hr | 50 images |
| Organize into `training_data/vision/` | 30 min | Structured folders |
| Run `tools/validate_training_data.py` | 5 min | Confirmation |

**Priority order if short on time:**
1. Download at least 1 large Roboflow dataset (300+ images)
2. Download 1 Kaggle dataset as backup
3. Skip your own photos if datasets are sufficient
4. Skip manual annotation entirely (use pre-annotated only)

---

## Backup Plan (If No Good Datasets Found)

**Use ImageNet/COCO pre-trained weights with fine-tuning:**
- YOLOv8-Seg comes pre-trained on COCO dataset
- Fine-tune on even a small dataset (50–100 images) of wall damage
- Lower accuracy, but functional for hackathon demo

**Or use unsupervised anomaly detection:**
- Train an autoencoder on "clean wall" images only
- Defects = reconstruction error spikes
- Not segmentation, but still detects damage regions

---

## License Compliance (Important)

**Safe licenses for commercial use:**
- ✅ CC BY 4.0 (Creative Commons Attribution)
- ✅ CC0 / Public Domain
- ✅ MIT License
- ✅ Apache 2.0

**Avoid for a product you might sell:**
- ❌ CC BY-NC (Non-Commercial)
- ❌ CC BY-NC-SA
- ❌ Proprietary/All Rights Reserved

**Always check the dataset's LICENSE file or README.**

---

## Next Steps After Data Collection

Once you have 200+ images in `training_data/vision/`:

1. Run `python tools/validate_training_data.py` to confirm
2. Review `DATA_COLLECTION_GUIDE.md` end-of-day checklist
3. Backup `training_data/` folder (external drive or cloud)
4. Move to **Day 3: Model Training** (acoustic classifier first)

---

**Questions during data collection?**
- Stuck on Roboflow download? → Check their docs: docs.roboflow.com
- Dataset format unclear? → Ask here before proceeding
- Not enough images by end of day? → Acoustic data is higher priority, defer vision to Day 3 if needed
