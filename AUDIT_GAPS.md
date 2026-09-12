# AUDIT_GAPS.md — Findings

**Severity:** P0 = kills the demo or the credibility in the room · P1 = loses points · P2 = polish.
Sorted by severity, then by estimated hours ascending. Hours assume one developer who knows this codebase.

---

# P0 — kills the demo

---

### P0-1 · Your privacy headline is contradicted by one attribute
**0.1 h**

**What is wrong** — `app/src/main/AndroidManifest.xml:23` sets `android:allowBackup="true"`. `app/src/main/res/xml/backup_rules.xml:4` and `data_extraction_rules.xml:4` exclude only `smartlease.db`. The generated PDF (`report/ReportGenerator.kt:120`) and the 13.7 MB copied model (`vision/YoloSegDefectSegmenter.kt:137-141`) both live in `filesDir` and are therefore inside Google Auto Backup's scope. The PDF prints *"Generated fully offline, on-device — no data left this phone."* (`ReportGenerator.kt:94`) on a document Android will upload to Google Drive.

**The question a judge asks** — "You have no INTERNET permission. Does that actually mean the data stays on the device?"

**The honest answer if unfixed** — "No. Auto Backup is on, and only the database is excluded. The report PDF goes to the user's Google Drive. The sentence printed inside the PDF is wrong."

**Minimal fix** — `android:allowBackup="false"` and `android:dataExtractionRules` with an empty `<cloud-backup>`. One line. Then the claim on slide 8/15/16 becomes true and you can say so without hedging.

---

### P0-2 · The home screen tells the judge the product is a placeholder
**0.25 h**

**What is wrong** — `ui/screens/HomeScreen.kt:43` renders, in shipping UI, on the first screen:
> `🔧 Placeholder: vision segmenter (heuristic mode until Days 6-8 model export), GenieX narrative synthesis (rule-based until wired in)`

It is also **stale**: `vision/DefectSegmenterFactory.kt:18-23` now loads the real `yolov8n_seg.ptl`, so the app is actively understating itself while leaking internal sprint language ("Days 6-8") into the judging room.

**The question a judge asks** — "Your own app says the vision model is a placeholder. Which is it?"

**The honest answer if unfixed** — "That text is out of date — the trained model does load. But we shipped the stale label, so you were right to ask."

**Minimal fix** — replace the card with a live status read from `DefectSegmenter.isTrainedModel` and the loaded bundle's `trainedTaps`, and delete every "Day N" string from `app/`. Say what is real *today* and say the narrative is rule-based, without calling it a placeholder for a thing that isn't coming.

---

### P0-3 · The deck still has `[Team name]` and `[Member 1]` in it
**0.5 h**

**What is wrong** — `docs/pitch/SmartLease-Edge-iQOO-Hackathon-2026.pptx` slide 1: `TEAM [Team name] · [Member 1] · [Member 2] · [Member 3]` and `[Student / Professional bucket]`. Slide 14 repeats `[Member 1]/[Member 2]/[Member 3]`. Slide 15: `[email]`. Slides 1 and 4 caption the product shots *"Target UI · app build in progress (Days 6–10)"* — i.e. the screenshots are mockups, labelled as such, on judging day.

**The question a judge asks** — nothing. They just stop reading carefully.

**The honest answer if unfixed** — there isn't one. This is the cheapest credibility you will ever buy back.

**Minimal fix** — fill the names. Replace the two mockup captions with real screenshots from the running app (you have a working app; take four screenshots).

---

### P0-4 · Slide 12 ticks two things that do not exist
**0.5 h**

**What is wrong** — slide 12, titled **"WORKING MVP, HONESTLY"**, lists under "Shipping before the battle (Days 3–10)" with ✓ marks: `✓ GenieX Llama 3.2 3B narrative` and `✓ SHA-256 fingerprint + dual signature`. Neither exists. `report/ReportGenerator.kt:61-75` is string templating; grep for `MessageDigest|Signature|sha256` across `app/src/` returns zero hits. Today is the battle.

**The question a judge asks** — "Show me the SHA-256 on the report."

**The honest answer if unfixed** — "It isn't implemented. The slide is wrong." Said on a slide with the word *Honestly* in the title, this costs more than the feature would have.

**Minimal fix** — move both rows out of the ✓ column into a "next" column, or implement P0-9 (SHA-256 is ~1.5 h and turns a lie into a demo).

---

### P0-5 · "Capture + Analyze" crashes if camera permission is denied
**0.5 h**

**What is wrong** — `ui/screens/WalkthroughScreen.kt:94-108`: the `PreviewView` (and therefore `cameraController.bindTo()` at `:99`) is only created when `hasCameraPermission` is true. But the Capture button at `:122` is gated only on `!busy`. With permission denied, `imageCapture` is still `null`, so `camera/CameraController.kt:47-50` resumes with `IllegalStateException`, inside a `try { } finally { }` at `WalkthroughScreen.kt:125-148` that has **no catch**. The coroutine throws and the app dies.

There is a second uncaught path: `ocr/OcrEngine.kt:26-28` calls `resumeWithException` on ML Kit failure, also uncaught.

**The question a judge asks** — none. They tap the button on a fresh install where they hit "Deny", and the app closes.

**The honest answer if unfixed** — "That's a crash. Sorry."

**Minimal fix** — `enabled = !busy && hasCameraPermission` at `:122`, and wrap the body in `try/catch(Exception)` logging to `findingsLog` instead of dying. Do the same for the report button.

---

### P0-6 · The IR finding writes "placeholder pattern, replace with Day 12 capture" into the report
**0.5 h**

**What is wrong** — `ui/screens/WalkthroughScreen.kt:172-177`:
```kotlin
// Placeholder pattern. Day 12 of the plan replaces this with the real
val placeholderPattern = intArrayOf(9000, 4500, 560, 560, 560, 1690)
… "IR transmit OK (" + profile.brand + " profile, placeholder pattern, replace with Day 12 capture)"
```
That string goes to `logFinding()` → `InspectionEntity.label` (`:74-86`) → the Room DB → `ReportGenerator.synthesizeNarrative()` (`:72`) → **printed in the tenant-facing PDF**.

The pattern itself is a 6-element NEC header stub, not a real AC command. `ir/IrController.kt:60-61` says so: *"Do not treat these ints as real transmittable patterns; only carrierFrequencyHz is populated here on purpose."*

**The question a judge asks** — "Read me the IR line on the report."

**The honest answer if unfixed** — "It says 'placeholder pattern'. We never captured the venue AC's code."

**Minimal fix** — capture the venue AC's real burst this morning (you need an external IR receiver — `IrController.kt:11-13` correctly notes `ConsumerIrManager` is transmit-only), hardcode it, and change the label to `"IR command transmitted — <brand>, 38 kHz"`. If capture fails, the label must read `"IR emitter fired (pattern not verified against this unit)"`. Never ship the word "placeholder" into a document you hand a judge.

---

### P0-7 · "Everything you saw ran on the Hexagon NPU" — it did not
**1 h (rewrite)**

**What is wrong** — `docs/pitch/PITCH_DECK_PROMPT.md:128` scripts that sentence at 2:35. Slide 8 draws a "HEXAGON NPU (HTP)" lane containing all three models. Slide 1's pill reads `SNAPDRAGON 8 ELITE GEN 5`. The app's only inference dependency is `org.pytorch:pytorch_android_lite:1.13.0` (`app/build.gradle.kts:81`, `gradle/libs.versions.toml:14`), which is a CPU interpreter. A repo-wide grep of the Android module for `executorch|\.pte|Qnn|NNAPI|Vulkan|delegate|XNNPACK` returns **zero code hits**. The `.pte` and the two `libQnn*.so` exist only in `ml/YOLOV8/qnn_android_bundle/` and are not in `app/src/main/assets/` or `jniLibs`.

Worse, the artifact you do have was compiled for the wrong chip: `ml/YOLOV8/run_qnn_conversion.sh:48` passes `--soc SM8650`, which is **Snapdragon 8 Gen 3**, not the 8 Elite Gen 5 in the iQOO 15 — and `convert_yolov8_seg_qnn_pte.py:170-173` silently falls back to SM8650 for any unrecognised name. The bundle is also incomplete: the script's own `needed_libs` list (`:215-220`) requires `libQnnHtpV75Skel.so` and `libqnn_executorch_backend.so`, neither of which is present. Without a Skel library the HTP cannot execute at all.

**The question a judge asks** — "Which of these runs on the NPU? Show me the delegate." Or, from a Qualcomm-side judge: "What SoC did you compile the `.pte` for?"

**The honest answer if unfixed** — "None of it. All three run on the CPU. We have an ExecuTorch INT8 `.pte` in the repo but it isn't wired into the app and it was lowered for SM8650."

**Minimal fix (do not attempt the integration today)** — rewrite the architecture slide to show what is true: *Host CPU — YOLOv8n-Seg INT8 via PyTorch Lite; 36-feature logistic regression, 36 MACs; deterministic safety gate; PdfDocument.* Add one honest line: *"ExecuTorch/QNN lowering is done and in the repo (`qnn_android_bundle/`); HTP integration is the next step."* Showing a compiled `.pte` and saying "not yet wired" scores better than claiming an NPU a judge can disprove with `adb shell dumpsys`.

---

### P0-8 · The signed report cannot leave the app
**1.5 h**

**What is wrong** — `report/ReportGenerator.kt:120` writes to `File(context.filesDir, "report_${sessionId}.pdf")` — private internal storage. There is no `<provider>` in `AndroidManifest.xml`, no `FileProvider`, no `ACTION_SEND`, no `ACTION_VIEW`. Grep for `Intent|FileProvider|MediaStore|share` across `app/src/` returns nothing. The user generates a PDF and then **cannot open it, view it, print it, or send it to the other party**. `ReportScreen.kt` renders the report from an in-memory object, not from the PDF.

The whole pitch is "tenant and landlord each hold a signed report" (slide 1, slide 4). Neither of them can get the file.

**The question a judge asks** — "Great. Now send it to me."

**The honest answer if unfixed** — "I can't. It's in internal storage with no share path."

**Minimal fix** — add a `FileProvider` + `res/xml/file_paths.xml`, and a "Share report" button on `ReportScreen` firing `ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`. This single change makes the demo's closing beat land instead of dying.

---

### P0-9 · "Tamper-evident" and "dual-party signature" do not exist
**1.5 h for the hash · 3 h with signatures**

**What is wrong** — `report/ReportGenerator.kt:19-22` states it plainly in its own doc comment:
> *"'signed' here means a visible timestamp + session ID baked into the document, not a cryptographic signature — the design doc explicitly calls out dropping real crypto-signing as unnecessary complexity for a demo, and that decision is carried through here."*

Grep across `app/src/` for `MessageDigest|KeyStore|Signature|sha256|Cipher|hmac`: **zero hits.** There is no signature-capture surface anywhere (no `Canvas`/`Path` drawing composable). The button at `WalkthroughScreen.kt:215` says "Generate Signed Report" and nothing is signed. Meanwhile `problem.md:20`, `solution1.md:27` and slides 1/4/8/12/15 all promise tamper-evidence, SHA-256 and dual signatures.

**The question a judge asks** — "What makes this tamper-evident? If I edit the findings and regenerate, what breaks?"

**The honest answer if unfixed** — "Nothing breaks. It's a timestamp and a session ID on a PDF. Anyone can regenerate it with different content."

**Minimal fix (1.5 h, buys most of the credit)** — serialise the findings to a canonical JSON string, `MessageDigest.getInstance("SHA-256")`, print the hex digest in the PDF footer on every page and show it on `ReportScreen` before generation. Then the honest claim becomes: *"the report carries a SHA-256 of exactly these findings; change one character and the digest no longer matches."* That is a real, defensible, one-sentence property.
**Add signatures (+1.5 h)** — two `Canvas`-backed signature pads rendered into the PDF *below* the printed digest, so each party visibly signs a specific hash. That is what binds a signature to findings; a signature above unhashed text binds nothing.

---

### P0-10 · Opening the Walkthrough screen freezes the phone; every capture blocks the UI thread
**1.5 h**

**What is wrong** — everything heavy runs on the main thread:
- `WalkthroughScreen.kt:51` — `DefectSegmenterFactory.create(context)` inside `remember { }`, i.e. during composition. On first launch this copies a **13.7 MB** asset to `filesDir` (`YoloSegDefectSegmenter.kt:136-148`) and then `LiteModuleLoader.load()`s it (`:124`).
- `:53` — `TrainedTapClassifier.create(context)` parses a 73 KB JSON containing a 10,280-float mel filterbank via `org.json` (`AcousticModelBundle.kt:60-90`), also on the main thread.
- `:126-142` — a full 640×640 YOLOv8n-Seg CPU forward pass, plus a decoder that sweeps 8400 anchors × 4 classes and then 32 coefficients per mask cell (`YoloSegDecoder.kt:90-123, 159-182`) — inside `scope.launch` on `Dispatchers.Main`, with no `withContext`.
- `:159` — `recordAndClassifyOneTap()` blocks for **1.2 s** of `AudioRecord` reads (`AcousticTapClassifier.kt:62-76`) plus a 4096-point FFT, on the main thread.

**The question a judge asks** — none. They watch the app hang, or Android shows "SmartLease Edge isn't responding".

**The honest answer if unfixed** — "It's doing inference on the UI thread."

**Minimal fix** — wrap the three heavy call sites in `withContext(Dispatchers.Default)` (`:126`, `:159`) and move segmenter/classifier construction into a `LaunchedEffect` with a loading state. This is a 1.5 h change that converts a demo that stutters into one that doesn't.

---

### P0-11 · "View Past Reports" is a dead button
**2 h**

**What is wrong** — `MainActivity.kt:60` navigates to `"report/none"`. `loadedReport` (`:54`) is only ever set inside the walkthrough callback at `:69`. So the past-reports route always renders `ReportScreen(report = null)`, which prints **"No report generated yet for this session."** (`ReportScreen.kt:20-24`).

There is no query for prior sessions anywhere — `InspectionDao.kt` exposes only `findingsForSession(sessionId)`; nothing lists sessions.

**The question a judge asks** — they tap it, because it is one of two buttons on the home screen.

**The honest answer if unfixed** — "That doesn't work yet."

**Minimal fix** — add `@Query("SELECT DISTINCT sessionId, MIN(timestampEpochMillis) … GROUP BY sessionId ORDER BY 2 DESC")` to `InspectionDao`, render the list, and load on tap. Or, if time is short, **delete the button** — a missing feature costs nothing, a broken one costs trust.

---

### P0-12 · The app retains no photograph and no audio clip — there is no evidence in the evidence tool
**4 h**

**What is wrong** — `CameraController.captureBitmap()` returns a `Bitmap` that `WalkthroughScreen.kt:126-142` analyses and drops; it is never encoded or written. Grep for `compress|\.jpg|\.png|MediaStore|FileProvider` across `app/src/main/java/com/smartlease/` → the only `FileOutputStream` uses are the PDF (`ReportGenerator.kt:121`) and the model-asset copy (`YoloSegDefectSegmenter.kt:141`). The tap PCM is likewise discarded (`AcousticTapClassifier.kt:63-78`). `InspectionEntity.detailJson` is hardcoded to `"{}"` at `WalkthroughScreen.kt:82`, so not even the bounding box, area or confidence survives — only a flattened English label string.

The product exists to settle a dispute about what a wall looked like. It stores a sentence about the wall.

**The question a judge asks** — "The landlord says the crack is new. Show me the photo."

**The honest answer if unfixed** — "We don't keep photos. The report says 'crack in wall surface, ~0.75 sq ft'."

**Minimal fix** — on capture, write the JPEG to `filesDir/sessions/<id>/<ts>.jpg`, store its path + SHA-256 in `detailJson` alongside the box/area/confidence, and draw the thumbnail into the PDF section. 4 h, and it converts the report from an assertion into a document.

---

### P0-13 · The stated problem — move-in vs move-out — is not addressed by the code
**Cannot be closed before judging. Reframe instead: 1 h**

**What is wrong** — walking the code against the problem statement in `problem.md:18-20`:

| Required capability | Where it is | Verdict |
|---|---|---|
| Move-in baseline captured | `ArAlignmentTracker.captureBaseline()` `:49-52` — two `Float`s, pitch and roll | Angle only. No photo, no findings, no room |
| Baseline stored | nowhere | **Absent.** In-memory fields destroyed at `WalkthroughScreen.kt:66` `onDispose`. `FindingType.AR_BASELINE_ALIGNMENT` (`InspectionEntity.kt:28`) is declared and **never used by any code path** |
| Baseline retrieved | nowhere | **Absent** |
| Transport landlord-phone → tenant-phone, offline | nowhere | **Absent.** No `Intent`, no Bluetooth, no Wi-Fi Direct, no NFC, no QR, no file export. The two phones in the pitch have no way to exchange anything |
| Move-out vs move-in diff | nowhere | **Absent.** No comparison code exists |
| Fair wear and tear vs tenant damage | nowhere | **Absent.** The only severity logic is a 9-keyword hazard list (`SafetyGate.kt:21-25`) for exposed wires, leaks and gas smells. `Severity.NOTABLE` is declared and never assigned |
| Fixture inventory (fan, tubelight, tap, geyser, switch, curtain rod) | nowhere | **Absent.** The four vision classes are `crack, peeling, spalling, stain_mould` — surface defects only. Nothing models a fixture, its presence, or its absence |
| Both parties co-sign; signature bound to findings | nowhere | **Absent** (see P0-9) |
| Per-room structure | nowhere | **Absent.** `propertyLabel` is the hardcoded string `"Demo Property, Chennai"` (`MainActivity.kt:69`, `WalkthroughScreen.kt:206`) |

**The question a judge asks** — "Walk me through it. Landlord inspects at move-in. Twelve months later the tenant moves out. How does the app compare the two?"

**The honest answer if unfixed** — "It doesn't. What we built is a single-session condition-capture tool. The comparison engine, the baseline store and the phone-to-phone handoff aren't there."

**Minimal reframe that is still true (1 h)** — stop pitching "compare move-in to move-out". Pitch **"one 60-second, timestamped, offline condition record that neither party can produce from memory"** — that *is* what the code does, and it is a real product. Then name the diff engine as the roadmap, on purpose, on a slide. A judge forgives a scoped V1. A judge does not forgive being walked into a comparison demo that doesn't exist.

---

# P1 — loses points

---

### P1-1 · Hardcoded 48″×36″ frame makes every square-footage number distance-invariant
**0.5 h to make honest · 6 h to make real**

**What is wrong** — `WalkthroughScreen.kt:131`:
```kotlin
val defects = visionSegmenter.segmentDefects(bitmap, frameWidthInches = 48f, frameHeightInches = 36f)
```
`YoloSegDefectSegmenter.kt:52,59` then computes `frameSqFt = (48f/12f) * (36f/12f) = 12.0` and `areaSqFtEstimate = coverageFraction * 12.0`. Hold the phone 30 cm from the wall or 3 m from it — same number. There is no `fx`, no `fy`, no `Z` anywhere in `app/src/` (verified by grep). The pinhole formula that `solution1.md:6-7`, `summary.md:75-76` and slides 6/8/16 present exists only in `ml/vision/predict.py:37-52`, never runs on the phone, hardcodes `focal_length_px = 500.0`, and takes Z from a Gradio slider (`ml/app.py:303-304`).

**The question a judge asks** — "I'll stand back two metres and scan the same crack. Does the square footage change?"

**The honest answer if unfixed** — "No. It's the fraction of the frame the mask covers, times a fixed 12 sq ft. It is not a physical measurement."

**Minimal fix (0.5 h)** — relabel the output as `"~6.3% of frame"` and drop "sq ft" from the UI, the PDF and the deck. A percentage you can defend beats a square footage you can't.
**Real fix (6 h)** — read `SENSOR_INFO_PHYSICAL_SIZE` + `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` from `CameraCharacteristics` to get true `fx, fy` in pixels, add a wall-distance input (a slider is fine and honest), and implement `area_m² = maskPixels × Z²/(fx·fy)`. Then the deck's formula is true.

---

### P1-2 · `vision_best.ptl` is the wrong model, is 12.5 MB of dead APK, and fails silently
**0.5 h**

**What is wrong** — TorchScript inspection of `app/src/main/assets/vision_best.ptl`: top-level class `DetectionModel`, `forward(x) -> Tensor` (single tensor, **no proto branch**), head reshaping to **5** class channels. It is a 5-class *detection* model. `yolov8n_seg.ptl` is the real one (`StaticYOLOv8SegWrapper -> Tuple[Tensor,Tensor]`, 4 classes + 32 coeffs, matching `YoloSegConfig.kt:19-21`).

`YoloSegDefectSegmenter.kt:114` lists it as fallback #2. If `yolov8n_seg.ptl` ever fails to load, `vision_best.ptl` loads **successfully**, sets `isTrainedModel = true` (`:26`), and then every inference hits the `error("Expected a (preds, protos) tuple…")` at `:68`, which is swallowed by the catch at `:46-50` and returns `emptyList()`. The UI reports "Capture: nothing flagged" (`:143-145`) in "YOLOv8n-Seg" mode, forever, with no visible error.

It is also 12.47 MB of an APK where `noCompress += "ptl"` (`build.gradle.kts:49`) means it is stored uncompressed. With `acoustic_cnn.ptl` (129 KB, loaded only by the unreachable bench) that is **~12.6 MB of dead weight**, roughly a fifth of the APK.

**The question a judge asks** — "You ship two vision models. Which one runs?"

**The honest answer if unfixed** — "`yolov8n_seg.ptl`. The other is from an earlier detection experiment and shouldn't be in there."

**Minimal fix** — delete `vision_best.ptl` and `acoustic_cnn.ptl` from assets; drop `vision_best.ptl` from `CANDIDATE_ASSETS`; make `create()` return `null` when the loaded module isn't a tuple, so the honest heuristic fallback engages instead of a silent zero-detection mode. Fix `solution1.md:4` and `summary.md:68`, which both name `vision_best.ptl` as the segmentation model.

---

### P1-3 · The detector has never seen an undamaged wall
**Cannot be fixed today. Pre-empt it: 0.5 h**

**What is wrong** — across all 2106 images in `YOLOV8/unified_defects/` there are **zero empty label files** in train, valid or test. Every image contains at least one defect instance. This is by construction: `merge_and_zip_datasets.py:100` only copies an image `if remapped_lines:` — any image whose labels didn't map to the four classes is discarded, taking every potential background frame with it. `docs/guides/DATASET_SOURCES.md:41` even identifies a Kaggle set as *"Good for negative samples (clean walls)"*; it was never used.

Class instances actually present (train): `crack 2431 · spalling 2363 · peeling 1507 · stain_mould 1102`.

Consequence: with `SCORE_THRESHOLD = 0.25f` (`YoloSegConfig.kt:47`) and a prior that says "something is here", the following will fire in a real Indian rental flat:

| Real-room object | Likely false class | Why |
|---|---|---|
| Grout lines between floor/wall tiles | `crack` | The crack class is trained almost entirely on thin dark linear features |
| Shadows in corners, under sills, behind furniture | `stain_mould` | Low-luminance blobs; no shadow negatives exist |
| Exposed-brick feature wall | `spalling` | **Guaranteed** — `merge_and_zip_datasets.py:24` maps `exposed brickwork → spalling` |
| Cables, conduit, wire routing | `crack` | Linear, high-contrast |
| Switchboards, sockets, switch plates | `spalling` | Rectangular surface interruptions resemble plaster loss |
| Patterned / terrazzo / mosaic tiles | `crack` + `spalling` | Dense linear + patch texture |
| Wood grain on doors, cupboards, window frames | `crack` | Fine parallel linear texture |
| Rusted window grilles, balcony railings | `stain_mould` | `merge_and_zip_datasets.py:26,29` folds `Corrosion` and `corrosion` into `stain_mould` |
| Textured / putty-finish wall | `peeling` | The peeling class absorbed `Scaling` (`:23`) |
| Wall art, posters, framed pictures | `peeling` / `stain_mould` | High-contrast bounded patches |

The class taxonomy itself will read badly to anyone who inspects it: an intentional exposed-brick wall is reported to a tenant as *"spalling (surface breaking away)"* (`YoloSegConfig.kt:42`), and rust on a window grille as *"staining or mould growth"* (`:43`).

**The question a judge asks** — points the phone at the clean conference-room wall. "What does it see?"

**The honest answer if unfixed** — "Probably something. The model was trained only on images that contain damage, so it has no concept of a clean surface. That's the first thing we'd fix."

**Minimal pre-emption (0.5 h)** — walk the venue this morning, find the wall you will demo on, and **know what it detects before a judge does it for you**. Prepare the sentence: *"we trained on 2106 public building-defect images with zero hard negatives — that's a known gap and here's the fix: 200 clean-wall backgrounds."* Volunteering the weakness beats having it demonstrated.

---

### P1-4 · Two unused permissions on a privacy pitch
**0.5 h**

**What is wrong** — `AndroidManifest.xml:17` declares `ACCESS_FINE_LOCATION` and `MainActivity.kt:37` requests it at launch, but `InspectionEntity.latitude/longitude` (`:19-20`) default `null` and **no code ever sets them** (verified by grep for `LocationManager|FusedLocation|getLastKnownLocation`). `AndroidManifest.xml:20` declares `VIBRATE` "for haptic confirmation on Tier-1 flags"; there is no `Vibrator` reference anywhere.

**The question a judge asks** — "Why does an offline inspection app want my precise location on first launch?"

**The honest answer if unfixed** — "It doesn't use it. The permission shouldn't be there."

**Minimal fix** — delete both from the manifest and from the launch request. Two lines, and the privacy slide stops having a hole in it.

---

### P1-5 · Release builds are unsigned and unshrunk
**0.5 h**

**What is wrong** — `app/build.gradle.kts:28-36`: the `release` block sets `isMinifyEnabled = false` and declares **no `signingConfig`**. `./gradlew assembleRelease` therefore emits an unsigned APK that will not install. `app/proguard-rules.pro` is a single comment line (*"Hackathon build — no obfuscation, no custom rules needed yet."*). The only installable artifact is a debug build. `README.md:122-125` documents `assembleDebug` as the build command and gives ~56 MB.

**The question a judge asks** — "Can I install this?"

**The honest answer if unfixed** — "It's a debug APK."

**Minimal fix** — add a `signingConfigs.release` with a generated keystore and point `release` at it. Leave minify off (R8 + PyTorch reflection is not a thing to debug tonight). Sideload a signed release build so "can I install it" has a clean answer.

---

### P1-6 · `unified_defects/data.yaml` points at a drive that does not exist
**0.1 h**

**What is wrong** — `YOLOV8/unified_defects/data.yaml` line 5: `path: D:\hackathon\YOLOV8\unified_defects`. Written by `merge_and_zip_datasets.py:123` via `str(out.resolve())`. The workspace is on `C:` and the mirror repo is on GitHub. Nobody can run training from this file as committed.

Two related reproducibility gaps: `ml/acoustic/export_for_android.py:66` hardcodes `audio_root = "../data/audio"`, so it only runs from `ml/acoustic/`; and `YOLOV8/prepare_calibration.py:13-16` globs `**/valid/images` across the *entire* workspace, so INT8 calibration sampled images from `concrete/` and `paint-peel/` — datasets the model was never trained on.

**The question a judge asks** — "Can I reproduce the training?"

**The honest answer if unfixed** — "Not from the repo as it stands."

**Minimal fix** — make the path relative. 30 seconds.

---

### P1-7 · Not one recorded metric exists for the vision model
**0.5 h to measure val mAP · 2 h to present it**

**What is wrong** — `find` across `YOLOV8/` for `results*.csv`, `*confusion*`, `*PR_curve*`, `runs/`, `args.yaml`, `*.png` returns **nothing**. `smartlease_edge_yolov8_seg.ipynb` has **0 saved outputs and every `execution_count` is `null`**, including its own §5 "Validation & Quantitative Metrics" cells (17-18). `ml/YOLOV8/README.md:65` claims the pipeline "Displays mAP@50, mAP@50-95, loss curves, and confusion matrix". Slide 6 states "Target metrics: 75%+ mAP". `best.pt` is a 6.78 MB binary with no provenance, no epoch count, no seed, no val score.

By contrast the acoustic side did this properly (`ml/models/acoustic_report.json`: n, grouped LOGO protocol, CI, majority baseline, an explicit verdict). The vision side has none of it.

**The question a judge asks** — "What's the mAP?"

**The honest answer if unfixed** — "We don't have one. We never recorded a validation run."

**Minimal fix (0.5 h)** — `yolo segment val model=best.pt data=unified_defects/data.yaml split=test` right now. You have the weights and the 107-image test set locally. Even a mediocre number, stated with its caveat, beats silence. **State the caveat honestly**: frames from the same source videos appear across all three splits — `DJI_0017` has 50 train / 5 valid / 3 test frames, and `DJI_0012`, `DJI_0015`, `VID_20241228_130232250` are split the same way — so val/test metrics are inflated by near-duplicates. Say that before a judge finds it.

---

### P1-8 · The acoustic model is the best work in the repo and rests on 15 taps from 8 WhatsApp voice notes
**0 h to fix the model · 0.5 h to frame it**

**What is wrong** — not the engineering. `AcousticModelBundle.kt`, `AcousticFeatureExtractor.kt`, `export_for_android.py` and `AcousticFeatureExtractorTest.kt` are genuinely well built: grouped `LeaveOneGroupOut` by source recording prevents sibling leakage (`export_for_android.py:72-84`), the mel filterbank and DCT basis ship as data so Kotlin cannot drift from Python, golden-vector parity is unit-tested (`AcousticFeatureExtractorTest.kt:75-95`), and the UI quotes the cross-validated score with its interval, never the 100% training score (`TrainedTapClassifier.kt:52-57`), with a test guarding that (`:117`).

The problem is the data. `ml/data/audio/` holds **8 files** — 4 hollow, 4 solid, every one named `WhatsApp Ptt 2026-09-06 at …ogg`, 31 KB total. `acoustic_tap_model.json` → `trainedTaps: 15`; `acoustic_report.json` → `majority_baseline 0.533`, `grouped_logo_accuracy 0.8`, `best_accuracy_95ci [0.621, 0.963]` — but note the SHIPPED bundle carries `groupedAccuracyCi95 [0.548, 0.930]`, and 55–93% is what the app actually renders. An 80% score whose interval reaches down to **55%** against a **53.3%** majority baseline is, statistically, *"we cannot yet distinguish this from a coin"* — the lower bound IS the baseline.

There is also a domain shift: WhatsApp voice notes are Opus at 16 kHz with noise suppression and AGC applied; the phone serves raw 44.1 kHz PCM (`AcousticTapClassifier.kt:29`) decimated to 22.05 kHz (`TrainedTapClassifier.kt:109-126`). The model was fitted on codec-processed audio and is served clean audio.

Slide 7 claims "100+ own recordings (50 hollow, 50 solid) captured on the demo phone". That is the most directly falsifiable number in the deck — the file listing disproves it in ten seconds.

**The question a judge asks** — "How many taps is that trained on?"

**The honest answer if unfixed** — "Fifteen, from eight WhatsApp recordings. The slide says 100+ and it's wrong."

**Minimal fix** — record 40–60 real taps on the demo phone this morning (tile board and solid wall, ~20 minutes), rerun `export_for_android.py`, and correct slide 7 to the true number. Then the strongest engineering in the repo is also the most defensible claim in the pitch, and you get to say: *"we report the grouped cross-validated score with its confidence interval, not the training score — here's the line of code that enforces it."* That answer wins technical-depth points on its own.

---

### P1-9 · The `com.iqoo.multimodal` package — 6 files — is unreachable and fake
**0.5 h**

**What is wrong** — `AndroidManifest.xml:44-48` declares `com.iqoo.multimodal.MainActivity` with `exported="false"` and no intent-filter; nothing in `com.smartlease.edge` references it (verified by grep). It cannot be launched without `adb`. `solution1.md:29-31` and `summary.md:64-66` present it as a "Dual Entry Point" and a "developer model benchmark validating raw tensor input/output shapes".

It validates nothing:
- `ImageModelScreen.kt:104` — `PyTorchHelper.predictImage(Bitmap.createBitmap(224,224,ARGB_8888))`: inference on a **blank bitmap**, while a live camera preview fills the screen purely as decoration. Its own comment at `:102` reads *"In a real app, capture the image to a file/bitmap and pass to PyTorchHelper"*.
- `AudioModelScreen.kt:69` — `predictAudio(ShortArray(16000))`, i.e. 16000 zeros. `:73` — *"Start AudioRecord (Placeholder for actual recording logic)"*. It never records.
- `PyTorchHelper.kt:74-80` — `predictAudio` ignores its input and returns the literal string `"Audio Model Output: [Sound Y] (Placeholder)"`.

**The question a judge asks** — only if they read the repo. Then: "What is this package?"

**The honest answer if unfixed** — "Leftover scaffolding. It isn't reachable and it doesn't do what the docs say."

**Minimal fix** — delete the package and the manifest entry, delete the "Dual Entry Points" claim from `solution1.md` and `summary.md`. This also removes the only reason `acoustic_cnn.ptl` and `vision_best.ptl` are in the APK (see P1-2).

---

### P1-10 · PyTorch Mobile 1.13.0 is an end-of-life runtime
**0 h to fix · have the answer ready**

**What is wrong** — `gradle/libs.versions.toml:14` pins `pytorch = "1.13.0"` for `org.pytorch:pytorch_android_lite`. PyTorch Mobile / the `org.pytorch` Android artifacts are deprecated in favour of ExecuTorch, and 1.13.0 dates from late 2022. You are shipping a ~4-year-old, no-longer-maintained inference runtime while the deck's entire technical-depth argument is built on ExecuTorch — the successor you already have a compiled `.pte` for, and did not integrate.

**The question a judge asks** — "Why PyTorch Lite and not ExecuTorch, when your own export script targets ExecuTorch?"

**The honest answer if unfixed** — "Time. The `.pte` and the QNN lowering are done and in the repo; wiring the ExecuTorch AAR and the HTP delegate into the APK is the step we didn't finish."

That is a genuinely good answer — it shows you know the difference. Rehearse it; do not let it come out as a surprise.

---

### P1-11 · Findings carry no structured detail
**1 h**

**What is wrong** — `WalkthroughScreen.kt:82` — `detailJson = "{}"`, always. The bounding box, area estimate, confidence, class index, tap probability, decay time and high-frequency ratio all exist at the call site (`:135-142`, `:159-163`) and are flattened into an English label string. Nothing structured is ever persisted, so `ReportGenerator.synthesizeNarrative()` (`:66-75`) can only re-join label strings with semicolons. The PDF therefore cannot show a table, a per-item confidence column, or anything a second tool could parse.

**The question a judge asks** — "Can I get the findings as data?"

**The honest answer if unfixed** — "Only as the printed sentence."

**Minimal fix** — populate `detailJson` with the real fields at both call sites, and render a table in `renderToPdf`. 1 h, and the report starts looking like a document rather than a log dump.

---

### P1-12 · Test coverage stops at two files; the instrumentation deps are dead
**0.5 h to remove dead deps · 3 h for meaningful coverage**

**What is wrong** — `app/src/test/` contains exactly two files, 253 lines total: `AcousticFeatureExtractorTest.kt` (125) and `YoloSegDecoderTest.kt` (128). Both are genuinely good — the decoder tests hand-compute letterbox padding, mask-derived coverage, NMS and IoU, and the acoustic test enforces Python↔Kotlin parity against golden vectors.

Nothing covers `ReportGenerator`, `SafetyGate`, `IrController`, `AcousticTapClassifier.classify()`, the DAO, or any UI. There is **no `app/src/androidTest/` directory at all**, yet `build.gradle.kts:88-89` declares `androidx.test.ext:junit` and `espresso-core`.

**The question a judge asks** — "What's tested?"

**The honest answer if unfixed** — "The two pieces of pure arithmetic that would silently produce a wrong number. Nothing else." That is a defensible answer if you say it that way — it's a deliberate risk-weighted choice, not an absence.

**Minimal fix** — delete the two unused androidTest dependencies. Add ~8 tests for `SafetyGate.evaluate()` (trivial, and it is the piece you pitch as your architectural novelty — it looks bad untested).

---

### P1-13 · Report generation races the last insert
**0.5 h**

**What is wrong** — `logFinding()` fires `scope.launch { db.inspectionDao().insert(...) }` (`WalkthroughScreen.kt:76-87`) without awaiting. "Generate Signed Report" (`:202-208`) immediately calls `findingsForSessionOnce(sessionId)`. Both are on the same `Main` dispatcher so they usually serialise, but there is no ordering guarantee, and `ReportGenerator.buildReport` is also called a *second* time from `MainActivity.kt:69` after navigation — duplicating work and re-reading the DB.

**The question a judge asks** — none, until the last finding is missing from the PDF on stage.

**Minimal fix** — make `logFinding` suspend and await the insert, and pass the already-built report through instead of rebuilding it in `MainActivity`.

---

### P1-14 · `Severity.NOTABLE` is unreachable; `AR_BASELINE_ALIGNMENT` is never emitted
**0.5 h**

**What is wrong** — `InspectionEntity.kt:31-35` defines `INFO, NOTABLE, STOP_ESCALATE`. `WalkthroughScreen.kt:72` is the only assignment: `verdict.escalatedSeverity ?: Severity.INFO`, and `SafetyGate.evaluate` (`:37-46`) only ever returns `STOP_ESCALATE` or `null`. **`NOTABLE` can never be assigned.** Likewise `FindingType.AR_BASELINE_ALIGNMENT` (`:28`) is never used — pressing "Set Baseline" (`WalkthroughScreen.kt:120`) records nothing at all.

So every visual defect, however severe, is filed as `INFO` unless its English label happens to contain one of nine hazard keywords like "exposed wire" or "gas smell" — which a defect label from `YoloSegConfig.CLASS_DESCRIPTIONS` never will.

**The question a judge asks** — "A 2 sq ft crack and a scuff mark are both 'INFO'?"

**The honest answer if unfixed** — "Yes. Severity is only driven by a hazard keyword list; the defect classifier doesn't feed it."

**Minimal fix** — map area + class to `NOTABLE` in `logFinding`, and record a finding on baseline capture. 0.5 h, and the report's severity column stops being decorative.

---

### P1-15 · Session IDs are 32-bit and client-generated
**0.25 h**

**What is wrong** — `WalkthroughScreen.kt:47`: `UUID.randomUUID().toString().take(8)` — 8 hex characters, ~32 bits, truncated from a v4 UUID, generated on the device, bound to nothing. It is the primary identifier printed on the report (`ReportGenerator.kt:93`) and the PDF filename (`:120`).

**The question a judge asks** — "What stops me making a second report with the same session ID?"

**The honest answer if unfixed** — "Nothing."

**Minimal fix** — use the full UUID and include it in the SHA-256 input from P0-9.

---

### P1-16 · `README.md` is frozen at "Day 2 of 12"
**0.5 h**

**What is wrong** — `README.md:103` — *"Current Status (Day 2 of 12) — Honest MVP Assessment"*, on judging day. `:131` still lists the vision segmenter as stubbed, though `yolov8n_seg.ptl` now ships and loads. `:132` lists GenieX as a Days 9-10 item that never landed. `:124` quotes an APK size of ~56 MB from `assembleDebug`.

The README is the first thing a technical judge opens after the repo link on slide 1 and slide 15.

**The question a judge asks** — "Your README says Day 2. What actually shipped?"

**Minimal fix** — rewrite the two status tables against what the code does today. 30 minutes, and it is the highest-traffic document you own.

---

# P2 — polish

---

### P2-1 · CC BY 4.0 attribution is absent from every product-facing document — **0.25 h**
All four merged Roboflow datasets are CC BY 4.0 (`YOLOV8/{1,2,3,concrete}/data.yaml`; `1/README.dataset.txt` states *"Provided by a Roboflow user / License: CC BY 4.0"`), and `paint-peel` is Public Domain. Attribution exists inside the dataset folders, but the pitch deck, README and `solution1.md` credit nothing — while slide 11 and slide 13 pitch a ₹299–499 commercial product. CC BY 4.0 permits commercial use *with attribution*. **Fix:** one credits line on slide 17, which already lists sources for everything else.

### P2-2 · `best.pt` is not in the mirror repo — **0.25 h**
`.gitignore` globally excludes `*.pt`, then negates `!ml/YOLOV8/best.pt` — but the file simply isn't there (`ls ml/YOLOV8/` shows no weights; `find . -name "*.pt"` across the repo returns nothing). Nor are any dataset images (`ml/YOLOV8/*/train/` excluded). A judge cloning `github.com/muhammedsayeedurrahman/smartlease-edge-chennai` — the link on slides 1 and 15 — gets the 3.7 MB `.pte` and two `libQnn*.so` that nothing loads, and **cannot retrain, re-export, or verify anything**. **Fix:** commit `best.pt` (6.78 MB, well under the 100 MB limit) or link a release asset.

### P2-3 · Two collected datasets were never merged — **0 h, just know it**
`YOLOV8/concrete/` (100 images) and `YOLOV8/paint-peel/` (50 images) sit on disk with `data.yaml` files, but `merge_and_zip_datasets.py:59-63` only iterates datasets `1, 2, 3`. 150 images — including the 40 most on-topic paint-peeling training images — were downloaded and left out. The notebook run is named `yolov8n_seg_balanced_4class` while the actual on-disk distribution is 2431/2363/1507/1102 (2.2:1 imbalance).

### P2-4 · `-wal` and `-shm` are not in the backup exclusion — **0.1 h**
`backup_rules.xml:4` and `data_extraction_rules.xml:4` name `smartlease.db` exactly. Room in WAL mode also writes `smartlease.db-wal` and `smartlease.db-shm`, which can hold committed findings. Moot once P0-1 sets `allowBackup="false"`, but worth knowing why the one-line fix is better than the rules.

### P2-5 · `proguard-rules.pro` is a single comment; `exportSchema = false` — **0.25 h**
`app/proguard-rules.pro:1` — *"Hackathon build — no obfuscation, no custom rules needed yet."* `AppDatabase.kt:8` sets `exportSchema = false`, so there is no schema history and no migration path for a v2. Both are reasonable hackathon calls; both are things a thorough judge will spot. Have the sentence ready.

### P2-6 · INT8 calibration preprocessing does not match inference preprocessing — **1 h**
`YOLOV8/prepare_calibration.py:39` does a plain aspect-distorting `cv2.resize(img_rgb, (640,640))`. The Android inference path letterboxes with 114/255 grey padding (`YoloSegDefectSegmenter.kt:82,111`, `Letterbox.kt:41-57`). The INT8 activation ranges in `yolov8n_seg_htp.pte` were therefore calibrated on a distribution the model will never actually see. Irrelevant today because the `.pte` isn't loaded — but it is the first bug you would hit when you do wire it up, and it will look like "quantisation lost accuracy" rather than "calibration preprocessing was wrong". Same mismatch exists in `PyTorchHelper.kt:41`.

### P2-7 · The exported bundle quotes an accuracy from a different fit — **0 h, no action**
`acoustic_tap_model.json` → `groupedAccuracy 0.8`; `ml/models/acoustic_report.json:11` → `LogisticRegression: 0.8667`. These look contradictory but are not: the two numbers come from two scripts with slightly different pipelines, and the shipped bundle correctly quotes the LOGO score of *the exact recipe it exports* (`export_for_android.py:82-84,114`). Noted so you are not caught flat-footed if a judge diffs the two files. **The shipped number is the conservative one, which is the right call.**
