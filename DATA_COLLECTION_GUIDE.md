# SmartLease Edge — Day 2 Data Collection Guide

**Timeline:** Sep 1–2, 2026 (Day 2 of 12-day plan)
**Deadline:** Complete by end of day Sep 2 to stay on schedule
**Next:** Days 3–5 model training begins Sep 3

---

## 1. Acoustic Tap Audio Collection

### Goal
Record **30–50 samples each** of hollow and solid taps on the actual iQOO 15 (or current demo phone).

### Equipment Setup
- **Phone:** iQOO 15 or whatever device you'll use for the hackathon demo
- **Recording app:** Use the built-in Voice Recorder app (or install "Easy Voice Recorder" for .wav format)
- **Settings:**
  - Format: WAV or M4A (lossless preferred)
  - Sample rate: 44.1kHz minimum (48kHz if available)
  - Mono is fine, stereo is better
  - Recording distance: **10–20cm** from surface (consistent across all samples)

### Recording Protocol

**HOLLOW TAPS (Target: 50 samples)**

Record 3-second clips of knuckle taps on:

| Surface Type | Where to Find | Samples Needed | Notes |
|--------------|---------------|----------------|-------|
| Hollow door | Any interior hollow-core door | 15 | Tap center panel, not frame |
| Drywall/plasterboard | Walls in modern apartments | 15 | Find area between studs |
| Empty cardboard box | Amazon/delivery boxes | 10 | Medium-to-large size boxes |
| Hollow plastic container | Empty storage bins, buckets | 10 | Tap side, not lid |

**Tips for hollow taps:**
- Listen for a resonant "boom" or echo
- Tap with consistent force (medium pressure)
- Each recording: 5–7 taps spaced 0.5 seconds apart
- Move to different spots on the same surface for variety

**SOLID TAPS (Target: 50 samples)**

Record 3-second clips of knuckle taps on:

| Surface Type | Where to Find | Samples Needed | Notes |
|--------------|---------------|----------------|-------|
| Concrete wall | Exterior walls, parking structures | 15 | Painted or bare concrete |
| Solid wood | Furniture, solid-core doors, tables | 15 | Tap flat surface, not edge |
| Filled containers | Water jugs, filled boxes | 10 | Full weight, not half-full |
| Tile on concrete | Bathroom/kitchen floor tiles | 10 | Tap grout area too for variety |

**Tips for solid taps:**
- Listen for a dull "thud" with no resonance
- Same consistent force as hollow samples
- Each recording: 5–7 taps spaced 0.5 seconds apart

### File Naming Convention

```
hollow_door_01.wav
hollow_door_02.wav
...
hollow_drywall_01.wav
...
solid_concrete_01.wav
solid_wood_01.wav
...
```

### Validation Checklist (Before Moving to Training)

- [ ] At least 30 hollow samples recorded
- [ ] At least 30 solid samples recorded
- [ ] All recordings are 3–5 seconds long
- [ ] Background noise is minimal (no TV, music, traffic)
- [ ] Tapping force is consistent across samples
- [ ] Recordings are in WAV or M4A format, 44.1kHz+
- [ ] Files are organized in `training_data/acoustic/hollow/` and `.../solid/`

---

## 2. Wall Damage Image Collection

### Goal
Collect **200–500 images** of wall defects (cracks, holes, stains, peeling paint) for training the YOLOv8-Seg vision model.

### Strategy: 70% Public Datasets + 30% Your Own Photos

#### Public Dataset Sources (Start Here — Fastest Path)

**Roboflow Universe** (https://universe.roboflow.com)

Search for these datasets:

1. **"wall damage"** — look for segmentation datasets (masks, not just bounding boxes)
2. **"surface crack detection"** — common in construction/infrastructure monitoring
3. **"concrete defect"** — often has annotated cracks, spalling, stains
4. **"building facade inspection"** — architectural datasets with damage labels

**Filter criteria:**
- Segmentation format (COCO JSON, YOLO segmentation, or polygon masks)
- At least 200 images
- License: CC BY 4.0, Public Domain, or MIT (commercial-use OK)
- Classes: crack, hole, stain, peeling_paint, water_damage

**Recommended datasets (search these by name on Roboflow):**
- "Wall Crack Detection" (various authors — check license)
- "Surface Defect Detection"
- "Concrete Crack Segmentation"

**Kaggle Datasets** (https://kaggle.com/datasets)

Search: `wall crack`, `surface damage`, `concrete defect`

**Download these if available:**
- "Concrete Crack Images for Classification" (40k images, may need to filter)
- "Surface Crack Detection" (any segmentation variant)

#### Your Own Photos (30% — Domain-Specific)

Take **50–100 photos** with the actual iQOO 15 camera:

| Defect Type | Where to Find | Photos Needed | Camera Settings |
|-------------|---------------|---------------|------------------|
| Small nail holes | Your apartment walls after removing frames | 15 | Macro mode, well-lit |
| Paint peeling | Bathroom corners, humid areas | 10 | Natural light, close-up |
| Water stains | Ceiling near AC vents, bathroom walls | 10 | Overhead shot, flash off |
| Small cracks | Wall corners, door frames, old plaster | 15 | 2x zoom for detail |

**Photo Guidelines:**
- Resolution: Full camera resolution (50MP main or 12MP processed — use processed)
- Lighting: Natural daylight or bright indoor light (no flash unless backlit)
- Distance: 30cm–1m from wall (same range users will actually use the app)
- Angles: Mix of straight-on and 45° angles
- Background: Include clean wall area in frame (not just the defect)

**File naming:**
```
own_crack_wall_01.jpg
own_hole_nail_01.jpg
own_stain_water_01.jpg
```

### Annotation (Required for Segmentation)

You have two options:

**Option 1: Use Pre-Annotated Public Datasets Only (Recommended for Speed)**
- Download datasets that already have segmentation masks
- Roboflow datasets often come pre-annotated
- Skip manual labeling entirely if you get 300+ pre-labeled images

**Option 2: Annotate Your Own Photos (If You Have Time)**
- Tool: **Roboflow Annotate** (free, web-based) or **LabelImg** (desktop)
- Format: Export as **YOLO segmentation** (polygon masks, not boxes)
- Classes: `crack`, `hole`, `stain`, `peeling_paint`
- Time estimate: 5–10 min per image → 50 images = 4–8 hours

**Realistic Day 2 path:** Use Option 1 (pre-annotated datasets) to stay on schedule. Annotate your own photos only if you finish early.

### Directory Structure (Set This Up Today)

```
C:\Users\HP\SmartLeaseEdge\training_data\
├── acoustic\
│   ├── hollow\
│   │   ├── hollow_door_01.wav
│   │   ├── hollow_door_02.wav
│   │   └── ...
│   └── solid\
│       ├── solid_concrete_01.wav
│       ├── solid_wood_01.wav
│       └── ...
└── vision\
    ├── images\
    │   ├── crack_001.jpg
    │   ├── hole_001.jpg
    │   └── ...
    ├── labels\   # YOLO segmentation .txt files (polygon format)
    │   ├── crack_001.txt
    │   └── ...
    └── datasets\  # Downloaded public datasets go here
        ├── roboflow_wall_damage\
        └── kaggle_concrete_cracks\
```

---

## 3. Data Validation Script

Run this after collecting all data to verify you're ready for Day 3 training.

**TODO:** Create `validate_training_data.py` script (see next section for implementation).

---

## 4. End-of-Day-2 Checklist

- [ ] 30+ hollow tap audio samples collected and organized
- [ ] 30+ solid tap audio samples collected and organized
- [ ] 200+ wall damage images downloaded from public datasets
- [ ] 50+ wall damage photos taken with iQOO 15 (optional if public datasets are sufficient)
- [ ] All data organized in `training_data/` directory structure
- [ ] Segmentation labels exist for vision data (pre-annotated or manually labeled)
- [ ] Data validation script confirms file counts and formats
- [ ] Backup of `training_data/` folder created (external drive or cloud)

**If any checkbox is unchecked by end of day:** Prioritize acoustic data first (highest risk per Chennai plan), vision data second.

---

## What Happens Next (Days 3–5)

- **Day 3:** Train acoustic classifier on tap samples (simple classifier, not full YamNet fine-tune)
- **Day 4:** Start YOLOv8-Seg training on wall damage dataset
- **Day 5:** Complete training, evaluate on test set, export checkpoints

This guide gets you ready for that work — no training happens until Day 3.
