# RECORDING_PROTOCOL.md — tap capture, one page

**Use the in-app screen, not a recorder app.** Home → **"Tap capture (debug)"**.
Debug builds only. `adb install -r .../app/build/outputs/apk/debug/app-debug.apk`

---

## The number I need, and why it is spots and not taps

**24 spots minimum. 32 is the target. Balanced: 16 solid / 16 hollow.**
4–6 strikes inside each 8-second window → roughly 110–190 usable taps.

The existing `docs/guides/ACOUSTIC_RECORDING_CHECKLIST.md` asks for "100 samples" across
8 surface types. **That target is the wrong shape and following it would waste your evening.**
Here is the reason, and it is the only piece of theory on this page.

`ml/acoustic/export_for_android.py:61` groups the cross-validation by **filename**:

```python
groups.append(os.path.basename(path))     # the file IS the fold
```

LeaveOneGroupOut then trains on every file but one and tests on the one left out. So:

- **The number of folds equals the number of files, not the number of taps.**
- Five taps struck at the same spot are near-duplicates. Grouping them into one file is
  *correct* — it stops the model training on one strike and being tested on its twin.
- 150 taps recorded across 6 surfaces gives **6 folds**. The interval barely moves.
  150 taps across 30 spots gives **30 folds**, and the interval closes.

Current state: 15 taps, 8 files → 80% accuracy, **95% CI 55–93%** against a 53.3% baseline.
The bottom of that interval is chance.

Rough targets, Wilson interval at a plausible 72–78% accuracy on harder, more varied data:

| Distinct spots | Approx. CI lower bound | Verdict |
|---:|---:|---|
| 8 *(today)* | 0.55 | chance — unusable |
| 16 | ~0.57 | still not clear |
| **24** | **~0.60** | **minimum to pass the C2-B gate** |
| **32** | **~0.63** | **comfortable** |

Accuracy will probably *drop* from 80% as the surfaces get more varied. That is expected and
it is the point — the current 80% is measured on eight easy WhatsApp files.

**If you only have time for half: record 16 spots with maximum surface variety rather than
32 spots on four surfaces.** Variety across folds beats volume within them.

---

## Surface plan — 32 spots

A "spot" is one physical location. **Move at least 30 cm between spots**, and prefer a
different object entirely. Two spots on the same tile are one spot.

### HOLLOW — 16 spots
| Surface | Spots | Where |
|---|---:|---|
| Hollow-core interior door | 3 | Centre panel of 3 *different* doors. Not the frame, not near the handle. |
| Debonded / hollow floor tile | 4 | Tap across a tiled floor; the hollow ones ring. **Highest-value class — this is the actual deposit-dispute item.** |
| Hollow wall tile | 3 | Bathroom or kitchen splashback. |
| Plasterboard between studs | 3 | Knock along the wall; hollow between studs, dull over one. |
| Empty cupboard / wardrobe back panel | 3 | Thin ply over air. |

### SOLID — 16 spots
| Surface | Spots | Where |
|---|---:|---|
| Well-bonded floor tile | 4 | Same floor as the hollow tiles, on the dull-sounding areas. **Pair these with the hollow tiles — same room, same mic, same ambience. This is the comparison that matters.** |
| Solid plaster / RCC wall | 4 | Structural walls, 4 different walls. |
| Concrete floor or sill | 3 | Balcony, window sill, parking. |
| Solid wood | 3 | Dining table, solid door, heavy furniture. |
| Stone / granite counter | 2 | Kitchen counter. |

**The single most valuable pairing is hollow tile vs bonded tile on the same floor.** If the
classifier can only do one thing, that is the thing worth being able to do, and it is the one
a judge can reproduce by tapping the table in front of them.

---

## How to record one spot

1. Pick **Label** (solid / hollow) and type a **Surface** slug — `tile_floor`,
   `hollow_door`, `rcc_wall`. It goes in the filename so the mix is auditable later.
2. Hold the phone **10–15 cm** from the surface, mic end pointing at it. Keep it consistent.
3. Press **Record spot (8s)**.
4. Strike **4–6 times**, about one per second, with the **middle knuckle**, medium force.
   Leave clear silence between strikes — the Python side finds transients by peak and needs
   the gaps.
5. Read the peak line that appears:
   - `good` → move on.
   - `CLIPPING` (above −1.5 dBFS) → move back, **Discard last**, redo.
   - `WEAK` (below −30 dBFS) → move closer, **Discard last**, redo.

**Discard and redo if:** someone spoke, a fan or AC cut in, you scraped rather than struck,
you hit a grout line instead of the tile, or you are unsure. A discarded spot costs 20
seconds; a mislabelled one poisons a whole fold.

**Do not** change rooms mid-spot, tap the edge or corner of a tile, or use a knuckle on one
spot and a pen on another. One striking implement throughout: your middle knuckle.

Ambient: normal room quiet. Do not chase silence — inference will run in a real flat, so
mild background noise is *in-domain*. A running mixer or TV is not.

---

## When you are done

1. **Export all (zip)** on the capture screen → share to yourself (Drive, email, whatever).
2. Unzip into `smartlease-edge-chennai-master/ml/data/audio/` so it merges with the
   existing layout:

```
ml/data/audio/
├── hollow/   hollow_tile_floor_1757…wav   …
└── solid/    solid_rcc_wall_1757…wav      …
```

3. Tell me it is there. I run C2-B: re-export through `export_for_android.py` (same recipe,
   same grouped LOGO, same golden-vector parity test) and the pre-committed decision gate
   in `HANDOFF.md` fires on the new interval.

**Keep the 8 original WhatsApp files where they are.** I will report the interval with and
without them — if the codec-shifted originals are dragging it down, dropping them is a
finding worth having rather than an assumption worth making.

---

## Time

| | |
|---|---|
| Per spot | ~45 s including the peak check |
| 24 spots | **~20 min** |
| 32 spots | **~25 min** |
| Moving between rooms / finding surfaces | +15 min |

**Budget 40 minutes.** It is the highest-value 40 minutes available to this project: it is
the difference between the strongest sentence in `NOVELTY.md` being sayable and not.
