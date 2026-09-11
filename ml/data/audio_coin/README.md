# Coin-tap recordings

The demo taps walls with a **coin**, so this is the data the shipped classifier must be
trained on. `ml/data/audio/` holds the original recordings made with a different striker;
a model fitted on those is being asked to generalise across a change in excitation, which
is exactly the kind of shift that quietly wrecks accuracy.

A coin is small, hard and low-mass: short contact time, energy pushed into higher
frequencies, a sharper transient and a faster decay. A knuckle is soft and damped: longer
contact, more low-frequency energy. Every feature the classifier uses -- MFCCs, spectral
centroid, rolloff, the low/mid/high band ratios, decay time -- moves under that change.

## Recording

Drop files into `hollow/` and `solid/`, labelled by **ground truth** (tap somewhere you
know is hollow, e.g. a stud cavity or a drummy tile), not by what it sounds like.

- Use the phone the demo runs on, held roughly as it will be held on the day
- Same coin every time
- Uncompressed **WAV, 44.1 kHz** -- not WhatsApp voice notes; Opus at ~16 kHz throws away
  the high-frequency detail that separates the two classes
- Vary walls, rooms and distances. 100+ taps per class across several surfaces beats 500
  taps on one wall, because the model otherwise learns the wall rather than the cavity
- Several taps per recording is fine; onset detection splits them automatically

## Train on it

```bash
py -3 ml/acoustic/train_tap_classifier.py --audio ml/data/audio_coin
py -3 ml/acoustic/predict_tap.py some_unseen_coin_recording.wav --expect hollow
```

Evaluation is grouped by recording, so the reported accuracy stays honest as the set grows.
