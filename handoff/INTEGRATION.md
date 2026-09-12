# INTEGRATION.md

## Swap one file and change two string literals. Nothing else.

```
handoff/models/vision/yolov8n_seg.ptl
   ->  app/src/main/assets/yolov8n_seg.ptl        (overwrite)
```

Then two display strings in `YoloSegConfig.kt`. **No shape change, no decoder change, no
threshold change, no test change, no Gradle change.**

Verified, not assumed:

| check | result |
|---|---|
| bytecode version | **8** — same as the artifact you are replacing; `pytorch_android_lite:1.13.0` loads it |
| loads under torch 1.13.1 | **PASS** — same source tree as the Android native lib |
| output signature | `Tuple[Tensor, Tensor]`, `is_tuple = True` |
| preds shape | `[1, 40, 8400]` — 4 box + 4 class + 32 mask coeff |
| protos shape | `[1, 32, 160, 160]` |
| exported vs trained model | `max |diff| = 0.0` — bit-exact |
| size | 13.74 MB (was 13.77 MB) |

---

## The one Kotlin change

`app/src/main/java/com/smartlease/edge/vision/YoloSegConfig.kt`

```diff
     val CLASS_NAMES = listOf("crack", "peeling", "spalling", "stain/mould")
+    val CLASS_NAMES = listOf("crack", "peeling", "spalling", "damp stain")

     val CLASS_DESCRIPTIONS = listOf(
         "crack in wall surface",
         "peeling paint",
         "spalling (surface breaking away)",
-        "staining or mould growth"
+        "damp staining"
     )
```

**Why it is not optional.** Class 3 no longer means what the old string says. It used to be
`stain_mould` = damp + moss + blackening + **rust** + **efflorescence** merged together — three
different physical phenomena in one label, which is why it scored AP50 **0.065**. It is now
`damp_stain`: damp, moss and moisture blackening only, 832 instances, scoring **0.058** on A
and **0.123** on B. Corrosion and efflorescence were dropped, so a report that says "staining
or mould growth" would now describe a class that contains neither rust nor efflorescence.

It is still our weakest class, and the reason is worth knowing rather than hiding: a damp
patch has no crisp edge, so "where the stain ends" is a judgement call, and the mask metric
requires two annotators to agree on a boundary that does not physically exist.

`NUM_CLASSES` stays **4**. Index order is unchanged. The decoder is untouched.

> If you would rather ship with **zero** Kotlin changes, keep the old strings — the app will
> build and run correctly, it will just mislabel class 3 to the tenant. I would not, but it is
> your call and it is a one-line revert either way.

### What does NOT change

```
INPUT_SIZE        640     unchanged
NUM_CLASSES       4       unchanged
MASK_COEFFS       32      unchanged
PRED_CHANNELS     40      unchanged
PROTO_SIZE        160     unchanged
SCORE_THRESHOLD   0.25f   unchanged - deliberately, see below
```

`YoloSegDecoder.kt`, `YoloSegDefectSegmenter.kt`, `Letterbox.kt`, `YoloSegDecoderTest.kt`:
**no changes.** The load-time tuple probe you added in `546386c` passes.

---

## Why `SCORE_THRESHOLD` stays at 0.25

Not because 0.25 is good. At 0.25 the new model still puts a box on **81%** of clean surfaces.

Because the threshold buys specificity by spending recall, and the specificity being bought is
measured on drone photographs of building exteriors — **not on the surfaces your app will
actually see.** Until there are indoor negatives, raising it is a guess dressed as a decision.

**What the swap buys you today, measured on 63 held-out clean images:**

| conf | model on the phone now | this model |
|---:|---:|---:|
| **0.25** *(shipped)* | leaves **3.2%** of clean images alone | leaves **19.0%** |
| 0.45 | 22.2% | 39.7% |
| 0.65 | 41.3% | 52.4% |

**Fisher exact p = 0.0086 at the shipped threshold.** This is the one comparison that is fair
between the old model and the new one — it counts detections on images that should have none,
so it does not depend on labels or on the taxonomy change.

When the clean-surface photos land (`SHOOT_LIST.md`), the threshold becomes a decision and I
will send you one number.

---

## What you are getting, honestly

**A candidate was NOT promoted.** The rebuild trained three. All figures below are on the
**pooled 686-image held-out set** — every held-out group, 1,867 instances:

| | mask mAP50 | crack | damp_stain | specificity @0.25 | verdict |
|---|---:|---:|---:|---:|---|
| **A** yolov8n @640 | 0.2145 | 0.2343 | 0.0580 | 19.0% | **ships** |
| B yolo11n @640 | **0.2510** | **0.2527** | **0.1225** | 20.6% | gate open — see below |
| C yolov8n @800 | 0.2087 | 0.2164 | 0.0482 | 17.5% | failed — last on every measure, breaks input-size contract |

The gate was fixed before any number was seen: *promote only if it beats A on both accuracy
and clean-image false positives*. **B beats A on every split and every class**, and the
specificity half is **not decidable at any sample size we can reach** — the real gap is 0–10
points, which needs 425–14,776 backgrounds per model to separate. Promoting on half a gate is
what the rule exists to prevent, so A ships now; the gate has been rewritten as a
non-inferiority test (`TARGETS.md §8 G3`) and B is re-evaluated against it in T4.

**Expect a second drop.** B is very likely the eventual winner. When it comes it will be the
same one-file swap — B was contract-checked *before* training and emits the identical
`(1, 40, 8400)` + `(1, 32, 160, 160)`.

> **Numbers in this file were revised on 2026-09-12.** The earlier per-class figures came from
> a 268-image test split that holds only **46 crack instances** and **14** backgrounds. Five
> claims built on that sample were wrong and have been retracted in `METRICS_MODELS.md`, each
> shown next to the number that replaced it. **Nothing you need to change on your side moved:
> same file, same two string literals, same contract.**

### Do not compare the new mAP to 0.287

`METRICS.md` reports mask mAP50 **0.287** for the model you are running. That number is from a
**leaky split** — `DJI_0017` had 50 frames in train, 5 in valid and 3 in test — and a different
taxonomy. The new 0.2488 is measured on genuinely unseen groups. **The two are not comparable
and putting them side by side would be misleading in the direction that flatters us.**

For the same reason I did not quote the old model's score on the new test set: I measured it
(0.6585) and then content-hashed the images — **81% of the new test set was in its training
data**. That number is memorisation and it is discarded.

---

## Acoustic model — nothing to integrate yet

`acoustic_tap_model.json` is **unchanged**. The refit needs recordings that do not exist yet:
the current model is fitted on 15 taps from 8 WhatsApp voice notes, grouped-LOGO accuracy 0.80
with **95% CI [0.548, 0.930]** against a 0.533 majority baseline — the interval's lower bound
is chance.

`RECORDING_PROTOCOL.md` (workspace root) specifies the capture. When those land the refit
regenerates `acoustic_tap_model.json` **and** the golden vectors inside it, so
`AcousticFeatureExtractorTest.kt` keeps passing automatically — the feature extractor and DSP
constants are untouched, and the golden block is emitted by the same script that fits the
model.

**If that refit does not clear its gate (CI lower bound above 0.60), I ship nothing and the
existing JSON stays.** More architecture does not fix a sample-size problem.

---

## Verify it yourself

```bash
# bytecode version and load, under the same torch the Android runtime is built from
handoff/env/export/Scripts/python.exe \
  handoff/scripts/p0_1_verify_on_1131.py handoff/models/vision/yolov8n_seg.ptl
# expect: bytecode_version 8, load_ok True, out_shapes [[1,40,8400],[1,32,160,160]], PASS
```

After the swap, the app's own guard does the rest: `YoloSegDefectSegmenter.create()` runs a
probe forward at load and falls back to the labelled heuristic segmenter if the model does not
return a `(preds, protos)` tuple. If the swap were wrong, you would see "heuristic" in the
findings labels rather than silent wrong answers.

---

## One trap, if you ever re-export

**Do not call `optimize_for_mobile`.** I did, on the first pass, and it silently corrupted the
output: traced-vs-optimized `max |diff| = 524` on box coordinates in 640-pixel space, while
trace-vs-eager and optimized-vs-saved were both bit-exact `0.000000`. The shapes stayed right
and the file loaded fine — it would have shipped wrong detections with nothing visibly broken.
`ml/YOLOV8/export_torchscript_lite.py:66-69` already warned about extra optimisation passes on
torch 2.11. `p3_export.py` now asserts `max |eager - lite| < 1e-3` and refuses to write the
file otherwise.

**Also:** torch's `PytorchStreamReader` fails on any path containing a space — including an
absolute one — with *"failed reading zip archive: failed finding central directory … high
likelihood that your checkpoint file is corrupted"*. The file is fine; the workspace is
`IQOO Hackathon`. Pass an open file handle, or work in a space-free directory.
