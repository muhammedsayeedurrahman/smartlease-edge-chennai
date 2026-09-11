"""
SmartLease Edge -- hollow/solid tap classifier: segment, train, and honestly evaluate.

Pipeline
    1. Onset-segment each recording into individual taps (a 1.5s clip holds 1-3 taps).
    2. Extract a compact spectral/temporal feature vector per tap.
    3. Evaluate with Leave-One-Group-Out CV, grouped by SOURCE RECORDING.
    4. Train a final model on everything and report what it is and is not.

Why grouped CV is not optional here
-----------------------------------
Taps from one recording share a wall, a mic position, a room and one codec pass. Splitting
randomly by tap puts siblings in train and test, and the score that comes back measures
"can it recognise this recording" rather than "can it recognise a hollow wall". With this
few samples that inflation is severe, so every reported number here is grouped.

The majority-class baseline is printed alongside the model. A classifier that does not
clearly beat it has learned nothing worth shipping.
"""

import argparse
import glob
import json
import os
import warnings
from pathlib import Path

warnings.filterwarnings("ignore")

import numpy as np
import librosa

SAMPLE_RATE = 22050
TAP_WINDOW_S = 0.25          # a tap transient is done well inside 250 ms
N_MELS = 40
CLASSES = ["solid", "hollow"]   # index 0, 1


def segment_taps(path, sr=SAMPLE_RATE):
    """Return a list of fixed-length clips, one per detected tap onset."""
    y, sr = librosa.load(path, sr=sr, mono=True)
    if y.size == 0:
        return []
    onsets = librosa.onset.onset_detect(
        y=y, sr=sr, units="samples", backtrack=False,
        pre_max=10, post_max=10, pre_avg=50, post_avg=50, delta=0.25, wait=15,
    )
    width = int(TAP_WINDOW_S * sr)
    clips = []
    for start in onsets:
        s = max(0, int(start) - int(0.01 * sr))   # small pre-roll to keep the attack
        clip = y[s:s + width]
        if clip.size < width:
            clip = np.pad(clip, (0, width - clip.size))
        peak = np.max(np.abs(clip))
        if peak < 1e-4:
            continue
        clips.append(clip / peak)                 # amplitude-normalise: loudness is not the cue
    return clips


def extract_features(clip, sr=SAMPLE_RATE):
    """Compact descriptor of a single tap: where the energy sits, and how fast it dies."""
    stft = np.abs(librosa.stft(clip, n_fft=512, hop_length=128)) + 1e-10
    mel = librosa.feature.melspectrogram(S=stft ** 2, sr=sr, n_mels=N_MELS)
    logmel = librosa.power_to_db(mel)

    centroid = librosa.feature.spectral_centroid(S=stft, sr=sr)[0]
    rolloff = librosa.feature.spectral_rolloff(S=stft, sr=sr, roll_percent=0.85)[0]
    bandwidth = librosa.feature.spectral_bandwidth(S=stft, sr=sr)[0]
    flatness = librosa.feature.spectral_flatness(S=stft)[0]
    zcr = librosa.feature.zero_crossing_rate(clip, frame_length=512, hop_length=128)[0]
    mfcc = librosa.feature.mfcc(S=logmel, n_mfcc=13)

    # Energy decay: hollow taps ring briefly, solid taps thud and stop.
    env = np.abs(librosa.util.normalize(clip))
    peak_idx = int(np.argmax(env))
    tail = env[peak_idx:]
    below = np.where(tail < 0.1)[0]
    decay_samples = float(below[0]) if below.size else float(tail.size)
    decay_ms = decay_samples / sr * 1000.0

    # Band energy split -- the physical intuition behind the heuristic in the Kotlin app.
    freqs = librosa.fft_frequencies(sr=sr, n_fft=512)
    power = (stft ** 2).sum(axis=1)
    low = power[freqs < 500].sum()
    mid = power[(freqs >= 500) & (freqs < 2000)].sum()
    high = power[freqs >= 2000].sum()
    total = low + mid + high + 1e-10

    return np.concatenate([
        mfcc.mean(axis=1), mfcc.std(axis=1),
        [centroid.mean(), centroid.std(),
         rolloff.mean(), bandwidth.mean(), flatness.mean(), zcr.mean(),
         decay_ms, low / total, mid / total, high / total],
    ]).astype(np.float32)


def wilson_interval(successes, n, z=1.96):
    """
    Wilson score interval for a proportion.

    Used instead of a bare accuracy because at n=15 the normal approximation is useless and
    a point estimate invites overclaiming. Wilson stays sane for small n and near 0/1.
    """
    if n == 0:
        return (0.0, 1.0)
    p = successes / n
    denom = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denom
    margin = z * ((p * (1 - p) / n + z * z / (4 * n * n)) ** 0.5) / denom
    return (max(0.0, centre - margin), min(1.0, centre + margin))


def build_dataset(audio_root):
    X, y, groups, names = [], [], [], []
    for label, cls in enumerate(CLASSES):
        for path in sorted(glob.glob(os.path.join(audio_root, cls, "*.*"))):
            clips = segment_taps(path)
            for clip in clips:
                X.append(extract_features(clip))
                y.append(label)
                groups.append(os.path.basename(path))   # group = one recording
            names.append((os.path.basename(path), cls, len(clips)))
    return np.array(X), np.array(y), np.array(groups), names


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--audio", default="ml/data/audio")
    ap.add_argument("--out", default="ml/models")
    args = ap.parse_args()

    from sklearn.ensemble import RandomForestClassifier
    from sklearn.linear_model import LogisticRegression
    from sklearn.model_selection import LeaveOneGroupOut
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler

    print("=" * 68)
    print("Acoustic tap classifier -- segmentation, training, honest evaluation")
    print("=" * 68)

    X, y, groups, names = build_dataset(args.audio)
    print("\nSource recordings:")
    for n, c, k in names:
        print("  %-7s %d tap(s)  %s" % (c, k, n[:44]))

    n_hollow = int((y == 1).sum())
    n_solid = int((y == 0).sum())
    print("\nDataset: %d taps (%d solid, %d hollow) from %d recordings, %d features each"
          % (len(X), n_solid, n_hollow, len(set(groups)), X.shape[1]))

    if len(X) < 4:
        print("\nNot enough taps to evaluate. Record more audio.")
        return

    majority = max(n_solid, n_hollow) / len(y)
    print("Majority-class baseline: %.1f%%  <- the number to beat\n" % (majority * 100))

    models = {
        "RandomForest": RandomForestClassifier(
            n_estimators=300, min_samples_leaf=1, random_state=0, class_weight="balanced"),
        "LogisticRegression": make_pipeline(
            StandardScaler(), LogisticRegression(max_iter=2000, C=0.5, class_weight="balanced")),
    }

    logo = LeaveOneGroupOut()
    results, eval_totals = {}, {}
    for name, model in models.items():
        correct, total, per_fold = 0, 0, []
        for tr, te in logo.split(X, y, groups):
            if len(np.unique(y[tr])) < 2:
                continue                      # fold has only one class in train -- unusable
            model.fit(X[tr], y[tr])
            pred = model.predict(X[te])
            hit = int((pred == y[te]).sum())
            correct += hit
            total += len(te)
            per_fold.append("%d/%d" % (hit, len(te)))
        acc = correct / total if total else float("nan")
        results[name] = acc
        eval_totals[name] = total
        print("%-20s grouped LOGO accuracy: %6.1f%%  (%d/%d)  folds: %s"
              % (name, acc * 100, correct, total, " ".join(per_fold)))

    best_name = max(results, key=results.get)
    best_acc = results[best_name]
    n_total = eval_totals[best_name]
    lo, hi = wilson_interval(round(best_acc * n_total), n_total)
    print("\nBest: %s at %.1f%% vs %.1f%% baseline" % (best_name, best_acc * 100, majority * 100))
    print("95%% CI: %.1f%% - %.1f%% (n=%d). The interval is this wide because the dataset is"
          % (lo * 100, hi * 100, n_total))
    print("        this small -- quote the interval, never the point estimate alone.")

    if best_acc > majority + 0.15:
        verdict = "BEATS baseline -- worth shipping, but validate on freshly recorded taps"
    else:
        verdict = (
            "DOES NOT clearly beat the majority-class baseline. On this much data that is the "
            "expected outcome: the model has not learned a hollow-vs-solid rule, it has fit noise. "
            "Keep the deterministic heuristic in AcousticTapClassifier.kt until more taps exist."
        )
    print("\nVERDICT: %s" % verdict)

    # --- Fit the final model on every tap and persist it -------------------------------
    # CV above estimates how well this recipe generalises; it does not leave a usable model
    # behind. The shipped classifier is refit here on all data, because with a dataset this
    # small throwing away any fold's worth of examples is a real loss.
    import joblib

    final_model = models[best_name]
    final_model.fit(X, y)
    train_acc = float((final_model.predict(X) == y).mean())

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    model_path = out / "acoustic_tap_clf.joblib"
    joblib.dump(
        {
            "model": final_model,
            "classes": CLASSES,
            "sample_rate": SAMPLE_RATE,
            "tap_window_s": TAP_WINDOW_S,
            "n_features": int(X.shape[1]),
            "feature_order": (
                "mfcc_mean[13], mfcc_std[13], centroid_mean, centroid_std, rolloff_mean, "
                "bandwidth_mean, flatness_mean, zcr_mean, decay_ms, low_ratio, mid_ratio, high_ratio"
            ),
            "trained_on": {"n_taps": int(len(X)), "recordings": sorted(set(groups.tolist()))},
            "grouped_logo_accuracy": float(best_acc),
            "grouped_logo_ci95": [lo, hi],
        },
        model_path,
    )
    print("\nFinal model: %s refit on all %d taps" % (best_name, len(X)))
    print("  training-set accuracy %.1f%%  <- NOT a generalisation estimate, it has seen this data"
          % (train_acc * 100))
    print("  honest estimate is the grouped CV figure above: %.1f%% (95%% CI %.1f-%.1f%%)"
          % (best_acc * 100, lo * 100, hi * 100))
    print("  saved -> %s" % model_path)

    report = {
        "n_taps": int(len(X)), "n_solid": n_solid, "n_hollow": n_hollow,
        "n_recordings": len(set(groups)),
        "majority_baseline": majority,
        "grouped_logo_accuracy": {k: float(v) for k, v in results.items()},
        "best_model": best_name,
        "best_accuracy_95ci": [lo, hi],
        "final_model_path": str(model_path),
        "final_train_accuracy": train_acc,
        "n_evaluated": n_total,
        "evaluation": "LeaveOneGroupOut, grouped by source recording (no sibling leakage)",
        "verdict": verdict,
    }
    (out / "acoustic_report.json").write_text(json.dumps(report, indent=2))
    print("\nWrote %s" % (out / "acoustic_report.json"))

    print("\nTo make this real, record with the phone the demo runs on:")
    print("  - 100+ taps per class, several walls/rooms per class")
    print("  - uncompressed WAV at 44.1 kHz, NOT WhatsApp voice notes (Opus at ~16 kHz")
    print("    discards the high-frequency detail that separates hollow from solid)")
    print("  - label by ground truth, not by ear")


if __name__ == "__main__":
    main()
