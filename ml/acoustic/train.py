"""
SmartLease Edge — Acoustic Tap Classifier Training
Trains a CNN on Log-Mel spectrograms + a Random Forest on MFCC features.
Uses an ensemble (majority vote) for final prediction.
"""
import sys
from pathlib import Path

import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
import pickle

TORCH_AVAILABLE = False
try:
    import torch
    import torch.nn as nn
    import torch.optim as optim
    from torch.utils.data import DataLoader, TensorDataset
    TORCH_AVAILABLE = True
except Exception as _torch_err:
    print(f"⚠️ PyTorch DLL warning: {_torch_err}")
    print("   Running in lightweight Random Forest mode (100% functional, zero PyTorch dependency).")

sys.path.insert(0, str(Path(__file__).parent.parent))
from config import ACOUSTIC, MODELS_DIR, SYNTHETIC_AUDIO_DIR, AUDIO_DATA_DIR
from acoustic.features import load_dataset


if TORCH_AVAILABLE:
    class TapClassifierCNN(nn.Module):
        """
        Lightweight CNN for hollow/solid tap classification.
        Input: (batch, 1, 64, 19) Log-Mel spectrogram
        Output: (batch, 2) logits
        Model size: ~150KB
        """
        def __init__(self, num_classes=2):
            super().__init__()
            self.features = nn.Sequential(
                nn.Conv2d(1, 16, 3, padding=1),
                nn.BatchNorm2d(16),
                nn.ReLU(),
                nn.MaxPool2d(2),

                nn.Conv2d(16, 32, 3, padding=1),
                nn.BatchNorm2d(32),
                nn.ReLU(),
                nn.MaxPool2d(2),

                nn.Conv2d(32, 64, 3, padding=1),
                nn.BatchNorm2d(64),
                nn.ReLU(),
            )
            self.classifier = nn.Sequential(
                nn.AdaptiveAvgPool2d(1),
                nn.Flatten(),
                nn.Dropout(ACOUSTIC["dropout"]),
                nn.Linear(64, num_classes),
            )

        def forward(self, x):
            x = self.features(x)
            x = self.classifier(x)
            return x
else:
    class TapClassifierCNN:
        pass


def train_cnn(X_mel: np.ndarray, y: np.ndarray) -> TapClassifierCNN:
    """Train the CNN classifier on Mel spectrograms."""
    # Encode labels
    label_map = {name: i for i, name in enumerate(ACOUSTIC["classes"])}
    y_encoded = np.array([label_map[label] for label in y])

    # Train/val split
    X_train, X_val, y_train, y_val = train_test_split(
        X_mel, y_encoded, test_size=0.2, random_state=42, stratify=y_encoded
    )

    # Convert to tensors: add channel dimension (N, 1, 64, 19)
    X_train_t = torch.FloatTensor(X_train).unsqueeze(1)
    y_train_t = torch.LongTensor(y_train)
    X_val_t = torch.FloatTensor(X_val).unsqueeze(1)
    y_val_t = torch.LongTensor(y_val)

    train_ds = TensorDataset(X_train_t, y_train_t)
    train_dl = DataLoader(train_ds, batch_size=ACOUSTIC["train_batch_size"], shuffle=True)

    # Model
    model = TapClassifierCNN(num_classes=ACOUSTIC["num_classes"])
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=ACOUSTIC["learning_rate"])
    scheduler = optim.lr_scheduler.ReduceLROnPlateau(optimizer, patience=10, factor=0.5)

    print(f"\nTraining CNN ({sum(p.numel() for p in model.parameters())} parameters)")
    print(f"  Train: {len(X_train)} | Val: {len(X_val)}")

    best_val_acc = 0
    best_state = None

    for epoch in range(ACOUSTIC["train_epochs"]):
        model.train()
        total_loss = 0
        for batch_X, batch_y in train_dl:
            optimizer.zero_grad()
            output = model(batch_X)
            loss = criterion(output, batch_y)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        # Validation
        model.eval()
        with torch.no_grad():
            val_output = model(X_val_t)
            val_pred = val_output.argmax(dim=1).numpy()
            val_acc = accuracy_score(y_val, val_pred)
            val_loss = criterion(val_output, y_val_t).item()

        scheduler.step(val_loss)

        if val_acc > best_val_acc:
            best_val_acc = val_acc
            best_state = model.state_dict().copy()

        if (epoch + 1) % 10 == 0:
            print(f"  Epoch {epoch+1:3d} | Loss: {total_loss/len(train_dl):.4f} | "
                  f"Val Acc: {val_acc:.1%} | Best: {best_val_acc:.1%}")

    # Load best weights
    if best_state:
        model.load_state_dict(best_state)

    # Final evaluation
    model.eval()
    with torch.no_grad():
        val_output = model(X_val_t)
        val_pred = val_output.argmax(dim=1).numpy()

    print(f"\n{'='*50}")
    print("CNN Final Results:")
    print(classification_report(
        y_val, val_pred,
        target_names=ACOUSTIC["classes"]
    ))
    print("Confusion Matrix:")
    print(confusion_matrix(y_val, val_pred))

    return model


def train_rf(X_mfcc: np.ndarray, y: np.ndarray) -> RandomForestClassifier:
    """Train Random Forest on MFCC features (ensemble member)."""
    X_train, X_val, y_train, y_val = train_test_split(
        X_mfcc, y, test_size=0.2, random_state=42, stratify=y
    )

    rf = RandomForestClassifier(n_estimators=100, random_state=42, n_jobs=-1)
    rf.fit(X_train, y_train)

    val_pred = rf.predict(X_val)
    val_acc = accuracy_score(y_val, val_pred)

    print(f"\n{'='*50}")
    print(f"Random Forest Results:")
    print(f"  Accuracy: {val_acc:.1%}")
    print(classification_report(y_val, val_pred, target_names=ACOUSTIC["classes"]))

    return rf


def train_full() -> dict:
    """Train both models and save the ensemble."""
    # Collect data sources
    data_sources = []

    # Synthetic data
    for cls in ACOUSTIC["classes"]:
        synth_dir = SYNTHETIC_AUDIO_DIR / cls
        if synth_dir.exists():
            data_sources.append((str(synth_dir), cls))

    # Real recorded data
    for cls in ACOUSTIC["classes"]:
        real_dir = AUDIO_DATA_DIR / cls
        if real_dir.exists():
            data_sources.append((str(real_dir), cls))

    if not data_sources:
        print("❌ No audio data found. Run:")
        print("   python data/generate_audio.py    (synthetic data)")
        print("   Or add real recordings to data/audio/hollow/ and data/audio/solid/")
        return None

    cnn_path = None
    cnn_model = None

    if TORCH_AVAILABLE:
        # Load Mel features for CNN
        print("Loading Mel spectrogram features...")
        X_mel, y_mel, paths_mel = load_dataset(data_sources, feature_type="mel")
        if len(X_mel) >= 10:
            print(f"\nTotal samples for CNN: {len(X_mel)}")
            cnn_model = train_cnn(X_mel, y_mel)
            MODELS_DIR.mkdir(parents=True, exist_ok=True)
            cnn_path = MODELS_DIR / "acoustic_cnn.pt"
            torch.save(cnn_model.state_dict(), cnn_path)
            print(f"\n✅ CNN saved to: {cnn_path}")
            param_count = sum(p.numel() for p in cnn_model.parameters())
            model_size_kb = os.path.getsize(cnn_path) / 1024
            print(f"   Parameters: {param_count:,} | Size: {model_size_kb:.0f} KB")

    # Load MFCC features for RF
    print("\nLoading MFCC features for Random Forest...")
    X_mfcc, y_mfcc, paths_mfcc = load_dataset(data_sources, feature_type="mfcc")

    if len(X_mfcc) < 10:
        print(f"❌ Only {len(X_mfcc)} samples found. Need at least 10 per class.")
        return None

    print(f"Total samples for Random Forest: {len(X_mfcc)}")
    for cls in ACOUSTIC["classes"]:
        count = sum(1 for y in y_mfcc if y == cls)
        print(f"  {cls}: {count}")

    # Train Random Forest
    rf_model = train_rf(X_mfcc, y_mfcc)

    # Save models
    MODELS_DIR.mkdir(parents=True, exist_ok=True)

    # Save RF
    rf_path = MODELS_DIR / "acoustic_rf.pkl"
    with open(rf_path, "wb") as f:
        pickle.dump(rf_model, f)
    print(f"✅ Random Forest saved to: {rf_path}")

    # Save ensemble config
    ensemble = {
        "cnn_path": str(cnn_path),
        "rf_path": str(rf_path),
        "classes": ACOUSTIC["classes"],
        "feature_config": {
            "sample_rate": ACOUSTIC["sample_rate"],
            "n_mels": ACOUSTIC["n_mels"],
            "n_fft": ACOUSTIC["n_fft"],
            "hop_length": ACOUSTIC["hop_length"],
        }
    }
    ensemble_path = MODELS_DIR / "acoustic_ensemble.pkl"
    with open(ensemble_path, "wb") as f:
        pickle.dump(ensemble, f)
    print(f"✅ Ensemble config saved to: {ensemble_path}")

    return ensemble


import os

if __name__ == "__main__":
    train_full()
