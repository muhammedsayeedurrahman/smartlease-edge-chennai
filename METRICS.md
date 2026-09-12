# METRICS.md — the first recorded evaluation of the vision model

Produced 2026-09-12 by `AUDIT_GAPS.md` item **P1-7**, which found that the project had **no
recorded vision metric of any kind**: no `results.csv`, no confusion matrix, no PR curve, no
`runs/` directory, and a training notebook with zero saved outputs and every
`execution_count` null — including its own "Validation & Quantitative Metrics" section.

**Read the caveat before the numbers. It is larger than the numbers.**

---

## Caveat, stated first

**Neither split below is genuinely held out.** The splits are inherited from the source
Roboflow datasets, and datasets 1 and 2 contain consecutive frames from the same videos:

| Source video | train | valid | test |
|---|---:|---:|---:|
| `DJI_0017_MP4` | 50 | 5 | 3 |
| `DJI_0012_MP4` | ✓ | ✓ | ✓ |
| `DJI_0015_MP4` | ✓ | ✓ | ✓ |
| `VID_20241228_130232250_mp4` | ✓ | ✓ | ✓ |

Near-duplicate frames appear on both sides of every split, so **these figures are inflated**.
The honest version of this table needs a re-split grouped by source video — the same
discipline the acoustic model already applies with `LeaveOneGroupOut` grouped by recording.
Until that is done, treat everything below as an optimistic ceiling, not an estimate.

Second caveat, straight from the validator's own scan line:

```
val: Scanning .../unified_defects/test/labels... 107 images, 0 backgrounds, 0 corrupt
```

**`0 backgrounds`** — not one image in the evaluation set is free of defects, because not one
image in the whole dataset is. The model has never seen a clean wall and is never scored on
one, so none of these numbers say anything about its false-positive rate in a real room.
That is the single most important thing missing here.

---

## Run

```
cd YOLOV8
yolo segment val model=best.pt data=unified_defects/data.yaml split=test imgsz=640 device=cpu
```

`ultralytics 8.4.148` · `torch 2.12.1+cpu` · AMD Ryzen 9 8940HX · YOLOv8n-seg, 3,258,844
parameters, 11.3 GFLOPs. Raw run artifacts in `YOLOV8/runs_audit/`.

---

## Test split — 107 images, 336 instances

| Class | Images | Instances | Mask AP50 | Mask P | Mask R | Box AP50 |
|---|---:|---:|---:|---:|---:|---:|
| **all** | 107 | 336 | **0.287** | 0.360 | 0.331 | **0.319** |
| spalling | 50 | 96 | 0.589 | 0.606 | 0.577 | 0.637 |
| crack | 51 | 132 | 0.249 | 0.415 | 0.307 | 0.316 |
| peeling | 21 | 50 | 0.244 | 0.233 | 0.320 | 0.259 |
| stain_mould | 21 | 58 | **0.065** | 0.187 | 0.121 | 0.065 |

**Mask mAP50-95: 0.148.  Box mAP50-95: 0.190.**

## Valid split — 203 images, 785 instances

| | Mask mAP50 | Mask mAP50-95 | Box mAP50 | Box mAP50-95 |
|---|---:|---:|---:|---:|
| all | 0.267 | 0.136 | 0.302 | 0.176 |

Valid and test agree closely (0.267 vs 0.287 mask mAP50), which is what you would expect
when both are contaminated by the same video frames — agreement here is not evidence of
generalisation.

---

## What these numbers actually say

1. **Overall mask mAP50 is 0.287.** The deck previously claimed a target of "75%+ mAP". That
   line has been removed (commit `da552fd`). Nothing in this repo supports it.
2. **`stain_mould` does not work.** AP50 of 0.065 is close to nothing. It is also the class
   built from the most incoherent merge: `merge_and_zip_datasets.py` folds `Corrosion`,
   `Corrosion stain`, `corrosion`, `Moss growth due to damping`, `dampness`, `efflorescence`
   and `pollution-moisture induced blackening` into one label. That is at least three
   different physical phenomena sharing a class, and the model has not learned it.
3. **`spalling` is the only class carrying the model** (0.589). It is also the class that
   absorbed `exposed brickwork` and `Incomplete Plaster Work`, so an intentional exposed-brick
   feature wall is likely to be reported to a tenant as "spalling (surface breaking away)".
   The class that works best is the one most likely to produce an embarrassing false positive.
4. **`crack` — the headline use case — is at 0.249 mask AP50**, with recall 0.307. It misses
   roughly two thirds of the cracks it is scored on, on data it has partly seen in training.

## Latency — a bound, not a measurement

`21.8 ms inference per image` at batch 8, **on a desktop Ryzen 9 CPU**, via ultralytics/PyTorch.

This is **not** the phone and **not** the app's path. The APK runs the same weights through
PyTorch Lite on an ARM CPU, single image, with a Kotlin decoder over 8400 anchors on top. No
on-device measurement exists. Do not quote 21.8 ms as an app latency; quote it, if at all, as
"the model is small — 11.3 GFLOPs, 22 ms on a laptop CPU — and we have not yet measured it on
the handset."

The `<30 ms` figure in the original deck referred to INT8 on the Hexagon HTP, which is not
wired into the APK at all. Those slides have been corrected.

---

## What to say if a judge asks for the mAP

> "Mask mAP50 is 0.287 on our test split. I'd discount that, because our splits share video
> frames across train and test, so it's inflated. Three things are visible in the per-class
> numbers: spalling works at 0.59, crack is weak at 0.25, and stain_mould is broken at 0.07
> because we merged corrosion, damp and efflorescence into one class that isn't one thing.
> And there isn't a single background image in the dataset, so none of this measures false
> positives on a clean wall — which is the number that actually matters for this product."

That answer scores better than 0.287 does, because it demonstrates you know what the number
is worth. It is also the honest one.

## What would fix it, in order

1. Re-split grouped by source video. Costs an hour, and turns every figure here from
   decorative into real.
2. 200 clean-surface backgrounds with empty labels. Attacks the actual failure mode.
3. Split `stain_mould` apart, or drop it. One class cannot be rust, damp and mould.
4. Only then retrain, and only then quote a number.

---

## Operating point, measured — PART 1 of 2, INCOMPLETE

`SCORE_THRESHOLD` is `0.25f` (`YoloSegConfig.kt:47`). Sweep run 2026-09-12 via
`YOLOV8/threshold_sweep.py`, ultralytics `val()` on the held-out test split — which
letterboxes with 114 fill exactly as the Android path does, *not* the aspect-distorting
`cv2.resize` in `prepare_calibration.py:39` (P2-6).

| conf | mask mAP50 | mask recall | mask precision | FP / clean image | % clean images flagged |
|-----:|-----------:|------------:|---------------:|---:|---:|
| **0.25** *(shipped)* | 0.231 | 0.330 | 0.360 | **—** | **—** |
| 0.30 | 0.223 | 0.320 | 0.388 | — | — |
| 0.35 | 0.212 | 0.295 | 0.408 | — | — |
| 0.40 | 0.197 | 0.271 | 0.432 | — | — |
| 0.45 | 0.184 | 0.245 | 0.462 | — | — |
| 0.50 | 0.174 | 0.227 | 0.487 | — | — |
| 0.55 | 0.162 | 0.203 | 0.494 | — | — |
| 0.60 | 0.148 | 0.178 | 0.505 | — | — |
| 0.65 | 0.136 | 0.159 | 0.522 | — | — |

### No threshold change has been shipped, and that is deliberate

**The two right-hand columns are the entire reason to raise the threshold, and they are
empty** — they need photographs of clean surfaces, which do not exist yet. Recall alone
cannot justify the change: every row below 0.25 is a *pure loss* of recall until there is a
measured false-positive reduction to weigh it against. Picking 0.40 today would mean giving
up 18% of relative recall for a benefit nobody has measured.

What the recall curve does say, and it is worth having:

- There is **no knee**. Recall decays smoothly, 0.330 → 0.159 across the range; precision
  climbs 0.360 → 0.522. Nothing in this curve picks a threshold on its own.
- **0.30–0.40 is the candidate band.** Cheapest region: recall 0.330 → 0.271 while precision
  gains 0.36 → 0.43.
- **Above 0.50 recall collapses** to under a quarter of instances. Off the table regardless
  of what the FP data says.

### To complete this

Shoot 20–30 photos of clean surfaces — painted wall, tiled floor with grout, switch plate,
wood door, patterned tile, curtain, exposed brick — then:

```
cd YOLOV8 && python threshold_sweep.py --clean path/to/clean_photos
```

The operating point is then the row where FP/clean-image drops sharply and recall has not
yet collapsed, and the tradeoff taken gets written here explicitly.

**Until then `SCORE_THRESHOLD` stays at 0.25 and `JUDGE_QA` Q10/Q11 keep their current
"known gap" answers**, which are honest and already rehearsed.
