"""
Decide the shipped feature set by measurement, not by preference.

Two questions this answers:

1. Can we drop MFCCs? They are the one feature here that is genuinely hard to reproduce
   faithfully in Kotlin (mel filterbank construction + DCT-II, with librosa's exact
   normalisation conventions). Any mismatch between the Python trainer and the Kotlin
   runtime is silent train/serve skew -- the model sees different numbers in production
   than it was fitted on, and accuracy quietly degrades with nothing in the logs. If a
   portable feature set scores the same, dropping MFCCs removes that entire failure class.

2. Does augmentation help? Gain, noise, time-shift and resampling perturbations are applied
   to TRAINING FOLDS ONLY, and augmented copies inherit their parent's group so they can
   never leak into the fold being scored.

Every number is Leave-One-Group-Out, grouped by source recording.
"""

import warnings

warnings.filterwarnings("ignore")

import numpy as np
import librosa
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import LeaveOneGroupOut
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from train_tap_classifier import CLASSES, SAMPLE_RATE, segment_taps, wilson_interval

import glob
import os

# Bands chosen to be reproducible from a plain FFT magnitude spectrum in Kotlin.
BAND_EDGES_HZ = [0, 250, 500, 1000, 2000, 4000, 8000, 11025]


def portable_features(clip, sr=SAMPLE_RATE):
    """
    Features computable in Kotlin from one real FFT, with no filterbank or DCT.

    Everything here is a moment or a ratio of the magnitude spectrum, plus a time-domain
    decay measure -- each a handful of lines of Kotlin that can be verified against golden
    vectors exported from this trainer.
    """
    n_fft = 512
    stft = np.abs(librosa.stft(clip, n_fft=n_fft, hop_length=128)) + 1e-10
    power = (stft ** 2).sum(axis=1)
    freqs = librosa.fft_frequencies(sr=sr, n_fft=n_fft)
    total = power.sum() + 1e-10

    # Spectral moments
    centroid = float((freqs * power).sum() / total)
    spread = float(np.sqrt(((freqs - centroid) ** 2 * power).sum() / total))
    cumulative = np.cumsum(power)
    rolloff85 = float(freqs[np.searchsorted(cumulative, 0.85 * total)])
    rolloff95 = float(freqs[np.searchsorted(cumulative, 0.95 * total)])
    geo = float(np.exp(np.log(power + 1e-12).mean()))
    flatness = geo / (float(power.mean()) + 1e-12)

    # Band energy ratios -- the physical hollow/solid cue, stated directly
    bands = []
    for lo, hi in zip(BAND_EDGES_HZ[:-1], BAND_EDGES_HZ[1:]):
        mask = (freqs >= lo) & (freqs < hi)
        bands.append(float(power[mask].sum() / total))

    # Time domain: zero crossings and how fast the tap dies
    zcr = float(np.mean(np.abs(np.diff(np.sign(clip))) > 0))
    env = np.abs(clip) / (np.max(np.abs(clip)) + 1e-12)
    peak = int(np.argmax(env))
    tail = env[peak:]
    for thresh, out in ((0.1, "t10"), (0.5, "t50")):
        pass
    below10 = np.where(tail < 0.1)[0]
    below50 = np.where(tail < 0.5)[0]
    decay10 = float(below10[0]) / sr * 1000.0 if below10.size else float(tail.size) / sr * 1000.0
    decay50 = float(below50[0]) / sr * 1000.0 if below50.size else float(tail.size) / sr * 1000.0

    # Log-energy envelope slope: a single-number decay rate, robust to threshold choice
    frame_energy = (stft ** 2).sum(axis=0)
    log_energy = np.log(frame_energy + 1e-12)
    idx = np.arange(len(log_energy), dtype=np.float64)
    slope = float(np.polyfit(idx, log_energy, 1)[0]) if len(log_energy) > 2 else 0.0

    return np.array(
        [centroid, spread, rolloff85, rolloff95, flatness, zcr, decay10, decay50, slope]
        + bands,
        dtype=np.float32,
    )


def full_features(clip, sr=SAMPLE_RATE):
    """Portable set plus MFCC statistics (the part that is awkward to port)."""
    stft = np.abs(librosa.stft(clip, n_fft=512, hop_length=128)) + 1e-10
    mel = librosa.feature.melspectrogram(S=stft ** 2, sr=sr, n_mels=40)
    mfcc = librosa.feature.mfcc(S=librosa.power_to_db(mel), n_mfcc=13)
    return np.concatenate([portable_features(clip, sr), mfcc.mean(axis=1), mfcc.std(axis=1)])


def augment(clip, rng, n=6):
    """Plausible recording variations. Cannot add information -- only reduce overfit."""
    out = []
    for _ in range(n):
        aug = clip.copy()
        aug = aug * rng.uniform(0.6, 1.4)                                  # mic gain / strike force
        aug = aug + rng.normal(0, rng.uniform(0.0005, 0.005), aug.shape)   # room / preamp noise
        shift = rng.integers(-200, 200)                                    # onset alignment jitter
        aug = np.roll(aug, shift)
        rate = rng.uniform(0.95, 1.05)                                     # slight timbre shift
        stretched = librosa.effects.time_stretch(aug, rate=rate)
        if stretched.size < clip.size:
            stretched = np.pad(stretched, (0, clip.size - stretched.size))
        aug = stretched[: clip.size]
        peak = np.max(np.abs(aug))
        if peak > 1e-6:
            aug = aug / peak
        out.append(aug.astype(np.float32))
    return out


def load_clips(audio_root):
    clips, labels, groups = [], [], []
    for label, cls in enumerate(CLASSES):
        for path in sorted(glob.glob(os.path.join(audio_root, cls, "*.*"))):
            for clip in segment_taps(path):
                clips.append(clip)
                labels.append(label)
                groups.append(os.path.basename(path))
    return clips, np.array(labels), np.array(groups)


def run(clips, y, groups, featfn, use_aug, seed=0):
    rng = np.random.default_rng(seed)
    X = np.array([featfn(c) for c in clips])
    logo = LeaveOneGroupOut()
    correct = total = 0
    for tr, te in logo.split(X, y, groups):
        if len(np.unique(y[tr])) < 2:
            continue
        Xtr, ytr = X[tr], y[tr]
        if use_aug:
            extra_X, extra_y = [], []
            for i in tr:
                for a in augment(clips[i], rng):
                    extra_X.append(featfn(a))
                    extra_y.append(y[i])
            Xtr = np.vstack([Xtr, np.array(extra_X)])
            ytr = np.concatenate([ytr, np.array(extra_y)])
        model = make_pipeline(
            StandardScaler(),
            LogisticRegression(max_iter=5000, C=0.5, class_weight="balanced"),
        )
        model.fit(Xtr, ytr)
        correct += int((model.predict(X[te]) == y[te]).sum())
        total += len(te)
    return correct, total


def main():
    clips, y, groups = load_clips("../data/audio")
    print("%d taps from %d recordings\n" % (len(clips), len(set(groups))))
    print("  %-34s %7s   %-22s" % ("configuration", "n_feat", "grouped LOGO accuracy"))
    print("  " + "-" * 72)

    for label, featfn in (("portable (no MFCC)", portable_features), ("full (with MFCC)", full_features)):
        n_feat = len(featfn(clips[0]))
        for aug_label, use_aug in ((" ", False), (" + augmentation", True)):
            c, t = run(clips, y, groups, featfn, use_aug)
            lo, hi = wilson_interval(c, t)
            print("  %-34s %7d   %5.1f%%  (%2d/%d)  95%% CI %.0f-%.0f%%"
                  % (label + aug_label, n_feat, c / t * 100, c, t, lo * 100, hi * 100))


if __name__ == "__main__":
    main()
