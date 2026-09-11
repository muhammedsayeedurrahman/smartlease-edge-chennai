"""
SmartLease Edge — Acoustic Wall Test Sound Generator
Generates test sounds including:
1. Low-frequency resonant tones (150Hz - 600Hz) simulating hollow wall cavities
2. Broadband white noise simulating solid, dense structural impact
3. Realistic transient tap impulses for testing the acoustic classifier

Usage:
    python data/generate_test_sound.py
"""
import sys
from pathlib import Path
import numpy as np
from scipy.io import wavfile

# Project paths
PROJECT_ROOT = Path(__file__).parent.parent
OUTPUT_DIR = PROJECT_ROOT / "outputs" / "test_sounds"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


def generate_sine_wave(frequency: float, duration: float, sample_rate: int = 16000) -> np.ndarray:
    """Generates a pure sine wave tone (continuous resonant frequency)."""
    t = np.linspace(0, duration, int(sample_rate * duration), endpoint=False)
    audio_data = np.sin(2 * np.pi * frequency * t)
    return (audio_data * 32767).astype(np.int16)


def generate_white_noise(duration: float, sample_rate: int = 16000) -> np.ndarray:
    """Generates broad-spectrum white noise (diffuse acoustic impact)."""
    num_samples = int(sample_rate * duration)
    audio_data = np.random.uniform(-1, 1, num_samples)
    return (audio_data * 32767).astype(np.int16)


def generate_solid_impact(frequency: float = 600, duration: float = 0.5, decay_constant: float = 40, sample_rate: int = 44100) -> np.ndarray:
    """
    Generates a high-frequency, rapid-decay tone mimicking a solid surface impact.
    - Base sine wave at a higher, snappier frequency (default 600 Hz)
    - Exponential decay envelope np.exp(-decay_constant * t) to simulate fast dampening
    """
    t = np.linspace(0, duration, int(sample_rate * duration), endpoint=False)
    sine_wave = np.sin(2 * np.pi * frequency * t)
    envelope = np.exp(-decay_constant * t)
    audio_data = sine_wave * envelope
    return (audio_data * 32767).astype(np.int16)


def generate_acoustic_tap_pulse(frequency: float, is_hollow: bool = True, sample_rate: int = 16000) -> np.ndarray:
    """
    Generates a realistic acoustic tap impulse:
    - Hollow: low resonant tone (150-600Hz) with slow exponential decay (>80ms)
    - Solid: broadband white noise with rapid decay (<20ms)
    """
    duration = 0.250  # 250ms clip
    t = np.linspace(0, duration, int(sample_rate * duration), endpoint=False)

    if is_hollow:
        # Low frequency cavity resonance with slow ringing decay
        envelope = np.exp(-22 * t)
        impact = np.exp(-180 * t) * 0.4
        tone = (
            0.6 * np.sin(2 * np.pi * frequency * t) +
            0.3 * np.sin(2 * np.pi * (frequency * 2.1) * t)
        )
        signal = (tone * envelope) + impact
    else:
        # High-frequency diffuse white noise with immediate damping
        envelope = np.exp(-120 * t)
        noise = np.random.uniform(-1, 1, len(t))
        signal = noise * envelope

    # Normalize to 16-bit PCM
    signal = signal / (np.max(np.abs(signal)) + 1e-9)
    return (signal * 32767).astype(np.int16)


def main():
    SAMPLE_RATE = 16000  # Standard SmartLease Edge pipeline sample rate
    DURATION = 2.0       # 2 seconds for continuous tones

    print("=" * 60)
    print("🔊 Generating Acoustic Test Sounds...")
    print("=" * 60)

    # 1. Pure 150 Hz Sine Wave (Deep cavity hollow simulation)
    sine_150 = generate_sine_wave(frequency=150, duration=DURATION, sample_rate=SAMPLE_RATE)
    path_sine = OUTPUT_DIR / "acoustic_test_sine_150hz.wav"
    wavfile.write(str(path_sine), SAMPLE_RATE, sine_150)
    print(f"✅ Saved 150Hz Sine Tone:      {path_sine}")

    # 2. Pure White Noise (Broadband frequency absorption test)
    noise = generate_white_noise(duration=DURATION, sample_rate=SAMPLE_RATE)
    path_noise = OUTPUT_DIR / "acoustic_test_white_noise.wav"
    wavfile.write(str(path_noise), SAMPLE_RATE, noise)
    print(f"✅ Saved White Noise:          {path_noise}")

    # 3. Hollow Cavity Acoustic Tap (150Hz resonance with ringing decay)
    hollow_tap = generate_acoustic_tap_pulse(frequency=220, is_hollow=True, sample_rate=SAMPLE_RATE)
    path_hollow = OUTPUT_DIR / "test_tap_hollow_cavity.wav"
    wavfile.write(str(path_hollow), SAMPLE_RATE, hollow_tap)
    print(f"✅ Saved Hollow Tap Sample:    {path_hollow}")

    # 4. Solid Structural Tap (Broadband diffuse with instant decay)
    solid_tap = generate_acoustic_tap_pulse(frequency=0, is_hollow=False, sample_rate=SAMPLE_RATE)
    path_solid = OUTPUT_DIR / "test_tap_solid_wall.wav"
    wavfile.write(str(path_solid), SAMPLE_RATE, solid_tap)
    print(f"✅ Saved Solid Tap Sample:     {path_solid}")

    # 5. Solid Impact Sound (600Hz, rapid dampening decay constant = 40)
    solid_sound_44k = generate_solid_impact(frequency=600, duration=0.5, decay_constant=40, sample_rate=44100)
    path_solid_impact = OUTPUT_DIR / "acoustic_test_solid.wav"
    wavfile.write(str(path_solid_impact), 44100, solid_sound_44k)
    wavfile.write(str(PROJECT_ROOT / "acoustic_test_solid.wav"), 44100, solid_sound_44k)
    print(f"✅ Saved Solid Impact (44.1k): {path_solid_impact}")

    # Also save 16kHz version for direct compatibility with SmartLease Edge classifier
    solid_sound_16k = generate_solid_impact(frequency=600, duration=0.5, decay_constant=40, sample_rate=SAMPLE_RATE)
    path_solid_16k = OUTPUT_DIR / "acoustic_test_solid_16k.wav"
    wavfile.write(str(path_solid_16k), SAMPLE_RATE, solid_sound_16k)
    print(f"✅ Saved Solid Impact (16kHz): {path_solid_16k}")

    print("\n📁 All test sounds saved in: outputs/test_sounds/ and project root")
    print("=" * 60)
    print("\n👉 You can now test these in your terminal or in the app:")
    print(f"   python -m acoustic.predict outputs/test_sounds/test_tap_hollow_cavity.wav")
    print(f"   python -m acoustic.predict outputs/test_sounds/acoustic_test_solid.wav")
    print(f"   python -m acoustic.predict outputs/test_sounds/test_tap_solid_wall.wav")


if __name__ == "__main__":
    main()
