# JUDGE_QA.md — The 25 hardest questions

> **PATCHED 2026-09-12, after Budget 1 shipped** (see `FIXED.md`; baseline `281e35b` -> head `3a4af66`).
> Answers reflect the code **as it now stands**. Each question is tagged:
> **`[CHANGED]`** - the honest answer improved tonight.
> **`[UNCHANGED]`** - nothing shipped for this. **These are the ones you have to say out loud tomorrow.**
> Nothing here has been compiled - there is no JDK on the audit machine. If a commit fails to build and you revert it, revert its answer too.

**Three rules for the room:**
1. Never claim the NPU. It is disprovable in thirty seconds by anyone who opens `app/build.gradle.kts`.
2. When the honest answer is bad, say the bad thing *first*, then the reframe. Reversing that order reads as evasion and costs more than the defect.
3. Volunteer your two worst gaps before a judge finds them. A known weakness you named is engineering judgement. The same weakness found by a judge is a credibility failure.

---

## Hardware & on-device AI

### Q1. `[UNCHANGED]` "Which of your models runs on the Hexagon NPU? Show me the delegate."
**Honest answer — and it is still bad:** None of them. The app's only inference dependency is `org.pytorch:pytorch_android_lite:1.13.0` (`app/build.gradle.kts:81`), a CPU interpreter. There is no ExecuTorch runtime, no QNN delegate and no `.pte` in the APK.

**Reframe that is still true:** "The ExecuTorch INT8 lowering is done and in the repo — `ml/YOLOV8/qnn_android_bundle/yolov8n_seg_htp.pte`, 3.7 MB, produced by `convert_yolov8_seg_qnn_pte.py` through PT2E with `QnnQuantizer` 8a8w. What we didn't finish is linking the ExecuTorch AAR and the HTP delegate into the APK. So today all three run on the CPU, and I'd rather show you a compiled artifact and an honest gap than claim a delegate you can disprove by reading one Gradle line."

> ✅ **Done:** that line is gone from `PITCH_DECK_PROMPT.md` (`da552fd`), slide 8's "HEXAGON NPU (HTP)" lane is now "HOST CPU — MODELS", and slide 9's "Runs all three models" row says *"Not used."* The deck no longer contradicts you when you give this answer.

### Q2. `[UNCHANGED]` "What SoC did you compile the `.pte` for?"
**Honest answer:** SM8650 — Snapdragon 8 Gen 3. `run_qnn_conversion.sh:48` passes `--soc SM8650`, which is also the default at `convert_yolov8_seg_qnn_pte.py:86`, and `:170-173` silently falls back to SM8650 for any unrecognised chipset name. That is not the iQOO 15's 8 Elite Gen 5.

**Reframe:** "Wrong target, and I know why — SM8650 is the default in our own script and we never overrode it. The lowering path is right and it's a one-flag re-run once we have the correct `QcomChipset` enum. Worth adding: the bundle is also incomplete — our script's own `needed_libs` list at line 215 wants `libQnnHtpV75Skel.so` and `libqnn_executorch_backend.so` and neither got copied, so that `.pte` couldn't have executed on HTP even if we'd wired it."

*(Say the second half. A judge who finds the missing Skel library after you've claimed the bundle is ready is a judge you've lost.)*

### Q3. `[CHANGED again — you can now take the measurement in front of them]` "You claim <30 ms inference. Where's the measurement?"
**The app measures itself now (`84e4144`).** Home → **Device & Model Check** reports cold
load and warm inference (mean/min/max over 10 runs on a deterministic 640px bitmap) for both
the vision and acoustic paths, on whatever handset it is running on. Open it and read the
number off the screen.

> "Let me just take it. — That's cold load and that's the mean over ten runs on this handset.
> We don't quote a number we haven't measured, which is why the slide said 'not measured'
> until now."

**Superseded (do not use):** There is still no on-device measurement. What you now have is a bound: **21.8 ms per
image on a desktop Ryzen 9 CPU** via ultralytics (`METRICS.md`), on a model that is 11.3
GFLOPs. Say it exactly that way:

> "We haven't measured it on the handset — that's why the slide says 'not measured'. What I can tell you is the model is 11.3 GFLOPs and runs in 22 ms on a laptop CPU, so we're not worried about it; we just won't quote a number we haven't taken."

*(Do not use the pre-patch version of this answer — the deck no longer carries an unqualified `<30 ms`.)*

### Q4. `[UNCHANGED]` "So what does the iQOO 15 actually give you that a ₹15,000 phone doesn't?"
**Honest answer:** "Right now, honestly: the IR blaster. That's the one piece of this that genuinely doesn't work on most phones, and it's the reason this is an iQOO app rather than an Android app. Everything else — camera, mic, rotation vector — is commodity. The NPU story is where we *want* to be, not where we are."

*This is a better answer than a list. Claiming five hardware hooks you don't use loses more than claiming one you do.*

### Q5. `[CHANGED]` "Your deck says the Sensing Hub continuously listens. Show me that call."
**The deck no longer says that.** Slide 7 now reads *"No always-on listener — the Qualcomm
Sensing Hub audio path is not reachable from a third-party app"*, and slide 9's row is
retitled "Stereo mics". You get to make this point proactively instead of defensively, which
is the whole value of it.

**Honest answer if asked anyway:** There is no such call. Audio capture is a button press that opens a standard `AudioRecord` on `MediaRecorder.AudioSource.MIC` and blocks for 1.2 seconds (`AcousticTapClassifier.kt:54-76`, triggered at `WalkthroughScreen.kt:155`).

**Reframe:** "That claim is wrong and it's wrong in a way I should have caught: Qualcomm's always-on audio path isn't reachable from a non-privileged third-party app. There's no public API for it. So it isn't a thing we skipped, it's a thing we shouldn't have put on the slide."

*Saying "unimplementable, not just unimplemented" converts a false claim into demonstrated platform knowledge. It is the single best recovery available to you in this whole document.*

---

## The measurement

### Q6. `[UNCHANGED — and code and docs now disagree]` "I'll stand two metres back and scan the same crack. Does the square footage change?"
⚠️ The deck, README and `solution1.md` now say "share of the frame". **The app UI and the PDF
still print "sq ft".** That is the one place code and docs still disagree — close it if you
get 30 minutes, or get ahead of it verbally.

**Honest answer — still bad:** No. `WalkthroughScreen.kt:131` passes a hardcoded `frameWidthInches = 48f, frameHeightInches = 36f`, so `YoloSegDefectSegmenter.kt:59` computes `areaSqFt = maskCoverage × 12.0`. Distance is not an input. There is no `fx`, `fy` or `Z` anywhere in the Android app.

**Reframe:** "It's not a physical measurement today — it's the fraction of the frame the mask covers, scaled by an assumed 4 ft × 3 ft frame. The pinhole math on the slide is real and it's implemented in `ml/vision/predict.py`, but that's the Python path and it never made it into the app. What the phone gives you honestly is 'this defect covers ~6% of the frame', which still beats a WhatsApp photo, and the fix is reading `fx`/`fy` from `CameraCharacteristics` plus a distance input."

> **30 minutes, and the highest-value thing left:** relabel `areaSqFtEstimate` in the UI string at `WalkthroughScreen.kt` and in the PDF. The docs already say the right thing; only the app is lagging.

### Q7. `[CHANGED]` "Where does Z come from?"
**The ARCore line is gone from slides 6, 8 and 16** (`da552fd`). Slide 6 now states there is no depth sensor and no camera-intrinsics read on device.

**Honest answer if asked:** Nowhere. There is no ARCore dependency and no depth source of any kind in the app. That slide line is wrong.

**Reframe:** "No depth sensor, no ARCore. The ±10%/±20% figures on slide 6 describe a design we specified and didn't build. Today there's no Z at all."

### Q8. `[CHANGED]` "What's the mAP of the vision model?"
**You now have a number.** Test split, 107 images, 336 instances — full table in `METRICS.md`:

| | Mask AP50 | Box AP50 |
|---|---:|---:|
| **all** | **0.287** | 0.319 |
| spalling | 0.589 | 0.637 |
| crack | 0.249 | 0.316 |
| peeling | 0.244 | 0.259 |
| stain_mould | **0.065** | 0.065 |

Mask mAP50-95 0.148. Valid split agrees closely (0.267), which is what contamination looks
like, not what generalisation looks like.

**Say this:**
> "Mask mAP50 is 0.287, and I'd discount even that — our splits share video frames across train and test, so it's inflated. Three things the per-class numbers show: spalling works at 0.59, crack is weak at 0.25, and stain_mould is broken at 0.07 because we merged corrosion, damp and efflorescence into one class that isn't one thing. And ultralytics prints `0 backgrounds` when it scans our test set — there isn't a single clean image in the dataset, so none of this measures false positives on a clean wall, which is the number that actually matters for this product."

That answer scores better than 0.287 does. The deck's old "Target metrics: 75%+ mAP" line is
gone (`da552fd`) — nothing would have supported it.

### Q9. `[CHANGED]` "How many images, and where did they come from?"
**Honest answer:** 2106 total — 1796 train / 203 val / 107 test in `YOLOV8/unified_defects/`. Four Roboflow Universe datasets merged and remapped to four classes by `merge_and_zip_datasets.py`: `infrastructure-monitoring` (713 train), `structural-defects` (854 — 48% of the set), `building-anomalies` (229). All CC BY 4.0.

**Say the caveat yourself:** "The domain match to an Indian rental flat interior is weak. Dataset 1 is substantially DJI drone frames of building exteriors — you can see it in the filenames, `d1_DJI_0012_MP4-0017`. Dataset 2 is structural crack imagery. None of it is a phone camera inside a 2BHK under tubelight. That's the gap between the model and the pitch, and closing it is ~200 labelled indoor frames."

✅ **Done:** slide 6 now reads "2,106 labelled defect images (1,796 train / 203 val / 107 test), merged from four CC BY 4.0 Roboflow datasets. Classes: crack, peeling, spalling, stain_mould", and slide 17 carries the CC BY 4.0 credit the product-facing docs never had.

### Q10. `[UNCHANGED — and deliberately so]` "Point it at that wall. What does it see?" *(a clean conference-room wall)*

> **Threshold calibration was started and NOT shipped.** The recall half of the sweep is in
> `METRICS.md` ("Operating point, measured — PART 1 of 2"): raising `SCORE_THRESHOLD` from
> 0.25 to 0.40 costs 18% of relative recall. The false-positive half needs photographs of
> clean surfaces, which do not exist yet, so there is no measured benefit to weigh that
> against. Shipping a threshold change on half the evidence would be guessing with extra
> steps. The answer below stands unchanged.
**Honest answer — and this is the one that can end the demo:** Possibly a false positive. Across all 2106 training images there is **not one empty label file** — every single image contains at least one defect. The merge script drops any image whose labels don't map (`merge_and_zip_datasets.py:100`), which removed every potential background frame. The detector has never seen a clean wall.

**Reframe, delivered *before* they point the phone:** "I'll pre-empt this — we have zero hard negatives. Every training image contains damage, so the model's prior is 'something is here'. I expect false positives on grout lines, shadows, cables and sockets. And there's a taxonomy problem underneath it: our merge maps 'exposed brickwork' to spalling, so an intentional feature wall gets reported as 'surface breaking away'. The fix is 200 clean-wall backgrounds and a taxonomy pass, and it's the first thing on our list."

> **Do this before judging:** scan the actual wall you will demo on and know the answer. Then either demo on a wall you've verified, or open with the sentence above.

### Q11. `[UNCHANGED]` "What will this trip on in a real room?"

> Still the list below, still unmeasured. `VENUE_RUNBOOK.md` step 6 is 10 minutes of walking
> the judging area with **Capture + Analyze** and writing down what fires. Do that and this
> answer becomes specific instead of general.
**Answer — give the list, unprompted. It reads as rigour:**
grout lines → `crack` · shadows in corners → `stain_mould` · exposed brick → `spalling` (guaranteed by the class mapping) · cables and conduit → `crack` · switch plates and sockets → `spalling` · patterned or terrazzo tiles → `crack`+`spalling` · wood grain on doors and frames → `crack` · rusted window grilles → `stain_mould` (corrosion was folded into that class) · textured putty finish → `peeling` · wall art and posters → `peeling`/`stain_mould`.

"No hard negatives in the training set, so none of these has a counter-example. The score threshold is 0.25, which makes it worse."

### Q12. `[CHANGED]` "Your slide says confidence under 0.30 is hidden and users can dismiss a detection."
**The slide no longer says that.** Slide 6 and the slide-16 Q&A row now read: *"Detections
below 0.25 confidence are discarded. Every surviving detection is shown with its confidence
and labelled with the segmenter that produced it."* Which is what the code does.

**Honest answer if asked about hiding or dismissing:** Neither is implemented. `YoloSegConfig.kt:47` sets `SCORE_THRESHOLD = 0.25f` — *below* the claimed hide threshold — and `WalkthroughScreen.kt:135-142` logs every detection with its raw confidence. There is no review band and no dismiss control in the UI.

**Reframe:** "That's a designed behaviour on a slide, not shipped code. What does ship is the confidence printed next to every finding, so nothing is hidden from the user either."

---

## The report

### Q13. `[CHANGED — now one of your better answers]` "What makes this report tamper-evident?"
**Shipped tonight (`f5bd2e0`).** Every page of the PDF, and the report screen, carries a
SHA-256 over the canonical findings.

> "Every page carries a SHA-256 over exactly these findings. Change one character of one finding and the digest stops matching, so a regenerated report is detectably a different report. I want to be precise about what that is and isn't: it proves the findings are unaltered. It does *not* identify who recorded them, and the timestamp is the device clock, which a user can move. That's why the slide says SHA-256 and not 'signed'."

**And now they can check it themselves (`a38a65d`).** The report screen and the last PDF
page carry the digest as a **QR**. Hand them your phone, or let them scan your screen with
theirs — stock camera app, no install, no network. It reads back the 64-character hex.
Then change one finding, regenerate, and ask them to scan again: different code.

> "Don't take my word for it. Scan that with your own phone. Now watch — I'll add one
> finding and regenerate. Scan it again."

That is the only claim in this submission a judge can verify with their own hands, which is
worth more than one they have to believe.

If they push on the construction — and this half is worth volunteering:

> "The canonical form length-prefixes every field rather than tab-separating them. With a delimiter, a finding labelled `crack<TAB>INFO` and one labelled `crack` with detail `INFO<TAB>{}` serialise identically and get the same digest — a way to alter a report without changing its fingerprint. We found it by porting the algorithm to Python and running the test assertions against it. Both collision cases are regression tests now."

**Superseded (do not use):** Nothing. `ReportGenerator.kt:19-22` says so in its own doc comment: *"'signed' here means a visible timestamp + session ID baked into the document, not a cryptographic signature."* There is no `MessageDigest`, no `KeyStore`, no `Signature` anywhere in the app.

**Reframe:** "It isn't. 'Signed' in our code means a timestamp and a session ID printed on the page, and the comment in `ReportGenerator` says exactly that — we wrote it down rather than hiding it. Tamper-evidence is a SHA-256 over the canonical findings printed in the footer, which is about an hour of work, and it's the top item on our list."

> Slide 12 now shows `✓ SHA-256 of findings on every page · NEXT: dual signature` — a real tick, with an honest one beside it.

### Q14. `[UNCHANGED]` "Where do both parties sign?"
**Honest answer — still bad:** Nowhere. There is no signature-capture surface in the app. The button reads "Generate Signed Report" (`WalkthroughScreen.kt:215`) and nothing is signed.

**Reframe:** "They don't. And I'd rather explain why that ordering matters than hand-wave: a signature above unhashed text binds nothing. The right build is hash the findings, show the hash to both parties, then capture two signatures below it. We have the report pipeline; we don't have either half of that yet."

### Q15. `[CHANGED]` "Send me the PDF."
**Shipped (`ad2840e`).** Tap "Share report (PDF)" — a `FileProvider` share sheet scoped to
`filesDir/reports/`, with the digest in the message body so a recipient can compare it
without opening the file. Do it rather than answer it.

Since `a38a65d` there is a second path that needs no transfer at all: the last PDF page and
the report screen both carry the digest as a **QR**, so they can hold the commitment on their
own phone in two seconds without you sending them anything.

**Superseded (do not use):** You can't. `ReportGenerator.kt:120` writes to `context.filesDir` — private internal storage — and there is no `FileProvider` in the manifest and no share intent anywhere in the app. The PDF is real, correctly rendered, and unreachable.

*(This was the worst answer in the original audit. It is now a demo beat.)*

### Q16. `[UNCHANGED]` "Does an LLM write the narrative?"
**Honest answer — still no.** `ReportGenerator.kt:66-75` is string templating: `"$count finding(s) recorded. Items: $labels. Severity breakdown: $severities."` There is no GenieX, no Llama, no on-device LLM of any kind in the dependency graph.

**Reframe:** "It's rule-based templating. Slide 12 lists it under NEXT, not under what shipped — we moved that tick tonight. What's real is that the report pipeline runs end to end today, and swapping the body of that one function is the entire integration."

### Q17. `[CHANGED AGAIN — the clean yes is gone, and the honest answer is narrower]` "Your app says data never leaves the phone. Is that true?"
**Changed by the backend sync feature (2026-09-12).** The app now declares `INTERNET` and
`ACCESS_NETWORK_STATE` for real, because the product owner decided it should sync signed
reports to a backend (`com.smartlease.edge.sync`, server in `server/`). Sync is **off by
default**, but the permission is genuinely there and a judge can see it.

> "Not as a blanket statement, so let me give you the precise version. The inspection never touches the network — capture, the vision model, the tap test, the costing, the PDF and its SHA-256 all run on the handset, and it works in a basement. Photos, video and audio never leave the phone at all, and that one is structural rather than a promise: the sync wire types have no field that can hold image bytes, the server schema has no binary column, and the API rejects unknown fields outright, so media cannot be uploaded even by accident. What can leave — only if you switch sync on — is a 64-character digest plus the rupee totals, so both sides can hold the same tamper-evident record. `allowBackup` is false and every backup domain is excluded, so nothing reaches Google Drive either."

**If asked to prove it, the demo changed.** It used to be "look, no INTERNET permission in
`dumpsys package`" — that proof is retired and must not be used. The replacement is to show
`ReportUploadRequest.kt` (no media field exists on the wire type) and `server/app/storage/db.py`
(no binary column), then POST a payload with an extra `photoBase64` field and let the server
reject it with `422 Extra inputs are not permitted`. That demonstrates a property of the
system rather than an absence you have to take on trust.

**Superseded (do not use) — the previous "clean yes":** *"Yes, and it's checkable three ways: no
INTERNET permission in the manifest, allowBackup is false, and every backup domain is
excluded..."* The first of those three is no longer true. Before the sync feature it was
verified twice on 2026-09-12 (zero `INTERNET` entries in the merged manifest; `dumpsys package`
on the installed build listed only `TRANSMIT_IR`, `CAMERA`, `RECORD_AUDIO` and an internal
broadcast permission), so it was a fair claim at the time — it simply stopped being one.

**The PDF wording changed with it, in three places.** Page one used to print *"Generated fully
offline, on-device — no data left this phone."*; it now prints *"Captured and analysed on-device.
Photos, video and audio never leave this phone."* — true under every build configuration. The
Section 63 certificate's "Manner of production" line used to end *"; no network transmission at
any point."* and the attestation paragraph justified skipping Play Integrity *"since it declares
no INTERNET permission"*; both were true before sync and false after it. They are now written by
`ReportGenerator.mannerOfProduction()` and `attestationProvenanceNote()`, which state what the
running build actually does, and `ReportProvenanceClaimsDeviceTest` fails the build if either
sentence ever denies the INTERNET permission again. A stale claim printed on the tenant-facing
evidence artefact is the last place one should be allowed to survive — and on a document offered
as a Section 63 certificate it is a false statement about the provenance of evidence, not a
marketing overreach.

**If a judge asks what the certificate says now:** with sync unconfigured (the demo build) it
reads *"Captured on-device (camera, microphone, IR emitter, motion sensors) and recorded to local
app storage. Sync is not configured in this build, so nothing was transmitted."* With sync on it
names what leaves — the findings digest and the rupee counters — and states that the media is not
uploaded and cannot be.

**Superseded (do not use):** No. `AndroidManifest.xml:23` sets `allowBackup="true"` and the backup rules exclude only `smartlease.db`. The generated PDF sits in `filesDir` and is inside Google Auto Backup's scope — so it goes to the user's Google Drive. The PDF prints *"Generated fully offline, on-device — no data left this phone."* on page one.

**Superseded (do not use) — reframe from the `allowBackup` era:** *"The manifest declares no
INTERNET permission, which is true and which is what we meant. But 'no INTERNET permission' and
'data never leaves the device' aren't the same statement..."* Both halves of that are now stale:
`allowBackup` was fixed in `ce9989f`, and the manifest does declare INTERNET as of the sync
feature. Saying the first sentence on stage hands a judge a ten-second disproof (`dumpsys
package com.smartlease.edge`). Use the live answer at the top of Q17.

> **Also superseded:** *"The PDF still prints 'Generated fully offline, on-device — no data left
> this phone.' on page one."* It does not; see the PDF wording paragraph above.

### Q18. `[CHANGED]` "Why does an offline app want my precise location?"
**It no longer asks (`a2ad9ae`).** `ACCESS_FINE_LOCATION` and `VIBRATE` are gone from the
manifest and from the launch request. The runtime permissions the app asks a user for are CAMERA
and RECORD_AUDIO — it also declares TRANSMIT_IR, and (since the sync feature) INTERNET and
ACCESS_NETWORK_STATE, none of which prompt. Do not say "CAMERA and RECORD_AUDIO, nothing else";
that was true before sync and a judge reading the manifest will see otherwise. If asked why
location and vibrate were ever there:

> "They were declared for features we never built — a geotagged report and haptic feedback. Neither was used, and on a pitch built around privacy they shouldn't have been in the manifest, so we removed them."

**Superseded (do not use):** It doesn't use it. `ACCESS_FINE_LOCATION` is declared (`AndroidManifest.xml:17`) and requested at launch (`MainActivity.kt:37`), but `latitude`/`longitude` on `InspectionEntity` are never set by any code path. `VIBRATE` is declared and never used either.

**Reframe:** "Both are leftovers from a design where the report carried a geotag. Neither is used, and on a pitch built around privacy they shouldn't be in the manifest. Deleting them is two lines."

---

## The problem statement

### Q19. `[UNCHANGED — the biggest one]` "Landlord inspects at move-in. Twelve months later the tenant moves out. Walk me through the comparison."
**Honest answer — and this is the deepest gap in the submission:** There is no comparison. The move-in baseline is two floats — pitch and roll — held in memory and destroyed when the screen is disposed (`ArAlignmentTracker.kt:49-52`, `WalkthroughScreen.kt:66`). It is never written to the database. No photo is ever saved. There is no diff engine, no wear-and-tear rule, no fixture inventory, and no way to move a baseline from the landlord's phone to the tenant's — no share intent, no Bluetooth, no Wi-Fi Direct, no NFC, no QR.

**Reframe — use this, and use it early rather than being walked into it:** "I'll be straight: what we built is not a move-in/move-out diff. It's a single-session condition record — 60 seconds, timestamped, offline, multi-modal — and that on its own solves the thing every one of those disputes actually starts from, which is that neither party has an objective record of day one. The diff, the baseline handoff and the wear-and-tear policy are V2 and they're genuinely hard, especially the offline phone-to-phone transport. I'd rather show you one flow that works than four that half-work."

> **Change the deck to match.** If slide 5's demo script implies a comparison you cannot run, you will be asked to run it.

### Q20. `[UNCHANGED]` "How does the baseline get from the landlord's phone to the tenant's phone with no internet?"
**Honest answer — bad:** It doesn't. There is no transport of any kind in the codebase.

**Reframe:** "Not built. And it's the interesting problem in this space, not a checkbox — offline, two devices, mutual distrust, needs to be verifiable by both. The shape I'd build is a signed bundle over Wi-Fi Direct or a QR-chained hash, and the reason I'm not hand-waving it is that getting it right is most of the product."

### Q21. `[UNCHANGED]` "Where do you distinguish fair wear and tear from tenant damage?"
**Honest answer — still bad:** Nowhere. One nuance did change: a visual defect is now filed
`NOTABLE` rather than `INFO` (`9e5c814`), so the severity column is no longer decorative. But
that is a defect/not-defect distinction, not a wear-and-tear one. The only severity logic is a nine-keyword hazard list (`SafetyGate.kt:21-25`) for exposed wires, leaks, gas and smoke. Every visual defect is filed as `INFO` — `Severity.NOTABLE` exists in the enum and is unreachable by any code path.

**Reframe:** "Not modelled. And it's the judgement call the whole dispute turns on, which is exactly why I'd want it to be a stated, auditable policy — age of tenancy, defect class, area band — rather than something a model infers. It's a rules table we haven't written."

### Q22. `[UNCHANGED]` "Where are missing fixtures inventoried — the fan, the tubelight, the geyser, the tap?"
**Honest answer — bad:** Nowhere. The four vision classes are `crack`, `peeling`, `spalling`, `stain_mould` — surface defects only. There is no fixture concept in the data model at all.

**Reframe:** "Absent. Our detector answers 'what's wrong with this surface', not 'what's missing from this room', and those are different problems — the second one needs a room checklist and a presence/absence model, not a defect segmenter. The IR check is the one place we touch function rather than appearance, and it only covers appliances with a remote."

### Q23. `[UNCHANGED]` "How does the IR test prove the AC works?"
**Honest answer — still bad:** It doesn't. `IrController.kt:11-13` states it in the source: *"There is no public Android API to receive/decode an arbitrary IR signal (ConsumerIrManager is transmit-only)."* Success is logged purely because `transmit()` returned without throwing (`IrController.kt:43-44`). There is no feedback channel — no mic listen-back, no thermal read, no current sense. **The feature proves only that the phone's emitter fired.** It cannot distinguish a working AC from an empty room. The pattern shipped in `WalkthroughScreen.kt:174` is a 6-element placeholder, and the finding string written into the report literally reads *"placeholder pattern, replace with Day 12 capture"*.

**Reframe:** "Today it proves the emitter fired and nothing more. The loop isn't closed. We know exactly how to close it — a 3-second mic listen for the compressor's ~50-60 Hz start signature is well within what `AcousticTapClassifier` already does — and it's the honest gap between 'we sent a command' and 'the appliance works'."

> ✅ **Done:** the report string now reads *"IR command transmitted — Voltas, 38 kHz (pattern not verified against this unit)"* (`b44f14f`). The word "placeholder" no longer reaches the DB or the PDF.
> ⚠️ **Still outstanding:** capturing the venue AC's real burst. Needs an external IR receiver; `ConsumerIrManager` cannot record.

---

## Engineering & product

### Q24. `[CHANGED — better]` "What's actually tested?"
**Four JVM test files, 36 test methods** (was two files, 15). Both new ones cover things that
fail *silently* rather than loudly. `YoloSegDecoderTest.kt` hand-computes letterbox padding, mask-derived coverage, NMS and IoU — because a box that forgets padding still looks plausible, and area from the bounding box instead of the mask overstates a diagonal crack several times over. `AcousticFeatureExtractorTest.kt` checks all 36 Kotlin features and the final probability against golden vectors emitted by the Python trainer, so train/serve skew fails the build instead of silently degrading accuracy.

> "`YoloSegDecoderTest` hand-computes letterbox padding and mask-derived coverage, because a box that forgets padding still looks plausible and area from the bounding box overstates a diagonal crack several times over. `AcousticFeatureExtractorTest` checks all 36 Kotlin features against golden vectors from the Python trainer, so train/serve skew fails the build. `FindingsDigestTest`, 13 cases, pins the digest: same findings same hash, one character different hash, row order irrelevant, and two field-boundary forgery cases. `SafetyGateTest`, 8 cases, including that the gate can never return a severity *below* STOP_ESCALATE — the direction that would let a model suppress a hazard."

Still uncovered: no instrumented tests, no UI tests, no `ReportGenerator` render test.

### Q25. `[CHANGED]` "What's the one thing you'd fix first, and what would you never ship?"
**The answer that wins this question:**

"First fix: `allowBackup="false"`. One line, and it's the difference between our privacy claim being true and being wrong — right now the report PDF is in Google Auto Backup's scope while the PDF itself prints 'no data left this phone'. I'd rather tell you that than have you find it.

Never ship: the square-footage number. It's presented as a physical measurement and it isn't one — there's no focal length and no depth in the app, so it's mask coverage times a hardcoded 12 square feet. Charging a tenant against that number would be indefensible, so it doesn't go to a user until the pinhole math is actually reading `CameraCharacteristics`.

And the thing I'd point you at that I *am* confident in: the acoustic classifier. It's grouped-cross-validated by source recording so sibling taps can't leak, the app quotes the cross-validated score with its 95% interval rather than the training score, and there's a unit test that fails if anyone exports the training number by mistake. It's 80% on fifteen taps with a 55-93% interval against a 53% baseline — the bottom of that interval is chance, so it is not a result yet, and we say so in the UI. That's the standard I'd want the rest of it held to."

---

## Pre-flight checklist — updated after Budget 1

### ✅ Done (commits in `FIXED.md`)

| | Item | Commit |
|---|---|---|
| ☑ | `allowBackup="false"`, every backup domain excluded (Q17) | `ce9989f` |
| ☑ | *"Everything you saw ran on the Hexagon NPU"* deleted from the script (Q1) | `da552fd` |
| ☑ | Slide 12: GenieX → `NEXT:`, SHA-256 → a real `✓` (Q13, Q16) | `da552fd`, `698d11c` |
| ☑ | "Placeholder / Days 6-8" card gone from the home screen | `b44f14f` |
| ☑ | "placeholder pattern, replace with Day 12 capture" out of the IR string (Q23) | `b44f14f` |
| ☑ | Capture button gated on camera permission — no longer crashes (Q5 of `AUDIT_GAPS`) | `0dc402c` |
| ☑ | `yolo segment val` run; mask mAP50 0.287 with the leakage caveat (Q8) | `METRICS.md` |
| ☑ | Slide 6: 2,106 images, correct 4 classes (Q9) | `da552fd` |
| ☑ | Slide 7: "15 taps from 8 recordings", CI kept (Q&A backup) | `da552fd` |
| ☑ | SHA-256 on every PDF page (Q13) · share sheet (Q15) · no UI freeze | `f5bd2e0`, `ad2840e`, `78fc6e7` |

### ☐ Still to do — by you, tomorrow morning

| | Item | Time |
|---|---|---|
| ☐ | **Build it.** Nothing above has been compiled — no JDK on the audit machine. `./gradlew :app:assembleDebug` then `:app:testDebugUnitTest` (36 tests) | 30 min |
| ☐ | **Fill 6 deck placeholders**: `[Team name]`, `[Student / Professional bucket]` (slide 1), `[Member 1-3]` (slides 1, 14), `[email]` (slide 15) | 20 min |
| ☐ | Replace the slide 1 and 4 mockup images with real screenshots | 20 min |
| ☐ | Scan the wall you will demo on. Know what it detects before a judge does (Q10) | 15 min |
| ☐ | Relabel "sq ft" → "% of frame" in the app UI and PDF — the one place code and docs still disagree (Q6) | 30 min |
| ☐ | Capture the venue AC's real IR burst, if you have a receiver (Q23) | — |
| ☐ | Decide who says the Q19 reframe, and say it in the first 60 seconds | — |

### The three you must still say out loud

1. **Q19** — there is no move-in ↔ move-out comparison. Lead with the single-session reframe.
2. **Q1/Q2** — nothing runs on the NPU, and the `.pte` was lowered for the wrong SoC.
3. **Q10/Q11** — zero hard negatives; expect false positives on grout, shadows and sockets.
