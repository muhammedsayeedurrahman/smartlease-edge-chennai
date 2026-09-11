"""
SmartLease Edge — Acoustic Tap Prediction
Classifies a tap recording as hollow or solid using the trained ensemble.
"""
import sys
from pathlib import Path
from dataclasses import dataclass

import numpy as np
import pickle

TORCH_AVAILABLE = False
try:
    import torch
    TORCH_AVAILABLE = True
except Exception:
    pass

sys.path.insert(0, str(Path(__file__).parent.parent))
from config import ACOUSTIC, MODELS_DIR
from acoustic.features import extract_mel_spectrogram, extract_mfcc_features


@dataclass
class TapResult:
    prediction: str         # "hollow" | "solid"
    confidence: float       # 0.0 - 1.0
    cnn_prediction: str
    cnn_confidence: float
    rf_prediction: str
    rf_confidence: float
    spectral_centroid: float
    decay_rate: float


class AcousticPredictor:
    def __init__(self):
        self.cnn_model = None
        self.rf_model = None
        self.classes = ACOUSTIC["classes"]
        self._load_models()

    def _load_models(self):
        # Load CNN
        cnn_path = MODELS_DIR / "acoustic_cnn.pt"
        if TORCH_AVAILABLE and cnn_path.exists():
            try:
                from acoustic.train import TapClassifierCNN
                self.cnn_model = TapClassifierCNN(num_classes=ACOUSTIC["num_classes"])
                self.cnn_model.load_state_dict(torch.load(cnn_path, weights_only=True))
                self.cnn_model.eval()
                print("✅ CNN model loaded")
            except Exception as e:
                print(f"⚠️ CNN model load skipped: {e}")
        elif not TORCH_AVAILABLE and cnn_path.exists():
            pass
        else:
            pass

        # Load RF
        rf_path = MODELS_DIR / "acoustic_rf.pkl"
        if rf_path.exists():
            with open(rf_path, "rb") as f:
                self.rf_model = pickle.load(f)
            print("✅ Random Forest model loaded")
        else:
            print(f"⚠️  RF model not found at {rf_path}")

    def predict(self, audio_path: str = None, y: np.ndarray = None) -> TapResult:
        """
        Classify a tap as hollow or solid using ensemble voting.
        
        Args:
            audio_path: Path to audio file
            y: Raw audio array (alternative to audio_path)
        
        Returns:
            TapResult with predictions and confidence
        """
        # Extract features
        mel = extract_mel_spectrogram(audio_path=audio_path, y=y)
        mfcc = extract_mfcc_features(audio_path=audio_path, y=y)

        cnn_pred, cnn_conf = "unknown", 0.0
        rf_pred, rf_conf = "unknown", 0.0

        # CNN prediction
        if self.cnn_model is not None:
            with torch.no_grad():
                mel_tensor = torch.FloatTensor(mel).unsqueeze(0).unsqueeze(0)  # (1,1,64,19)
                logits = self.cnn_model(mel_tensor)
                probs = torch.softmax(logits, dim=1)[0]
                cnn_idx = probs.argmax().item()
                cnn_pred = self.classes[cnn_idx]
                cnn_conf = probs[cnn_idx].item()

        # RF prediction
        if self.rf_model is not None:
            rf_probs = self.rf_model.predict_proba([mfcc])[0]
            rf_idx = rf_probs.argmax()
            rf_pred = self.classes[rf_idx]
            rf_conf = rf_probs[rf_idx]

        # Ensemble: majority vote with confidence weighting
        if self.cnn_model and self.rf_model:
            if cnn_pred == rf_pred:
                final_pred = cnn_pred
                final_conf = (cnn_conf + rf_conf) / 2
            else:
                # Disagreement: pick the more confident one
                if cnn_conf > rf_conf:
                    final_pred = cnn_pred
                    final_conf = cnn_conf * 0.6  # Penalize for disagreement
                else:
                    final_pred = rf_pred
                    final_conf = rf_conf * 0.6
        elif self.cnn_model:
            final_pred, final_conf = cnn_pred, cnn_conf
        elif self.rf_model:
            final_pred, final_conf = rf_pred, rf_conf
        else:
            return TapResult(
                prediction="unknown", confidence=0.0,
                cnn_prediction="N/A", cnn_confidence=0.0,
                rf_prediction="N/A", rf_confidence=0.0,
                spectral_centroid=float(mfcc[26]) if len(mfcc) > 26 else 0.0,
                decay_rate=0.0,
            )

        return TapResult(
            prediction=final_pred,
            confidence=float(final_conf),
            cnn_prediction=cnn_pred,
            cnn_confidence=float(cnn_conf),
            rf_prediction=rf_pred,
            rf_confidence=float(rf_conf),
            spectral_centroid=float(mfcc[26]) if len(mfcc) > 26 else 0.0,
            decay_rate=0.0,
        )


def predict_single(audio_path: str) -> TapResult:
    """Convenience function for single audio prediction."""
    predictor = AcousticPredictor()
    return predictor.predict(audio_path=audio_path)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Classify a tap recording")
    parser.add_argument("audio", type=str, help="Path to audio file")
    args = parser.parse_args()

    result = predict_single(args.audio)

    icon = "⚠️" if result.prediction == "hollow" else "✅"
    print(f"\n{icon}  Prediction: {result.prediction.upper()}")
    print(f"   Confidence: {result.confidence:.0%}")
    if result.cnn_prediction != "unknown" and result.cnn_prediction != "N/A":
        print(f"   CNN: {result.cnn_prediction} ({result.cnn_confidence:.0%})")
        print(f"   RF:  {result.rf_prediction} ({result.rf_confidence:.0%})")
    else:
        print(f"   Model: Random Forest ({result.rf_confidence:.0%})")

    if result.prediction == "hollow":
        print("\n   ⚠️  Possible void or loose tile/plaster detected")
        print("   Recommended: Further inspection by qualified professional")
    else:
        print("\n   ✅ Surface appears structurally sound")
