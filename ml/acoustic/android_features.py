"""
Feature extraction defined so Kotlin can reproduce it bit-for-bit.

The trainer in train_tap_classifier.py calls librosa helpers (spectral_centroid,
spectral_rolloff, melspectrogram, mfcc, ...). Each carries conventions -- centred frames,
reflect padding, periodic vs symmetric Hann, power_to_db's top_db clamp, DCT-II ortho
normalisation -- and re-deriving those by hand in Kotlin is where train/serve skew is born:
the model is fitted on one set of numbers and served another, accuracy drops, and nothing
logs an error.

So every step here is written as an explicit formula, and the two matrices that are genuinely
awkward to rebuild (the mel filterbank and the DCT-II basis) are exported as data for Kotlin
to multiply rather than reconstruct. Kotlin then needs only: FFT -> power -> matmul -> log ->
matmul -> moments. No convention left to guess.

Feature layout (36 values, order is load-bearing -- Kotlin indexes it positionally):
    [ 0:13]  MFCC mean over frames
    [13:26]  MFCC standard deviation over frames
    [26]     spectral centroid, energy-weighted mean over frames
    [27]     spectral centroid, standard deviation over frames
    [28]     spectral rolloff at 85%
    [29]     spectral bandwidth
    [30]     spectral flatness
    [31]     zero-crossing rate
    [32]     decay time to 10% of peak, milliseconds
    [33]     low-band energy ratio   (< 500 Hz)
    [34]     mid-band energy ratio   (500-2000 Hz)
    [35]     high-band energy ratio  (>= 2000 Hz)
"""

import numpy as np

SAMPLE_RATE = 22050
N_FFT = 512
HOP = 128
N_MELS = 40
N_MFCC = 13
TOP_DB = 80.0
AMIN = 1e-10
N_BINS = N_FFT // 2 + 1          # 257
FEATURE_COUNT = 36


def hz_to_mel(f):
    """Slaney-style mel scale, matching librosa's default htk=False."""
    f = np.atleast_1d(np.asarray(f, dtype=np.float64))
    f_min, f_sp = 0.0, 200.0 / 3
    mels = (f - f_min) / f_sp
    min_log_hz, min_log_mel = 1000.0, (1000.0 - f_min) / f_sp
    logstep = np.log(6.4) / 27.0
    hi = f >= min_log_hz
    mels[hi] = min_log_mel + np.log(f[hi] / min_log_hz) / logstep
    return mels


def mel_to_hz(m):
    m = np.atleast_1d(np.asarray(m, dtype=np.float64))
    f_min, f_sp = 0.0, 200.0 / 3
    freqs = f_min + f_sp * m
    min_log_hz, min_log_mel = 1000.0, (1000.0 - f_min) / f_sp
    logstep = np.log(6.4) / 27.0
    hi = m >= min_log_mel
    freqs[hi] = min_log_hz * np.exp(logstep * (m[hi] - min_log_mel))
    return freqs


def mel_filterbank(sr=SAMPLE_RATE, n_fft=N_FFT, n_mels=N_MELS):
    """Slaney-normalised triangular mel filterbank, shape (n_mels, n_bins)."""
    fft_freqs = np.linspace(0, sr / 2, n_fft // 2 + 1)
    mel_pts = np.linspace(float(hz_to_mel(0.0)[0]), float(hz_to_mel(sr / 2.0)[0]), n_mels + 2)
    hz_pts = mel_to_hz(mel_pts)

    fb = np.zeros((n_mels, len(fft_freqs)))
    fdiff = np.diff(hz_pts)
    ramps = hz_pts.reshape(-1, 1) - fft_freqs.reshape(1, -1)
    for i in range(n_mels):
        lower = -ramps[i] / fdiff[i]
        upper = ramps[i + 2] / fdiff[i + 1]
        fb[i] = np.maximum(0, np.minimum(lower, upper))
    # Slaney normalisation: equal area per filter
    enorm = 2.0 / (hz_pts[2:n_mels + 2] - hz_pts[:n_mels])
    fb *= enorm.reshape(-1, 1)
    return fb


def dct_matrix(n_out=N_MFCC, n_in=N_MELS):
    """DCT-II with orthonormal scaling, shape (n_out, n_in)."""
    m = np.zeros((n_out, n_in))
    for k in range(n_out):
        m[k] = np.cos(np.pi * k * (2 * np.arange(n_in) + 1) / (2 * n_in))
    m[0] *= 1.0 / np.sqrt(n_in)
    m[1:] *= np.sqrt(2.0 / n_in)
    return m


def periodic_hann(n=N_FFT):
    """Periodic (fftbins=True) Hann -- NOT the symmetric variant."""
    return 0.5 - 0.5 * np.cos(2.0 * np.pi * np.arange(n) / n)


def power_spectrogram(clip):
    """
    Frames without centring or padding: frame i starts at i*HOP.

    Dropping librosa's center=True removes reflect-padding from the contract entirely,
    which is one less thing for Kotlin to reproduce, and costs nothing here because every
    clip is already cropped to the tap.
    """
    window = periodic_hann(N_FFT)
    n_frames = 1 + max(0, (len(clip) - N_FFT) // HOP)
    spec = np.zeros((N_BINS, n_frames))
    for i in range(n_frames):
        frame = clip[i * HOP: i * HOP + N_FFT] * window
        spec[:, i] = np.abs(np.fft.rfft(frame, n=N_FFT)) ** 2
    return spec


_MEL_FB = mel_filterbank()
_DCT = dct_matrix()
_FREQS = np.linspace(0, SAMPLE_RATE / 2, N_BINS)


def extract(clip, sr=SAMPLE_RATE):
    """Return the 36-value feature vector described in the module docstring."""
    clip = np.asarray(clip, dtype=np.float64)
    power = power_spectrogram(clip)                       # (257, frames)
    if power.shape[1] == 0:
        return np.zeros(FEATURE_COUNT, dtype=np.float32)

    # --- MFCC -----------------------------------------------------------------
    mel = _MEL_FB @ power                                  # (40, frames)
    log_mel = 10.0 * np.log10(np.maximum(mel, AMIN))
    log_mel = np.maximum(log_mel, log_mel.max() - TOP_DB)  # top_db clamp
    mfcc = _DCT @ log_mel                                  # (13, frames)

    # --- Spectral moments, per frame then aggregated ---------------------------
    frame_energy = power.sum(axis=0) + 1e-12
    centroid = (_FREQS[:, None] * power).sum(axis=0) / frame_energy
    bandwidth = np.sqrt((((_FREQS[:, None] - centroid[None, :]) ** 2) * power).sum(axis=0) / frame_energy)

    cumulative = np.cumsum(power, axis=0)
    threshold = 0.85 * cumulative[-1, :]
    rolloff = np.array([_FREQS[np.searchsorted(cumulative[:, i], threshold[i])]
                        for i in range(power.shape[1])])

    log_power = np.log(power + 1e-12)
    flatness = np.exp(log_power.mean(axis=0)) / (power.mean(axis=0) + 1e-12)

    # --- Time domain ------------------------------------------------------------
    zcr = float(np.mean(np.abs(np.diff(np.sign(clip))) > 0)) if clip.size > 1 else 0.0

    env = np.abs(clip) / (np.max(np.abs(clip)) + 1e-12)
    peak = int(np.argmax(env))
    tail = env[peak:]
    below = np.where(tail < 0.1)[0]
    decay_ms = (float(below[0]) if below.size else float(tail.size)) / sr * 1000.0

    # --- Band energy ratios ------------------------------------------------------
    total_power = power.sum(axis=1)
    total = total_power.sum() + 1e-12
    low = total_power[_FREQS < 500].sum() / total
    mid = total_power[(_FREQS >= 500) & (_FREQS < 2000)].sum() / total
    high = total_power[_FREQS >= 2000].sum() / total

    return np.concatenate([
        mfcc.mean(axis=1), mfcc.std(axis=1),
        [centroid.mean(), centroid.std(), rolloff.mean(), bandwidth.mean(),
         flatness.mean(), zcr, decay_ms, low, mid, high],
    ]).astype(np.float32)
