# CONTRACTS.md — what the Android app requires

Everything here is read from app source and, where marked **[verified]**, confirmed by
actually loading the shipping artifact and measuring it. Any deviation is a **CONTRACT
BREAK** and needs a Kotlin change from the app owner — those are tracked in `INTEGRATION.md`.

Source of truth: `smartlease-edge-chennai-master/app/src/main/java/com/smartlease/edge/`
Verified against commit `84e4144`.

---

## 0. Export contract — **GATE PASSED**

| | |
|---|---|
| Android runtime | `org.pytorch:pytorch_android_lite:1.13.0` (`gradle/libs.versions.toml:14`) |
| **N (max bytecode version)** | **8** — read from the shipping `yolov8n_seg.ptl` **[verified]** |
| What torch 2.12.1 emits | **8** — so **no backport step is needed** **[verified]** |
| `_backport_for_mobile` | available if a future torch bumps the default; currently a no-op |

**Proxy verification.** `handoff/env/export` is a Python 3.10 venv on **torch 1.13.1+cpu** —
the same source tree the Android native library is built from. Both a toy traced-under-2.12
model *and the shipping model* load there and run a forward pass:

```
-- toy_backported.ptl --     bytecode 8  load OK  forward OK  [[1,4,256],[1,4,16,16]]  PASS
-- yolov8n_seg.ptl    --     bytecode 8  load OK  forward OK  [[1,40,8400],[1,32,160,160]]  PASS
OVERALL (torch 1.13.1 proxy): PASS
```

This is the check that matters. Matching the version *number* only proves the container is
readable; loading under 1.13.1 proves **operator coverage** — that nothing traced under torch
2.12 references an op the 1.13 runtime lacks or whose schema changed. That failure mode
appears at load or first forward, on the phone.

> ⚠️ **Two environment traps, both real, both cost me time.**
>
> 1. **A space anywhere in the path breaks torch's `PytorchStreamReader`** on Windows — even
>    an absolute path. It raises *"failed reading zip archive: failed finding central
>    directory … high likelihood that your checkpoint file is corrupted"*. The file is fine;
>    the workspace is `IQOO Hackathon`. **Pass an open file handle, or work in a space-free
>    directory.** Every script here does one or the other.
> 2. `torch.jit._load_for_lite_interpreter` **moved to `torch.jit.mobile`** in torch 2.x.
>
> Also noted: torch 2.12 prints *"Lite Interpreter is deprecated, consider ExecuTorch"* on
> save. It still works and still emits v8. Not a problem today; it is a reason this pin will
> not age well.

---

## 1. Vision — module signature

`ml/YOLOV8/convert_yolov8_seg_qnn_pte.py:23-44` defines the wrapper; the shipping `.ptl`
top-level class is `StaticYOLOv8SegWrapper` **[verified by TorchScript inspection]**.

```
forward(x: Tensor) -> Tuple[Tensor, Tensor]
   x      float32 [1, 3, 640, 640], RGB, normalised to [0, 1]
   preds  float32 [1, 40, 8400]          <- 4 box + 4 class + 32 mask coeff
   protos float32 [1, 32, 160, 160]
```

**Measured on the shipping artifact under torch 1.13.1: `[[1, 40, 8400], [1, 32, 160, 160]]`.**
It must be a **tuple** — `YoloSegDefectSegmenter.kt:66-73` calls `error()` if `!output.isTuple`,
and since commit `546386c` `create()` runs a probe forward at load time and returns `null`
(falling back to the labelled heuristic) unless it gets a ≥2-element tuple.

### Channel layout, from `YoloSegDecoder.kt:90-123`

`preds` is **channel-major**: value(c, a) is at `c * 8400 + a`.

| channels | meaning | decoder |
|---|---|---|
| 0-3 | cx, cy, w, h in **640-input pixels** | `:102-107` |
| 4-7 | class scores, **already sigmoid'd** in the graph | `:93-99` |
| 8-39 | 32 mask coefficients, **raw** (sigmoid applied after the prototype sum) | `:109-111`, `:178` |

`protos` is `value(k, y, x)` at `k * 160 * 160 + y * 160 + x` (`:176`).

---

## 2. Vision — `YoloSegConfig.kt` constants

| Constant | Value | Line | Changing it |
|---|---|---|---|
| `INPUT_SIZE` | `640` | `:14` | **CONTRACT BREAK** — Kotlin diff + latency cost |
| `ANCHORS` | `8400` | `:17` | derived from 640; changes with input size |
| `BOX_CHANNELS` | `4` | `:18` | fixed |
| **`NUM_CLASSES`** | **`4`** | `:19` | **CONTRACT BREAK** |
| `MASK_COEFFS` | `32` | `:20` | **CONTRACT BREAK** |
| `PRED_CHANNELS` | `40` | `:21` | `4 + nc + 32`, asserted at `YoloSegDecoder.kt:50-53` |
| `PROTO_SIZE` | `160` | `:24` | **CONTRACT BREAK** |
| `PROTO_STRIDE` | `4` | `:27` | `640 / 160` |
| `SCORE_THRESHOLD` | `0.25f` | `:47` | **safe to change** — one float, no shape impact |
| `NMS_IOU_THRESHOLD` | `0.45f` | `:50` | safe |
| `MASK_THRESHOLD` | `0.5f` | `:53` | safe |
| `MAX_DETECTIONS` | `32` | `:56` | safe |

**Class order is positional and index-aligned across three places** — `data.yaml`,
`CLASS_NAMES` (`:33`), `CLASS_DESCRIPTIONS` (`:39`):

| idx | `CLASS_NAMES` | `CLASS_DESCRIPTIONS` (shown to the tenant) |
|---|---|---|
| 0 | `crack` | "crack in wall surface" |
| 1 | `peeling` | "peeling paint" |
| 2 | `spalling` | "spalling (surface breaking away)" |
| 3 | `stain/mould` | "staining or mould growth" |

**Reordering classes without reordering both lists silently mislabels every finding.** The
decoder hard-fails on a wrong *count* (`require` at `:50-57`) but cannot detect a wrong
*order* — that ships as confidently wrong text in a tenant's report.

---

## 3. Vision — preprocessing, which the training pipeline must match

`Letterbox.kt:41-57` + `YoloSegDefectSegmenter.kt:79-107`:

1. `scale = min(640/w, 640/h)` — **aspect preserved**
2. `contentW = round(w*scale)`, `contentH = round(h*scale)`, each clamped to 640
3. `padX = (640-contentW)/2`, `padY = (640-contentH)/2` — **centred**
4. Canvas pre-filled with **`PAD_VALUE = 114f/255f`** (`:111`) — grey 114
5. Scaled image written at `(padX, padY)`; channels split R, G, B into CHW; each `/255f`
6. `Tensor.fromBlob(data, [1, 3, 640, 640])`

Mask coverage is divided by `contentAreaInProtoCells()` — content area, **not** the full
160×160 grid (`Letterbox.kt:34-38`) — so padding cannot inflate a defect's coverage.

> ⚠️ `YOLOV8/prepare_calibration.py:39` uses a plain `cv2.resize` to 640×640, which
> **distorts aspect** and does not letterbox. Any threshold or quantisation decision made
> under that preprocessing does not transfer to the phone. Fixed in P1.4.

---

## 4. Acoustic — the 36-feature vector

Generated by `ml/acoustic/android_features.py`, mirrored in Kotlin by
`AcousticFeatureExtractor.kt`. **Order is positional and load-bearing**
(`AcousticFeatureExtractor.kt:26-29`):

| index | feature |
|---|---|
| 0-12 | MFCC mean, 13 coefficients |
| 13-25 | MFCC standard deviation (population, ddof=0 — matches numpy default) |
| 26 | spectral centroid mean |
| 27 | spectral centroid std |
| 28 | rolloff 85% |
| 29 | bandwidth |
| 30 | flatness |
| 31 | zero-crossing rate |
| 32 | decay time, ms |
| 33 | low band ratio (< 500 Hz) |
| 34 | mid band ratio (500–2000 Hz) |
| 35 | high band ratio (> 2000 Hz) |

DSP constants — `android_features.py:33-40`, echoed into the JSON and read back by
`AcousticModelBundle.parse`:

```
SAMPLE_RATE 22050   N_FFT 512   HOP 128   N_MELS 40   N_MFCC 13
TOP_DB 80.0         AMIN 1e-10  N_BINS 257
```

Window is **periodic** Hann (`fftbins=True`), *not* the symmetric variant the heuristic path
uses (`AcousticFeatureExtractor.kt:34-37`). `top_db` clamping is applied over the **whole
spectrogram**, not per frame (`:69-77`).

**Changing the feature set, its order, or any DSP constant is a CONTRACT BREAK** — it
requires rewriting `AcousticFeatureExtractor.kt` and regenerating the golden vectors.

---

## 5. Acoustic — `acoustic_tap_model.json` schema

Written by `export_for_android.py:96-124`, parsed by `AcousticModelBundle.parse` (`:69-91`).
**Every key is required** — `getInt`/`getDouble`/`getJSONArray` throw on a missing key, and
`load()` catches that and returns `null`, which silently degrades the app to the heuristic.

| key | type | consumed by |
|---|---|---|
| `sampleRate` `nFft` `hop` `nMels` `nMfcc` | int | extractor geometry |
| `topDb` `amin` | double | log-mel clamping |
| `featureCount` | int | **must be 36**; `hollowProbability` has `require(features.size == featureCount)` |
| `classes` | string[2] | `["solid", "hollow"]`, positive class is index 1 |
| `positiveClass` | string | `"hollow"` |
| `melFilterbank` | float[40 × 257 = 10280] | row-major, shipped as data so Kotlin never rebuilds it |
| `dctMatrix` | float[13 × 40 = 520] | row-major DCT-II basis |
| `scalerMean` `scalerScale` | float[36] | StandardScaler |
| `coefficients` | float[36] | logistic regression weights |
| `intercept` | double | |
| `groupedAccuracy` | double | shown in the UI |
| `groupedAccuracyCi95` | [double, double] | shown in the UI |
| `trainedTaps` | int | shown in the UI |
| `golden` | object | `length`, `freq`, `decay`, `features`[36], `hollowProbability` |

Inference is `sigmoid(intercept + Σ coefficients[i] · (features[i] − mean[i]) / scale[i])`
(`AcousticModelBundle.kt:44-53`) — 36 multiply-adds, pure Kotlin, no runtime.

---

## 6. Acoustic — golden vectors and the parity test

`AcousticFeatureExtractorTest.kt` is what stops Python and Kotlin drifting apart. The golden
signal is **closed-form so both languages can regenerate it identically** — no waveform
fixture (`export_for_android.py:39-51`):

```python
t    = arange(5512)                         # GOLDEN_LENGTH, 0.25 s at 22050
env  = exp(-t / 900.0)                      # GOLDEN_DECAY
wave = sin(2π·1200·t/sr) + 0.5·sin(2π·2400·t/sr)   # GOLDEN_FREQ and its octave
signal = wave * env
```

The test regenerates that from `golden.length/freq/decay` in the JSON, runs the Kotlin
extractor, and asserts all 36 features and the final probability against `golden.features`
and `golden.hollowProbability`.

**Three invariants the test also enforces** — a refit must not break them:

- `groupedAccuracy < 0.99` — guards against exporting the training-set score by mistake
- `groupedAccuracyCi.first < groupedAccuracy < groupedAccuracyCi.second`
- a clip shorter than one FFT frame returns `featureCount` zeros rather than throwing

Since `golden` is regenerated by the same script that fits the model, **the parity test keeps
passing across a refit automatically** — as long as the feature extractor and DSP constants
are untouched. If they change, the new golden vectors are part of the handoff and the app
owner must be told explicitly.

---

## 7. Summary — what a refit may and may not change

| Change | Verdict |
|---|---|
| Vision weights, same nc / input size / output shapes | ✅ **file swap, zero Kotlin** |
| `SCORE_THRESHOLD`, NMS IoU, mask threshold, max detections | ✅ one-line Kotlin constant |
| Acoustic weights, scaler, accuracy, CI, trainedTaps, golden | ✅ **file swap, zero Kotlin** |
| `NUM_CLASSES` ≠ 4, or class reorder | ❌ **CONTRACT BREAK** — `YoloSegConfig.kt` diff |
| `INPUT_SIZE` ≠ 640 | ❌ **CONTRACT BREAK** — plus a latency cost to accept |
| Proto size, mask coeff count, non-tuple output | ❌ **CONTRACT BREAK** |
| Acoustic feature set / order / DSP constants | ❌ **CONTRACT BREAK** — Kotlin rewrite |
| A model that is not a lite-interpreter file at bytecode ≤ 8 | ❌ **will not load at all** |
