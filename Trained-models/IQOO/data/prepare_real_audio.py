"""
SmartLease Edge — Real Audio Preprocessor
Converts WhatsApp .ogg recordings to .wav, detects and splits
individual tap transients, and augments them for training.

Usage:
    python data/prepare_real_audio.py
"""
import sys
import subprocess
from pathlib import Path
import numpy as np
from scipy.io import wavfile
from scipy import signal

sys.path.insert(0, str(Path(__file__).parent.parent))

# ─── Paths ────────────────────────────────────────
PROJECT_ROOT = Path(__file__).parent.parent
RAW_AUDIO_DIR = PROJECT_ROOT / "data" / "audio"
SYNTH_AUDIO_DIR = PROJECT_ROOT / "data" / "synthetic_audio"
TARGET_SR = 16000
TAP_DURATION = 0.250  # 250ms per tap clip


def convert_ogg_to_wav(ogg_path: Path, wav_path: Path, sr: int = 16000) -> bool:
    """Convert .ogg to .wav using ffmpeg (via imageio-ffmpeg or system ffmpeg)."""
    try:
        # Try imageio-ffmpeg first (already installed for video support)
        try:
            from imageio_ffmpeg import get_ffmpeg_exe
            ffmpeg_exe = get_ffmpeg_exe()
        except ImportError:
            ffmpeg_exe = "ffmpeg"

        cmd = [
            ffmpeg_exe, "-y", "-i", str(ogg_path),
            "-ar", str(sr), "-ac", "1", "-sample_fmt", "s16",
            str(wav_path)
        ]
        result = subprocess.run(cmd, capture_output=True, timeout=10)
        return wav_path.exists()
    except Exception as e:
        print(f"  ⚠️ ffmpeg conversion failed: {e}")
        return False


def detect_tap_onsets(audio: np.ndarray, sr: int = 16000, threshold_db: float = -20) -> list:
    """
    Detect individual tap transients in an audio signal using energy envelope.
    Returns list of onset sample indices.
    """
    # Compute short-time energy envelope
    frame_len = int(0.005 * sr)  # 5ms frames
    hop = frame_len // 2
    n_frames = (len(audio) - frame_len) // hop + 1

    energy = np.zeros(n_frames)
    for i in range(n_frames):
        start = i * hop
        frame = audio[start:start + frame_len]
        energy[i] = np.sum(frame ** 2) / frame_len

    # Convert to dB
    energy_db = 10 * np.log10(energy + 1e-10)
    max_energy = np.max(energy_db)
    threshold = max_energy + threshold_db  # e.g. -20dB below peak

    # Find peaks above threshold with minimum distance between taps
    min_distance_frames = int(0.100 * sr / hop)  # At least 100ms between taps

    above_threshold = energy_db > threshold
    onsets = []
    i = 0
    while i < len(above_threshold):
        if above_threshold[i]:
            # Find the peak in this region
            region_end = min(i + min_distance_frames, len(energy_db))
            peak_idx = i + np.argmax(energy_db[i:region_end])
            onset_sample = peak_idx * hop

            # Back up slightly before the peak to capture the attack
            onset_sample = max(0, onset_sample - int(0.005 * sr))
            onsets.append(onset_sample)

            # Skip ahead past minimum distance
            i = peak_idx + min_distance_frames
        else:
            i += 1

    return onsets


def extract_taps(audio: np.ndarray, sr: int = 16000) -> list:
    """Extract individual tap clips from a recording that may contain 1-3 taps."""
    onsets = detect_tap_onsets(audio, sr)

    if not onsets:
        # If no clear transients, use the whole clip
        n_samples = int(TAP_DURATION * sr)
        clip = audio[:n_samples]
        if len(clip) < n_samples:
            clip = np.pad(clip, (0, n_samples - len(clip)))
        return [clip]

    tap_clips = []
    n_samples = int(TAP_DURATION * sr)

    for onset in onsets:
        clip = audio[onset:onset + n_samples]
        if len(clip) < n_samples:
            clip = np.pad(clip, (0, n_samples - len(clip)))
        tap_clips.append(clip)

    return tap_clips


def augment_tap(y: np.ndarray, sr: int = 16000) -> list:
    """Generate augmented versions of a single tap clip."""
    augmented = []

    # 1. Pitch shift ±1, ±2 semitones
    for semitones in [-2, -1, 1, 2]:
        factor = 2 ** (semitones / 12.0)
        indices = np.round(np.arange(0, len(y), factor)).astype(int)
        indices = indices[indices < len(y)]
        shifted = y[indices]
        if len(shifted) < len(y):
            shifted = np.pad(shifted, (0, len(y) - len(shifted)))
        else:
            shifted = shifted[:len(y)]
        augmented.append(shifted)

    # 2. Add noise at different SNR levels
    for snr_db in [15, 20, 25, 30]:
        noise = np.random.randn(len(y)).astype(np.float32)
        signal_power = np.mean(y ** 2) + 1e-10
        noise_power = signal_power / (10 ** (snr_db / 10))
        noisy = y + noise * np.sqrt(noise_power)
        noisy = noisy / (np.max(np.abs(noisy)) + 1e-8) * np.max(np.abs(y))
        augmented.append(noisy.astype(np.float32))

    # 3. Time stretch
    for rate in [0.85, 0.95, 1.05, 1.15]:
        indices = np.round(np.arange(0, len(y), rate)).astype(int)
        indices = indices[indices < len(y)]
        stretched = y[indices]
        if len(stretched) < len(y):
            stretched = np.pad(stretched, (0, len(y) - len(stretched)))
        else:
            stretched = stretched[:len(y)]
        augmented.append(stretched)

    # 4. Amplitude scaling
    for scale in [0.4, 0.6, 0.8, 1.2, 1.5]:
        scaled = np.clip(y * scale, -1.0, 1.0)
        augmented.append(scaled.astype(np.float32))

    return augmented  # 17 augmentations per tap


def save_wav(filepath: Path, audio: np.ndarray, sr: int = 16000):
    """Save float32 audio as int16 WAV."""
    filepath.parent.mkdir(parents=True, exist_ok=True)
    audio_int16 = (np.clip(audio, -1.0, 1.0) * 32767).astype(np.int16)
    wavfile.write(str(filepath), sr, audio_int16)


def process_class(class_name: str):
    """Process all recordings for a single class (hollow or solid)."""
    raw_dir = RAW_AUDIO_DIR / class_name
    output_dir = SYNTH_AUDIO_DIR / class_name

    if not raw_dir.exists():
        print(f"  ⚠️ No directory found: {raw_dir}")
        return 0

    # Find all audio files (.ogg, .wav, .mp3, .m4a, .mp4)
    audio_files = []
    for ext in ["*.ogg", "*.wav", "*.mp3", "*.m4a", "*.mp4", "*.aac", "*.webm"]:
        audio_files.extend(raw_dir.glob(ext))

    if not audio_files:
        print(f"  ⚠️ No audio files found in {raw_dir}")
        return 0

    print(f"\n  📂 Found {len(audio_files)} recordings in {raw_dir}")

    total_saved = 0
    temp_dir = raw_dir / "_temp_wav"
    temp_dir.mkdir(exist_ok=True)

    for file_idx, audio_file in enumerate(sorted(audio_files)):
        print(f"    Processing: {audio_file.name}")

        # Step 1: Convert to WAV if needed
        if audio_file.suffix.lower() == ".wav":
            wav_path = audio_file
        else:
            wav_path = temp_dir / f"converted_{file_idx:02d}.wav"
            if not convert_ogg_to_wav(audio_file, wav_path, TARGET_SR):
                print(f"    ❌ Skipping {audio_file.name} (conversion failed)")
                continue

        # Step 2: Load the WAV
        try:
            file_sr, data = wavfile.read(str(wav_path))
            if data.dtype == np.int16:
                audio = data.astype(np.float32) / 32767.0
            elif data.dtype == np.int32:
                audio = data.astype(np.float32) / 2147483647.0
            else:
                audio = data.astype(np.float32)

            if len(audio.shape) > 1:
                audio = np.mean(audio, axis=1)

            # Resample if needed
            if file_sr != TARGET_SR:
                num_samples = int(len(audio) * TARGET_SR / file_sr)
                audio = signal.resample(audio, num_samples)

        except Exception as e:
            print(f"    ❌ Failed to load {wav_path.name}: {e}")
            continue

        # Step 3: Detect and extract individual taps
        taps = extract_taps(audio, TARGET_SR)
        print(f"      → Detected {len(taps)} tap(s)")

        # Step 4: Save each tap + augmentations
        for tap_idx, tap in enumerate(taps):
            # Normalize
            tap = tap / (np.max(np.abs(tap)) + 1e-8) * 0.85

            # Save original real tap with unique prefix
            base_name = f"real_{file_idx:02d}_tap{tap_idx:02d}"
            save_wav(output_dir / f"{base_name}.wav", tap, TARGET_SR)
            total_saved += 1

            # Save augmented versions
            for aug_idx, aug_tap in enumerate(augment_tap(tap, TARGET_SR)):
                save_wav(
                    output_dir / f"{base_name}_aug{aug_idx:02d}.wav",
                    aug_tap, TARGET_SR
                )
                total_saved += 1

    # Cleanup temp files
    if temp_dir.exists():
        for f in temp_dir.glob("*"):
            f.unlink()
        temp_dir.rmdir()

    return total_saved


def main():
    print("=" * 60)
    print("🎙️  SmartLease Edge — Real Audio Preprocessor")
    print("=" * 60)
    print(f"\n📁 Source: {RAW_AUDIO_DIR}")
    print(f"📁 Output: {SYNTH_AUDIO_DIR}")
    print(f"🎯 Target SR: {TARGET_SR} Hz | Tap Duration: {TAP_DURATION * 1000:.0f}ms")

    total = 0
    for class_name in ["hollow", "solid"]:
        print(f"\n{'─' * 40}")
        print(f"  Processing class: {class_name.upper()}")
        count = process_class(class_name)
        total += count
        print(f"  ✅ {class_name}: {count} samples saved")

    print(f"\n{'=' * 60}")
    print(f"✅ Total real-world samples generated: {total}")
    print(f"   (Added to existing synthetic data in {SYNTH_AUDIO_DIR})")
    print(f"\n👉 Now retrain the model:")
    print(f"   python acoustic/train.py")
    print("=" * 60)


if __name__ == "__main__":
    main()
