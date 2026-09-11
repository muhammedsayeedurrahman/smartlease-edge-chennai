# SmartLease Edge

AI-Powered Property Condition Assessment — Fully Offline

## Quick Start

```bash
# One-command setup (creates venv, installs deps, generates sample data)
python setup.py

# Activate virtual environment
venv\Scripts\activate          # Windows
source venv/bin/activate       # Linux/Mac

# Train models
python -m acoustic.train       # Acoustic tap classifier
python -m vision.train         # Vision defect segmenter

# Generate sample report
python -m report.generator --sample

# Launch demo
python app.py
# Opens at http://localhost:7860
```

## Dataset

The vision dataset is **not tracked in git**. An earlier commit checked in the full
Roboflow export -- 8,731 images and 5,755 label files -- which pushed the repository
to 350 MB and made cloning painful. It is ignored now (`ml/.gitignore`), so fetch it
yourself before training:

**Internal Wall Finishing Defects v5** (CC BY 4.0, 6 classes: paint drips, peeling
paint, pin holes, rough and patchy surface, stain marks, trowels marks)

https://universe.roboflow.com/chew-poh-yee/internal-wall-finishing-defects/dataset/5

Export in YOLOv8 format and unzip so the layout is:

```
ml/data/dataset/internal-defect/
├── data.yaml
├── train/{images,labels}/
├── valid/{images,labels}/
└── test/{images,labels}/
```

Base YOLO weights (`*.pt`) are ignored too -- Ultralytics downloads them on first run.

The acoustic taps under `ml/data/audio/{hollow,solid}/` **are** tracked: they are the
team's own recordings, only 8 files, and not reproducible from anywhere else.

## Project Structure

```
├── setup.py                  # One-command setup
├── config.py                 # All hyperparameters & paths
├── app.py                    # Gradio demo UI
├── requirements.txt          # Dependencies
│
├── data/
│   ├── download_dataset.py   # Dataset download & validation
│   └── generate_audio.py     # Synthetic tap audio generator
│
├── vision/
│   ├── train.py              # YOLOv8-Seg fine-tuning
│   └── predict.py            # Inference + area quantification
│
├── acoustic/
│   ├── features.py           # Log-Mel + MFCC extraction
│   ├── train.py              # CNN + RF ensemble training
│   └── predict.py            # Ensemble inference
│
├── report/
│   ├── templates.py          # Cost tables & verdict logic
│   └── generator.py          # PDF report engine
│
├── export/                   # ExecuTorch/ONNX export (hackathon day)
├── models/                   # Trained model artifacts
├── datasets/                 # Training datasets
├── outputs/                  # Generated PDF reports
└── data/
    ├── audio/                # Real recorded taps
    └── synthetic_audio/      # Generated training audio
```

## Subsystems

| Module | Input | Output | Model |
|--------|-------|--------|-------|
| Vision | Wall photo (640×640) | Defect masks + area (sq.ft.) | YOLOv8n-Seg |
| Acoustic | Tap audio (150ms) | Hollow/Solid + confidence | CNN + Random Forest |
| Report | Structured findings | Signed PDF report | Template-based (GenieX at hackathon) |

## For the Hackathon

The models trained here (`models/vision_best.pt`, `models/acoustic_cnn.pt`) will be exported to ExecuTorch `.pte` format and loaded on the iQOO 15's Snapdragon 8 Elite Hexagon NPU.
