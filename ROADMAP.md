# ROADMAP.md — Ranked fixes under three budgets

**Assumptions:** one developer. Judging is 2026-09-13. Hours are working hours, not elapsed. Every item traces to a finding in `AUDIT_GAPS.md`.

**Rubric weights** (from deck slide 14, used to price each fix):
End product quality **30%** · Novelty & impact **20%** · Creative phone use **15%** · Technical depth **15%** · Demo & presentation **10%** · Office Kit usage **10%**.

**The governing principle for the next 24 hours:** you cannot add a capability that changes what this product *is*. You can remove every reason a judge stops believing you. Points lost to a contradiction are worth more than points gained from a feature, because a contradiction discounts everything you say afterwards. **Spend the budget on truth, not on scope.**

---

# Budget 1 — 8 hours (tonight)

Ordered by return per hour. Items 1-6 are 2h 15m combined and eliminate every contradiction a judge can find without reading code.

### 1. `allowBackup="false"` — **5 min** — buys: Novelty & impact, Technical depth
`AndroidManifest.xml:23`. One attribute. Today the report PDF is inside Google Auto Backup's scope while the PDF itself prints *"no data left this phone."* (`ReportGenerator.kt:94`). This is the only P0 in the audit where the fix is shorter than the sentence explaining it. **Highest return per minute in this entire document.**

### 2. Strip the deck's self-inflicted wounds — **45 min** — buys: Demo & presentation, all downstream credibility
- Fill `[Team name]`, `[Member 1-3]`, `[Student / Professional bucket]` (slide 1, 14), `[email]` (slide 15).
- Un-tick `✓ GenieX Llama 3.2 3B narrative` and `✓ SHA-256 fingerprint + dual signature` on slide 12 — the slide titled "WORKING MVP, **HONESTLY**".
- Delete *"Everything you saw ran on the Hexagon NPU"* from `PITCH_DECK_PROMPT.md:128`. Replace with: *"The IR blaster and the mic are why this is an iQOO app; the ExecuTorch NPU path is compiled and in the repo, not yet in the APK."*
- Fix slide 6: **2106** images, not 580+; classes `crack, peeling, spalling, stain_mould`, not `crack, stain, mould, peeling, hole`.
- Fix slide 7: **15 taps from 8 recordings**, not "100+ own recordings (50 hollow, 50 solid)".
- Redraw slide 8's NPU lane as a CPU lane.

*Every one of these is a ten-second disproof for a judge holding the repo. You are not adding claims — you are removing the ones that cost you the room.*

### 3. Purge placeholder strings from shipping UI — **35 min** — buys: End product quality, Demo
- `HomeScreen.kt:43` — delete the `🔧 Placeholder: … until Days 6-8 model export` card. It is also stale: the trained model *does* load. Replace with a live status line driven by `DefectSegmenter.isTrainedModel`.
- `WalkthroughScreen.kt:172-177` — the IR label currently writes *"placeholder pattern, replace with Day 12 capture"* into the DB and therefore into the tenant-facing PDF. Change to `"IR command transmitted — <brand>, 38 kHz (pattern not verified against this unit)"`.
- Grep `app/src/` for `Day \d` and remove every hit.

### 4. Gate the Capture button on camera permission — **30 min** — buys: End product quality
`WalkthroughScreen.kt:122` → `enabled = !busy && hasCameraPermission`, and wrap the capture body in `try/catch(Exception)`. Right now, denying camera permission and tapping Capture **crashes the app** (`CameraController.kt:47-50` throws into an uncaught coroutine). A judge on a fresh install who taps "Deny" will find this.

### 5. Delete the dead package and the dead assets — **20 min** — buys: Technical depth
- Delete `com.iqoo.multimodal` (6 files) and its manifest entry at `AndroidManifest.xml:44-48`. It is unreachable, and it runs inference on a blank bitmap (`ImageModelScreen.kt:104`) and returns a hardcoded string for audio (`PyTorchHelper.kt:79`).
- Delete `vision_best.ptl` (12.47 MB — a 5-class *detection* model, not a segmentation model) and `acoustic_cnn.ptl` from assets; drop `vision_best.ptl` from `CANDIDATE_ASSETS` (`YoloSegDefectSegmenter.kt:114`).
- **Removes ~12.6 MB of APK and the "Dual Entry Points" claim you cannot defend.**

### 6. Delete unused permissions — **10 min** — buys: Novelty & impact
`ACCESS_FINE_LOCATION` and `VIBRATE` from `AndroidManifest.xml:17,20` and the launch request at `MainActivity.kt:37`. Neither is used. On a privacy-first pitch, a location prompt you don't use is the first thing a judge notices.

---

### 7. SHA-256 over canonical findings, printed in the PDF footer — **1 h 30 m** — buys: Technical depth, End product quality
**The highest-leverage single feature available to you.** Serialise findings to a canonical string, `MessageDigest.getInstance("SHA-256")`, print the hex digest on every page of the PDF and show it on `ReportScreen` before generation.

This converts your worst answer ("nothing makes it tamper-evident") into a real, one-sentence, defensible property: *"the footer carries a SHA-256 of exactly these findings; change one character and the digest stops matching."* It also converts one of slide 12's two false ticks into a true one. Nothing else in this list moves that much credibility for 90 minutes.

### 8. Share the PDF — **1 h 30 m** — buys: End product quality, Demo
`FileProvider` + `res/xml/file_paths.xml` + an `ACTION_SEND` button on `ReportScreen` with `FLAG_GRANT_READ_URI_PERMISSION`. Today the PDF is written to `filesDir` with no share path (`ReportGenerator.kt:120`), so **neither party can receive the document the entire pitch is about**. Your demo's closing beat currently ends with a file nobody can open.

### 9. Get everything off the main thread — **1 h 30 m** — buys: End product quality, Demo
`withContext(Dispatchers.Default)` around `WalkthroughScreen.kt:126` (640×640 CPU forward pass + 8400-anchor decode) and `:159` (1.2 s blocking `AudioRecord` + FFT). Move segmenter/classifier construction out of `remember { }` at `:51,53` into a `LaunchedEffect` with a spinner — first launch currently copies a 13.7 MB asset and loads a TorchScript module during composition. **Today, opening the Walkthrough screen freezes the phone for seconds and every capture blocks the UI.** This is the difference between a demo that looks finished and one that looks like a prototype.

### 10. Run `yolo segment val` and get a number — **30 min** — buys: Technical depth
```
yolo segment val model=YOLOV8/best.pt data=YOLOV8/unified_defects/data.yaml split=test
```
(fix `data.yaml:5` first — it says `path: D:\hackathon\...`, a drive that doesn't exist). You have the weights and the 107-image test set locally. **You currently have zero recorded metrics of any kind**, and "what's the mAP?" is a guaranteed question. Report it with the caveat that video frames leak across splits (`DJI_0017`: 50 train / 5 valid / 3 test) — the caveat is what makes the number credible.

### 11. Scan the demo wall — **15 min** — buys: Demo, and prevents a catastrophe
The model has **zero hard negatives** — every one of the 2106 training images contains a defect. Point the phone at the exact wall you will demo on and find out what it detects *before* a judge does. Then either pick a wall you've verified or open with the pre-emption line from `JUDGE_QA.md` Q10.

> **8 h total: 7 h 40 m of work, 20 min slack.** At the end of it: every disprovable claim is gone, the app doesn't crash or freeze, the report is hashed and shareable, and you have a validation number. Nothing new was built. That is the correct trade.

---

# Budget 2 — 24 hours (tonight + tomorrow's build window)

Everything in Budget 1, plus the following in order. **Stop adding at hour 20** and spend the last four on rehearsal — three timed runs in airplane mode, as the deck's own plan says.

### 12. Record 40-60 real taps and retrain the acoustic model — **2 h** — buys: Technical depth, Creative phone use
Tile board and solid wall, on the demo phone, ~20 min of recording; then `py ml/acoustic/export_for_android.py` (run it from `ml/acoustic/` — `:66` hardcodes a relative path).

This is the best-engineered thing in the repo — grouped `LeaveOneGroupOut` by source recording, mel filterbank and DCT shipped as data so Kotlin cannot drift from Python, golden-vector parity unit-tested, and the UI quotes the cross-validated score with its 95% interval rather than the training score. It is currently fitted on **15 taps from 8 WhatsApp voice notes**, which is (a) not a result — 80% with a 55-93% CI over a 53% baseline, so the interval's lower bound is chance — and (b) codec-processed audio served against clean 44.1 kHz PCM. Fifty real taps on the demo phone makes your strongest engineering story also your most defensible number, and eliminates slide 7's most falsifiable claim.

### 13. Persist photos as evidence — **4 h** — buys: End product quality **(30%)**, Novelty & impact
On capture, write the JPEG to `filesDir/sessions/<id>/<ts>.jpg`, store path + SHA-256 + box + area + confidence in `detailJson` (currently hardcoded `"{}"` at `WalkthroughScreen.kt:82`), and draw the thumbnail into the PDF section.

**This is the single biggest product gap.** The app exists to settle a dispute about what a wall looked like, and it currently stores a sentence about the wall. Photos in the report, each covered by the P0-9 hash, convert the output from an assertion into a document — and make your answer to "show me the photo" a demo instead of an apology.

### 14. Two on-screen signatures below the printed hash — **1 h 30 m** — buys: End product quality, Novelty & impact
Two `Canvas`-backed signature pads rendered into the PDF *below* the SHA-256 from item 7. Order matters and is the whole point: a signature above unhashed text binds nothing; a signature below a visible digest binds to specific findings. Turns slide 12's second false tick true, and makes "dual-party signature capture" — in `problem.md`, `solution1.md` and four slides — a claim you can demo.

### 15. Honest area semantics — **1 h** — buys: Technical depth, Novelty & impact
Relabel `areaSqFtEstimate` output as **"% of frame"** everywhere — UI, PDF, deck. Today `WalkthroughScreen.kt:131` hardcodes a 48″×36″ frame so the sq-ft figure is `maskCoverage × 12.0` and does not change with distance. A percentage you can defend beats a square footage that collapses the moment a judge steps backwards. Keep the pinhole formula on the slide, but move it under a "next" heading.

### 16. Room structure + a real property label — **2 h** — buys: End product quality, Novelty & impact
Add a `room` column to `InspectionEntity`, a room picker in the walkthrough, and a property-name field. Currently `propertyLabel` is the hardcoded literal `"Demo Property, Chennai"` (`MainActivity.kt:69`, `WalkthroughScreen.kt:206`) and there is no room concept at all — so the PDF cannot be per-room and a skipped room is invisible. Sectioning the report by room is also what makes selective omission detectable.

### 17. Severity that means something — **1 h** — buys: End product quality
`Severity.NOTABLE` is declared and **unreachable** — `SafetyGate.evaluate` only ever returns `STOP_ESCALATE` or nothing, so every visual defect is filed `INFO`. Map class + area band → `NOTABLE` in `logFinding` (`WalkthroughScreen.kt:69-88`), and record a finding when "Set Baseline" is pressed (`FindingType.AR_BASELINE_ALIGNMENT` is declared and never emitted). Without this the severity column in the PDF is decoration.

### 18. Fix or delete "View Past Reports" — **2 h to fix, 5 min to delete** — buys: End product quality
It is one of two buttons on the home screen and it is dead — `MainActivity.kt:60` navigates to `report/none` and `ReportScreen.kt:20-24` prints "No report generated yet". Add a `SELECT DISTINCT sessionId … GROUP BY` query to `InspectionDao` and a session list, **or delete the button**. A missing feature costs nothing; a broken one a judge taps costs trust.

### 19. Release signing config — **30 min** — buys: End product quality
`build.gradle.kts:28-36` has no `signingConfig`, so `assembleRelease` produces an unsigned APK that will not install. Add a keystore, keep minify off (R8 plus PyTorch reflection is not a thing to debug the night before), and sideload a signed release build so "can I install this?" has a clean answer.

### 20. Rewrite the README and the three root docs — **1 h 30 m** — buys: Technical depth, Demo
`README.md:103` still reads *"Current Status (Day 2 of 12)"* on judging day, and its status tables describe a build two weeks old. Correct `solution1.md:4` (`vision_best.ptl` is not the segmentation model), `solution1.md:44-45` (NPU, Sensing Hub), `solution1.md:52` (zero-network), `summary.md:115` ("Encrypted" — it is plain Room). **A technical judge opens the README straight after the repo link on slides 1 and 15.** It is your highest-traffic document and currently your most contradicted.

### 21. Commit `best.pt` and fix the absolute path — **30 min** — buys: Technical depth
`find . -name "*.pt"` across the mirror repo returns nothing — a judge cloning `github.com/muhammedsayeedurrahman/smartlease-edge-chennai` gets a `.pte` nothing loads and cannot retrain or verify anything. Commit the 6.78 MB weights (well under the limit) and make `unified_defects/data.yaml:5` relative. Add a one-line CC BY 4.0 credit for the four Roboflow datasets to slide 17, which already lists sources for everything else.

### 22. Tests for `SafetyGate` — **1 h** — buys: Technical depth
You pitch it as your architectural novelty (`SafetyGate.kt:6`) and it has zero tests. Eight cases: each hazard keyword, case-insensitivity, a clean label, an empty string. Also delete the unused `androidTest` dependencies at `build.gradle.kts:88-89` — there is no `androidTest` directory.

> **24 h total: ~20 h of work, 4 h of rehearsal.** At the end: photos in a hashed, dual-signed, shareable report; a real acoustic dataset; honest measurement semantics; a signed APK; and documentation that matches the code.

---

# Budget 3 — 72 hours

**First, the reality:** 72 hours do not exist before judging on 2026-09-13. Treat this as (a) the plan if the event's build window genuinely is the full Sat-Sun 30 hours and you start from a finished Budget 2, or (b) the post-event roadmap — which is worth writing down, because slide 13 promises a roadmap and "here is the ranked plan with hours" is a better answer than a phase diagram.

Everything in Budget 2, plus:

### 23. 200 clean-wall hard negatives + retrain — **8 h** — buys: End product quality, Technical depth
Photograph 200 undamaged Indian interior surfaces — painted walls, tiled floors with grout, exposed brick, switch plates, wood doors, patterned tiles, curtains — add them as empty-label backgrounds, re-split **by source video** to kill the frame leakage (`DJI_0017`: 50 train / 5 valid / 3 test), and retrain for 50 epochs.

This attacks the model's actual failure mode. With zero negatives today, the detector has no concept of a clean surface and will fire on grout lines, shadows, cables and sockets. This is also the only item that makes the live demo robust against an arbitrary wall.

### 24. Real pinhole area — **6 h** — buys: Technical depth, Novelty & impact
Read `SENSOR_INFO_PHYSICAL_SIZE` and `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` from `CameraCharacteristics` to derive true `fx, fy` in pixels at the capture resolution; add a wall-distance input; implement `area_m² = maskPixels × Z²/(fx·fy)`, then `× 10.7639`. Validate against a taped 1 ft × 1 ft square at 1 m, 2 m and 3 m and publish the error band. Makes the formula on slides 6, 8 and 16 true, and the port target already exists in `ml/vision/predict.py:37-52`.

### 25. Taxonomy pass on the class mapping — **4 h** — buys: End product quality
`merge_and_zip_datasets.py` maps `exposed brickwork → spalling` (`:24`) and folds `Corrosion`/`corrosion`/`Corrosion stain` into `stain_mould` (`:26,29,44`). An intentional feature wall is therefore reported to a tenant as *"spalling (surface breaking away)"* and a rusted window grille as *"staining or mould growth"* (`YoloSegConfig.kt:42-43`). Drop the incoherent source classes, relabel the ambiguous ones, and re-merge.

### 26. Baseline capture, storage and offline handoff — **16 h** — buys: Novelty & impact **(20%)**, End product quality
The actual product. A `Baseline` entity holding per-room photos + findings + hashes; export as a signed bundle; transport over Wi-Fi Direct with a QR-displayed hash for out-of-band verification by both parties. **This is the first item on this list that makes the pitch's premise true**, and it is 16 hours because the hard part is the trust model, not the sockets.

### 27. Move-out vs move-in diff — **12 h** — buys: Novelty & impact, End product quality
Per-room, per-class delta against the imported baseline: new defects, grown defects, unchanged defects. Depends entirely on item 26. Present the diff, not the raw findings, as the report's front page — that is the artifact that actually settles a deposit dispute.

### 28. Fixture inventory — **8 h** — buys: End product quality, Novelty & impact
A per-room checklist (fan, tubelight, tap, geyser, switch, curtain rod) with present/absent/damaged states and a photo per item, diffed against baseline. Note this is a **checklist plus presence detection**, not a defect segmenter — a different problem from the one your vision model solves, and pretending otherwise is how the current gap happened.

### 29. Close the IR loop — **6 h** — buys: Creative phone use **(15%)**, Novelty & impact
Post-transmit, listen 3 s on the mic for the compressor start signature (~50-60 Hz onset plus a broadband step) and record `functional` / `no response` instead of `transmitted`. `AcousticTapClassifier` already does harder DSP than this. It converts "the emitter fired" into "the appliance responded" and is the only path that makes the functional-testing claim — your stated core differentiator on slides 4, 9 and 11 — actually true.

### 30. ExecuTorch + QNN HTP integration — **16 h, high risk** — buys: Technical depth **(15%)**
Re-lower with the correct `QcomChipset` for Snapdragon 8 Elite Gen 5 (not the SM8650 default at `convert_yolov8_seg_qnn_pte.py:86`); fix calibration preprocessing to letterbox exactly as `YoloSegDefectSegmenter.kt:82,111` does rather than `cv2.resize`-distort (`prepare_calibration.py:39`); bundle the missing `libQnnHtpV##Skel.so` and `libqnn_executorch_backend.so`; add the ExecuTorch Android AAR; write a `PteDefectSegmenter` behind the existing `DefectSegmenter` interface; measure INT8-vs-FP32 mAP delta and on-device latency. **Then, and only then, the NPU claim becomes true and the <30 ms number becomes measured.**

---

# Explicitly excluded — and why

| Item | Why it is out |
|---|---|
| **ExecuTorch/QNN HTP in the APK before judging** | 16 h of high-variance native integration — missing Skel libraries, wrong SoC target, a runtime nobody on the team has debugged on device. Attempting it inside 24 h risks a broken build on the morning of judging and buys nothing you cannot buy with an honest slide. Show the compiled `.pte`, say it is not wired, move on. |
| **On-device Llama 3.2 3B via GenieX** | The model alone is ~1.5 GB and the SDK integration is days, not hours. It cannot be started tonight. Correct the slide instead — one un-tick. |
| **Retraining vision with hard negatives before judging** | Collecting 200 negatives plus a 50-epoch retrain plus re-export is ≥8 h of mostly-waiting, and the result is unverifiable without a val protocol you also don't have yet. Pre-empt the weakness verbally (Budget 1, item 11) — that is 15 min for most of the credit. |
| **Move-in/move-out diff engine** | 28 h across items 26-27, and the hard part is a trust model, not code. Cannot be built before judging. **Reframe the pitch instead** (`JUDGE_QA.md` Q19) — a scoped, working V1 outscores a half-built V2. |
| **SQLCipher database encryption** | `summary.md:115` claims "Encrypted local inspection history" and `AppDatabase.kt:18-22` is plain Room. Fix the *document*, not the code — encryption adds no judging points once `allowBackup="false"` ships, and a keystore-backed passphrase is a 4 h rabbit hole with a migration path you don't need. |
| **R8 / minification** | `isMinifyEnabled = false` with an empty `proguard-rules.pro`. Turning R8 on the night before, against PyTorch's reflection-heavy JNI loader, is how you arrive at judging with an APK that crashes on launch. Ship it off. |

---

# Verdict — 10 lines

1. This is a real, running Android app with genuinely good work inside it — the acoustic pipeline's grouped cross-validation, the Kotlin↔Python golden-vector parity test, the YOLO decoder's mask-not-box area reasoning, and a safety gate that is deliberately not a model. Several source files are more honest about their own limits than the pitch deck is.
2. **Thing one that loses this hackathon: the NPU claim.** Every doc and the spoken script say three models run on the Hexagon HTP. The only inference dependency is `pytorch_android_lite:1.13.0` — a CPU interpreter on an end-of-life runtime — and a grep for any delegate across the whole Android module returns zero code hits. In a hardware-integration hackathon judged by people who can read a Gradle file, this is not a missing feature; it is the central claim, and it is false.
3. **Thing two: the report is not what you say it is.** "Tamper-evident", "SHA-256", "dual-party signature" — none exist, and `ReportGenerator.kt:19-22` admits it in the source. The PDF also cannot be opened, shared or sent, because it is written to private storage with no `FileProvider`. Both parties are supposed to hold this document. Neither can receive it.
4. **Thing three: the product does not address its own problem statement.** There is no stored baseline, no photograph retained anywhere, no diff, no phone-to-phone transport, no wear-and-tear rule, no fixture inventory. The pitch is move-in versus move-out. The code is one session, in memory, with a sentence about a wall.
5. Underneath those: `allowBackup="true"` sends the report PDF to Google Drive while the PDF prints "no data left this phone"; the square-footage figure is mask coverage times a hardcoded 12 sq ft and does not change with distance; the vision model has zero recorded metrics of any kind and zero hard negatives across all 2106 training images.
6. The deck's honesty framing is your greatest asset and, unfixed, your greatest liability. Slide 12 is titled "WORKING MVP, HONESTLY" and carries ✓ marks against GenieX and SHA-256, neither of which exists. Slide 1 still says `[Team name]`. Claiming honesty and then being caught is strictly worse than claiming nothing.
7. The counterweight is real and you should lead with it: the acoustic model reports its grouped cross-validated score *with its 95% interval*, never the training score, with a unit test that fails if anyone exports the training number by mistake. That is a level of discipline most submissions will not have. It is fitted on 15 taps from 8 WhatsApp voice notes, which is why fifty real taps tonight is the best two hours you can spend.
8. **The single highest-leverage fix: spend tonight making every document match the code, and ship SHA-256 in the PDF footer.** That is roughly six hours — the deck edits, the placeholder purge, `allowBackup="false"`, the permission deletions, the hash, the share intent. It adds almost no capability and removes every contradiction a judge can find in under a minute.
9. If you only get one hour: `allowBackup="false"`, fill the deck placeholders, delete "Everything you saw ran on the Hexagon NPU", un-tick slide 12's two false ✓, and strip the word "placeholder" from `HomeScreen.kt:43` and the IR finding string. Five edits, one hour, and the three questions most likely to end your Q&A no longer have an answer that ends it.
10. You are not short a feature. You are short the version of this pitch that is true — and that version, told well, still competes. Tell the true one.
