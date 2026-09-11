"""
SmartLease Edge — Central Configuration
All paths, thresholds, model hyperparameters, and cost tables.
No magic numbers anywhere else in the codebase.
"""
from pathlib import Path

# ─── Project Paths ────────────────────────────────────────────
ROOT_DIR = Path(__file__).parent
DATA_DIR = ROOT_DIR / "data"
DATASETS_DIR = ROOT_DIR / "datasets"
MODELS_DIR = ROOT_DIR / "models"
OUTPUTS_DIR = ROOT_DIR / "outputs"
AUDIO_DATA_DIR = DATA_DIR / "audio"
SYNTHETIC_AUDIO_DIR = DATA_DIR / "synthetic_audio"

# ─── Vision Config ────────────────────────────────────────────
VISION = {
    "model_base": "yolov8n-seg.pt",         # Nano — smallest, NPU-friendly
    "imgsz": 640,
    "epochs": 80,
    "batch_size": 16,                         # Lower to 8 if GPU OOM
    "patience": 15,
    "conf_threshold": 0.3,
    "iou_threshold": 0.45,
    "classes": ["crack", "mold", "peeling_paint", "stairstep_crack", "water_seepage"],
    "augmentation": {
        "mosaic": 1.0,
        "flipud": 0.5,
        "fliplr": 0.5,
        "degrees": 15.0,
        "translate": 0.1,
        "scale": 0.5,
        "hsv_h": 0.015,
        "hsv_s": 0.7,
        "hsv_v": 0.4,
    },
}

# ─── Acoustic Config ─────────────────────────────────────────
ACOUSTIC = {
    "sample_rate": 16000,
    "duration_sec": 0.150,                    # 150ms transient window
    "n_samples": 2400,                        # 16000 * 0.150
    "n_fft": 512,
    "hop_length": 128,
    "n_mels": 64,
    "fmin": 50,
    "fmax": 8000,
    "input_shape": (1, 64, 19),               # (C, n_mels, time_frames)
    "num_classes": 2,
    "classes": ["hollow", "solid"],
    "train_epochs": 100,
    "train_batch_size": 32,
    "learning_rate": 1e-3,
    "dropout": 0.25,
    "synthetic_samples_per_class": 100,
    "rms_threshold": 0.02,                    # Tap detection energy threshold
    "augmentation": {
        "pitch_shift_semitones": 2,
        "time_stretch_range": (0.9, 1.1),
        "noise_snr_db": [15, 20, 25],
    },
}

# ─── Report Config ────────────────────────────────────────────
REPORT = {
    # Chennai-specific repair cost tables (INR per sq.ft. or per unit)
    "cost_table": {
        "crack": {"minor": (500, 1500), "moderate": (1500, 4000), "severe": (4000, 8000)},
        "mold": {"minor": (500, 1200), "moderate": (1200, 3000), "severe": (3000, 6000)},
        "peeling_paint": {"minor": (350, 900), "moderate": (900, 2200), "severe": (2200, 4500)},
        "stairstep_crack": {"minor": (1200, 2500), "moderate": (2500, 6000), "severe": (6000, 14000)},
        "water_seepage": {"minor": (800, 2000), "moderate": (2000, 5000), "severe": (5000, 11000)},
        "stain": {"minor": (300, 800), "moderate": (800, 2000), "severe": (2000, 5000)},
        "corrosion": {"minor": (400, 1000), "moderate": (1000, 3000), "severe": (3000, 7000)},
        "deterioration": {"minor": (600, 1500), "moderate": (1500, 4000), "severe": (4000, 10000)},
        "hollow_tile": {"per_sqft": (3000, 8000)},
    },
    "severity_thresholds": {
        "minor": 2.0,       # < 2 sq.ft.
        "moderate": 5.0,    # 2-5 sq.ft.
        # > 5 sq.ft. = severe
    },
    "verdict_rules": {
        "full_refund_max_issues": 0,
        "partial_deduction_max_cost": 5000,
        # Above 5000 INR = significant deduction
    },
}

# ─── GenieX / LLM Config ─────────────────────────────────────
GENIEX = {
    "model_name": "llama-3.2-3b-instruct",
    "fallback_model": "llama-3.2-1b-instruct",
    "context_window": 1024,
    "max_tokens": 512,
    "runtime": "qairt",                       # Hexagon NPU via QAIRT
    "fallback_runtime": "llama_cpp",          # CPU fallback
}

# ─── iQOO 15 Hardware Specs (for reference) ──────────────────
IQOO15 = {
    "chipset": "Snapdragon 8 Elite Gen 5",
    "npu": "Hexagon HTP",
    "camera_main": "Sony IMX921 50MP OIS",
    "camera_ultrawide": "50MP",
    "camera_telephoto": "Periscope 100x digital",
    "ir_blaster": True,
    "ir_frequency": 38000,                    # 38kHz carrier
    "battery_mah": 7000,
    "display_nits": 6000,
    "cooling": "8K Vapor Chamber",
}
