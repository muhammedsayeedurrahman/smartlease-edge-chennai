# Acoustic Tap Recording — Quick Start Checklist

**Target:** 30–50 samples each (hollow + solid) × 3 seconds per sample
**Equipment:** iQOO 15 (or demo phone) + Voice Recorder app
**Time estimate:** 2–3 hours total

---

## Recording Settings (Set These First)

Open your phone's **Voice Recorder** app (or download "Easy Voice Recorder"):

- **Format:** WAV (preferred) or M4A
- **Quality:** High / Lossless
- **Sample rate:** 44.1kHz or 48kHz (not 8kHz or 16kHz)
- **Channels:** Mono is fine, stereo is better

**Test recording:**
1. Tap a hollow door 5 times
2. Play it back — should hear clear knocks, minimal background noise
3. If too quiet: move phone closer (10–15cm from surface)
4. If distorted/clipping: reduce mic gain or move phone back (20–25cm)

---

## Recording Protocol

**For each surface:**

1. Position phone **10–20cm** from surface (consistent distance!)
2. Press record
3. Tap with **knuckle**, medium force, **5–7 taps**
4. Space taps ~0.5 seconds apart (count "one, two, three...")
5. Stop recording after 3–5 seconds
6. Listen back immediately — if background noise is loud, re-record

**Tapping technique:**
- Use **middle knuckle** (most consistent)
- Same force every time (practice a few taps first)
- Tap flat surface, not edges or corners
- Don't scratch/drag — clean, percussive taps only

---

## HOLLOW SURFACES (50 samples)

### Hollow Door (15 samples)
- **Where:** Any interior door (bedroom, bathroom, closet)
- **Not:** Front door (usually solid), cabinet door (too small)
- **Tap area:** Center panel, not the frame or handle area
- **Sound check:** Should hear a "boom" or resonant echo

**File naming:** `hollow_door_01.wav`, `hollow_door_02.wav`, ...

### Drywall/Plasterboard (15 samples)
- **Where:** Interior walls in modern apartments/houses
- **How to find hollow area:** Knock along wall — hollow = resonant, solid = dull (over a stud)
- **Tap area:** Flat wall surface between studs
- **Sound check:** Echo-y, drum-like sound

**File naming:** `hollow_drywall_01.wav`, `hollow_drywall_02.wav`, ...

### Empty Cardboard Box (10 samples)
- **Where:** Amazon delivery boxes, storage boxes (medium-to-large size)
- **Condition:** Empty, or filled with soft packing material only
- **Tap area:** Flat side panel, not creased corners
- **Sound check:** Hollow thud, cardboard rattle

**File naming:** `hollow_cardboard_01.wav`, ...

### Hollow Plastic Container (10 samples)
- **Where:** Empty storage bins, buckets, plastic drawers
- **Condition:** Completely empty
- **Tap area:** Side panel, not lid or bottom
- **Sound check:** Resonant plastic knock

**File naming:** `hollow_plastic_01.wav`, ...

---

## SOLID SURFACES (50 samples)

### Concrete Wall (15 samples)
- **Where:** Exterior walls, parking structures, stairwells, balcony walls
- **Tap area:** Flat painted or bare concrete
- **Sound check:** Dull, dead thud — no echo at all

**File naming:** `solid_concrete_01.wav`, `solid_concrete_02.wav`, ...

### Solid Wood (15 samples)
- **Where:** Dining table, solid-core door, wood furniture, shelves
- **Not:** Hollow furniture, IKEA particleboard (usually hollow)
- **How to check:** Tap it — if it sounds solid and heavy, it's good
- **Tap area:** Flat surface, not edge grain or joints

**File naming:** `solid_wood_01.wav`, `solid_wood_02.wav`, ...

### Filled Container (10 samples)
- **Where:** Full water jug, filled cardboard box, full bucket
- **Condition:** At least 80% full (weight dampens resonance)
- **Tap area:** Side panel
- **Sound check:** Very dull thud, no hollow echo

**File naming:** `solid_filled_01.wav`, ...

### Tile on Concrete (10 samples)
- **Where:** Bathroom floor, kitchen floor (if tile over solid substrate)
- **Not:** Tile over hollow subfloor (sounds different)
- **How to check:** Tap — should sound solid, not drum-like
- **Tap area:** Center of tile, or grout line for variety

**File naming:** `solid_tile_01.wav`, ...

---

## File Transfer & Organization

**After recording all samples:**

1. **Connect phone to laptop:**
   - USB cable → enable "File Transfer" mode
   - Or use cloud storage (Google Drive, OneDrive)

2. **Copy all recordings to:**
   ```
   C:\Users\HP\SmartLeaseEdge\training_data\acoustic\
   ```

3. **Organize into folders:**
   - Move all `hollow_*.wav` → `training_data/acoustic/hollow/`
   - Move all `solid_*.wav` → `training_data/acoustic/solid/`

4. **Delete mistakes/bad recordings:**
   - Background noise too loud
   - Phone moved during recording
   - Tapping force inconsistent
   - Wrong surface type

---

## Quick Quality Check (Before Day 3 Training)

Open a few random samples in a media player (VLC, Windows Media Player):

**Hollow samples should:**
- ✅ Have clear, resonant taps
- ✅ Sound noticeably different from solid samples
- ✅ Have minimal background noise (no TV, traffic, conversations)

**Solid samples should:**
- ✅ Have dull, dead thuds
- ✅ No echo or resonance
- ✅ Same background noise quality as hollow

**If samples sound too similar:** Re-record that surface type with more exaggerated examples (very hollow door vs. very solid concrete wall).

---

## Validation

Run this when done:

```bash
cd C:\Users\HP\SmartLeaseEdge
python tools/validate_training_data.py
```

Should see:
```
✓ Acoustic data collection complete
  Hollow: 50 samples
  Solid: 50 samples
```

---

## Time-Saving Tips

**If you're running out of time on Day 2:**

1. **Minimum viable set:** 30 hollow + 30 solid (not 50+50)
2. **Prioritize variety over quantity:**
   - 10 hollow door + 10 drywall + 10 cardboard = 30 hollow ✓
   - Better than 30 hollow door samples only
3. **Skip perfect audio quality:**
   - Some background noise is OK
   - Perfect silence is not realistic for a real inspection anyway
4. **Record in batches:**
   - All hollow doors at once (walk room to room)
   - All concrete walls at once (exterior tour)

---

## Next: Day 3 Model Training

Once you have 30+ samples per class:

1. Run `tools/validate_training_data.py` ✓
2. Backup `training_data/acoustic/` folder
3. Move to acoustic classifier training (simple model, not full YamNet)

**This data is the foundation for Days 3–5 training — don't skip it!**
