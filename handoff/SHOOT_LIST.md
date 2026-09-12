# SHOOT_LIST.md — clean-surface photos

**What you are shooting:** surfaces with **no defect at all**. These become empty-label
background images. Ultralytics treats an empty `.txt` as "this whole image is negative", and
the dataset currently has **zero** of them — which is why a clean wall lights up at
`SCORE_THRESHOLD 0.25`.

**Target: 360 images.** ~50 minutes. Phone camera, default settings, JPEG.

---

## Why this is the highest-value thing you can do today

Across all 2,106 training images there is not one empty label file. The model has literally
never been shown a surface and told "nothing here". It cannot represent the concept.

Two things I found while preparing this, both worth knowing:

- **The source datasets already contained 78 empty-label images.**
  `merge_and_zip_datasets.py:100` only copies an image `if remapped_lines:` — so all 78 were
  silently deleted during the merge. The "0 backgrounds" line ultralytics prints is not
  because the data never had any; it is because the merge threw them away.
- **Another 76 become usable negatives once the taxonomy is fixed** (images whose only
  annotations were corrosion, efflorescence, graffiti or a broken window pane — none of
  which we keep as classes).

So **154 hard negatives already exist in the data** and I will recover them for free. Your
360 photos are what make it a real negative set, and — unlike the recovered ones, which are
drone shots of building exteriors — **yours are the only ones that match the actual domain:
an Indian rental flat interior, phone camera, indoor lighting.**

---

## Counts

| # | Surface | Shots | Why it matters |
|---|---|---:|---|
| 1 | **Painted wall, plain** | 60 | The single most common surface a judge will point the phone at. |
| 2 | **Tiled floor with grout lines** | 50 | Grout is a dark straight line. `crack` is trained almost entirely on dark straight lines. **Highest false-positive risk in the whole list.** |
| 3 | **Textured / putty finish wall** | 40 | Texture reads as `peeling`. |
| 4 | **Wood doors, frames, skirting** | 40 | Grain is parallel linear texture → `crack`. |
| 5 | **Exposed-brick feature wall** | 30 | ⚠️ See the warning below — shoot these, but I handle them separately. |
| 6 | **Switch plates, sockets, boards** | 30 | Rectangular surface interruptions → `spalling`. |
| 7 | **Patterned / terrazzo / mosaic tile** | 30 | Dense pattern → `crack` + `spalling` together. |
| 8 | **Misc: ceiling, cupboard doors, worktop** | 30 | General indoor coverage. |
| 9 | **Curtains and fabric** | 25 | Folds cast soft dark lines. |
| 10 | **Window grilles, railings (clean, no rust)** | 25 | Must be **rust-free** — rust is a real detection, not a negative. |
| | **Total** | **360** | |

---

## How to shoot

**Frame it the way the app will be used.** These are negatives for a phone held by someone
inspecting a wall — not architectural photographs. If the framing does not match the
walkthrough, the negatives teach the model the wrong thing.

- **Distance 0.5–1.5 m.** Arm's length to a step back. Fill the frame with the surface.
- **Angle:** roughly two thirds straight-on, one third oblique (20–45°). Real capture is not
  perpendicular.
- **Lighting: vary it deliberately.** Tubelight, warm LED, daylight through a window, and a
  few in poor light. Tubelight colour temperature is the single most under-represented thing
  in the current training data, which is mostly outdoor daylight.
- **Move between shots.** Ten photos of the same square metre is one photo. Shift along the
  wall, change height, change angle.
- **Hold still enough to be sharp**, but do not chase perfection — mild blur is in-domain.

**Deliberately include the hard cases.** These are the shots that actually move the number:

- shadows in corners, under sills, behind furniture
- cable runs, conduit, wall-mounted wires
- reflections and glare on tile or gloss paint
- skirting joints, tile edges, corner beads
- scuffs and marks that are **dirt, not damage** — a scuff is not peeling paint

**Do NOT include:** any actual crack, peeling, spalling, damp patch, mould or rust. If you
are unsure whether something is a defect, **do not shoot it** — a mislabelled negative is
worse than a missing one, because it actively teaches the model that a defect is background.

---

## ⚠️ Exposed brick — shoot it, but it is handled differently

Do not skip it, but know what happens to it.

`merge_and_zip_datasets.py:24` maps **`exposed brickwork` → `spalling`** (323 instances).
That mapping is why the best-performing class is the one most likely to call a deliberate
feature wall "spalling (surface breaking away)" in a tenant's report.

The problem is that deliberate exposed brick and spalling-that-has-exposed-brick look
**nearly identical**. Labelling one background and the other `spalling` is asking the model
to infer *intent* from pixels, which it cannot do. Contradictory supervision would degrade
`spalling`, the one class that currently works.

So: **89 source images that contain both a kept class and exposed brickwork get excluded from
the dataset entirely** — not used as positives, not used as backgrounds. Your 30 clean
feature-wall shots stay in as negatives, because a *whole frame* of tidy brick with no
substrate damage is unambiguous in a way a mixed frame is not.

I will state this in `DATASET.md` rather than burying it.

---

## Folder and naming

One folder per surface. The folder name becomes the **group ID** for splitting, so all shots
of one surface land on the same side of the train/val boundary and val stops being a
near-duplicate of train.

```
clean_photos/
├── painted_wall/        IMG_0001.jpg …
├── tiled_floor_grout/
├── textured_putty/
├── wood_door_frame/
├── exposed_brick/
├── switch_socket/
├── patterned_tile/
├── misc_indoor/
├── curtain_fabric/
└── window_grille/
```

Filenames do not matter — the folder does. **If you shoot two different rooms' painted walls,
put them in `painted_wall_a/` and `painted_wall_b/`.** More distinct groups is strictly
better; it is the same reason the tap recordings are grouped by spot.

---

## When you are done

Tell me the path. The ingest script resizes, writes the empty `.txt` labels, and merges them
into **train, val and test**.

**Backgrounds go into val and test as well as train** — roughly 70/20/10 by group. If they
only went into train, the false-positive rate would improve and no metric would show it,
which is the failure mode this whole exercise exists to avoid.
