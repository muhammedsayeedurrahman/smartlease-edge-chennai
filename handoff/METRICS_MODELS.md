# METRICS_MODELS.md

Every number here was produced by scripts in `handoff/scripts/`. Nothing is quoted from an
earlier document.

**Verdict up front: no candidate cleared the promotion gate, so the control (A) ships.**
A is nonetheless a **statistically significant** improvement over the model currently on the
phone on the one metric the two can fairly be compared on: on 63 held-out clean images, A
leaves **19.0%** of them alone where the deployed model leaves **3.2%** — Fisher exact
**p = 0.0086**.

> **Revision, 2026-09-12.** Every per-class and false-positive figure in this document was
> re-measured after `TARGETS.md §1` found that the test split holds only **46 crack
> instances** and only **14** backgrounds. Accuracy is now reported on the **686-image pooled
> held-out set** and specificity on **63** backgrounds. **Five claims have been retracted**;
> each retraction is shown inline with the number that replaced it. Nothing was deleted
> silently.

---

## ⚠️ Read this before comparing any two numbers

**There are three different test sets in this project's history and they are not
interchangeable.** Most of the confusion available here comes from comparing across them.

| | split | taxonomy | mask mAP50 |
|---|---|---|---|
| **Old** (`METRICS.md`) | leaky — `DJI_0017` had 50 train / 5 valid / 3 test | `stain_mould` = damp + rust + efflorescence | 0.287 |
| **New** (this document) | grouped + stratified by source dataset, zero leakage | `damp_stain`, coherent | see below |

### Every number below carries its split. Two were circulating without one.

`TARGETS.md §1` reconciles them in full; the short version:

| set | images | instances | what it is | A | B | C |
|---|---:|---:|---|---:|---:|---:|
| **validation** | 418 | 1,334 | **training telemetry.** Written per-epoch to `runs/*/results.csv` and re-measured in `runs/t0_1_val_split.json`. It drove checkpoint selection, so it is optimistically biased and is **not** an evaluation result. | 0.1792 | 0.2045 | 0.1675 |
| **test** | 268 | 533 | held out. `p2_evaluate.py` → `runs/eval_*.json`. **Too small and too skewed to read per-class** — see the warning below. | 0.2488 | 0.2977 | 0.2928 |
| **pooled val+test** | 686 | 1,867 | **every held-out group.** `runs/t0_1_pooled.json`. The most reliable point estimate available; mildly optimistic only because validation picked the checkpoint. | 0.2145 | **0.2510** | 0.2087 |

**"A 0.171 / B 0.196" was the validation line scrolling past during training** (A hits 0.1707
at epoch 65, B 0.1946–0.1962 across epochs 73–100). It is telemetry. It should not have been
quoted and it is not quoted anywhere else in this package.

### ⚠️ The test split cannot measure per-class accuracy — it has 46 cracks

| split | images | bg | crack | peeling | spalling | damp_stain |
|---|---:|---:|---:|---:|---:|---:|
| train | 1515 | 91 | 2221 (37.8%) | 1449 (24.6%) | 1560 (26.5%) | 652 (11.1%) |
| valid | 418 | 49 | 594 (44.5%) | 102 (7.6%) | 541 (40.6%) | 97 (7.3%) |
| **test** | 268 | 14 | **46 (8.6%)** | 127 (23.8%) | **277 (52.0%)** | 83 (15.6%) |

`p1_rebuild_dataset.py:108` stratifies groups by **source dataset** but not by **class**. That
fixed the previous bug (the test set had become a cross-dataset transfer test) and left this
one standing. The test split is 52% `spalling` — the only class that works — which is why
every candidate scores higher on test than on validation.

**Read every per-class number below on the pooled column, not the test column.** Two claims
that were in this document are retracted at the bottom of this section.

> **The allocator has been rewritten (T3.1) and the numbers in this document did not move —
> deliberately.** `YOLOV8/unified_v3/` is built, class-stratified and verified: test crack
> goes 46 → **104** instances and test backgrounds 14 → **29**. But **20% of v3's held-out
> pool was in v2's training data** (`runs/t31_v2_v3_overlap.json`), so scoring A/B/C on it
> would be the incumbent's memorisation problem again in milder form. **No model in this
> document is evaluated on v3.** T4 retrains on v3 from scratch and those figures will be
> v3-native. There is no third set of numbers. See `DATASET.md`, last section.

**0.287 and 0.249 are not the same measurement and must never be shown side by side.** The
old figure was inflated by near-duplicate video frames on both sides of the split; the new
one is measured on genuinely unseen groups. A drop between them is expected and is not a
regression.

### The incumbent cannot be scored on the new test set — measured, not assumed

I evaluated the shipping `YOLOV8/best.pt` on the new test split. It returned **mask mAP50
0.6585**, which would make every retrained candidate look catastrophic. It is contamination:

```
WHERE MY NEW TEST IMAGES SAT IN THE OLD SPLIT
   train                216  (81%)
   valid                 32  (12%)
   test                  12   (4%)
   not_in_old_dataset     8   (3%)
```

**81% of the new test set was in the incumbent's training data.** Its 0.6585 is memorisation.
That number is discarded and appears nowhere else in this package.

**The one incumbent measurement that IS valid is its clean-image false-positive rate.** FP on
a background image counts detections where there should be none — it does not depend on
labels, taxonomy, or which split an image sat in. That comparison is below and it is fair.

---

## Accuracy — pooled held-out groups, 686 images, 1,867 instances

The primary accuracy table. Source: `runs/t0_1_pooled.json`.

| model | mask mAP50 | mask mAP50-95 | box mAP50 |
|---|---:|---:|---:|
| **A** yolov8n-seg @640 *(control)* | 0.2145 | 0.1071 | 0.2386 |
| **B** yolo11n-seg @640 | **0.2510** | **0.1221** | **0.2741** |
| **C** yolov8n-seg @800 | 0.2087 | 0.1034 | 0.2275 |

### Per class, mask AP50 — pooled

| class | A @640 | B @640 | C @800 | instances (train) | instances (pooled eval) |
|---|---:|---:|---:|---:|---:|
| crack | 0.2343 | **0.2527** | 0.2164 | 2221 | 640 |
| peeling | 0.1163 | **0.1297** | 0.1043 | 1449 | 229 |
| spalling | 0.4493 | **0.4989** | 0.4657 | 1560 | 818 |
| damp_stain | 0.0580 | **0.1225** | 0.0482 | 652 | 180 |

**B is ahead on every class and on every split.** That is the one result in this bake-off that
survives every way of cutting the data.

**The weakest class is `damp_stain` at 0.058**, not `crack`. It is the smallest class (652
train instances) and the one whose boundary is a judgement rather than an edge — two
annotators disagree about where a damp patch ends by far more than the few pixels IoU 0.5
tolerates. B more than doubles it (0.058 → 0.123), which is the largest real gain here.

`crack` sits at 0.23 and is bounded by output resolution rather than data volume: the mask is
drawn on a 160×160 prototype grid, and **25.7% of our crack instances are ≤4 grid cells
wide**. `TARGETS.md §3.1` has the measurement and the mechanism.

### For reference only — the same models on the two smaller splits

Do not quote these per class. They are here so the numbers in circulation can be identified.

| | A | B | C |
|---|---:|---:|---:|
| **test** split, 268 images — mask mAP50 | 0.2488 | 0.2977 | 0.2928 |
| **validation** split, 418 images — mask mAP50 *(training telemetry)* | 0.1792 | 0.2045 | 0.1675 |

### ⚠️ Two claims previously in this document are retracted

> ~~"`crack` is broken on both — ~0.05 — and it is the headline use case. It has the *most*
> training instances (2861), so this is not a data-volume problem."~~

0.0516 was measured on **46** test-split crack instances. The same weights score **0.2343** on
the 640 crack instances in the pool. `crack` is our weakest-but-one class, not a broken one,
and the original figure was a small-sample artefact.

> ~~"Candidate C … **confirms it**: crack goes 0.052 → 0.168 at 800px, a 3.3× improvement, the
> largest single-class movement anywhere in this bake-off."~~

It does not replicate. On the pool C is the **worst** of the three on `crack` (0.2164 vs A
0.2343, B 0.2527) and the worst overall (0.2087). The 3.3× was noise on 46 instances reported
as a mechanism. Higher input resolution remains the most plausible *explanation* for why
`crack` is hard — §3.1's prototype-grid measurement is independent of C — but C did not
demonstrate it, and this package no longer claims it did.

---

## Clean-surface specificity — the metric this exercise exists to move

**Now measured on 63 background images, not 14.** Backgrounds carry no labels, so pooling the
49 in validation with the 14 in test leaks nothing — they were simply never used. Source:
`runs/t0_1_clean_fp_63.json`.

**Specificity = the % of clean images producing ZERO detections.** One box anywhere in the
frame fails the image. The app ships `SCORE_THRESHOLD = 0.25`.

| conf | incumbent | **A** | **B** | C | incumbent FP/img | A FP/img | B FP/img |
|---:|---:|---:|---:|---:|---:|---:|---:|
| **0.25** | **3.2%** | **19.0%** | 20.6% | 17.5% | 4.95 | 3.79 | 3.40 |
| 0.35 | 14.3% | 30.2% | 34.9% | 27.0% | 3.70 | 2.70 | 2.22 |
| 0.45 | 22.2% | 39.7% | 41.3% | 38.1% | 3.08 | 1.87 | 1.59 |
| 0.55 | 31.7% | 47.6% | 47.6% | 47.6% | 2.43 | 1.41 | 1.10 |
| 0.65 | 41.3% | 52.4% | **61.9%** | 60.3% | 1.83 | 1.03 | 0.65 |

**At the shipped threshold the model on the phone leaves 2 clean images out of 63 alone —
3.2%. A leaves 12 — 19.0%. Fisher exact p = 0.0086.** That is now a statistically supported
statement rather than an anecdote, and it is the strongest honest claim in this package.

| conf | Fisher, A vs incumbent | Fisher, A vs B |
|---:|---:|---:|
| **0.25** | **0.0086** | 1.000 |
| 0.35 | 0.0526 | 0.704 |
| 0.45 | 0.0533 | 1.000 |
| 0.55 | 0.1008 | 1.000 |
| 0.65 | 0.2840 | 0.368 |

### Three claims from the 14-image sample are retracted

> ~~"At the shipped threshold the incumbent flags 100% of clean images — every single one."~~

It flags **96.8%** (61 of 63). The 100% was 14 of 14 — true of that sample, and an
overstatement of the population. The real number is still bad enough to lead with.

> ~~"The incumbent's false positives are dominated by one class: `stain_mould` produced 16 of
> its 25 detections at conf 0.25."~~

On 63 images the incumbent produces **312** detections, of which **`spalling` is 213** and
`stain_mould` is 68. **`spalling` dominates, not `stain_mould`** — and it dominates A's false
positives too (161 of 239). The taxonomy argument for splitting `stain_mould` still stands on
its own (AP50 0.065 from three unrelated phenomena), but **this was not evidence for it.**

> ~~"Separating those rates at p<0.05 needs roughly 60–70 background images."~~

That estimate was derived from a 29%-vs-57% gap that was itself noise. See below.

### The A-vs-B specificity tiebreak is not closeable, and 360 photos will not close it

The gaps that actually exist are 0–10 points, not 28. Two-proportion sample size, α 0.05,
power 0.80:

| conf | A | B | backgrounds needed **per model** |
|---:|---:|---:|---:|
| 0.25 | 19.0% | 20.6% | 9,737 |
| 0.35 | 30.2% | 34.9% | 1,560 |
| 0.45 | 39.7% | 41.3% | 14,776 |
| 0.55 | 47.6% | 47.6% | no n suffices |
| 0.65 | 52.4% | **61.9%** | **425** |

**This changes the gate rather than the data collection.** Demanding that B prove
*superiority* on specificity was a badly-specified requirement: the true difference is small,
and a small difference needs a sample nobody is going to shoot. The replacement is a
non-inferiority test, pre-committed in `TARGETS.md §8 G3` before the retrained numbers exist.

---

## Promotion gate

> **Rule as originally fixed:** a candidate is promoted only if it beats A on **both** mAP and
> clean-image FP. If nothing beats the incumbent, ship the incumbent.

**Gate fired for C: NOT PROMOTED — failed**, and more clearly than before. On the 686-image
pool C is the worst of the three on mAP50 (0.2087) *and* on every class, and it breaks the
input-size contract. The test-split result that made it look competitive was 46 crack
instances.

**Gate fired for B: NOT PROMOTED — indeterminate, not failed.**

- Accuracy: **B beats A on every split and every class.** Pooled 0.2510 vs 0.2145, over 686
  images and 1,867 instances.
- Specificity: **B is numerically better at four of five thresholds and worse at none**, but
  no comparison is significant and, per the table above, none can be made significant.

**The gate as written cannot be satisfied by any evidence obtainable tonight.** That is a
defect in the gate, not a verdict on B. `TARGETS.md §8 G3` replaces it with a non-inferiority
condition — B must not be *worse* than A by more than 5 points — which is both decidable and
the question anybody actually cares about.

### What ships today

**A — the control**, unchanged, because the original gate has not been satisfied and the
replacement gate is evaluated in T4 on the retrained models, not retro-fitted to these.

A is not a consolation prize. Against the model actually on the phone, on the one metric the
two can fairly be compared on, **A leaves 19.0% of clean surfaces alone where the deployed
model leaves 3.2% — p = 0.0086.**

**B remains the likely winner**: ahead on every accuracy cut and never behind on specificity.

---

## Recommended operating threshold

**Hold `SCORE_THRESHOLD` at 0.25 until the indoor photos exist.** Not because 0.25 is good —
at 0.25 A leaves only 19% of clean surfaces alone — but because raising it trades recall for
specificity and **the specificity being bought is measured on drone frames of building
exteriors, not on the surfaces the app will actually see.**

A's tradeoff from 0.25 → 0.65 on these backgrounds: specificity 19.0% → 52.4%, and mask recall
falls with it. **No threshold reaches 90% on this background set**, which is the honest
context for the ≥90% target in `TARGETS.md`: that target assumes domain-matched negatives *in
training*, plus the T2 damage gate in front. It is not reachable by thresholding alone.

---

## Latency, and why no `s` or `m` scale was trained

The app runs PyTorch Lite on a phone CPU with **no delegate** — no NNAPI, no GPU, no HTP.

| model | GFLOPs | relative |
|---|---:|---:|
| yolov8n-seg @640 | 11.3 | 1.0× |
| yolov8n-seg @800 (C) | ~17.7 | 1.56× |
| yolov8s-seg @640 | ~42.4 | 3.75× |

The measured reference point is **21.8 ms on a desktop Ryzen 9** for yolov8n-seg @640
(`METRICS.md`). A phone CPU core is several times slower, and the app adds a Kotlin decoder
over 8400 anchors on top. At 3.75× the FLOPs, `s` puts a single capture into seconds — a UX
failure, not a metric tradeoff. It was not trained and should not be.

**Candidate C at 800px costs ~1.56× and breaks the input-size contract** (`INPUT_SIZE = 640`
in `YoloSegConfig.kt:14`), so it needs a Kotlin change *and* the app owner accepting the
latency.

**And on the pooled set it does not buy anything.** C is last on overall mask mAP50 (0.2087 vs
A 0.2145, B 0.2510) and last on `crack` (0.2164 vs A 0.2343, B 0.2527). Its specificity is
also A's or slightly worse at every threshold below 0.65. **Not promoted, and now for a
straightforward reason rather than a split decision.**

**The roadmap note is withdrawn.** "If crack detection is the priority, resolution is the
lever" was inferred from C beating A on 46 test-split crack instances, and it reverses on 640.
The prototype-grid measurement in `TARGETS.md §3.1` — 25.7% of crack instances are ≤4 cells
wide at 640, 18.0% at 800 — is independent of C and remains the best available explanation for
why `crack` is hard. **It is a mechanism with a plausible fix, not a measured win, and this
package no longer presents it as one.**
