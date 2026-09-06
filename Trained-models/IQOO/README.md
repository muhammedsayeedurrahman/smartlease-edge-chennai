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
