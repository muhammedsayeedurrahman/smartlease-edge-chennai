# CORRECTIONS_FOR_APP_REPO.md

**For whoever is answering questions about the vision model tomorrow.**
Written 2026-09-12. Everything here comes from `handoff/` and its scripts.

Six figures in `METRICS.md` and `JUDGE_QA.md` have been retracted by the model work. If you
answer from the file you have, you will quote a number our own pipeline has withdrawn — and
the per-class table is the one a technical judge is most likely to press on.

---

## 0 · Read this first

**You do not have to relearn the deck.** Of the 25 questions in `JUDGE_QA.md`, **19 are
completely unaffected** (§6 lists them). Everything below touches five: **Q8, Q9, Q10, Q11**,
and one line in **Q25**.

**There are two states, and which one you are in changes the answers.**

| | |
|---|---|
| **STATE A — you did NOT swap the model** | The `.ptl` on the phone is the original. Q10 and Q11 stay exactly as written; they are still true. You only fix the **numbers** in Q8 and Q9. |
| **STATE B — you swapped in `handoff/models/vision/yolov8n_seg.ptl`** | Two string literals change in `YoloSegConfig.kt`, and Q9/Q10/Q11 change with them. `INTEGRATION.md` is the swap instructions. |

**If you are unsure which state you are in, you are in STATE A.** Answer from STATE A. It is
the honest and defensible position and it needs no code change.

### What is FINAL and what is PROVISIONAL

Say the final ones freely. Say the provisional ones **only** with the words "as of tonight" —
a retrain is running against a rebuilt split and those figures will move.

| status | figure |
|---|---|
| ✅ **FINAL** | The old per-class table (0.287 / 0.589 / 0.249 / 0.065) is **withdrawn**. Do not quote it in any state. |
| ✅ **FINAL** | The shipped model leaves **3.2%** of 63 held-out clean images undetected at conf 0.25 (2 of 63). |
| ✅ **FINAL** | The retrained control leaves **19.0%** alone. Fisher exact **p = 0.0086**. |
| ✅ **FINAL** | **25.7%** of crack instances are ≤4 cells wide on the 160×160 prototype grid. |
| ✅ **FINAL** | **81%** of the rebuilt test set was in the shipped model's training data — so the shipped model cannot be scored on it. |
| ✅ **FINAL** | `nc` stays **4**. The decoder, input size, and all shapes are unchanged. |
| ⚠️ **PROVISIONAL** | mask mAP50 **0.2145** (control) / **0.2510** (challenger) and all per-class AP. Measured on the v2 split; a v3 retrain supersedes them. |
| ⚠️ **PROVISIONAL** | Which model ships. Today: the control. The challenger leads on every cut and is expected to be promoted. |
| ⚠️ **PROVISIONAL** | `SCORE_THRESHOLD` 0.25. |
| ⚠️ **PROVISIONAL** | Everything acoustic. Captures are being recorded tonight. |

**Rule of thumb: quote a provisional number once, with its caveat, and never as a headline.**

---

## 1 · The retractions, one by one

### R1 — the per-class mAP table
**Where it lives:** `METRICS.md` lines 56–61 and the "What these numbers actually say" list
(items 1–4); `JUDGE_QA.md` Q8 lines 92–104.

**Old wording:** *"mask mAP50 0.287 … spalling 0.589, crack 0.249, peeling 0.244,
stain_mould 0.065."*

**Why it is wrong — two independent reasons, either one is sufficient.**
1. **The split leaked.** `DJI_0017` had 50 frames in train, 5 in valid and 3 in test.
   `DJI_0012`, `DJI_0015` and `VID_20241228_130232250` were split the same way. Near-duplicate
   video frames on both sides of the boundary inflate every figure in the table.
2. **`stain_mould` no longer exists** as a class. It was corrosion + efflorescence + damp +
   moss merged together — three unrelated physical phenomena — which is why it scored 0.065.

**Replacement:** measured on the pooled held-out groups, 686 images, 1,867 instances, zero
group leakage (`handoff/runs/t0_1_pooled.json`):

| | mask mAP50 | crack | peeling | spalling | damp_stain |
|---|---:|---:|---:|---:|---:|
| control (ships today) | **0.2145** | 0.2343 | 0.1163 | 0.4493 | 0.0580 |
| challenger | 0.2510 | 0.2527 | 0.1297 | 0.4989 | 0.1225 |

> **Never put 0.287 and 0.2145 side by side.** They are different test sets and the
> comparison flatters us in the wrong direction. If asked why the number went down, the
> answer is "it didn't — we fixed the measurement", and §5 is how you say that.

### R2 — "crack is broken at ~0.05"
**Where:** this is a figure from our model work that may have reached you verbally. It is not
in your files, but retract it if you have heard it.
**Why wrong:** 0.0516 was measured on **46** crack instances in a 268-image test split. The
same weights score **0.2343** on the 640 crack instances in the pool. It was a small-sample
artefact.
**Replacement:** *"crack sits around 0.23, it is our second-weakest class, and we know the
mechanism."* → §5.

### R3 — "higher resolution is the crack lever, a 3.3× improvement"
**Where:** verbal only.
**Why wrong:** the 800-pixel candidate beat the control on crack on 46 test instances and
**loses** on 640 pooled instances (0.2164 vs 0.2343). It is also last overall. The finding did
not replicate.
**Replacement:** resolution remains the best *explanation* for why crack is hard — the
prototype-grid measurement in §5 is independent of that experiment — **but it is a hypothesis,
not a demonstrated win.** Do not claim a measured improvement.

### R4 — "the shipped model flags 100% of clean images"
**Where:** verbal, and in earlier drafts of `handoff/INTEGRATION.md`.
**Why wrong:** 100% was 14 of 14. On the full 63 held-out backgrounds it is **96.8%** (61 of
63).
**Replacement:** *"it leaves 3.2% of clean images alone, and the retrained one leaves 19%."*
Still the strongest honest claim we have, and now it is significant: **p = 0.0086**.

### R5 — "the shipped model's false positives are dominated by `stain_mould` — 16 of 25"
**Where:** verbal, and it reads as evidence for the taxonomy fix.
**Why wrong:** on 63 backgrounds the shipped model produces **312** detections, of which
**`spalling` is 213** and `stain_mould` is 68. **`spalling` dominates.**
**Replacement:** the taxonomy argument still stands on its own — a class built from three
different phenomena scored 0.065 — **but the false-positive breakdown was not evidence for
it, and if you offer it as evidence and someone checks, it does not hold.**

### R6 — "60–70 background photos will settle which model is better"
**Where:** verbal.
**Why wrong:** that estimate came from a 29%-vs-57% gap that was itself noise at n=14. The
real gap between the two retrained models is 0–10 points, which needs **425–14,776**
backgrounds per model to separate.
**Replacement:** the promotion decision is now a **non-inferiority** test — the challenger
must not be *worse* — plus a clear accuracy margin. If asked, that is a good answer: *"we
rewrote the gate when we measured that the old one was unsatisfiable at any sample size we
could reach."*

---

## 2 · Drop-in replacement — `JUDGE_QA.md` Q8

Replace lines 89–107 with this block, verbatim.

---

````markdown
### Q8. `[CHANGED — the earlier table is SUPERSEDED, see the box]` "What's the mAP of the vision model?"

> **SUPERSEDED — DO NOT USE**
> ~~mask mAP50 0.287 · spalling 0.589 · crack 0.249 · peeling 0.244 · stain_mould 0.065~~
> ~~"Valid split agrees closely (0.267), which is what contamination looks like."~~
> Those figures came from a split in which `DJI_0017` had 50 frames in train, 5 in valid and
> 3 in test, and from a `stain_mould` class that merged corrosion, efflorescence, damp and
> moss. Both defects are fixed. The numbers below replace them and are not comparable to them.

**You have a number, and a better answer than the number.** Pooled held-out groups — 686
images, 1,867 instances, no group appears in more than one split:

| | mask mAP50 | crack | peeling | spalling | damp_stain |
|---|---:|---:|---:|---:|---:|
| **control (shipping)** | **0.2145** | 0.2343 | 0.1163 | 0.4493 | 0.0580 |
| challenger | 0.2510 | 0.2527 | 0.1297 | 0.4989 | 0.1225 |

**Say this:**
> "Mask mAP50 is 0.21 on a clean grouped split, and I'll tell you why that's the wrong
> question for this product before I tell you why it's low. The mask is drawn on a 160×160
> prototype grid — a quarter of our cracks are four grid cells wide or fewer, so a one-cell
> error is a 25 to 50 percent area error, and IoU-0.5 scores that as a total miss. The same
> three-pixel error on a spalling patch costs three points of IoU. For reference, this
> architecture reaches about 0.50 on COCO with a hundred and eighteen thousand images; 0.90
> mask mAP50 is above published state of the art on the best-resourced benchmark in vision,
> so nobody should be quoting it. The number I'd judge us on is what happens when you point
> the phone at a clean wall: the model we started with leaves 3.2 percent of clean images
> alone, the retrained one leaves 19 percent, and that's significant at p equals 0.0086."

**If they push on "0.21 is low":**
> "It is. Three reasons and we've measured all three: 2,201 images against COCO's 118,000;
> not one training image is an Indian flat interior shot on a phone; and the two weakest
> classes fail at opposite ends of the same metric — cracks are too thin for IoU-0.5 to
> forgive a boundary error, damp stains have no boundary two annotators would agree on."

**If they ask about the crack literature's 90%:**
> "Those are pixel-level F1 on semantic segmentation, on datasets that are exclusively
> high-contrast close-ups of cracks. Different metric, different task. It isn't comparable
> to instance mask AP at IoU 0.5 and I wouldn't claim it."
````

---

## 3 · Drop-in replacement — `METRICS.md`

Two edits. Do not delete the old table — strike it, so the correction is visible.

**Edit 1 — replace the table at lines 56–61 with:**

````markdown
> **SUPERSEDED — DO NOT USE.** The table that was here reported mask mAP50 0.287 with
> spalling 0.589, crack 0.249, peeling 0.244 and stain_mould 0.065, on a 107-image test
> split. That split leaked — `DJI_0017` had 50 frames in train, 5 in valid, 3 in test — and
> the `stain_mould` class merged three unrelated phenomena. Replaced 2026-09-12.

## Test — pooled held-out groups, 686 images, 1,867 instances

Grouped split, stratified by source dataset; 226 of 226 groups sit in exactly one split.
Source: `handoff/runs/t0_1_pooled.json`.

| | mask mAP50 | mask mAP50-95 | crack | peeling | spalling | damp_stain |
|---|---:|---:|---:|---:|---:|---:|
| **control** *(the `.ptl` in `handoff/`)* | **0.2145** | 0.1071 | 0.2343 | 0.1163 | 0.4493 | 0.0580 |
| challenger | 0.2510 | 0.1221 | 0.2527 | 0.1297 | 0.4989 | 0.1225 |

**These numbers are provisional.** A retrain on a class-stratified rebuild is in progress;
the split it uses shares 20% of its held-out images with this one's training data, so these
models are deliberately *not* scored on it. See `handoff/DATASET.md`.

## Clean-surface specificity — 63 held-out background images

% of clean images producing **zero** detections. One box anywhere fails the image.

| conf | model on the phone | control |
|---:|---:|---:|
| **0.25** *(shipped)* | **3.2%** | **19.0%** |
| 0.45 | 22.2% | 39.7% |
| 0.65 | 41.3% | 52.4% |

**Fisher exact p = 0.0086 at the shipped threshold.** This is the only comparison that is
fair between the old model and the new one — it counts detections on images that should have
none, so it does not depend on labels or on the taxonomy change.

**No threshold reaches 90%,** and all 63 backgrounds are drone frames of building exteriors,
not indoor walls. Indoor specificity has never been measured.
````

**Edit 2 — replace items 1–4 of "What these numbers actually say" with:**

````markdown
1. **Mask mAP50 is 0.21 on a clean split, and that is the wrong headline.** The mechanism is
   in `handoff/TARGETS.md §3`: 25.7% of crack instances are ≤4 cells wide on the 160×160
   prototype grid, and IoU 0.5 is a cliff for thin structures rather than a slope.
2. **`damp_stain` is the weakest class at 0.058**, not `crack`. A damp patch has no crisp
   edge, so the metric demands agreement on a boundary that does not physically exist.
3. **`spalling` still carries the model** (0.4493) — and it also dominates false positives:
   213 of the shipped model's 312 detections on clean images. The class that works best is
   the one most likely to embarrass us on a clean wall.
4. **`crack` is 0.2343, not 0.05 and not 0.249.** The 0.05 figure that circulated was
   measured on 46 instances; the 0.249 came from the leaky split.
````

---

## 4 · What changes **only** if you swap the model (STATE B)

Do not make these changes unless `app/src/main/assets/yolov8n_seg.ptl` has actually been
replaced. `handoff/INTEGRATION.md` is the instruction sheet.

**The code change is two string literals** in `YoloSegConfig.kt` — `CLASS_NAMES[3]` and
`CLASS_DESCRIPTIONS[3]` become `"damp stain"` / `"damp staining"`. `nc` stays 4, the decoder,
input size and every shape are unchanged.

**Q9 — "How many images?"** 2,106 → **2,201** images in **226** capture groups, of which
**154 are backgrounds**. Still the same four CC BY 4.0 Roboflow datasets. Class 3 is
`damp_stain`, not `stain_mould`.

**Q10 — "Point it at that wall."** ⚠️ **The current answer says "the detector has never seen
a clean wall." That stops being true the moment you swap.** After the swap:

> "It's better than it was and it's still not good. We recovered 154 background images the
> original merge had silently deleted — it only copied an image if its labels mapped, so every
> clean frame was thrown away. With those in training, the model leaves 19% of clean test
> images completely alone; the one we started with leaves 3.2%. That's a real, significant
> improvement and it is nowhere near good enough, which is why we're shooting 360 indoor
> negatives tonight."

**Q11 — "What will it trip on?"** Two items in the list change:
- `exposed brick → spalling (guaranteed by the class mapping)` — **no longer guaranteed.**
  The `exposed brickwork → spalling` mapping was removed; 323 instances were dropped.
- `rusted window grilles → stain_mould (corrosion was folded into that class)` — **corrosion
  is no longer in the class.** Rust is now more likely to be **missed** than mislabelled.
  That is a different failure and an honest one to state.
- **Add:** *"`spalling` produces most of our false positives — 161 of 239 on clean images."*

**Q8's closing line** — *"ultralytics prints `0 backgrounds` when it scans our test set"* — is
no longer true of the rebuilt dataset. Delete it in STATE B; keep it in STATE A.

---

## 5 · The mAP mechanism, in four sentences

Learn this. It is the difference between "our number is low" and "we understand our number",
and it is worth more than the number itself.

> **"The mask isn't drawn at 640 — it's drawn on a 160-by-160 prototype grid and upsampled.
> We measured every instance in the dataset: a quarter of our cracks are four grid cells wide
> or fewer, so a one-cell boundary error is a 25 to 50 percent area error, and IoU 0.5 scores
> that as a complete miss — while the same error on a spalling patch costs three points.
> For scale, this architecture gets about 0.50 mask mAP50 on COCO with 118,000 images, and
> 0.90 would be above published state of the art on the best-resourced benchmark in computer
> vision. The 90-percent figures you see in the crack literature are pixel-level F1 on
> semantic segmentation — a different metric on a different task."**

**Three follow-ups you will get:**

| they ask | you say |
|---|---|
| *"So fix it — train at higher resolution."* | "We tried 800 pixels. It moved crack on one split and reversed on a larger one, and it made clean-wall false positives worse, because at higher resolution a grout line resolves into a crack. It also breaks our input-size contract and costs about 56% more latency on a phone CPU with no delegate. It's a costed option, not a free win." |
| *"Then your model doesn't work."* | "For tracing outlines precisely, no. For the question a tenant is actually asking — is there damage on this wall, and is the app going to invent some — the numbers are specificity and image-level precision, and those are the ones we're optimising. I'd rather show you the whole threshold curve than one number." |
| *"Why not just use a bigger model?"* | "It runs PyTorch Lite on the phone CPU with no NNAPI, no GPU and no HTP delegate. The small scale is 3.75× the FLOPs, which puts a single capture into seconds. That's a UX failure, not a metric trade." |

---

## 6 · Unaffected — do not re-learn these

**19 of the 25 answers in `JUDGE_QA.md` are untouched by any of this.** Nothing in the model
work changed the app, the PDF, the hashing, the permissions, or the IR path.

| | |
|---|---|
| **Q1, Q2, Q4** | NPU / `.pte` / what the iQOO gives you — unchanged. |
| **Q3** | Latency. Unchanged; the self-test screen still takes the measurement in front of them. |
| **Q5, Q7** | Sensing Hub, where Z comes from — unchanged. |
| **Q6** | The square-footage question — unchanged. |
| **Q12** | Confidence and dismissal — unchanged. |
| **Q13, Q14, Q15** | Tamper-evidence, signing, sharing the PDF — unchanged, and Q13 is still one of your best. |
| **Q16, Q17, Q18** | LLM, offline, location — unchanged. Q17 is still a clean yes. |
| **Q19–Q23** | Baseline comparison, transport, wear and tear, fixtures, IR — unchanged. |
| **Q24** | What's tested — unchanged. |
| **Q25** | One line only: if "retrain the vision model" was your answer to *what would you fix first*, it has been done and the honest answer is now **"shoot domain-matched data — every training image is an outdoor drone frame or a highway crack, and not one is a flat interior."** |

---

## 7 · If someone asks you a number that is not in this document

Say you don't have it to hand and you will not guess. Every figure in this package came out
of a script, and a made-up one is the only thing that would cost more than a low one.
