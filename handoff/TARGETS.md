# TARGETS.md

**The contract for T1–T6.** Every target below was fixed *before* the work that measures it.
Anything that misses its target ships with the real number and one sentence saying why —
never rounded up, never quietly dropped.

**Date:** 2026-09-12 · **Status of every number here:** measured by scripts in
`handoff/scripts/` or by the T0 measurement scripts named inline. External reference points
are marked **[EXTERNAL]** and are used only for calibration, never as our results.

---

## 0 · The short answer to "can we say 90%?"

**Yes — for three of the six metrics, and they are the three that matter for this product.
No — for mask mAP50, and no amount of work tonight changes that.**

| | can it reach 90% | why in one line |
|---|:--:|---|
| Clean-surface specificity | **YES** | free labels, 360 domain-matched images, directly optimisable |
| Damage-gate accuracy (image level) | **YES, with a caveat that could invalidate it** | binary tasks on 1.5k images routinely land 90–95% — but see §6.2, the shortcut trap |
| Image-level precision | **YES, at a stated recall cost** | it is a threshold choice, and we can show the whole curve |
| Mask mAP50 overall | **NO** | ~0.30–0.38 is the honest ceiling tonight; see §3 |
| Mask AP50 on `crack` | **NO** | the mask is drawn on a 160×160 grid and a quarter of our cracks are ≤4 cells wide |
| Acoustic accuracy ≥0.90 with CI lower bound ≥0.80 | **NO at 32 spots** | needs ~54 independent spots even at a perfect-looking 90%; 32 tops out at 0.758 |

**I agree with your argument, and the measurements support it.** For a tenant-facing tool,
*"does it leave a clean wall alone"* and *"when it flags something, is it real"* are both the
more honest questions and the ones that can truthfully carry a 90. Mask mAP50 answers
*"how precisely did it trace the outline"*, which nobody in a deposit dispute is asking.

The one place I would push back on your framing: **do not replace mAP, report it next to
them.** A judge who knows segmentation will ask for mAP, and a suite that has quietly dropped
it reads as evasion. A suite that shows it, low, with the reason, reads as understanding.
That is the difference §7 is built around.

---

## 1 · T0.1 — the two circulating numbers, reconciled

**Both are real. They are different splits, and neither was labelled.**

| | images | instances | A | B | C | produced by |
|---|---:|---:|---:|---:|---:|---|
| **VALIDATION** split | 418 | 1,334 | **0.1792** | **0.2045** | 0.1675 | `t0_1_val_split.json`; also written per-epoch to `runs/*/results.csv` |
| **TEST** split (held out) | 268 | 533 | **0.2488** | **0.2977** | 0.2928 | `p2_evaluate.py` → `runs/eval_*.json` |

**Where "A 0.171 / B 0.196" came from:** the per-epoch validation line in the training
console. `A_yolov8n_640/results.csv` reports val mask mAP50 **0.1707 at epoch 65** and
**0.1723 at epoch 90**; `B_yolo11n_640/results.csv` reports **0.1946–0.1962** across epochs
73–100. Those are training telemetry, not an evaluation result, and they should never have
left the terminal.

**Resolution, applied to `METRICS_MODELS.md` in this pass:**

- Every table there is now headed with its split and its image count.
- The validation figures are recorded as training telemetry with that label.
- The **TEST** figures remain the headline, because validation drove checkpoint selection
  (`best.pt` is chosen by validation fitness) and is therefore optimistically biased.

### But reconciling them exposed something worse, and it changes two published claims

The splits have incompatible class mixes. Measured over all 2,201 label files:

| split | images | bg | crack | peeling | spalling | damp_stain | instances |
|---|---:|---:|---:|---:|---:|---:|---:|
| train | 1515 | 91 | 2221 (37.8%) | 1449 (24.6%) | 1560 (26.5%) | 652 (11.1%) | 5882 |
| valid | 418 | 49 | 594 (44.5%) | 102 (7.6%) | 541 (40.6%) | 97 (7.3%) | 1334 |
| **test** | 268 | 14 | **46 (8.6%)** | 127 (23.8%) | **277 (52.0%)** | 83 (15.6%) | 533 |

Three consequences, in increasing order of seriousness:

1. **Test scores higher than validation for every candidate** — not because the models
   generalise, but because test is 52% `spalling`, the one class that works (AP50 0.62), and
   only 8.6% `crack`, the one that does not.

2. **`crack` AP50 on the test split is computed from 46 instances.** The same weights on the
   594-instance validation split score **0.2565**. Five times higher, same model, same day.

3. **The "resolution is the crack lever" finding does not replicate.** C beats A on crack on
   the test split (0.1683 vs 0.0516) and **loses** to A on crack on the validation split
   (0.2237 vs 0.2565) and on the 686-image pool (0.2164 vs 0.2343). The sign reverses the
   moment the sample is large enough to mean anything.

**Two claims in `METRICS_MODELS.md` are therefore retracted tonight:**

> ~~"`crack` is broken on both — ~0.05 — … it has the *most* training instances (2861), so
> this is not a data-volume problem."~~
> It is 0.05 on 46 test instances and 0.26 on 594 validation instances. **Unresolved, not
> broken.** The honest statement is that `crack` is our weakest class and our worst-measured
> one, and the second half of that sentence was missing.

> ~~"Candidate C … **confirms it**: crack goes 0.052 → 0.168 at 800px, a 3.3× improvement, the
> largest single-class movement anywhere in this bake-off."~~
> On the 686-image pool C is the **worst** of the three on crack (0.2164 vs A 0.2343, B
> 0.2527) and the worst overall (0.2087 vs A 0.2145, B 0.2510). **A single-split,
> 46-instance result was reported as a confirmed mechanism.** The resolution hypothesis is
> still the best available explanation of why `crack` is our weakest class (§3.1 gives the
> mechanism), but it is a hypothesis, and C did not test it — it measured noise.

**What is unaffected:** the overall mAP ordering (B > A on *both* splits and on the pool
below), the entire clean-image false-positive table (no labels involved, so no mix effect),
and the incumbent-contamination finding.

**Root cause:** `p1_rebuild_dataset.py:108` stratifies group allocation by **source dataset**
but not by **class**. That was the correct fix for the previous bug — the domain shift that
made the test set a cross-dataset transfer test — and it left a second one standing. Fixed in
T1.3.

### Pooled over every held-out group — the most reliable point estimate available today

686 images, 1,867 instances, 63 backgrounds. Val and test are both held-out *groups*, so
pooling them leaks nothing; it is mildly optimistic only because validation drove checkpoint
selection.

| candidate | **pooled mask mAP50** | val | test | pooled crack | pooled damp_stain | pooled spalling |
|---|---:|---:|---:|---:|---:|---:|
| **A** yolov8n-seg @640 | 0.2145 | 0.1792 | 0.2488 | 0.2343 | 0.0580 | 0.4493 |
| **B** yolo11n-seg @640 | **0.2510** | **0.2045** | **0.2977** | **0.2527** | **0.1225** | **0.4989** |
| C yolov8n-seg @800 | 0.2087 | 0.1675 | 0.2928 | 0.2164 | 0.0482 | 0.4657 |

**B leads on every split and every class.** That is the one conclusion from the bake-off that
survives every way of cutting the data, and it is what T4 is built on.

**C is the worst of the three on the pool**, not the middle as the test split suggested. Its
entire case rested on 46 crack instances.

Note also what the pool does to the *shape* of the result: `crack` is no longer the worst
class — **`damp_stain` is** (A 0.058, B 0.123). The test split said crack 0.05 / damp_stain
0.10; the pool says crack 0.23 / damp_stain 0.06. Both published per-class orderings were
artefacts of which 268 images landed in test.

---

## 2 · The six metrics, defined and bounded

Every definition below is the one the T3 sweep will implement, so there is no room to move
the goalposts later.

### M1 · Clean-surface specificity
**Definition:** of held-out clean images, the % producing **zero** detections at the operating
confidence. One detection anywhere in the frame fails the image.

| | |
|---|---|
| today, re-measured on **63** held-out backgrounds | **19.0%** for A at conf 0.25 · **3.2%** for the model on the phone · Fisher **p = 0.0086** |
| at conf 0.65 | A 52.4% · B 61.9% · incumbent 41.3% — **no threshold reaches 90% on this set** |
| but | all 63 are recovered drone frames of building exteriors. **Indoor specificity has never been measured.** These are also *harder* than a plain wall — busy, textured, full of real edges. |
| ceiling | **85–95% on indoor surfaces**, but that is a projection, not a measurement, and the evidence above is a caution rather than support: thresholding alone tops out near 60% on hard backgrounds. Reaching 90% **requires domain-matched negatives in training plus the T2 gate**, not a threshold. |
| **TARGET** | **≥ 90%**, measured on ≥120 indoor clean images never trained on and never used to pick the threshold |
| if it misses | report the real number and the curve. `METRICS_MODELS.md` already carries the fallback: A leaves 19.0% of hard clean surfaces alone where the shipped model leaves 3.2%, significant at p = 0.0086. That is true, useful, and not 90%. |
| statistics | at n=120 and an observed 90%, the Wilson 95% interval is **[0.833, 0.942]** — 11 points wide, decisive. At the original n=14 it was [0.685, 0.987], 30 points wide, an anecdote — and three claims built on that anecdote have since been retracted. |

### M2 · Damage-gate accuracy (binary, image level)
**Definition:** balanced accuracy of the T2 classifier on held-out images: does this frame
contain any defect at all.

| | |
|---|---|
| today | does not exist |
| ceiling | **90–95% is routine for a binary task at this data scale — but see §6.2.** Our positives are outdoor drone frames and our negatives will be indoor phone photos, and a classifier can score 99% by learning "is this indoors", which is the one feature that is perfectly wrong at inference time. |
| **TARGET** | **≥ 90% balanced accuracy AND ≥ 90% specificity on indoor clean AND ≥ 90% specificity on the 154 recovered outdoor backgrounds** |
| why the third clause | it is the shortcut probe. If indoor specificity ≫ outdoor specificity, the gate learned the room. |

### M3 · Image-level precision at the operating point
**Definition:** of images the app flags as damaged, the % that genuinely contain a defect.
**This is the number a tenant experiences**, and it is not the same as instance mask precision.

| | |
|---|---|
| today | unmeasured — there has never been a negative set to measure it against |
| ceiling | **≥90% is reachable**, because precision is a threshold choice and we will have the full curve |
| **TARGET** | **≥ 90%, reported with the recall it costs, per class** |

### M4 · Mask mAP50, overall (held-out)
| | |
|---|---|
| today | A 0.2145 / B 0.2510 pooled |
| ceiling tonight | **0.30 – 0.38** with the expanded dataset and a fixed split |
| ceiling with unlimited in-domain labelling, same architecture | ~0.45 – 0.55 |
| ceiling of the metric itself on this task | **~0.75 – 0.80 even at COCO scale** — see §3 and §4 |
| **TARGET** | **≥ 0.30**, and ship the real number regardless |
| **90% is not attainable and will not be claimed.** | |

### M5 · Mask AP50, `crack`
| | |
|---|---|
| today | **0.2343 on the 686-image pool** (A). The circulating 0.0516 is 46 test instances; the 0.2565 is 594 validation instances. Use the pool. |
| ceiling | **0.25 – 0.32** |
| **TARGET** | **≥ 0.20 on a test split containing ≥150 crack instances.** The instance-count clause is the real target — the AP is secondary to being able to measure it at all. |

### M5b · Mask AP50, `damp_stain` — the class the pool says is actually worst
| | |
|---|---|
| today | **0.058** (A) / 0.123 (B) on the pool. This, not `crack`, is the weakest class. |
| why | it is the opposite problem to `crack`: a damp patch has **no crisp boundary to trace**, so two annotators disagree about where it ends and IoU 0.5 punishes that directly. It is also the smallest class at 832 instances after the rebuild. |
| **TARGET** | **≥ 0.12**, i.e. match what B already reaches. Do not target more; this class is bounded by label ambiguity, not by the model. |

### M6 · Instance precision at the operating point (mask, IoU 0.5)
| | |
|---|---|
| today (max-F1 point, 686-image pool, A) | crack 0.42 · peeling 0.23 · spalling 0.55 · damp_stain 0.29 |
| **TARGET** | **spalling ≥ 0.70; report the rest, do not target them** |
| honest statement | ≥90% *instance* precision at IoU 0.5 is not achievable for any class here at a useful recall, for the reason in §3.2. Chasing it would mean raising conf until the class effectively stops firing, which is worse than reporting it low. **M3 — image-level precision — is the one that can carry a 90, and it is the one a tenant actually experiences.** |

### M7 · Acoustic grouped-LOGO accuracy
| | |
|---|---|
| today | 0.80 — 12 of 15 taps, from 8 recordings. Wilson 95% **[0.548, 0.930]** against a 0.533 majority baseline. **The lower bound is chance.** |
| your target | ≥0.90 with CI lower bound ≥0.80 |
| **is it reachable?** | **Not with 32 spots.** See §5. |
| **TARGET** | **accuracy ≥ 0.85 with a spot-level CI lower bound > 0.60**, which is the gate already pre-committed in `HANDOFF.md` and which 24–32 spots can actually clear |

---

## 3 · Why mask mAP50 cannot be 0.90 — four reasons, each measured here

### 3.1 The mask is not drawn at 640. It is drawn at 160×160.

This is the mechanism, and it is architectural rather than a training failure.

YOLOv8-seg emits 32 prototype masks at `[1, 32, 160, 160]` — confirmed in
`handoff/models/vision/export_report.json`. Every instance mask is a linear combination of
those prototypes, cropped to the box and upsampled. **The finest structure the network can
represent is one cell of a 160×160 grid — a stride of 4 pixels at 640 input.**

Measured over all 7,749 polygon instances, width in *prototype cells*:

| class | p10 | p25 | median | **% of instances ≤ 4 prototype cells wide** |
|---|---:|---:|---:|---:|
| **crack** | **2.4** | **3.9** | 6.8 | **25.7%** |
| peeling | 3.1 | 5.6 | 11.4 | 15.4% |
| spalling | 5.0 | 7.9 | 14.6 | 6.6% |
| damp_stain | 5.4 | 8.5 | 16.5 | 5.8% |

**A quarter of our cracks are two to four grid cells across.** A one-cell error in a
four-cell-wide object is a 25–50% area error. There is no training schedule that fixes that;
it is the resolution of the output head.

At 800px input the prototype grid is 200×200 — same stride 4, but the crack now occupies 3.1
cells at p10 instead of 2.4, and the ≤4-cell share drops 25.7% → **18.0%**. That is the
mechanism the C experiment was built to test. **C's evidence for it did not replicate (§1),
so the mechanism stands as the best explanation and not as a demonstrated result.**

### 3.2 IoU ≥ 0.5 is a cliff for thin structures, and a formality for blobs

AP50 gives full credit above IoU 0.5 and zero below. For a long strip of width *w* with a
lateral boundary error *d* on each side, IoU = (w−d)/(w+d):

| width at 640 | d=1px | d=2px | d=3px | d=4px |
|---:|---:|---:|---:|---:|
| **6 px** | 0.71 | 0.50 | **0.33** | **0.20** |
| **8 px** | 0.78 | 0.60 | **0.45** | **0.33** |
| 10 px | 0.82 | 0.67 | 0.54 | **0.43** |
| 20 px | 0.90 | 0.82 | 0.74 | 0.67 |
| 200 px | 0.99 | 0.98 | 0.97 | 0.96 |

Bold = below 0.5 = **scored as a complete miss**, identical to not detecting it at all.

The same 3-pixel error that annihilates an 8-pixel crack costs a 200×200 spalling patch
**0.03 of IoU**. `crack` is not being scored more harshly because it is harder to see. It is
being scored more harshly because it is thin, and AP50 does not have a way to express
"found it, traced it 3 pixels off".

Our measured aspect ratios say the same thing: `crack` has a median major:minor of **5.2**,
against 2.0–2.3 for every other class.

**`damp_stain` fails the same threshold from the opposite direction.** It is the largest class
by median area and the *worst* on the pool at AP50 0.058. A damp patch has no crisp edge —
where the stain "ends" is a judgement, and two annotators will differ by far more than the
3 pixels that cost a crack its match. IoU 0.5 requires agreement on a boundary that does not
physically exist. **Between them, thin structures and diffuse structures account for the two
weakest classes, and both failures are properties of the metric as much as of the model.**

### 3.3 It is *not* an annotation-quality story — I checked

The obvious counter-hypothesis is that the ground truth is coarse: boxes drawn around
diagonal cracks, masks that were never going to be matchable. **Measured, and it is not
true.**

| class | median polygon vertices | % drawn as ≤5 vertices (i.e. a box) |
|---|---:|---:|
| crack | 26 | **0.7%** |
| peeling | 16 | 1.5% |
| spalling | 23 | 1.0% |
| damp_stain | 17 | 8.7% |

The masks are properly traced. That removes the comfortable explanation and leaves the
architectural one, which is why §3.1 is the answer I will actually give a judge.

### 3.4 Scale and domain, stated plainly

2,201 images. **[EXTERNAL]** COCO instance segmentation is 118,287 training images — we have
**1.9% of it**, across four classes whose boundaries are genuinely ambiguous, and **not one
training image is an Indian rental flat interior shot on a phone under a tubelight.** That
last clause is the one that matters most for the demo and the one §6 spends its budget on.

---

## 4 · Calibration — what 0.90 mask mAP50 would actually mean **[EXTERNAL]**

These are published reference points, not our measurements. They exist here so the ceiling in
§2 is a calibrated judgement rather than pessimism.

- **The same architecture on COCO.** YOLOv8n-seg reports mask mAP50-95 of **0.305** on COCO
  val — 118k images, 80 everyday classes, crowd-verified masks. Using the mAP50 : mAP50-95
  ratio measured on *our own* runs (1.66–1.77×), that implies mask mAP50 of roughly **0.50**.
  **Asking this architecture for 0.90 is asking for nearly double what it achieves on COCO,
  using 1.9% of the data.**
- **Unlimited compute and the best published models** reach mask AP50-95 around 0.54–0.56 on
  COCO, implying mask mAP50 in the **0.75–0.80** range. **0.90 mask mAP50 is above the
  published state of the art on the best-resourced benchmark in computer vision.** It is not
  a stretch goal; it is not a number that exists.
- **The "90%+ crack detection" figures in the literature are a different metric on a
  different task.** Crack benchmarks (CrackForest, DeepCrack and similar) report **pixel-level
  F1 or IoU for semantic segmentation** — every crack pixel pooled into one number, no
  instance matching, no IoU-0.5 threshold, on datasets that are exclusively high-contrast
  close-ups of cracks. Those numbers do reach 0.85–0.90. **They are not comparable to instance
  mask AP50 and anyone quoting them against us is comparing two different things.** This is
  §7's answer to "why isn't your mAP 90%", and it is a stronger answer than an apology.

---

## 5 · The acoustic target, costed honestly

You asked for **≥0.90 accuracy with a CI lower bound ≥0.80**. Here is what that costs.

Grouped LeaveOneGroupOut makes the **recording (the spot)** the independent unit —
`ml/acoustic/export_for_android.py:61` uses the filename as the fold. Taps within one spot are
near-duplicates, so the honest denominator for an interval is spots, not taps.

Smallest n reaching a Wilson 95% lower bound of 0.80:

| observed accuracy | independent units needed |
|---:|---:|
| 0.85 | **230** |
| 0.88 | 87 |
| **0.90** | **54** |
| 0.92 | 40 |
| 0.94 | 33 |

And what the protocol's current targets actually deliver:

| spots | LB at 0.90 observed | LB at 0.94 observed |
|---:|---:|---:|
| 8 *(today)* | 0.529 | 0.676 |
| 24 | 0.742 | 0.798 |
| **32** *(protocol target)* | **0.758** | 0.799 |
| 54 | ~0.801 | — |
| 64 | 0.810 | 0.850 |

**32 spots cannot clear a 0.80 lower bound even at a flawless-looking 90%.** It tops out at
**0.758**. To clear 0.80 you need roughly **54–64 spots**, which is 45–50 minutes of tapping
rather than 25 — and that assumes accuracy *rises* on harder, more varied surfaces, when the
honest expectation is that it falls from today's 80%.

**Recommendation, and it is a real choice, not a formality:**

- **If you have 45–50 minutes: record 56–64 spots** and the ≥0.80 lower bound becomes
  reachable. That is the only path to the target you asked for.
- **If you have 25 minutes: record 24–32 spots**, keep the pre-committed **LB > 0.60** gate,
  and the capability ships as supporting evidence rather than as the headline. 24 spots at
  80% observed already clears 0.60 comfortably.
- **Either way, report both intervals** — tap-level (the existing convention, optimistic
  because sibling taps are correlated) and spot-level (conservative). Gating on the
  conservative one is the defensible choice and it is a good thing to be asked about.

**The existing shipped interval is tap-level:** 12/15 taps → [0.548, 0.930], which reproduces
`groupedAccuracyCi95` in the bundle exactly. From **8** recordings. That over-states the
precision, and the fix is to report both, not to quietly switch.

---

## 6 · Where the effort goes, and the one trap I will not walk into

### 6.1 T1.2 — auto-labelling masks with SAM: **no.** Here is the arithmetic.

- Wiring SAM 2.1 + a proposal loop + an uncertainty-sorted review queue: **60–90 min of my
  time before you touch it**, plus a 180 MB–900 MB checkpoint download.
- Realistic human throughput verifying and correcting a polygon mask when the proposing model
  scores 0.25 mAP — most proposals need real correction, not a click: **60–90 images/hour**.
- Moving mask mAP50 measurably against run-to-run noise needs on the order of **+500–1000**
  in-domain images against a 1,515-image training set. At 75/hour that is **7–13 hours**.
- In the ~3 hours available before retraining has to start, you would label ~200 images —
  **+13% of the training set, all from one domain.** That is inside the noise.

**So: no. The time goes to §6.2 instead.**

### 6.2 What to shoot instead — and the trap that makes it necessary

**The trap, stated precisely, because it would have produced a 99%-accurate gate that rejects
every real capture:**

Our positives are 2,047 outdoor drone and structural images. Our negatives will be 360 indoor
phone photos. A binary classifier trained on those two piles can reach ~99% by learning
**"is this indoors?"** — a feature perfectly correlated with the label in training and
perfectly *anti*-correlated with it in the room, where every frame is indoors. A gate that
learned it would post an excellent held-out accuracy on the way to rejecting every real
capture.

**Two mitigations, both in the T2 plan:**

1. **The shortcut probe, pre-committed:** the gate must reach **≥90% specificity on the 154
   recovered *outdoor* backgrounds** as well as on the indoor held-out set. If indoor
   specificity ≫ outdoor specificity, it learned the room and does not ship. This is decidable
   with data already on disk.
2. **The data fix — 80–120 indoor *defect* photos, image-level labels only.** Folder name is
   the label; **zero annotation cost**; same walk, about 15 extra minutes. This is what makes
   the gate's positive class in-domain and lets the held-out test be indoor-against-indoor
   instead of indoor-against-drone.

**The same argument applies to the segmenter.** Adding 360 indoor negatives to a training set
whose positives are all outdoor teaches it, in part, that *indoor means nothing here* — which
is a recall risk on real captures. The indoor positives are the mitigation for both models,
and they are the single highest-value 15 minutes in this whole plan.

**Revised ask, replacing T1.2:**

| | shots | cost | what it buys |
|---|---:|---|---|
| `SHOOT_LIST.md` clean surfaces, unchanged | 360 | ~50 min | M1, M3, the threshold decision, the B-vs-A tiebreak |
| **NEW — indoor defects, image-level label only** | **80–120** | **~15 min** | makes M2 honest and de-risks the segmenter's recall |
| tap recordings, `RECORDING_PROTOCOL.md` | 24–32 spots (56–64 to hit LB 0.80) | 25–50 min | M7 |

Folder layout for the new set — no annotation, no boxes, the folder *is* the label:

```
indoor_defects/
├── crack/          any wall/floor/ceiling crack, close and mid range
├── peeling/        flaking or blistered paint
├── damp/           damp patch, water stain, mould
└── other_damage/   chipped plaster, broken tile, gouges  (used as positives only)
```

### 6.3 If the shoot does not happen

Then M1, M2 and M3 cannot be measured on the domain the app runs in, and **none of them may
be quoted**. The fallback position is already in `METRICS_MODELS.md` and it is not a bad one:
A ships, the threshold stays at 0.25, and the headline is the incumbent comparison —
**on 63 held-out clean images the model on the phone today leaves 3.2% of them alone; A leaves
19.0%, Fisher p = 0.0086.** That comparison is fair between old and new because detections on
an unlabelled background do not depend on labels or taxonomy. It is real, it is significant,
and it is not 90%.

### 6.4 Why the A-vs-B promotion gate had to be rewritten before T4

The original gate required a candidate to *beat* the control on clean-image false positives.
Re-measuring on 63 backgrounds instead of 14 showed the true gaps are 0–10 points, not the 28
the small sample appeared to show. Sample size to detect them at α 0.05, power 0.80:

| conf | A | B | backgrounds needed per model |
|---:|---:|---:|---:|
| 0.25 | 19.0% | 20.6% | 9,737 |
| 0.35 | 30.2% | 34.9% | 1,560 |
| 0.55 | 47.6% | 47.6% | no n suffices |
| 0.65 | 52.4% | 61.9% | 425 |

**360 photos do not close that gate, and neither would 3,600.** A superiority requirement on a
difference this small is unsatisfiable by construction, which makes it a bad gate rather than
a strict one — it would hold B back forever while looking rigorous.

G3 therefore tests **non-inferiority**: B must not be meaningfully *worse*. Combined with a
decisive accuracy margin on 1,867 instances, that is the question that actually matters — and
it is decidable with 120 backgrounds. **Committed here, before the retrained numbers exist.**

---

## 7 · The metric suite the product should be judged on

In this order, because it is the order a tenant cares about:

1. **"Does it leave a clean wall alone?"** → M1, clean-surface specificity, with its interval.
2. **"When it flags something, is it real?"** → M3, image-level precision, with the recall it
   costs.
3. **"What does it find?"** → per-class recall at the operating point, honestly per class:
   `spalling` works, `crack` is weak and we can say exactly why.
4. **"How precisely does it trace the outline?"** → M4/M5, mask mAP50, **reported low with
   §3.1's mechanism attached.** Not hidden. The explanation is the asset.
5. **"What can it tell that a camera cannot?"** → M7, the acoustic tap, with the conservative
   interval and the pre-committed gate — or silence, if the gate does not clear.

**The number to lead with is M1**, and the reason is not that it is the highest. It is the only
one a judge can falsify in ten seconds by pointing the phone at the wall behind them — and the
one where the answer to "how did you do against the model you started with" is already
measured and already significant: **3.2% → 19.0% on hard outdoor backgrounds, p = 0.0086**,
with the indoor number still to come.

`ACCURACY_ANSWER.md` (T6) is built on exactly this ordering, and every number in it will come
from this pipeline.

---

## 8 · Pre-committed gates for T2–T5

Fixed now, before any of the measurements exist.

| gate | condition | if it fails |
|---|---|---|
| **G1 · T2 gate ships** | ≥90% balanced accuracy **and** ≥90% specificity on indoor clean **and** ≥90% specificity on the 154 outdoor backgrounds | do not ship it. A second stage that is wrong 15% of the time is worse than no second stage. `INTEGRATION.md` says so and the `.ptl` is not delivered. |
| **G2 · shortcut probe** | indoor specificity − outdoor specificity **< 15 points** | do not ship it, and say it learned the domain. This overrides G1 even if G1 passes. |
| **G3 · T4 promotion** | **(a)** beats the control on **pooled** mask mAP50 by **≥ 0.02 absolute**, **and (b)** its clean-image specificity at the chosen operating point is **not worse than the control's by more than 5 points**, as a one-sided 95% non-inferiority bound on ≥120 indoor backgrounds | the control ships. **Superiority on specificity is deliberately not required — see §6.4; it is not testable at any sample size we can reach.** |
| **G4 · T3 operating point** | a threshold exists with specificity ≥90% **and** image-level precision ≥90% | report the full sweep, recommend the best available point, and state which of the two was traded and by how much. |
| **G5 · T5 acoustic** | spot-level CI lower bound > 0.60 → ship the refit. > 0.80 → it may lead the demo. ≤ 0.60 → ship nothing new. | the existing JSON stays untouched and the capability does not appear in the pitch. |
| **G6 · export** | bytecode ≤ 8, shapes match `CONTRACTS.md`, `max |eager − lite| < 1e-3` | `p3_export.py` refuses to write the file. Already enforced in code — this is the assertion that caught `optimize_for_mobile` corrupting box coordinates by 524. |

**And the standing rule, restated because it is the whole point of this document:** nothing
in the pitch may cite a number this pipeline did not produce, and any metric that misses its
target above ships with its real value and one sentence of explanation.
