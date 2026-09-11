"""
SmartLease Edge — Synthetic Audio Data Generator
Generates hollow and solid tap audio samples for bootstrapping
the acoustic classifier before real data is available.

Physics basis:
- Hollow: Impact on a cavity produces flexural resonance 400Hz-2.5kHz
  with sustained exponential decay >80ms
- Solid: Impact on dense material produces diffuse high frequencies
  with rapid attenuation <25ms
"""
import os
import sys
import numpy as np
from pathlib import Path
from scipy import signal

# Fallback audio writer: soundfile -> scipy.io.wavfile -> wave (standard library)
def save_wav(filepath, audio_data, sr=16000):
    filepath = Path(filepath)
    filepath.parent.mkdir(parents=True, exist_ok=True)
    audio_int16 = (np.clip(audio_data, -1.0, 1.0) * 32767).astype(np.int16)
    try:
        from scipy.io import wavfile
        wavfile.write(str(filepath), sr, audio_int16)
        return
    except Exception:
        pass
    try:
        import soundfile as sf
        sf.write(str(filepath), audio_data, sr)
        return
    except Exception:
        pass
    import wave
    with wave.open(str(filepath), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(audio_int16.tobytes())

sys.path.insert(0, str(Path(__file__).parent.parent))
from config import SYNTHETIC_AUDIO_DIR, ACOUSTIC


def generate_hollow_tap(sr: int = 16000, duration: float = 0.150) -> np.ndarray:
    """
    Simulate a hollow tap: resonant, sustained, lower frequency content.
    Flexural resonance at 400-2500Hz with slow exponential decay.
    """
    n_samples = int(sr * duration)
    t = np.linspace(0, duration, n_samples, dtype=np.float32)

    # Resonant frequencies (hollow cavity modes)
    f1 = np.random.uniform(400, 800)     # Fundamental
    f2 = np.random.uniform(800, 1500)    # First overtone
    f3 = np.random.uniform(1500, 2500)   # Second overtone

    # Slow decay (hollow = sustained resonance)
    decay_rate = np.random.uniform(15, 35)  # Slower decay = more hollow
    envelope = np.exp(-decay_rate * t)

    # Impact transient (sharp onset)
    impact = np.exp(-200 * t) * np.random.uniform(0.5, 1.0)

    # Resonant components
    y = (
        0.5 * np.sin(2 * np.pi * f1 * t) +
        0.3 * np.sin(2 * np.pi * f2 * t + np.random.uniform(0, np.pi)) +
        0.2 * np.sin(2 * np.pi * f3 * t + np.random.uniform(0, np.pi))
    )

    # Apply envelope
    y = (y * envelope + impact * 0.3)

    # Add slight reverb (cavity reflection)
    delay_samples = int(np.random.uniform(0.002, 0.005) * sr)
    if delay_samples < len(y):
        reverb = np.zeros_like(y)
        reverb[delay_samples:] = y[:-delay_samples] * 0.3
        y = y + reverb

    # Normalize
    y = y / (np.max(np.abs(y)) + 1e-8) * np.random.uniform(0.6, 0.95)

    return y.astype(np.float32)


def generate_solid_tap(sr: int = 16000, duration: float = 0.150) -> np.ndarray:
    """
    Simulate a solid tap: sharp, quickly damped, higher frequency content.
    Diffuse high frequencies with rapid attenuation <25ms.
    """
    n_samples = int(sr * duration)
    t = np.linspace(0, duration, n_samples, dtype=np.float32)

    # Higher frequencies (dense material, concrete, stud-backed masonry)
    f1 = np.random.uniform(500, 2000)   # Includes 600Hz solid concrete/stud impact
    f2 = np.random.uniform(1500, 4000)
    f3 = np.random.uniform(3000, 6000)

    # Fast decay (solid = rapid energy dissipation, decay constant 40-200)
    decay_rate = np.random.uniform(40, 200)
    envelope = np.exp(-decay_rate * t)

    # Sharp impact transient
    impact = np.exp(-500 * t) * np.random.uniform(0.7, 1.0)

    # Broadband noise component (solid materials scatter energy)
    noise = np.random.randn(n_samples).astype(np.float32) * 0.15
    noise_envelope = np.exp(-150 * t)

    # Tonal components (weaker, quickly damped)
    y = (
        0.4 * np.sin(2 * np.pi * f1 * t) +
        0.3 * np.sin(2 * np.pi * f2 * t) +
        0.15 * np.sin(2 * np.pi * f3 * t)
    )

    # Apply envelope + add noise + impact
    y = y * envelope + noise * noise_envelope + impact * 0.4

    # No reverb (solid absorbs reflections)

    # Normalize
    y = y / (np.max(np.abs(y)) + 1e-8) * np.random.uniform(0.6, 0.95)

    return y.astype(np.float32)


def generate_solid_impact(frequency: float = 600, duration: float = 0.25, decay_constant: float = 40, sr: int = 16000) -> np.ndarray:
    """
    Generates a high-frequency, rapid-decay tone mimicking a solid surface impact.
    - Base sine wave at a higher frequency (e.g. 600 Hz)
    - Exponential decay envelope np.exp(-decay_constant * t) to simulate fast dampening
    """
    t = np.linspace(0, duration, int(sr * duration), endpoint=False, dtype=np.float32)
    sine_wave = np.sin(2 * np.pi * frequency * t)
    envelope = np.exp(-decay_constant * t)
    y = sine_wave * envelope
    y = y / (np.max(np.abs(y)) + 1e-8) * np.random.uniform(0.6, 0.95)
    return y.astype(np.float32)


def generate_acoustic_tap_pulse(frequency: float = 220, is_hollow: bool = True, sr: int = 16000, duration: float = 0.25) -> np.ndarray:
    """
    Generates a realistic acoustic tap impulse:
    - Hollow: low resonant tone (150-600Hz) with slow exponential decay (>80ms)
    - Solid: broadband white noise with rapid decay (<20ms)
    """
    t = np.linspace(0, duration, int(sr * duration), endpoint=False, dtype=np.float32)

    if is_hollow:
        envelope = np.exp(-22 * t)
        impact = np.exp(-180 * t) * 0.4
        tone = (
            0.6 * np.sin(2 * np.pi * frequency * t) +
            0.3 * np.sin(2 * np.pi * (frequency * 2.1) * t)
        )
        signal = (tone * envelope) + impact
    else:
        envelope = np.exp(-120 * t)
        noise = np.random.uniform(-1, 1, len(t)).astype(np.float32)
        signal = noise * envelope

    signal = signal / (np.max(np.abs(signal)) + 1e-8) * np.random.uniform(0.6, 0.95)
    return signal.astype(np.float32)


def augment_audio(y: np.ndarray, sr: int = 16000) -> list:
    """Apply augmentations to create variations of an audio sample."""
    augmented = []

    # Pitch shift ±2 semitones
    for semitones in [-2, -1, 1, 2]:
        factor = 2 ** (semitones / 12.0)
        indices = np.round(np.arange(0, len(y), factor)).astype(int)
        indices = indices[indices < len(y)]
        shifted = y[indices]
        # Pad or trim to original length
        if len(shifted) < len(y):
            shifted = np.pad(shifted, (0, len(y) - len(shifted)))
        else:
            shifted = shifted[:len(y)]
        augmented.append(shifted)

    # Add noise at different SNR levels
    for snr_db in [15, 20, 25]:
        noise = np.random.randn(len(y)).astype(np.float32)
        signal_power = np.mean(y ** 2)
        noise_power = signal_power / (10 ** (snr_db / 10))
        noisy = y + noise * np.sqrt(noise_power)
        noisy = noisy / (np.max(np.abs(noisy)) + 1e-8) * np.max(np.abs(y))
        augmented.append(noisy.astype(np.float32))

    # Time stretch (simple resampling)
    for rate in [0.9, 1.1]:
        indices = np.round(np.arange(0, len(y), rate)).astype(int)
        indices = indices[indices < len(y)]
        stretched = y[indices]
        if len(stretched) < len(y):
            stretched = np.pad(stretched, (0, len(y) - len(stretched)))
        else:
            stretched = stretched[:len(y)]
        augmented.append(stretched)

    # Amplitude scaling
    for scale in [0.5, 0.75, 1.25]:
        scaled = np.clip(y * scale, -1.0, 1.0)
        augmented.append(scaled)

    return augmented


def generate_dataset(
    samples_per_class: int = 100,
    augment: bool = True,
    sr: int = 16000,
) -> Path:
    """Generate the full synthetic audio dataset."""
    output_dir = SYNTHETIC_AUDIO_DIR
    hollow_dir = output_dir / "hollow"
    solid_dir = output_dir / "solid"
    hollow_dir.mkdir(parents=True, exist_ok=True)
    solid_dir.mkdir(parents=True, exist_ok=True)

    print(f"Generating {samples_per_class} samples per class...")

    total_hollow = 0
    total_solid = 0

    for i in range(samples_per_class):
        # Generate base samples across realistic physical variations:
        # Mode 0: Multi-modal tap (cavity resonance vs. dense material attenuation)
        # Mode 1: Decaying sine wave impact (600Hz solid impact vs. 150-350Hz hollow cavity)
        # Mode 2: Transient impulse pulse (slow ringing cavity vs. broadband white noise damping)
        mode = i % 3
        if mode == 0:
            hollow = generate_hollow_tap(sr)
            solid = generate_solid_tap(sr)
        elif mode == 1:
            hollow = generate_acoustic_tap_pulse(frequency=float(np.random.uniform(150, 350)), is_hollow=True, sr=sr)
            solid = generate_solid_impact(
                frequency=float(np.random.uniform(500, 1000)),
                duration=0.25,
                decay_constant=float(np.random.uniform(35, 70)),
                sr=sr
            )
        else:
            hollow = generate_hollow_tap(sr)
            solid = generate_acoustic_tap_pulse(is_hollow=False, sr=sr)

        # Save originals
        save_wav(hollow_dir / f"synthetic_{i:04d}.wav", hollow, sr)
        save_wav(solid_dir / f"synthetic_{i:04d}.wav", solid, sr)
        total_hollow += 1
        total_solid += 1

        # Save augmented versions
        if augment:
            for j, aug in enumerate(augment_audio(hollow, sr)):
                save_wav(hollow_dir / f"synthetic_{i:04d}_aug{j:02d}.wav", aug, sr)
                total_hollow += 1
            for j, aug in enumerate(augment_audio(solid, sr)):
                save_wav(solid_dir / f"synthetic_{i:04d}_aug{j:02d}.wav", aug, sr)
                total_solid += 1

        if (i + 1) % 20 == 0:
            print(f"  Generated {i + 1}/{samples_per_class} base samples...")

    print(f"\n✅ Synthetic audio dataset generated at {output_dir}")
    print(f"   Hollow: {total_hollow} samples")
    print(f"   Solid:  {total_solid} samples")

    return output_dir


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Generate synthetic tap audio data")
    parser.add_argument("--count", type=int, default=100, help="Samples per class")
    parser.add_argument("--no-augment", action="store_true", help="Skip augmentation")
    args = parser.parse_args()

    generate_dataset(
        samples_per_class=args.count,
        augment=not args.no_augment,
    )
