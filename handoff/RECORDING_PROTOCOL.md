# RECORDING_PROTOCOL.md — tap capture

**Target: 56–64 spots, balanced 28–32 hollow / 28–32 solid.** ~45–50 min.
Do the **2-spot pilot in `PILOT_CHECKLIST.md` first** and wait for the all-clear. Do not
record 60 spots into a format problem you cannot re-shoot.

**Use the in-app screen. Home → "Tap capture (debug)".** Debug builds only.
`adb install -r .../app/build/outputs/apk/debug/app-debug.apk`

---

## 1 · The labelling rule: construction, never sound

**A spot's label is decided by what the thing is made of, established before you strike it.
Never by how it sounds.**

If you label a spot "hollow" because it sounded hollow, the label is a function of the signal
the model is being tested on. Grouped cross-validation would then measure how well the
classifier reproduces *your ear*, not whether it detects a void — and it would score well
while proving nothing. That is the failure this whole exercise exists to avoid.

**The standard is not "the label must be causally unrelated to the void".** It cannot be; the
void is the thing. The standard is: **the label must not come through the same channel the
model uses.** Sound → sound is circular. *Knowing the construction* → sound is not. *Feeling
it deflect under your foot* → sound is not.

**If you cannot establish a spot's construction with confidence, discard it.** There is no
"probably hollow". A discarded spot costs 45 seconds. A guessed one poisons a whole fold and
you will never know which one it was.

---

## 2 · The construction map

Every row names the **evidence** that fixes the label. If you cannot produce that evidence at
the spot you are standing at, move on.

### HOLLOW — an air gap immediately behind the struck surface

| # | Construction | How you know, without tapping | Typical spots |
|---|---|---|---|
| H1 | **Hollow-core flush door** | Swing it — it is light and has almost no inertia. ~30–35 mm thick. Strike the **centre of the panel**, never the stile or near the lock. Each door is a separate spot. | 3–5 |
| H2 | **Cupboard / wardrobe / kitchen shutter** | Open it. You can see the panel thickness and the air behind it. Strike the centre of the closed shutter. | 6–10 |
| H3 | **Wardrobe or cabinet back panel** | Open it and look: 3–6 mm ply against the wall with a gap, or a visible cavity. | 3–5 |
| H4 | **False ceiling** (gypsum board or mineral-fibre tile) | Visibly below the slab; has a grid, a cove, or an access hatch. Strike mid-panel, away from the channel. | 2–6 |
| H5 | **Boxed-in service duct / plumbing chase cover** | Bathroom or kitchen — a ply or board box over pipework, usually with an access panel. Open it once to confirm the void. | 2–4 |
| H6 | **Gypsum / plasterboard stud partition** | Non-structural, total thickness ~100–125 mm, often stops short of the slab. Find the studs (screw dimples, a stud detector, or a torch raked along the wall) and strike **midway between two studs**. | 0–4 |
| H7 | **Debonded floor or wall tile** | The hard case — **see §3.** | 0–8 |
| H8 | **Constructed pair — tile or panel on supports** | You built it, so the label is certain. **See §3.** | up to 8 |

### SOLID — continuous dense material, no gap

| # | Construction | How you know, without tapping | Typical spots |
|---|---|---|---|
| S1 | **RCC column** | A structural column: it projects from the wall plane at a corner, or is boxed at a known grid position. | 2–4 |
| S2 | **RCC beam / slab soffit** | Underside of the slab where there is **no** false ceiling — usually a bathroom, utility, or balcony. | 2–4 |
| S3 | **Solid brick / solid block wall, plastered** | Measure the thickness at a door or window reveal: **≥ 200 mm**. It runs floor to ceiling and carries load. External walls almost always qualify. | 4–8 |
| S4 | **Bedded floor tile on screed** | **See §3.** | 4–8 |
| S5 | **Bare concrete floor / balcony slab / sill / step** | Concrete, no tile, no void under it. Balconies, utility areas, window sills, stair treads. | 3–5 |
| S6 | **Stone counter, bedded** | Granite or quartz **sitting on a masonry or full-ply base**. Strike over the supported area, never an overhang or a sink cut-out edge. | 1–3 |
| S7 | **Solid timber** | Dining table top, a solid-core door, heavy furniture. ⚠️ **A solid-core door looks identical to a hollow-core one** — weigh it by swinging it. If in doubt, discard. | 2–4 |

> **⚠️ One rule that overrides all of the above.** Do not strike within **10 cm** of an edge,
> corner, joint, stud, batten, tile edge or grout line. Boundary conditions dominate the
> response there, and a strike near the edge of a bonded tile can ring like a debonded one.
> **Centre of the panel, middle of the tile, middle of the bay.**

---

## 3 · Bedded tile vs debonded tile — the question you asked

**Honest answer first: you largely cannot tell by looking, and that is exactly why the tap
test has value.** Debonding is a subsurface condition with no reliable visual signature. Any
protocol that asks you to eyeball it will produce noisy labels. So do not eyeball it. Use one
of these three, in this order.

### 3a · Build it — the cleanest data in the whole capture (H8 / S4 pair)

You do not need a naturally debonded tile to have a hollow tile. You need **a ceramic or stone
surface with an air gap behind it**, and you can create that with certainty in two minutes.

```
HOLLOW:   spare floor tile / granite offcut / 6 mm ply sheet
          resting on two supports ~15 cm apart  (two pencils, two coin stacks, two books)
          5–20 mm air gap under the middle, on a solid floor
          -> strike the CENTRE of the span

SOLID:    the SAME tile, laid flat on the SAME floor, full contact, no gap
          -> strike the same point on the tile
```

Same tile, same floor, same room, same microphone distance, same knuckle, minutes apart.
**The only variable that changed is the air gap.** That is a controlled experiment and it is
worth more than a dozen uncertain spots in the field.

Move the supports, change the gap, use a different tile or a different offcut — each is a new
spot. Roughly 4 hollow and 4 solid from this method.

> **Cap it at a quarter of each class.** A tile resting freely on two supports rings *more*
> than a tile bonded at its edges with a void beneath — the boundary conditions differ. If
> most of your hollow class is loose plates, the model learns "loose plate", not "void behind
> a fixed surface". Use it as a certain core, not as the category.

### 3b · The foot test — for naturally debonded tiles in situ

Not your ear, and not the microphone: **your body weight.**

1. Stand with your full weight on the tile, heel to toe, then rock side to side.
2. A debonded tile **deflects, clicks, or grates** — you feel movement through your foot and
   often hear grout fretting at the perimeter.
3. Repeat on the neighbours to have something to compare against.

This is a mechanical channel, independent of the microphone. Combined with corroborating
evidence it is good enough to label:

| supporting evidence | what it means |
|---|---|
| **Grout missing, powdered or cracked around one tile** | it has been moving under load for a while |
| **Hairline crack across the tile to its edges** | the bed failed under it |
| **The tile sits proud of its neighbours, or rocks** | lost bedding, or tented under compression |
| **Efflorescence or a damp ring in the grout joint** | water is sitting in a void |

**Where to look:** doorways and thresholds, room perimeters, next to floor drains, balconies
and sun-exposed floors, under windows, and long uninterrupted tile runs without a movement
joint. Debonding concentrates there.

**Label `hollow` only when the foot test is positive *and* at least one row of that table
applies.** Foot test alone, with nothing visible: discard.

### 3c · Bedded tiles (S4) — confirm the same way, in reverse

A tile is `solid` when it is **rigid underfoot with no click or deflection**, its grout is
intact all round, and its neighbours behave identically. **Take your bedded tiles from the
same floor as your debonded ones** — same room, same tile lot, same ambience. That pairing is
the single most valuable comparison in the set, and it is the one a judge can reproduce by
tapping the table in front of them.

Do not take all your "solid" tiles from one pristine room and all your "hollow" ones from one
bad balcony. The classifier will learn the room.

---

## 4 · Where you will run short, and what to do about it

**Hollow is the constraint. Solid is available everywhere.** Capture hollow first; you can
always top up solid in ten minutes.

| likely to run short | why | fallback |
|---|---|---|
| **H6 gypsum stud partition** | most Indian flats are masonry throughout — you may have **zero** | do not hunt for it. Replace with H2 shutters and H5 duct boxes. |
| **H4 false ceiling** | absent in older flats | replace with H2 / H3 / H8 |
| **H7 debonded tile** | unpredictable, may be zero | **this is what H8 exists for.** Build the pair. |
| Hollow-block wall (visible) | plaster hides it; only visible at a socket cut-out or an unfinished area | skip — do not guess at a plastered wall |

**Abundant hollow fillers, all construction-certain:** every kitchen shutter, every wardrobe
door, every drawer front (closed, empty), every cabinet back panel, the bathroom duct box,
the false-ceiling hatch. A normal flat has fifteen of these before you leave the kitchen.

### The rule that matters more than the count: **keep it balanced**

| hollow / solid | n | majority baseline |
|---|---:|---:|
| 32 / 32 | 64 | 0.500 |
| 28 / 32 | 60 | 0.533 |
| 24 / 32 | 56 | 0.571 |
| **20 / 32** | 52 | **0.615** ⚠️ |
| **16 / 32** | 48 | **0.667** ⚠️ |
| **12 / 32** | 44 | **0.727** ⚠️ |

⚠️ **Once the baseline passes 0.60 the ship gate stops meaning anything** — a classifier that
always answers "solid" would pass it. So:

> **If you end up with more solid than hollow, do not record more solid. Stop, and keep only
> as many solid spots as you have hollow ones**, chosen to span the surface types. I will
> report the discards. **30 hollow + 30 solid beats 20 hollow + 40 solid, every time.**

### If you come back with 44 usable spots instead of 56

It still ships. It just cannot lead the demo unless the model is very good.

| usable spots | need this accuracy to clear **LB ≥ 0.80** (the stretch target) | need this to clear **LB > 0.60** (the ship gate) |
|---:|---:|---:|
| 32 | 93.8% — effectively out of reach | 78.1% |
| 40 | 92.5% | ~76% |
| **44** | **93.2%** | **75.0%** |
| 48 | 91.7% | ~74% |
| 56 | 91.1% | 73.2% |
| **64** | **90.6%** | **73.4%** |

Read that honestly: **even 64 spots needs ~91% observed accuracy to put the lower bound above
0.80.** Today's model is at 80% on eight easy files, and accuracy usually *falls* on harder,
more varied data. **The 64 spots buy you the chance at the target; they do not guarantee it.**
The ship gate at LB > 0.60 is comfortable from 44 spots upward.

**Priority if you run out of time:** balance first, then variety of surface types, then count.
**24 hollow + 24 solid across twelve different constructions beats 32 + 32 across four.**

---

## 5 · Why the app's own screen, and not a recorder app

This is not a convenience. `TapCaptureScreen.kt:29` records through
`AcousticTapClassifier.recordPcm` — **the identical `AudioRecord` configuration that inference
uses**: `AudioSource.MIC`, 44100 Hz, `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`, written out with
a hand-built 44-byte RIFF/WAVE header (`TapCaptureStore.kt:88`) so no codec ever touches it.

**Training bytes and serving bytes are then the same bytes.** The current model's worst
property is that it was fitted on WhatsApp Opus at 16 kHz with AGC and noise suppression, and
is served raw 44.1 kHz PCM. Any third-party recorder reintroduces that defect — different
audio source, possibly different processing — while looking like it solved it.

> Note for the record: `AudioSource.MIC` is not guaranteed to be unprocessed on every device.
> **That is fine and must not be "fixed".** Inference uses `MIC`, so capture must use `MIC`.
> Matching beats clean. If the A56 applies processing, both sides get it and the model learns
> through it. The pilot measures what the handset actually does.

**Fallback only if the debug APK cannot be installed** — and say so, because it changes what I
can promise:

| app | set it to | watch out for |
|---|---|---|
| **RecForge II** | WAV / PCM 16-bit, 44100 Hz, mono | the most configurable of the three |
| **Easy Voice Recorder** | WAV (PCM) 44.1 kHz mono — some formats are Pro-only | defaults to a compressed format |
| **Parrot Voice Recorder** | WAV / PCM, 44.1 kHz, mono | |

In any of them, **turn off**: skip-silence / silence trimming, noise reduction, auto gain,
"voice enhancement", call-recording mode, and stereo. If the app exposes an audio source,
choose **`UNPROCESSED`** if offered, otherwise **`VOICE_RECOGNITION`**, otherwise **`MIC`** —
but understand this now mismatches inference, and I will have to say so in the write-up.

**I cannot verify any of these three from here.** That is precisely what the pilot is for: send
me two files and I will tell you what the bytes actually are, whatever produced them.

---

## 6 · Recording one spot

1. Pick **Label** (solid / hollow) from §2 — **by construction, from the table.** Type a
   **Surface** slug that names the construction, not the sound: `h2_kitchen_shutter`,
   `s3_brick_ext_wall`, `h8_tile_on_battens`, `s4_bedded_tile_hall`. It goes in the filename,
   so the mix stays auditable and I can spot a category that dominates.
2. Hold the phone **10–15 cm** from the surface, mic end pointing at it. Keep that distance
   constant across every spot — it is the one capture variable most likely to leak into the
   classifier.
3. Press **Record spot (8s)**.
4. Strike **4–6 times**, about one per second, **middle knuckle**, medium force, **at the
   centre of the panel or tile**. Leave clear silence between strikes — the Python side finds
   transients by peak and needs the gaps.
5. Read the peak line:
   - `good` → move on.
   - `CLIPPING` (above −1.5 dBFS) → move back, **Discard last**, redo.
   - `WEAK` (below −30 dBFS) → move closer, **Discard last**, redo.

**Discard and redo if:** someone spoke, a fan or AC cut in, you scraped rather than struck,
you hit a grout line or an edge, or you are unsure of the construction.

**Do not** change rooms mid-spot, or use a knuckle on one spot and a pen or a coin on another.
**One striking implement throughout: your middle knuckle.** A different striker changes the
excitation spectrum more than the substrate does, and the classifier will happily learn it.

**Move at least 30 cm between spots**, and prefer a different object entirely. Two points on
the same tile are one spot. Two different doors are two spots.

Ambient: normal room quiet. Do not chase silence — inference runs in a real flat, so mild
background noise is *in-domain*. A running mixer or a TV is not.

---

## 7 · When you are done

1. **Export all (zip)** on the capture screen → share it to yourself.
2. Unzip into `smartlease-edge-chennai-master/ml/data/audio/` so it merges with the existing
   layout:

```
ml/data/audio/
├── hollow/   hollow_h2_kitchen_shutter_1757….wav   …
└── solid/    solid_s3_brick_ext_wall_1757….wav     …
```

3. Tell me it is there. I run T5: re-export through `export_for_android.py` (same recipe, same
   grouped LOGO, same golden-vector parity test), bake off logistic regression against
   gradient boosting and a small MLP **on the existing 36 features**, and the pre-committed
   gate in `TARGETS.md §8 G5` fires on the resulting interval.

**Keep the 8 original WhatsApp files where they are.** I will report the interval with and
without them. If the codec-shifted originals drag it down, dropping them is a finding worth
having rather than an assumption worth making.

---

## 8 · Time

| | |
|---|---|
| Pilot (2 spots + my check) | ~10 min, mostly waiting on me |
| Per spot after that | ~40 s including the peak check |
| 32 hollow (the constraint — do these first) | ~22 min |
| 32 solid | ~20 min |
| Moving between rooms, building the H8/S4 pairs | +10 min |
| **Total** | **~50 min after the pilot clears** |
