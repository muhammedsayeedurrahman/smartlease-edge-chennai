# DATASET.md

Built by `handoff/scripts/p1_rebuild_dataset.py`, which replaces
`YOLOV8/merge_and_zip_datasets.py`. Output: `YOLOV8/unified_v2/`.

---

## Provenance and licences

Four Roboflow Universe datasets. **All CC BY 4.0**, which permits commercial use **with
attribution** — and the product-facing docs carried no attribution until this audit.

| # | Roboflow project | workspace | licence | used |
|---|---|---|---|---|
| 1 | `infrastructure-monitoring` | `pothole-detection-h9muz` | CC BY 4.0 | yes |
| 2 | `structural-defects` | `kamar` | CC BY 4.0 | yes |
| 3 | `building-anomalies` | `image-classification-jf9vm` | CC BY 4.0 | yes |
| — | `concrete-defects-gn6lu` | `ammlworkspace` | CC BY 4.0 | **no** — downloaded, never merged |
| — | `detection-ghu6i` (paint-peel) | `image-classification-jf9vm` | Public Domain | **no** — downloaded, never merged |

The last two sit on disk with `data.yaml` files and were never included, by the original merge
or by this one — 150 images including the 40 most on-topic paint-peeling shots. Worth adding;
out of scope for this pass.

**Domain match is the dataset's central weakness.** Dataset 1 is substantially DJI drone
footage of building exteriors (`d1_DJI_0012_MP4-0017`). Dataset 2 is structural crack imagery.
None of it is an Indian rental flat interior shot on a phone under tubelight. The clean-surface
photos in `SHOOT_LIST.md` are the only domain-matched images in the whole corpus.

---

## Counts

| | images | backgrounds | % bg |
|---|---:|---:|---:|
| train | 1515 | 91 | 6.0% |
| valid | 418 | 49 | 11.7% |
| test | 268 | 14 | 5.2% |
| **total** | **2201** | **154** | **7.0%** |

226 groups. Instances: `crack` 2861 · `spalling` 2378 · `peeling` 1678 · `damp_stain` 832.

### Where the 154 backgrounds came from — none of them are new photographs

| source | n |
|---|---:|
| source label file was **already empty** | 78 |
| only annotations were classes we no longer keep | 76 |

**The original merge deleted all 78 of the first group.** `merge_and_zip_datasets.py:100` only
copies an image `if remapped_lines:` — so every image with an empty label was discarded. The
"0 backgrounds" line ultralytics printed was not because the data had none; it was because the
merge threw them away. Recovering them cost nothing.

**14 backgrounds in test is too few to measure a false-positive rate** — see
`METRICS_MODELS.md`. That is the gap `SHOOT_LIST.md` fills.

---

## Taxonomy

`nc` stays **4**, so the decoder contract is intact. Class 3 is redefined.

| idx | old | new | change |
|---|---|---|---|
| 0 | crack | crack | — |
| 1 | peeling | peeling | — |
| 2 | spalling | spalling | **`exposed brickwork` no longer maps here** (−323) |
| 3 | `stain_mould` | **`damp_stain`** | rust and efflorescence removed (1285 → 832) |

### Why class 3 was rebuilt

`stain_mould` scored AP50 **0.065** — effectively nothing. Measured composition:

| component | instances | share |
|---|---:|---:|
| pollution/moisture blackening | 514 | 40% |
| moss growth from damping | 246 | 19% |
| **corrosion / rust** (3 source labels) | **398** | **31%** |
| dampness | 72 | 6% |
| **efflorescence** | **55** | **4%** |

Three unrelated phenomena in one label. Rust is metal oxidation; efflorescence is mineral salt;
damp blackening is biological and moisture. Kept the coherent 65%, dropped the rest.

**Corrosion at 398 instances could support its own class** — but that makes `nc = 5`, a real
contract break requiring decoder-adjacent changes. Not worth it for a class that is about
fixture condition rather than wall damage. Flagged rather than decided unilaterally.

### `exposed brickwork` — and a correction

`merge_and_zip_datasets.py:24` mapped `exposed brickwork → spalling` (323 instances). That is
why the best-performing class was the one most likely to report a deliberate feature wall as
"spalling (surface breaking away)".

**My first fix was wrong and measuring it caught that.** I excluded all 89 brick-containing
images on the theory that deliberate brick and spalling-with-exposed-brick are confusable. The
data disagreed: **all 89 also contain a kept class and none is brick-only**, so exclusion cost
**931 instances — 640 of them spalling, 24% of the class** — and prevented no contradiction,
because there were no brick-only images to mislabel as background in the first place.

Corrected to drop the brick *annotation* and keep the image. Counts then reconcile exactly:
spalling 2701 → 2378 is precisely the 323 brick instances and nothing else. The explicit
negative signal for tidy feature walls comes from the 30 clean brick photos in `SHOOT_LIST.md`.

---

## Split methodology

**Group split, stratified by source dataset.** Groups never straddle a split; 226 of 226 sit in
exactly one, asserted at build time.

Group identity is the physical capture that produced a frame — video ID, phone-burst capture
day, or sequential-set bucket. Roboflow's per-image `_jpg.rf.<32 hex>` suffix is stripped first;
leaving it in makes every image its own group and the leak survives.

### The old split leaked

`DJI_0017` had **50 frames in train, 5 in valid, 3 in test**. `DJI_0012`, `DJI_0015` and
`VID_20241228_130232250` were split the same way. Near-duplicate frames on both sides of the
boundary inflate every metric.

### Stratification is load-bearing, and I got it wrong first

A first version allocated groups largest-first into whichever split was under quota. The result
measured the wrong thing entirely:

| | train | test |
|---|---|---|
| d2 share of images | 55% | **3%** |
| crack instances from d2 | 63% | 8% |
| crack instances from d1 | 36% | **80%** |

d2's large sequential buckets landed in train; d3's many single-image groups were scattered
into test to fill quota. That turns the evaluation into a **cross-dataset transfer test** — the
model asked to generalise from close-up crack photos to aerial imagery — and it is why `crack`
scored AP50 0.030. A real weakness, but a different question, and not one anybody can act on.

Stratifying per source dataset before group-splitting isolates the variable actually being
fixed — near-duplicate leakage — without confounding it with domain transfer. Result:

| | d1 | d2 | d3 |
|---|---:|---:|---:|
| train | 42% | 45% | 13% |
| valid | 44% | 43% | 13% |
| test | 32% | 57% | 10% |

Test is still somewhat d2-heavy. Worth knowing when reading the per-class numbers; far better
than 3% against 55%.

---

## The clean photos, when they arrive

```bash
handoff/env/train/Scripts/python.exe handoff/scripts/p1_rebuild_dataset.py --clean path/to/clean_photos
```

One folder per surface; the folder name becomes the group ID, so all shots of one wall stay on
the same side of the split.

**Change the background split ratio at the same time.** Backgrounds currently follow the global
70/20/10, which yields ~36 in test out of 360 — still thin. Backgrounds should go to test at a
higher rate than positives, targeting **60+**, or the false-positive column stays underpowered.

---

## The split allocator, rewritten — 2026-09-12 (T3.1)

`p1_rebuild_dataset.py:108` stratified group allocation by **source dataset** and by **image
count**, and by nothing else. That fixed the domain-shift bug described above and left a
second one standing: class composition was whatever fell out. It fell out badly.

| | images | bg | crack | peeling | spalling | damp_stain |
|---|---:|---:|---:|---:|---:|---:|
| **v2 test** (what A/B/C were measured on) | 268 | 14 | **46 (1.6% of class)** | 127 | 277 | 83 |
| **v3 test** (class-stratified) | 271 | **29** | **104 (3.6%)** | 118 | 563 | 79 |
| v3 pooled val+test | 703 | **76** | **666** | 343 | 1088 | 188 |

Allocation is now: hard image-share band, then a greedy "fill the biggest hole" score across
image count **and every class's instance count** and backgrounds, then a deterministic
hill-climb over single moves and pairwise swaps. Groups are still never split (226 of 226 sit
in exactly one split, asserted) and source stratification is preserved by construction,
because the whole thing runs inside each source stratum.

Backgrounds now target a 25% test share rather than 10% — they carry no labels, so
over-representing them in test costs no training signal and the specificity column is the one
that was underpowered.

### Why `crack` in test is 3.6% and not 10% — it is not the optimiser

Measured, by solving the knapsack directly:

```
87% of all 2861 crack instances sit in FIVE groups, totalling 1235 images - 56% of the dataset
  1:day:20241228        879 crack in 547 images
  2:seq:mildcrack:1     442 crack in 176 images
  2:seq:mildcrack:0     432 crack in 174 images
  2:seq:severecrack:1   383 crack in 178 images
  2:seq:severecrack:0   350 crack in 160 images
```

A 264-image test split *can* hold up to 702 crack instances — but only by taking one of the
`2:seq:*crack:*` blocks whole, which would simultaneously strip 15% of all crack from training
and push test to ~65% dataset 2, reinstating the cross-dataset transfer problem that the
source stratification exists to prevent.

**With 226 indivisible groups where five hold 87% of one class, you cannot have all four of:
no group leakage, source balance, class balance, and a 10% test split.** Something has to
give, and the least damaging thing to give up is the assumption that per-class AP should come
from the 10% split. It comes from the pooled val+test set instead — 666 crack instances, both
halves held-out groups — which is what `METRICS_MODELS.md` now reports.

### v3 is not evaluated against A, B or C, and here is why

`YOLOV8/unified_v3/` exists and is verified, but **no model is scored on it in this package.**
A, B and C were trained on v2's train split, and:

```
v3 pooled val+test, 703 images:  142 (20%) were in v2's TRAINING data
v3 test alone,      271 images:   84 (31%) were in v2's TRAINING data
```

Scoring A/B/C on v3 would be the incumbent's 0.6585 all over again, just milder — 20%
memorisation instead of 81%. **So the published A/B/C numbers stay on v2 and no third set of
numbers is produced.** T4 retrains on v3 from scratch, and those numbers will be v3-native
and clean.
