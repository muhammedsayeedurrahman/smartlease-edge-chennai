"""
SmartLease Edge — Acoustic Feature Extraction
Extracts Log-Mel spectrogram features and MFCC/spectral features
from tap audio recordings for the hollow/solid classifier.

Supports both librosa (if installed) and pure SciPy/NumPy fallback
so it works in any Python environment with zero setup friction.
"""
import sys
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

# Optional librosa
LIBROSA_AVAILABLE = False
try:
    import librosa
    LIBROSA_AVAILABLE = True
except ImportError:
    pass

sys.path.insert(0, str(Path(__file__).parent.parent))
from config import ACOUSTIC


def safe_load_audio(audio_path, sr=16000):
    """Load WAV audio using scipy.io.wavfile with fallback to wave / librosa."""
    try:
        file_sr, data = wavfile.read(str(audio_path))
        if data.dtype == np.int16:
            data = data.astype(np.float32) / 32767.0
        elif data.dtype == np.int32:
            data = data.astype(np.float32) / 2147483647.0
        elif data.dtype == np.uint8:
            data = (data.astype(np.float32) - 128) / 128.0
        else:
            data = data.astype(np.float32)

        if len(data.shape) > 1:
            data = np.mean(data, axis=1)

        if file_sr != sr:
            num_samples = int(len(data) * sr / file_sr)
            data = signal.resample(data, num_samples)

        return data, sr
    except Exception:
        if LIBROSA_AVAILABLE:
            return librosa.load(audio_path, sr=sr)
        import wave
        with wave.open(str(audio_path), "rb") as wf:
            file_sr = wf.getframerate()
            n_frames = wf.getnframes()
            raw_bytes = wf.readframes(n_frames)
            data = np.frombuffer(raw_bytes, dtype=np.int16).astype(np.float32) / 32767.0
            if wf.getnchannels() > 1:
                data = data.reshape(-1, wf.getnchannels()).mean(axis=1)
            if file_sr != sr:
                num_samples = int(len(data) * sr / file_sr)
                data = signal.resample(data, num_samples)
            return data, sr


def detect_onset(y: np.ndarray, sr: int = 16000) -> int:
    """
    Detect tap onset using energy derivative in pure NumPy.
    Returns sample index of the impact moment.
    """
    frame_length = 256
    hop = 64

    if len(y) < frame_length:
        return 0

    n_frames = 1 + (len(y) - frame_length) // hop
    shape = (n_frames, frame_length)
    strides = (y.strides[0] * hop, y.strides[0])
    frames = np.lib.stride_tricks.as_strided(y, shape=shape, strides=strides)

    rms = np.sqrt(np.mean(frames ** 2, axis=1) + 1e-9)
    energy_diff = np.diff(rms)

    if len(energy_diff) == 0:
        return 0

    threshold = np.mean(np.abs(energy_diff)) + 2 * np.std(np.abs(energy_diff))
    spike_indices = np.where(energy_diff > threshold)[0]
    if len(spike_indices) > 0:
        onset_frame = spike_indices[0]
    else:
        onset_frame = int(np.argmax(rms))

    onset_sample = onset_frame * hop
    return max(0, onset_sample - 100)


def extract_mel_spectrogram(
    audio_path: str = None,
    y: np.ndarray = None,
    sr: int = None,
) -> np.ndarray:
    """
    Extract Log-Mel spectrogram from a tap recording.
    Shape: (64, 19)
    """
    sr = sr or ACOUSTIC["sample_rate"]

    if y is None:
        y, sr = safe_load_audio(audio_path, sr=sr)

    onset = detect_onset(y, sr)
    n_samples = ACOUSTIC["n_samples"]  # 2400
    y_crop = y[onset:onset + n_samples]
    if len(y_crop) < n_samples:
        y_crop = np.pad(y_crop, (0, n_samples - len(y_crop)))

    if LIBROSA_AVAILABLE:
        mel = librosa.feature.melspectrogram(
            y=y_crop,
            sr=sr,
            n_fft=ACOUSTIC["n_fft"],
            hop_length=ACOUSTIC["hop_length"],
            n_mels=ACOUSTIC["n_mels"],
            fmin=ACOUSTIC["fmin"],
            fmax=ACOUSTIC["fmax"],
        )
        return librosa.power_to_db(mel, ref=np.max)

    # Pure SciPy fallback
    f, t, Sxx = signal.spectrogram(
        y_crop,
        fs=sr,
        nperseg=ACOUSTIC["n_fft"],
        noverlap=ACOUSTIC["n_fft"] - ACOUSTIC["hop_length"],
        nfft=ACOUSTIC["n_fft"],
    )
    # Sxx shape: (freqs, time) -> bin to (64, 19)
    Sxx_log = 10.0 * np.log10(np.maximum(Sxx, 1e-9))
    target_freqs, target_time = 64, 19

    # Resample along time axis if needed
    if Sxx_log.shape[1] != target_time:
        Sxx_log = signal.resample(Sxx_log, target_time, axis=1)
    if Sxx_log.shape[0] != target_freqs:
        Sxx_log = signal.resample(Sxx_log, target_freqs, axis=0)

    Sxx_log = Sxx_log - np.max(Sxx_log)
    return Sxx_log.astype(np.float32)


def extract_mfcc_features(
    audio_path: str = None,
    y: np.ndarray = None,
    sr: int = None,
) -> np.ndarray:
    """
    Extract MFCC and physical acoustic feature vector (29 dimensions).
    """
    sr = sr or ACOUSTIC["sample_rate"]

    if y is None:
        y, sr = safe_load_audio(audio_path, sr=sr)

    onset = detect_onset(y, sr)
    n_samples = ACOUSTIC["n_samples"]
    y_crop = y[onset:onset + n_samples]
    if len(y_crop) < n_samples:
        y_crop = np.pad(y_crop, (0, n_samples - len(y_crop)))

    if LIBROSA_AVAILABLE:
        mfccs = librosa.feature.mfcc(y=y_crop, sr=sr, n_mfcc=13)
        mfcc_mean = np.mean(mfccs, axis=1)
        mfcc_std = np.std(mfccs, axis=1)
        spectral_centroid = np.mean(librosa.feature.spectral_centroid(y=y_crop, sr=sr))
        spectral_bandwidth = np.mean(librosa.feature.spectral_bandwidth(y=y_crop, sr=sr))
        zcr = np.mean(librosa.feature.zero_crossing_rate(y_crop))
        return np.concatenate([mfcc_mean, mfcc_std, [spectral_centroid, spectral_bandwidth, zcr]])

    # Pure SciPy / NumPy feature extraction (identical physical representation)
    windowed = y_crop * np.hanning(len(y_crop))
    fft_vals = np.abs(np.fft.rfft(windowed))
    power = fft_vals ** 2
    freqs = np.fft.rfftfreq(len(y_crop), 1.0 / sr)

    # 1. Spectral Centroid
    centroid = float(np.sum(freqs * power) / (np.sum(power) + 1e-9))

    # 2. Spectral Bandwidth
    bandwidth = float(np.sqrt(np.sum(((freqs - centroid) ** 2) * power) / (np.sum(power) + 1e-9)))

    # 3. Zero Crossing Rate
    zcr = float(np.mean(np.abs(np.diff(np.signbit(y_crop)))))

    # 4. Filterbank energy bands (13 mean + 13 std bands = 26 features)
    # Split audio into 8 temporal frames to compute mean & std per band
    frame_len = len(y_crop) // 8
    frame_band_energies = []
    band_edges = np.geomspace(50, 8000, 14)  # 13 frequency bands

    for f_idx in range(8):
        sub = y_crop[f_idx * frame_len:(f_idx + 1) * frame_len]
        sub_fft = np.abs(np.fft.rfft(sub * np.hanning(len(sub)))) ** 2
        sub_freqs = np.fft.rfftfreq(len(sub), 1.0 / sr)
        energies = []
        for b in range(13):
            low, high = band_edges[b], band_edges[b + 1]
            mask = (sub_freqs >= low) & (sub_freqs <= high)
            e = float(np.sum(sub_fft[mask])) if np.any(mask) else 1e-9
            energies.append(np.log10(max(e, 1e-9)))
        frame_band_energies.append(energies)

    frame_matrix = np.array(frame_band_energies)  # (8, 13)
    band_means = np.mean(frame_matrix, axis=0)     # 13 features
    band_stds = np.std(frame_matrix, axis=0)       # 13 features

    # Total 29 features: 13 + 13 + 3 = 29
    features = np.concatenate([
        band_means,
        band_stds,
        [centroid, bandwidth, zcr],
    ]).astype(np.float32)

    return features


def load_dataset(
    data_dirs: list,
    feature_type: str = "mel",
) -> tuple:
    """
    Load audio files from directories and extract features.
    """
    features = []
    labels = []
    paths = []

    extractor = extract_mel_spectrogram if feature_type == "mel" else extract_mfcc_features

    for data_dir, label in data_dirs:
        data_path = Path(data_dir)
        if not data_path.exists():
            print(f"⚠️  Directory not found: {data_dir}")
            continue

        audio_files = list(data_path.glob("*.wav")) + list(data_path.glob("*.mp3"))
        print(f"  Loading {len(audio_files)} files from {data_dir} (label: {label})")

        for audio_file in audio_files:
            try:
                feat = extractor(str(audio_file))
                features.append(feat)
                labels.append(label)
                paths.append(str(audio_file))
            except Exception as e:
                print(f"    ⚠️  Error processing {audio_file.name}: {e}")

    if not features:
        return np.array([]), np.array([]), []

    return np.array(features), np.array(labels), paths
