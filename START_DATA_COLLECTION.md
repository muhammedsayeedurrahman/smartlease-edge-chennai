# Day 2 Data Collection — Quick Start Guide

**Timeline:** Complete by end of Sep 2, 2026
**Total time:** 4–6 hours (can be split across the day)

---

## 🎯 Two-Track Approach

You'll do these in parallel:

### Track 1: Acoustic Recording (2–3 hours, manual)
**You must do this yourself** — recording tap sounds with your phone

### Track 2: Vision Datasets (1–2 hours, mostly automated)
**I can help with this** — downloading pre-annotated datasets

---

## 🎤 Track 1: Acoustic Recording (Start Now)

### Step 1: Set up your recording app

1. **Open Voice Recorder** on your phone (or install "Easy Voice Recorder")
2. **Configure settings:**
   - Format: WAV (preferred) or M4A
   - Quality: High / Lossless
   - Sample rate: 44.1kHz or 48kHz

### Step 2: Test recording

1. Find a hollow door
2. Position phone 10–20cm from door
3. Record 3 seconds
4. Tap door 5 times with your knuckle (medium force)
5. Listen back — should hear clear knocks, minimal background noise

### Step 3: Start systematic collection

**Target: 100 total samples (50 hollow + 50 solid)**

#### Hollow surfaces (50 samples):

| Surface | Where to find | Samples needed |
|---------|--------------|----------------|
| Hollow door | Interior doors (bedroom, bathroom) | 15 |
| Drywall | Walls between studs | 15 |
| Cardboard box | Empty delivery boxes | 10 |
| Hollow plastic | Empty storage bins | 10 |

#### Solid surfaces (50 samples):

| Surface | Where to find | Samples needed |
|---------|--------------|----------------|
| Concrete | Exterior walls, parking structure | 15 |
| Solid wood | Dining table, solid furniture | 15 |
| Filled container | Full water jugs, filled boxes | 10 |
| Tile on concrete | Bathroom/kitchen floor | 10 |

### Step 4: File naming

Save each recording as:
```
hollow_door_01.wav
hollow_door_02.wav
hollow_drywall_01.wav
...
solid_concrete_01.wav
solid_wood_01.wav
...
```

### Step 5: Transfer to laptop

**After recording all samples:**

1. Connect phone to laptop via USB (enable File Transfer mode)
2. Copy all recordings to: `C:\Users\HP\SmartLeaseEdge\training_data\acoustic\`
3. Organize:
   - Move `hollow_*.wav` → `training_data/acoustic/hollow/`
   - Move `solid_*.wav` → `training_data/acoustic/solid/`

### Step 6: Track progress (optional)

While recording, run this to see your progress:

```bash
cd C:\Users\HP\SmartLeaseEdge
python acoustic_data_tracker.py
```

It shows:
- Real-time progress bars
- What to record next
- Estimated time remaining

---

## 📊 Track 2: Vision Datasets (Automated)

### Option A: Quick Setup (Roboflow - Recommended)

**Easiest path — web-based download:**

1. **Sign up:** Go to https://app.roboflow.com (free account)
2. **Browse datasets:** 
   - Search: "wall damage"
   - Filter: Segmentation datasets only
   - License: CC BY 4.0 or Public Domain
3. **Download:**
   - Select a dataset (aim for 200+ images)
   - Click "Download Dataset"
   - Format: **YOLOv8 Segmentation**
   - Save to: `C:\Users\HP\SmartLeaseEdge\training_data\vision\datasets\[dataset_name]\`

**Recommended datasets to search:**
- "Wall Crack Detection"
- "Surface Defect Segmentation"
- "Concrete Crack Detection"

**Target: 2–3 datasets, 400–800 total images**

### Option B: Automated Download (Advanced)

If you want to automate downloads:

```bash
# Install dependencies
pip install roboflow kaggle rich

# Set up Roboflow API key
# 1. Get key from https://app.roboflow.com → Settings → API Keys
# 2. Set environment variable:
set ROBOFLOW_API_KEY=your_key_here

# Download datasets
python download_datasets.py --roboflow
```

### Option C: Kaggle Datasets

**For additional data:**

1. **Set up Kaggle API:**
   - Sign in to https://www.kaggle.com
   - Go to Account → API → Create New Token
   - Download `kaggle.json`
   - Move to: `C:\Users\HP\.kaggle\kaggle.json`

2. **Download datasets:**
   ```bash
   python download_datasets.py --kaggle
   ```

### Option D: Your Own Photos (Optional, if time permits)

**Only if you finish early and want domain-specific data:**

Take 50 photos with your phone:
- 15 nail holes (close-up, 30cm distance)
- 10 paint peeling (bathroom corners)
- 10 water stains (ceilings, AC vents)
- 15 small cracks (wall corners)

Save to: `training_data/vision/images/`

---

## ✅ End-of-Day Validation

**After completing both tracks, run:**

```bash
python validate_training_data.py
```

**Expected output:**

```
✓ Acoustic data collection complete
  Hollow: 50 samples
  Solid: 50 samples

✓ Vision data collection complete (estimated 600 images)
  Public datasets: 3
```

**If all checkmarks are green → Ready for Day 3 training!**

---

## 📋 Quick Checklist

Print this and check off as you go:

**Acoustic (manual work):**
- [ ] Voice recorder app configured (WAV, 44.1kHz)
- [ ] Test recording successful
- [ ] 15 hollow door samples
- [ ] 15 drywall samples
- [ ] 10 cardboard samples
- [ ] 10 plastic container samples
- [ ] 15 concrete samples
- [ ] 15 solid wood samples
- [ ] 10 filled container samples
- [ ] 10 tile samples
- [ ] All files transferred to laptop
- [ ] Files organized into hollow/solid folders

**Vision (mostly automated):**
- [ ] Roboflow account created
- [ ] 1st dataset downloaded (200+ images)
- [ ] 2nd dataset downloaded
- [ ] 3rd dataset downloaded (optional)
- [ ] Datasets saved to `training_data/vision/datasets/`

**Final validation:**
- [ ] `validate_training_data.py` shows all green checkmarks
- [ ] `training_data/` folder backed up (external drive or cloud)
- [ ] Ready to start Day 3 model training tomorrow

---

## ⏱️ Realistic Timeline

**Morning (3 hours):**
- 8:00–8:30: Set up recording app, test recordings
- 8:30–10:00: Record hollow samples (50 samples × 1.5 min = 75 min)
- 10:00–10:15: Break
- 10:15–11:45: Record solid samples (50 samples × 1.5 min = 75 min)

**Afternoon (2 hours):**
- 12:00–12:30: Transfer files to laptop, organize folders
- 12:30–13:30: Download vision datasets (2–3 datasets)
- 13:30–14:00: Run validation, backup data

**Total: 5 hours** (can compress to 4 if you skip breaks)

---

## 🆘 Troubleshooting

**Acoustic recording issues:**

| Problem | Solution |
|---------|----------|
| Recordings too quiet | Move phone closer (10–15cm) |
| Distorted/clipping | Reduce mic gain or move phone back (20–25cm) |
| Too much background noise | Record in a quieter room, close windows |
| Samples sound too similar | Use more exaggerated examples (very hollow vs. very solid) |

**Vision dataset issues:**

| Problem | Solution |
|---------|----------|
| Roboflow download fails | Check internet connection, try smaller dataset first |
| Can't find 200+ image datasets | Download 2–3 smaller datasets (100 images each) |
| License unclear | Only use CC BY 4.0, Public Domain, or MIT |
| Wrong format downloaded | Make sure to select "YOLOv8 Segmentation" not "YOLOv5" |

---

## 📞 Need Help?

**During acoustic recording:**
- Run `python acoustic_data_tracker.py` to see real-time progress
- Refer to `ACOUSTIC_RECORDING_CHECKLIST.md` for detailed tips

**During dataset download:**
- Check `DATASET_SOURCES.md` for specific dataset names
- Run `python download_datasets.py --list` to see available datasets

**After completing everything:**
- Run `python validate_training_data.py` to confirm readiness

---

**START NOW:** Begin with acoustic recording (Track 1) while datasets download in the background (Track 2).

Good luck with Day 2 data collection! 🎤📊
