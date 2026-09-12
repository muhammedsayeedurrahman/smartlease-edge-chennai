# HackTracker, Thermal & Judge-Defense Strategy — SmartLease Edge

iQOO Hackathon 2026 · Chennai City Battle · Sep 12–13 2026 · written mid-event

Companions, both of which this document builds on and must not contradict:
`docs/BATTLE_PLAN.md` and `docs/research/IQOO_CHENNAI_2026_RESEARCH_LOG.md`.

**Labelling discipline used throughout.** Every substantive claim carries one of:

- **[VERIFIED]** — official documentation, or checked directly against this repository's files during the writing of this document.
- **[FIRST-PARTY, UNVERIFIED BY ME]** — supplied by you from the official site or the organiser briefing. I could not independently re-read it (see §0).
- **[INFERENCE]** — technical reasoning from verified facts. Not an official rule.
- **[SPECULATIVE]** — plausible, unsupported. Do not act on it alone.
- **[UNKNOWN]** — not publicly disclosed, and I will not guess.

---

## 0. A disclosure that has to come first

I attempted to fetch `https://iqoo.reskilll.com/guide` and `https://iqoo.reskilll.com/` while writing this. **Both returned no substantive content** — the guide page yielded only the title "iQOO Hackathon 2026 · City Battles · 4 Cities. 1 Grand Finale.", and the homepage returned nothing about HackTracker, scoring, Office Kit, or red/green light. This is the expected behaviour of a client-rendered single-page app against a text fetcher; it is **not** evidence the rules changed.

**Consequence, stated plainly:** every quotation of official scoring and HackTracker behaviour in this document is **[FIRST-PARTY, UNVERIFIED BY ME]** — it comes from your own reading of the site, which I treat as authoritative per your instruction, but which I did not confirm with my own eyes. If any of it is misremembered, this document inherits the error. **Before the Round-1 checkpoint, open the guide on a phone and re-read the scoring section yourself.** It takes ninety seconds and it is the cheapest risk reduction available today.

---

## 1. HACKTRACKER FACT SHEET

### 1.1 What is confirmed

| # | Statement | Label |
|---|---|---|
| F1 | Scoring weights: End Product 30 / Novelty & Impact 20 / HackTracker–Creative Phone Use 15 / Technical Depth 15 / HackTracker–Office Kit 10 / Demo & Presentation 10 | [FIRST-PARTY, UNVERIFIED BY ME] |
| F2 | Therefore 75% jury-scored, 25% telemetry-derived | [VERIFIED] arithmetic on F1 |
| F3 | HackTracker captures **counts and durations** for creative phone use and Office Kit usage | [FIRST-PARTY, UNVERIFIED BY ME] |
| F4 | HackTracker explicitly does **not** capture keystrokes, screenshots, or browsing | [FIRST-PARTY, UNVERIFIED BY ME] |
| F5 | "Creative phone use" is described as including camera, voice, on-device AI | [FIRST-PARTY, UNVERIFIED BY ME] |
| F6 | "Office Kit" is described as including screen mirroring, clipboard, file transfer, remote control | [FIRST-PARTY, UNVERIFIED BY ME] |
| F7 | Red Light ≈ phone only, laptop restricted to build machine, Office Kit is the phone↔laptop route; Green Light ≈ phone + laptop; roughly 55% red / 45% green of pure build time | [FIRST-PARTY, UNVERIFIED BY ME] |
| F8 | Organisers stated verbally: telemetry can shift ranking by several positions; local models/LLMs strongly encouraged; direct use of phone silicon is valuable; cloud APIs allowed; open models and open-source libraries allowed; **original work built on top of those is what is judged** | [FIRST-PARTY, UNVERIFIED BY ME — organiser briefing] |

### 1.2 What is NOT disclosed — and which I refuse to invent

Every one of the following is **[UNKNOWN / NOT PUBLICLY DISCLOSED]**. Anyone who hands you a number for any of these is making it up.

- The scoring function mapping counts and durations to the 15 and 10 points.
- Whether counts and durations are weighted equally, or one dominates.
- Whether there is a saturation ceiling, a diminishing-returns curve, or a linear map.
- Whether scores are absolute or normalised against other teams at the venue.
- Whether per-team telemetry is summed across members' devices, averaged, or taken from a single registered device.
- Whether each loaner phone carries its own HackTracker identity, and how those roll up to a team.
- Which specific Android signals the package reads (foreground app, camera-session open, audio-record session, NNAPI/QNN dispatch, file-transfer byte counts — all unknown).
- Whether the package can observe NPU utilisation at all.
- **Whether temperature, thermal state, battery drain or CPU load are captured in any form.** I found no official statement of this. Do not claim thermal is scored.
- Whether idle mirroring time counts the same as active mirroring time.
- Any threshold, target, or "good" value for any metric.

### 1.3 The single most important structural read

**[INFERENCE]** Twenty-five points accrue from *how you work*, not from *what you argue*. That is the cheapest quarter of the scoreboard in the entire event, and the only quarter that cannot be recovered at hour 29. The correct posture is therefore not cleverness but **discipline from now onward**: mirror on, build on the phone where feasible, exercise camera / microphone / IR in every working session because the product genuinely requires it.

**[INFERENCE]** Because F3 says *counts and durations* and F4 explicitly disclaims content capture, the package is almost certainly reading coarse OS-level session and app-usage signals, not semantic ones. It very likely cannot tell a real inference run from a fake one. **That is precisely why gaming it is both tempting and wrong** — see §16. The defensible position is that SmartLease Edge genuinely needs the camera, the microphone, the IR emitter and on-device compute, so honest work produces the signal anyway.

---

## 2. VERIFIED VS INFERRED TELEMETRY TABLE

Column *Confidence* is about whether HackTracker observes the signal, **not** whether the app produces the activity.

| Activity | Does SmartLease genuinely do it? | Likely observable signal | Confidence it is captured | Reasoning |
|---|---|---|---|---|
| Camera capture sessions | **Yes** — CameraX 1.4.1 wired, `CameraController.kt` [VERIFIED] | Camera session open/close, duration | **Strongly inferred** | F5 names camera explicitly |
| Microphone / audio capture | **Yes** — `RECORD_AUDIO` in manifest, `TapCaptureStore.kt`, tap capture screen [VERIFIED] | Audio-record session count + duration | **Strongly inferred** | F5 names voice; microphone is the nearest analogue |
| "Voice" specifically (speech input) | **No** — no speech recognition anywhere in the tree [VERIFIED] | — | n/a | Do not claim voice. You do acoustic sensing, which is a different thing, and saying so accurately is better |
| On-device AI inference | **Yes** — PyTorch Mobile Lite segmenter + on-device logistic-regression tap classifier + ML Kit OCR [VERIFIED] | App foreground time; possibly NNAPI/ML Kit hooks | **Speculative** for direct inference counting; **strongly inferred** for the app-usage time it produces | F5 names "on-device AI" but discloses no mechanism |
| NPU / Hexagon utilisation | **No — not in the shipped app** [VERIFIED, see §7] | — | **Unknown** | The `.pte` + `libQnnHtp.so` live in `ml/YOLOV8/qnn_android_bundle/` only. Not in `app/src/main/assets/`. No ExecuTorch dependency in `gradle/libs.versions.toml` |
| GPU workload | **Unconfirmed** | — | **Unknown** | PyTorch Lite *may* dispatch some ops off-CPU; this is not confirmed and must not be claimed |
| Sensor use (rotation vector) | **Yes** — `ArAlignmentTracker.kt`, SensorManager rotation vector [VERIFIED] | Sensor listener registration | **Speculative** | No official mention of sensors |
| IR emitter transmit | **Yes** — `IrController.kt`, `ConsumerIrManager` [VERIFIED]; iQOO 15 IR blaster confirmed 2-0 in research log | Possibly none; IR is an unusual API to instrument | **Speculative** | Strong *judge* value regardless of telemetry value |
| App foreground / usage duration | **Yes** | Per-app usage time | **Strongly inferred** | The single most likely thing any usage-stats package reads |
| Office Kit screen mirroring | Available | Session count + duration | **Confirmed category** (F6) | Named explicitly |
| Office Kit file transfer | Available and genuinely needed (APK push, model files, PDFs back) | Transfer count, possibly bytes | **Confirmed category** (F6) | Named explicitly |
| Office Kit clipboard | Available and genuinely needed | Clipboard sync events | **Confirmed category** (F6) | Named explicitly |
| Office Kit remote control | Available | Session duration | **Confirmed category** (F6) | Named explicitly |
| Sustained compute / thermal load | Happens naturally during testing | — | **Unknown — assume NOT captured** | No official statement. See §5 |
| Battery drain | Happens naturally | — | **Unknown — assume NOT captured** | No official statement |
| Temperature / thermal throttling | Happens naturally | — | **Unknown — assume NOT captured** | **Do not claim thermal is scored** |
| Keystrokes, screenshots, browsing | — | — | **Explicitly excluded** (F4) | Stated |

**Operating rule derived from this table [INFERENCE]:** optimise hard for the three rows marked *Confirmed category* or *Strongly inferred* — camera, microphone, app foreground time, and all four Office Kit modes. Treat everything marked *Speculative* or *Unknown* as worth doing **for product and judge reasons only**, and score it at zero for telemetry purposes in your own planning. If it turns out to count, that is upside you did not depend on.

---

## 3. SMARTLEASE PHONE-USAGE MATRIX

Every row below is an activity the product **actually requires**. None is invented to generate telemetry. Items marked *aspirational* are not in the shipped app today and are listed only for completeness — see §7 and §11 for why most should be cut today.

| # | Activity | Why SmartLease needs it | Phone hardware | Likely HackTracker signal | Judging criterion served | Demo value | Technical value |
|---|---|---|---|---|---|---|---|
| 1 | Move-in baseline capture, room by room | The entire thesis is *comparison between two moments*. Without a baseline there is no product | Rear camera, ISP, storage | Camera session count + duration [strongly inferred] | End Product 30, Phone Use 15 | **High** — it is the opening beat of the demo | Establishes the paired-capture data model |
| 2 | Viewpoint re-alignment at move-out | A change claim is worthless if the two photos were taken from different places | Accelerometer + magnetometer via rotation vector (`ArAlignmentTracker.kt`) | Sensor registration [speculative]; camera duration [strongly inferred] | Technical Depth 15, Novelty 20 | **High** — visibly non-trivial | Genuine original engineering; no ARCore involved |
| 3 | On-device defect segmentation | The detection step. Runs with no network permission at all | CPU via PyTorch Mobile Lite | App foreground time [strongly inferred]; inference itself [speculative] | End Product 30, Technical Depth 15 | Medium — model is weak (§10) | Real, but be honest about mAP50 0.249 |
| 4 | Acoustic knuckle-tap verification | Vision cannot tell a hairline crack from a hollow debonded tile. Sound can | Microphone, 16 kHz capture | Audio session count + duration [strongly inferred] | **Novelty 20**, Phone Use 15 | **Very high** — nobody else will do this | Real, trained, wired. Small sample — say so |
| 5 | OCR of the lease / meter readings | Deposit amount and meter values enter the record without typing | Camera + ML Kit on-device text recognition | Camera duration; app time | End Product 30 | Medium | On-device by default — supports the offline claim |
| 6 | IR appliance functional check | **The differentiator.** Every competitor documents how a flat *looks*. You test whether the AC *works* | **IR emitter** (`ConsumerIrManager`, 38 kHz) | Likely none [speculative] | **Novelty 20**, Phone Use 15, Demo 10 | **Highest in the deck** | Hardware confirmed present on iQOO 15 (2-0) |
| 7 | Deterministic repair pricing | Converts findings into the rupee figure that ends the demo | CPU only | App foreground time | **End Product 30**, Technical Depth 15 | **Highest** — it is the punchline | Original IP. Confidence floor at 0.60 |
| 8 | Safety escalation gate | A hazard must never be silently priced as cosmetic | CPU only | App time | Technical Depth 15, Novelty 20 | Medium | Original IP; deterministic, non-model |
| 9 | Offline PDF generation + SHA-256 fingerprint | The artefact both parties keep | CPU, storage | App time | End Product 30 | **High** | Real. Integrity digest, **not** a signature |
| 10 | Room-persisted property timeline | Multiple inspections of the same unit over time | Storage | App time | Novelty 20 | Medium | Real |
| 11 | On-device self-test / capability probe | `SelfTest.kt` already enumerates SOC model, ABIs, IR carrier frequencies, per-camera focal lengths [VERIFIED] | All of the above | App time | **Technical Depth 15** | **Underrated — use it** | See §5 and §8 |
| 12 | *Local LLM report narration* | *aspirational* | *would be NPU/CPU* | — | — | — | **Not built. Cut it today.** See §6 |
| 13 | *NPU/HTP delegate inference* | *aspirational* | *Hexagon* | — | — | — | **Not built. Cut it today.** See §7 |

**The honest summary of this matrix:** rows 1, 2, 4, 6, 7, 9 and 11 are, together, a complete and genuinely phone-native product. They also happen to exercise camera, microphone, sensors, IR and sustained on-device compute. **You do not need to do anything artificial to generate telemetry. You need to use your own app, repeatedly, for real work, starting now.**

---

## 4. OFFICE KIT USAGE STRATEGY

### 4.1 Per-mode analysis

| Mode | Dev-workflow contribution | Legitimate SmartLease use case | Counts or duration likely relevant? | Assessment |
|---|---|---|---|---|
| **Screen mirroring** | See the phone UI on the laptop while editing Compose code; watch a live camera preview while reading logs | Reviewing the walkthrough UI and PDF output during iteration; **driving the judge demo on a big screen** | Both plausible; **duration is the obvious one** [INFERENCE] | **Highest-value mode.** Turn it on at the start of every working session and leave it on. This is honest — you genuinely benefit from seeing the phone while you work |
| **File transfer** | Push APKs to the phone, pull generated PDFs and captured tap WAVs back to the laptop for analysis | Every build cycle; every acoustic sample you record; every report you inspect | **Counts almost certainly**; bytes possibly [INFERENCE] | **Second-highest.** Route every artefact through it rather than USB/cloud. Genuinely more convenient under Red Light anyway |
| **Clipboard** | Move a stack trace off the phone; move a config value, a hash, or a class name onto it | Copying a report's SHA-256 digest off-device to verify it recomputes identically on the laptop — *this is a real verification step in your own product story* | Counts [INFERENCE] | **Genuinely useful and underused.** The digest-verification use is the best legitimate example you have |
| **Remote control** | Drive the phone from the laptop keyboard when your hands are on the keyboard anyway | Stepping through the walkthrough repeatedly during rehearsal; typing long text into phone fields | Duration [INFERENCE] | **Useful but do not force it.** Camera and IR work demands the phone in hand. Use it for the typing-heavy parts only |

### 4.2 The questions you specifically asked

- **Is one teammate using Office Kit sufficient, or should all three?** **[UNKNOWN].** Nothing published says how per-device telemetry rolls up to a team score. **Do not assume three people using it yields three times the score** — that is exactly the kind of invented formula this document refuses to produce. The defensible position: each member should use Office Kit for the work that genuinely benefits from it. If roll-up is a sum, you benefit. If it is an average or a max, you lose nothing, because nobody is doing pointless activity.
- **Does each loaner phone carry its own HackTracker data?** **[UNKNOWN].** Ask the organiser. It is a five-second question and it changes nothing about what you should do today.
- **Is idle mirroring counted the same as active mirroring?** **[UNKNOWN].** Leaving the mirror connected while genuinely working is honest. Leaving it connected to an idle phone in a drawer overnight to farm duration is not, and §16 forbids it.

### 4.3 The one Office Kit rule that matters

**Turn mirroring on now and route every artefact through Office Kit for the rest of the event.** Not because of a formula — there isn't one — but because it is genuinely the better workflow under Red Light, and because it is the only category where a *confirmed* measurement category (F6) maps onto a *habit you control*.

---

## 5. THERMAL / PERFORMANCE STRATEGY

### 5.1 The four questions, answered directly

1. **Is temperature officially measured by HackTracker?** **[UNKNOWN — no official statement found, and I could not fetch the guide to check].** Assume **no**. Never tell a judge that thermal is scored.
2. **Is temperature likely relevant indirectly?** **[INFERENCE] Yes, and this is the real point.** Thermal throttling is a *demo-reliability* risk and a *Technical Depth* opportunity. A phone that has been running camera + inference for twenty minutes before the judges arrive is a different machine from the one you rehearsed on.
3. **Can thermal behaviour affect the device-use evidence?** **[INFERENCE] Indirectly.** Severe thermal throttling can cause the OS to close camera sessions or kill background work, which would *reduce* whatever telemetry you generate. Nothing about that argues for artificial cooling tricks; it argues for not pinning the SoC pointlessly.
4. **Should you benchmark it yourselves?** **Yes — but for §8 and §13 reasons, not telemetry reasons.** "We measured sustained inference latency across a twenty-minute walkthrough and it degraded by X%" is a Technical Depth answer that almost no team at a phone hackathon will have. It costs one person one hour.

### 5.2 What actually happens to your workloads — predicted, not measured

**All figures in this subsection are [SPECULATIVE] predictions written before measurement. They are hypotheses for §5.4 to test. Do not quote any of them to a judge as a result.**

Grounding facts: the shipped vision model is `yolov8n_seg.ptl`, **13.8 MB**, loaded via `LiteModuleLoader` — **PyTorch Mobile Lite on CPU** [VERIFIED]. The research log measured **210.7 ms/image on a laptop CPU** [VERIFIED]. A phone CPU is not a laptop CPU; expect the on-device number to be different in either direction and **measure it rather than guess**.

| Horizon | Predicted behaviour | Predicted risk |
|---|---|---|
| **30 s** | Single or few inferences. No meaningful heating. Latency ≈ steady-state cold number | None |
| **10 min** | Continuous camera preview + periodic inference. SoC skin temperature climbing; likely first throttle steps. Camera ISP contributes heat independently of your model | **Moderate** — latency creep; possible preview frame-rate drop |
| **1 h** | Sustained camera + inference. Expect meaningful sustained-clock reduction, battery down substantially, possible OS thermal warnings | **High** — this is the regime where demos die |

**The operationally important consequence [INFERENCE]:** your demo is roughly three minutes. The danger is not the demo itself — it is arriving at the demo with a phone already heat-soaked from an hour of rehearsal and debugging. **Mitigation is behavioural, not technical: stop heavy work on the demo phone ten minutes before you present, and carry the backup video.**

### 5.3 SMARTLEASE DEVICE STRESS TEST

Run what you can of this. If time is short, **run only rows S3 and S6** — they are the two that protect the demo.

| ID | Workload | Duration | Expected measurement | Acceptable threshold | Failure condition | Mitigation if it fails |
|---|---|---|---|---|---|---|
| **S1** | Cold single-image segmentation, app just launched | 1 run | End-to-end ms from shutter to mask | Record baseline; no pass/fail | Crash or OOM on model load | Reduce input resolution; confirm letterbox path |
| **S2** | 100 consecutive inferences on one cached bitmap, no camera | ~3–10 min | Latency per run; drift from run 1 to run 100 | <2× degradation run-1 → run-100 | >2× degradation, or crash | Insert a sleep between inferences in demo path; do not run inference per preview frame |
| **S3** | **Continuous camera preview + inference on demand, as a real walkthrough** | 20 min | Latency drift; preview fps; battery % drop; app responsiveness | App stays responsive; no dropped camera session | Camera session closes; UI stalls >2 s | **Do not hold a live preview between demo beats.** Bind camera only when capturing |
| **S4** | Camera + microphone tap capture + inference interleaved | 10 min | Audio capture success rate; any camera/mic contention | 100% tap captures succeed | Any failed audio capture | Release camera before opening the mic; serialise the two |
| **S5** | *Local LLM generation* | — | — | — | — | **Not applicable. There is no LLM in the app.** Row retained only to record that it was considered and cut (§6) |
| **S6** | **Full SmartLease workflow end to end, airplane mode ON, from cold app start** | 3 attempts, timed | Wall-clock to reach the rupee figure; any failure at any step | Reaches the rupee figure, three times, inside 100 s | Any step fails on any run | This *is* the demo rehearsal from BATTLE_PLAN §4 item 4. It is the highest-value row in this table |

**Recording protocol.** For each run record: starting battery %, ending battery %, start and end wall-clock, per-step latency, any thermal warning, any crash. If a device temperature reading is accessible from `SelfTest.kt`, record it; **if it is not accessible, write "not available" rather than estimating.** `SelfTest.kt` already enumerates `Build.SOC_MODEL`, supported ABIs, IR carrier frequencies and per-camera focal lengths [VERIFIED] — extending it with a thermal read is a small, honest, high-credibility addition **if and only if** everything in §11's green list is already done.

### 5.4 Thermal experiment mapping to the requested Tests A–F

| Requested | Maps to | Status |
|---|---|---|
| Test A — single image inference | **S1** | Run it. Ten minutes of work |
| Test B — 100 consecutive inferences | **S2** | Run it if you have a spare 15 minutes |
| Test C — continuous camera + inference | **S3** | Run it. Protects the demo |
| Test D — camera + microphone + inference | **S4** | Run it. Reveals a real contention bug class |
| Test E — local LLM generation | **S5** | **Cut. There is no local LLM.** Running a test of a thing you do not have is not a test |
| Test F — full SmartLease workflow | **S6** | **Non-negotiable. This is already priority 4 in BATTLE_PLAN §4** |

---

## 6. LOCAL AI STRATEGY

### 6.1 The split — and why it is already decided

| Component | Local or cloud | Status in repo |
|---|---|---|
| Vision defect segmentation | **Local** | [VERIFIED] `yolov8n_seg.ptl` in assets, PyTorch Lite, CPU |
| Acoustic tap classification | **Local** | [VERIFIED] `acoustic_tap_model.json` in assets — inspected: it carries `melFilterbank`, `dctMatrix`, `scalerMean`, `scalerScale`, `coefficients`, `intercept`. **This is the classical logistic-regression model with its feature pipeline serialised to JSON, not a distilled CNN.** It also carries `groupedAccuracy`, `groupedAccuracyCi95` and `trainedTaps` — the model ships with its own uncertainty, which is a genuinely good design decision and worth saying out loud |
| OCR | **Local** | [VERIFIED] ML Kit text-recognition, on-device by default |
| Deterministic pricing | **Local, non-model** | [VERIFIED] `DeductionEngine.kt` |
| Safety escalation | **Local, non-model** | [VERIFIED] `SafetyGate.kt` |
| Report + fingerprint + PDF | **Local** | [VERIFIED] `ReportGenerator.kt`, `ReportFingerprint.kt` |
| LLM narration | **Does not exist** | [VERIFIED] no llama.cpp, no gguf, no MediaPipe LLM, no ExecuTorch, no GenieX anywhere under `app/src/main/java` or in `gradle/libs.versions.toml` |

### 6.2 The claim you can make that almost nobody else can

**[VERIFIED — and this is your strongest single technical asset]** `app/src/main/AndroidManifest.xml` does not merely omit `INTERNET`; it **actively removes it**:

```xml
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />
```

The in-file comment explains exactly why this is load-bearing rather than decorative: ML Kit's `text-recognition` transitively pulls `com.google.android.datatransport:transport-backend-cct`, which **merges both permissions in regardless of what the source manifest asks for**. Without the explicit removal, a build whose entire pitch is "it cannot phone home" would ship holding a socket permission.

This is the rarest thing in a hackathon: **a privacy claim that is falsifiable and survives falsification.** A judge can verify it in thirty seconds from the merged manifest at `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`. **Offer that check before anyone asks for it.** "Offline" claimed by ninety other teams means airplane mode during the demo. Yours means the OS will refuse the syscall.

### 6.3 On the local LLM — the brutal answer

**Cut it. Today. Entirely.**

The reasoning is not that a local SLM is a bad idea. It is that:

1. It does not exist in the codebase — not a stub, not a dependency, nothing [VERIFIED].
2. `SafetyGate.kt`'s own doc comment already describes "the eventual GenieX report synthesis" as future work — the codebase itself concedes the point.
3. `BATTLE_PLAN.md` §4 item 6 already ranked this last, behind five other things, "only with hours to spare".
4. The research log **refuted 0-3** the claim that a comparable project ran Llama 3.2 3B on Hexagon via GENIE. You have no verified precedent that this path works quickly.
5. **Most importantly:** the mega-prompt's own constraint says the LLM must not decide liability, causality, cost, or structural integrity. Strip those out and what is left is *prose generation over a table you already have*. That is the lowest-value component in the architecture, and it is the most expensive to build.

**The answer to "why doesn't your report use an LLM?" is better than the LLM would be:** *"Because everything on this report has to be reproducible and attributable. `DeductionEngine` is deterministic — same findings in, same rupee figure out, every time, and the SHA-256 digest proves the document wasn't edited afterwards. A language model in that path would make the number unreproducible and the record unverifiable. We built the boring thing on purpose."*

That is a Technical Depth answer. Shipping a half-wired SLM that stutters on stage is a Demo failure. **The trade is not close.**

---

## 7. "WHAT ARE WE BUILDING ON TOP OF?" — THREE-LAYER ANALYSIS

### Layer 1 — Existing technology you did not write

| Component | Actually used? | Evidence |
|---|---|---|
| YOLOv8-Seg architecture (Ultralytics) | **Yes** | Trained weights exported to `yolov8n_seg.ptl` [VERIFIED] |
| PyTorch Mobile Lite runtime | **Yes** | `LiteModuleLoader.load(path)` in `YoloSegDefectSegmenter.kt`; `pytorch_android_lite` 1.13.0 [VERIFIED] |
| CameraX | **Yes** | 1.4.1 [VERIFIED] |
| ML Kit text recognition | **Yes** | 16.0.1, on-device [VERIFIED] |
| Android `SensorManager` rotation vector | **Yes** | `ArAlignmentTracker.kt` [VERIFIED] |
| Android `ConsumerIrManager` | **Yes** | `IrController.kt` [VERIFIED] |
| Room / SQLite | **Yes** | 2.7.2 [VERIFIED] |
| Android `PdfDocument` | **Yes** | `ReportGenerator.kt` [VERIFIED] |
| `MessageDigest` SHA-256 | **Yes** | `ReportFingerprint.kt` [VERIFIED] |
| ZXing (QR) | **Yes** | 3.5.3 [VERIFIED] |
| **ARCore / Depth API** | **NO** | No ARCore dependency in `gradle/libs.versions.toml` [VERIFIED]. Research log §4 records ARCore availability on vivo/iQOO hardware as **unanswered**. **Never put ARCore in a diagram or a sentence** |
| **ExecuTorch / QNN / Hexagon delegate** | **NO — not in the app** | `.pte` and `libQnnHtp.so` exist under `ml/YOLOV8/qnn_android_bundle/` only. Not in `app/src/main/assets/`, which contains exactly two files [VERIFIED] |
| **Llama / GenieX / any LLM** | **NO** | [VERIFIED] |

### Layer 2 — Your original engineering

This is the layer that answers "what did you build?", and it is stronger than you probably give it credit for.

| Module | What is original about it |
|---|---|
| `DeductionEngine.kt` + `RepairTariff.kt` + `DeductionModels.kt` + `FindingDetail.kt` | A **deterministic repair-impact rules engine** with `PRICING_CONFIDENCE_FLOOR = 0.60f` [VERIFIED]. Per-sq-ft rates with a minimum call-out floor, so a small crack costs what it costs *because a contractor charges for the visit*, not because a number was hardcoded. **Confirmed wired**: `ReportGenerator.kt:73` calls `DeductionEngine.summarise(...)`, and `drawDeductionSummary` prints deposit held, per-line basis, total deductions and refund due onto the PDF, followed by `drawDeductionHonestyNote` which prints the confidence floor as a percentage **onto the document itself** [VERIFIED]. BATTLE_PLAN priority 1 is **DONE** |
| `SafetyGate.kt` | **Deterministic, non-model hazard escalation** [VERIFIED]. Architecturally one-way: a model can escalate a finding but no model output can suppress a hard-coded rule. This is a real safety-architecture argument and very few hackathon projects have one |
| `ReportFingerprint.kt` | SHA-256 over a canonical record plus a file digest, with **code comments that already state honestly that this is integrity, not non-repudiation — there is no keypair** [VERIFIED]. Use that framing verbatim |
| `ArAlignmentTracker.kt` + `CameraController.kt` | **Move-in/move-out viewpoint alignment from raw rotation vector**, no ARCore, no depth sensor [VERIFIED]. Harder and more original than calling a framework |
| `AcousticFeatureExtractor.kt`, `Fft.kt`, `AcousticModelBundle.kt`, `TrainedTapClassifier.kt` | **A hand-written FFT, log-Mel filterbank and DCT/MFCC pipeline in Kotlin**, consuming a JSON-serialised model that ships with its own confidence interval [VERIFIED]. This is the module a technically literate judge will respect most and notice least — **point at it** |
| `ReportGenerator.kt`, `ReportModels.kt`, `FindingsDigest.kt`, `QrCode.kt` | A complete offline PDF pipeline with pagination and a per-line evidence basis [VERIFIED] |
| `AppDatabase.kt`, `InspectionDao.kt`, `InspectionEntity.kt` | Property/inspection timeline schema [VERIFIED] |
| `SelfTest.kt` + `SelfTestScreen.kt` | An on-device capability probe reporting SOC model, ABIs, IR emitter presence, carrier frequencies, 38 kHz support, and per-camera focal lengths — **and it states what the app will do on hardware that lacks each capability** [VERIFIED]. This is a self-documenting honesty instrument. See §13 |
| The manifest permission removal | See §6.2. Small diff, largest claim in the project |

### Layer 3 — Product / IP differentiation

**The sentence that separates you from "YOLO on a phone":**

> We are not classifying images. We are producing a **comparable, tamper-evident record of a property at two points in time** — and pricing only the differences the system is confident enough to price.

Five components, none of which is a model:

1. **Paired temporal capture** — baseline and current, geometrically realigned. The comparison is the product.
2. **Multimodal corroboration** — camera sees, microphone verifies, IR tests function. Three independent physical channels. A single false positive from one channel does not become a deduction.
3. **A confidence floor that refuses to price** — the model is not permitted to bill anyone below 0.60. **This is printed on the report.**
4. **Human decision, not model decision** — the system produces evidence and an estimate. A person signs.
5. **Tamper-evident record** — SHA-256 over canonical content, verifiable by recomputation, honestly described as integrity rather than authorship.

**How to demonstrate it in ten seconds, without saying any of the above:** during the demo, let a low-confidence detection appear and **point at the line on the PDF that says it was NOT priced and was sent to human review.** Every team can show a detection. Almost none can show a *refusal*. A refusal is what a real instrument does, and judges know it.

---

## 8. DEVICE BENCHMARK PLAN

**The rule that governs this entire section: never invent a result.** Any cell you have not measured stays literally "not measured". A blank you admit to costs you nothing. A number you invented costs you the whole Technical Depth score the moment someone asks how you got it.

| Suite | Metric | Status today | Action |
|---|---|---|---|
| **Vision** | Box mAP50, mask mAP50, mask mAP50-95, per-class AP50, clean-surface false-positive rate | **MEASURED, RETRAINED** — 0.287 / **0.249** / 0.150 on a leak-free 268-image test split; spalling 0.617, peeling 0.222, damp_stain 0.105, **crack 0.052**; clean images falsely flagged 28.6% at the shipping threshold 0.55; `ml/vision/handoff/benchmarks/eval_A.json` [VERIFIED] | Quote as-is. Do not round upward. **Keep crack out of the demo path — spalling is the class that works.** Volunteer the leakage story before it is asked |
| **Baseline comparison** | False-change rate, missed-change rate, robustness to viewpoint / lighting / distance | **NOT MEASURED** | Say "not measured". Do not estimate |
| **Acoustic** | Accuracy, CI, baseline | **MEASURED** — LeaveOneGroupOut 0.867, **95% CI [0.621, 0.963]**, majority baseline 0.533, **n = 15 taps from 8 recordings** [VERIFIED] | **Never quote 0.867 without the interval and the n.** The interval is the credibility |
| **Acoustic** | Confusion matrix, background-noise robustness | **NOT MEASURED** | Say so. Venue noise is a fair challenge — answer it with the design (knuckle tap is a loud transient close to the mic), not with a number you don't have |
| **Measurement** | Absolute / relative dimension error | **NOT MEASURED — and there is no depth sensor path** | **Do not claim dimensional accuracy at all.** See §13 Q8 |
| **Device** | Model size | **MEASURED** — `yolov8n_seg.ptl` 13.8 MB [VERIFIED] | Quote |
| **Device** | On-device inference latency, RAM, battery, thermal | **NOT MEASURED on phone** (210.7 ms/image is **laptop CPU**) [VERIFIED] | **Run S1/S2/S3 from §5.3.** One hour of work, and it converts a "not measured" into a Technical Depth answer |
| **Offline** | Airplane-mode full run; report and digest generation with no network | **Structurally guaranteed** — `INTERNET` removed at manifest level [VERIFIED] | **Demonstrate by recomputing the digest, not by asserting it** |
| **Reproducibility** | Same input → same output; deterministic rules; hash consistency | **Architecturally true** for `DeductionEngine` and `ReportFingerprint`; **not formally tested** | Run the same inspection twice and show the identical rupee total. Two minutes. Very persuasive |

---

## 9. DATASET PLAN

### 9.1 What you are ACTUALLY training on — verified from the repo

These are the real sources, read from the `README.dataset.txt` and `data.yaml` files in this repository. **Use these names and licenses. Do not cite SDNET2018, DeepCrack, Crack500, MVTec AD, CUBIT, MIMII, DCASE, Copel-AMR or UFPR-ADMR as if you had used them — you have not.**

| Path | Roboflow project | Workspace | Images | Classes | License |
|---|---|---|---|---|---|
| `ml/YOLOV8/1` | Infrastructure Monitoring | `pothole-detection-h9muz` | 902 | 12 | **CC BY 4.0** |
| `ml/YOLOV8/2` | Structural defects (crack-spall-seg) | `kamar` | 1019 | 3 | **CC BY 4.0** |
| `ml/YOLOV8/3` | Building Anomalies | `image-classification-jf9vm` | 281 | 6 | **CC BY 4.0** |
| `ml/YOLOV8/concrete` | concrete defects | `ammlworkspace` | 100 | 9 | **CC BY 4.0** |
| `ml/YOLOV8/paint-peel` | detection (`detection-ghu6i`) | `image-classification-jf9vm` | 50 | 1 | **Public Domain** |
| `ml/data` | Crack Detection using Instance Segmentation in YOLOv8 (v5) | `bach-khoa-ho-chi-minh-university-fyr43` | 2981 | — | **CC BY 4.0** |
| `ml/data/dataset/internal-defect` | Internal Wall Finishing Defects (v5) | `chew-poh-yee` | 5750 | 6 | **CC BY 4.0** |

Merged into `ml/YOLOV8/unified_defects` as four classes: `crack`, `peeling`, `spalling`, `stain_mould`.

**License position, stated cleanly:** every source is CC BY 4.0 or Public Domain. **No non-commercial restriction anywhere.** Attribution is required for the CC BY sources — the table above is that attribution and it lives in the repo. If a judge asks about data provenance, open this file. That answer is better than most teams can give and it took no extra work.

### 9.2 The diagnosis the research log already made, restated because it drives §10

The merged corpus spans **bridge concrete, pavement, building façades and interior walls** — domains that do not share visual statistics. The model is diluted across them. `spalling` (mask AP50 0.553) is the strongest class **and the least relevant to a rental flat**. `crack`, the hero class, sits at 0.276 with recall 0.379. `stain_mould` at 0.092 is non-functional.

**[INFERENCE]** The single-source `crack-seg` corpus alone — 2981 images from one domain — would very likely have produced a substantially stronger single-class crack detector than the four-class merge does. **There is no time to retrain today** (BATTLE_PLAN §4, and retraining is nowhere in the priority list). **Say this to a judge if the model is challenged.** Diagnosing your own model's failure correctly is a Technical Depth answer; it demonstrates you understand *why* the number is bad, which is the thing being assessed.

### 9.3 The proprietary dataset — as roadmap, explicitly not as work done

Say this in the future tense and only in the future tense:

> The correct dataset for this product does not exist publicly, because every public defect dataset is single-moment. Ours needs to be **paired**: the same wall, the same unit, photographed at move-in and at move-out, with the change annotated rather than the defect. Roughly 500 paired captures across 50 units, stratified by lighting, surface finish and distance, plus tap recordings on the same surfaces. That is a deployment programme, not a hackathon task.

This is strong *because* it is honest about being unbuilt. It shows you know what would make the product work, which is a different and more valuable thing than pretending it already works.

---

## 10. MODEL TRAINING PLAN

**Recommendation for the remaining hours: train nothing.**

| Option | Verdict | Reason |
|---|---|---|
| Retrain single-class crack detector on the 2981-image crack-seg corpus | **Tempting. Still no.** | Likely to improve crack recall materially. But it requires a laptop training run, re-export to `.ptl`, re-validation and re-integration, and it is **not in BATTLE_PLAN's priority list at all**. A better detector that arrives at hour 28 unvalidated is worse than a known-weak detector you can honestly describe |
| Re-train acoustic on more taps | **No** | You cannot collect a meaningfully larger tap corpus at a venue today, and the honest small-n story is already good |
| Quantise to INT8 | **No** | Serves only the NPU path, which is cut (§7) |
| **Re-validate nothing, change nothing, and spend the time on §11's green list** | **Yes** | This is the recommendation |

**If — and only if — everything in §11's green list is complete with hours genuinely to spare**, the single highest-value ML action is the single-class crack retrain, because it attacks the weakest number in the project (recall 0.379). It is still a distant second to rehearsing the demo a fourth time.

---

## 11. RED / GREEN LIGHT EXECUTION PLAN

### 11.1 The rule that was the highest risk in the project — now resolved, verbally

**[RESOLVED 2026-09-12 — was the HIGHEST RISK IN THE PROJECT.]** Qualcomm's rules for its own edge-AI events forbid closed-source pre-existing code and require a pre-existing proposal to be "significantly modified" during the event (confirmed 3-0 in the research log) — a different event, cited only as a directional signal, never as this event's rule.

**An organiser was asked directly, on-site, and confirmed verbally: pre-existing code is allowed, and judging looks at what is built on top of it during the event.** Label discipline applied consistently with the rest of this document: this is **[FIRST-PARTY, UNVERIFIED BY ME]** — an organiser's spoken word, not a document I read myself — but it is treated as authoritative, the same standard already applied to the organiser briefing quoted throughout this analysis and to the scoring weights in §1.

**What this changes:** nothing about the recommended behaviour. **What it removes:** the catastrophic tail risk of disqualification for carrying eleven days of pre-event work. **What it confirms instead of merely hoping:** "original work built on top of" was always the standard stated in the organiser briefing (§1, F8) — this exchange confirms that standard applies here specifically, not just in general terms.

**Still make the Round-1 checkpoint demonstrate in-event work** — the deduction engine wiring, the PDF balance sheet, the manifest permission removal, the on-device performance readings, the venue IR capture, the validation metrics. Not because a disqualification hinges on it anymore, but because "what was built on top" is explicitly the thing now confirmed to be judged, and `git log` is the evidence for that. **Keep committing with clear messages.**

### 11.2 Red Light (≈55% of build time) — phone-first

| Who | Does what on the phone | Office Kit use |
|---|---|---|
| **Android/device** | Runs the walkthrough repeatedly; tests camera binding and release; verifies IR degradation path via `SelfTestScreen`; UI fixes verified on-device | **Mirroring on continuously**; APK pushes in by file transfer |
| **AI/model** | Runs S1–S4 from §5.3 on-device; records latencies by hand; captures tap samples through the real app | Pulls captured WAVs and PDFs out by file transfer; moves stack traces off by clipboard |
| **Validation/demo** | **Rehearses the full flow, timed, in airplane mode**; captures the backup video; drives the Q&A dry run | Mirrors the phone to rehearse presenting on a large screen |

Red Light is not a handicap here — it is the regime your product was designed for. The camera work, the tap capture, the IR capture and the rehearsal all *require* the phone. **Do the phone-required work during Red Light and the laptop-required work during Green Light**, which is what you would do anyway.

### 11.3 Green Light (≈45%) — phone + laptop

Laptop-appropriate work, in priority order: PDF layout iteration, the deck strip (§16), reading `val_metrics.json` into the Q&A sheet, committing and pushing, and — last and only if everything else is done — the §10 retrain.

**Keep the phone genuinely involved even in Green Light.** Not for telemetry theatre: because every PDF layout change must be verified by generating a real report on the device, and every deck claim must be checked against the running app.

### 11.4 Pre-hackathon vs event work — the separation to state out loud

| Pre-event (research and preparation) | Event (implementation) |
|---|---|
| Dataset sourcing and licensing (§9.1) | `DeductionEngine` → `ReportGenerator` wiring and PDF balance sheet [today, commit `94cc5ea3`] |
| Model training and export | `ReportFingerprint.kt` SHA-256 [today] |
| Architecture and schema design | Manifest permission removal and the merged-manifest finding [today] |
| Benchmark methodology | First-ever vision validation metrics `ml/YOLOV8/val_metrics.json` [today] |
| UI planning, documentation | Venue IR pattern capture [today, on-site, pending] |
| | Rebuilt report and walkthrough UI [today, commits `4e89c249`, `75219c66`] |

**Do not blur this line. Present it as a table like the one above.** A team that can say precisely which parts were pre-existing is more credible than a team that claims everything was written on the day.

---

## 12. THREE-PERSON WORKFLOW

Per `docs/guides/TEAM_STRUCTURE.md` [VERIFIED]: Member 1 AI/model, Member 2 Android/device, Member 3 research/validation/demo. Note that document was written pre-event and assumes a GenieX/NPU plan that §6 and §7 now cut; the role split still holds, the deliverables do not.

| | Phone activity | Office Kit activity | Why it is legitimate | What they must NOT do |
|---|---|---|---|---|
| **M1 — AI / model** | Runs inference on-device; executes S1–S4; captures tap samples through the app | Pulls PDFs, WAVs, logs off the phone; clipboard for stack traces | Measurement requires the device; artefacts must reach the laptop to be analysed | **Must not** start the NPU port. **Must not** start an LLM. **Must not** retrain unless §11's green list is clear |
| **M2 — Android / device** | Builds and installs; verifies every UI change on-device; **captures the venue AC IR pattern early** | Mirroring on continuously; APK transfers in | Android work is not verifiable on an emulator; IR is capturable only in the room | **Must not** leave the IR capture until hour 28. **Must not** add an ARCore dependency |
| **M3 — validation / demo** | Rehearses the full flow timed, three times, airplane mode; records the backup video; maintains the Q&A sheet from §13 | Mirrors for presenting practice | 40 points ride on one live run | **Must not** put any §16 claim into the deck. **Must not** rehearse fewer than three times |

**If you are running solo or as two people** — the commit history shows a single author — then collapse this to a strict order and drop the rest: **(1) confirm the pre-existing-code rule with an organiser, (2) capture the venue IR pattern, (3) rehearse the full flow three times timed and record the backup video, (4) strip the false claims from the deck, (5) mirroring on and artefacts through Office Kit throughout.** Everything else in this document is optional.

---

## 13. JUDGE DEFENSE

Every answer below is grounded in what is true in this repository **today**. Answers marked **⚠** contain an admission — say the admission first, unprompted. That is not a weakness; per BATTLE_PLAN §5 it is the highest-scoring move available to you.

---

**Q1. "Are you just running YOLO on a phone?"**

- **FACT.** YOLOv8n-Seg is one of four evidence channels and it is not permitted to price anything on its own.
- **TECHNICAL.** Vision detects. The microphone verifies material state through tap response. The IR emitter tests whether an appliance functions. `DeductionEngine` prices, deterministically, and refuses anything below 0.60 confidence. `SafetyGate` can escalate but nothing can suppress it. The model is one input to a system that is mostly not a model.
- **DEMO.** Show a detection that gets **refused** and routed to human review, with the refusal printed on the PDF.

---

**Q2. "Why do you need an iQOO phone?"**

- **FACT.** The IR blaster. [VERIFIED: iQOO 15 lists an infrared blaster; confirmed 2-0 against the official spec page.]
- **TECHNICAL.** No inspection app can tell you whether the air conditioner works. This one transmits a 38 kHz IR command through `ConsumerIrManager` and observes whether the unit responds. That requires an IR emitter, which most flagship phones no longer ship. Plus a camera, a microphone and enough CPU to run segmentation with no network permission.
- **DEMO.** Point the phone at the AC. In airplane mode. It turns on.

---

**Q3. "Why not use a cloud API?"**

- **FACT.** The app **cannot** reach the network. `INTERNET` and `ACCESS_NETWORK_STATE` are explicitly removed at the manifest level [VERIFIED].
- **TECHNICAL.** This is not airplane mode during a demo — it is an OS-level denial. We found that ML Kit transitively merges both permissions back in via `transport-backend-cct`, so the removal is load-bearing; without it the build would have shipped holding a socket permission it never used. A rental inspection photographs the inside of someone's home, plus their lease, plus their meter readings. That should not transit a third party.
- **DEMO.** Open the merged manifest at `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml` and let them read it. **Offer this before being asked.**

---

**Q4. "What is actually original here?"**

- **FACT.** The rules engine, the safety gate, the alignment tracker, the Kotlin acoustic feature pipeline, the fingerprint, the offline report pipeline.
- **TECHNICAL.** Off the shelf: YOLOv8, PyTorch Lite, CameraX, ML Kit, Room, `PdfDocument`. Ours: a deterministic repair-impact engine with a 0.60 pricing floor; a one-way safety escalation gate no model output can override; move-in/move-out viewpoint alignment from raw rotation vector with no ARCore; **a hand-written FFT, log-Mel filterbank and MFCC pipeline in Kotlin**; a canonical-record SHA-256 fingerprint.
- **DEMO.** Open `AcousticFeatureExtractor.kt` and `Fft.kt`. That is the file that ends this question.

---

**Q5. "What happens if the AI is wrong?" ⚠**

- **FACT — say this first.** It **is** wrong, often. Mask mAP50 0.249 across four classes, and crack AP50 is 0.052 — it finds almost no cracks. Spalling, at 0.617, is the one class worth pointing a phone at.
- **TECHNICAL.** Which is exactly why `DeductionEngine.PRICING_CONFIDENCE_FLOOR` is 0.60 and why anything below it, or anything the heuristic flagged, is reported as requiring human review rather than priced. What we assert is the measurement and the signed record — not an infallible detector. The confidence floor is **printed on the report itself**, so the limitation travels with the document instead of living in a slide.
- **DEMO.** `ml/YOLOV8/val_metrics.json`, then the honesty note at the bottom of the generated PDF.

---

**Q6. "How do you know the tenant caused the damage?" ⚠**

- **FACT.** We don't, and the product does not claim to.
- **TECHNICAL.** The system answers a narrower question: *did this specific surface change between these two recorded moments, and by how much.* Causality and liability are legal determinations made by people. That is why the flow ends in human review and a signature rather than an automated verdict. A system that inferred intent from a photograph would be a worse product and an indefensible one.
- **DEMO.** The report says "change detected between capture A and capture B", not "tenant caused".

---

**Q7. "Can this be legally binding?" ⚠**

- **FACT.** **No, and we are careful to say so.** `ReportFingerprint` is a SHA-256 integrity digest, not a digital signature. There is no keypair.
- **TECHNICAL.** It proves the record has not changed since it was generated — anyone can recompute the digest over the canonical record and compare. It does **not** prove who generated it; that would need asymmetric keys and an identity binding we did not build. The code comments say exactly this. Whether a tamper-evident record is admissible is a question for a lawyer, not for us.
- **DEMO.** Recompute the digest in front of them and show it matches.

---

**Q8. "How accurate is your measurement?" ⚠**

- **FACT.** **We do not make dimensional accuracy claims, because we have no depth sensing.**
- **TECHNICAL.** There is no ARCore dependency and no depth API in this build. Alignment uses the rotation vector, which tells us we are looking from the same *angle*, not how far away the wall is. Pricing therefore uses surface-area rules with a minimum call-out floor — a small crack costs ₹1,500 because a contractor charges for the visit, not because we measured it to the millimetre. Metric dimensioning is the next hardware step, not a current claim.
- **DEMO.** `RepairTariff.kt`, which documents the provenance of every rate.

---

**Q9. "Why does acoustic tapping work?" ⚠**

- **FACT.** LeaveOneGroupOut accuracy **0.867, 95% CI [0.621, 0.963]**, against a majority baseline of 0.533 — over **15 taps from 8 source recordings**. **Always quote the interval and the n.**
- **TECHNICAL.** A debonded tile or a hollow plaster void has a different mechanical impedance from bonded material, so the tap transient has different spectral content and decay. We extract log-Mel/MFCC features from a 16 kHz capture — FFT, filterbank and DCT written in Kotlin, running on-device — and classify with logistic regression. It beats chance. With 15 samples it is **not** yet a product claim, and the interval is wide enough that we say so.
- **DEMO.** Tap the prop. Then show `groupedAccuracyCi95` inside `acoustic_tap_model.json` — **the model ships carrying its own uncertainty.**

---

**Q10. "What happens with different tile materials?" ⚠**

- **FACT.** Unknown — we have not tested across materials, and 15 taps cannot support a per-material claim.
- **TECHNICAL.** The physics generalises (impedance mismatch changes the transient) but the decision boundary was learned on a narrow sample. Deployment would need per-material calibration: a reference tap on a known-solid area of the same surface, used to normalise. That calibration step is designed but not built.
- **DEMO.** Say it plainly. A judge who asks this is testing whether you know your own limits.

---

**Q11. "What happens in bad lighting?" ⚠**

- **FACT.** Detection degrades, and we have not quantified by how much — robustness to lighting is in the "not measured" column of our own benchmark plan.
- **TECHNICAL.** The training corpus merges bridge concrete, pavement and interior walls, so the model is already domain-diluted before lighting is considered. What protects the *product* is the confidence floor: a poorly lit capture yields low-confidence detections, and low-confidence detections are not priced — they go to human review. Degrading into review rather than into a wrong bill is the intended failure mode.
- **DEMO.** Capture in poor light, show the finding route to review rather than to a rupee figure.

---

**Q12. "What happens when the phone gets hot?"**

- **FACT.** Sustained camera-plus-inference load will raise SoC temperature and eventually throttle. **If you have run S1–S3, give the measured numbers here. If you have not, say "we have not measured it on-device yet" — do not estimate.**
- **TECHNICAL.** A walkthrough is bursty rather than continuous: capture, infer, move to the next surface. We bind the camera per capture rather than holding a live preview between beats, which keeps the duty cycle low. The heavy path is the 13.8 MB segmenter on CPU; it runs on demand, not per preview frame.
- **DEMO.** Either the S1–S3 table, or an honest "not yet measured".

---

**Q13. "Why is this better than a normal inspection app?"**

- **FACT.** Normal inspection apps document how a flat *looks* at one moment. Three differences: paired temporal comparison, functional testing, and a tamper-evident offline record.
- **TECHNICAL.** Photographs alone do not establish change, because nothing guarantees the two photographs were taken from the same viewpoint — so we realign. Photographs cannot tell you a tile is debonded behind an intact surface — so we tap. Photographs cannot tell you the AC works — so we transmit IR. And none of it leaves the device.
- **DEMO.** The IR beat. Nobody else will have it.

---

**Q14. "Why does the LLM need to be local?" ⚠**

- **FACT.** **There is no LLM in this build, and that is deliberate.**
- **TECHNICAL.** Everything on the report must be reproducible and attributable. `DeductionEngine` is deterministic — the same findings produce the same rupee figure every time — and the SHA-256 digest proves the document was not altered afterwards. Putting a language model in that path would make the number unreproducible and weaken the record it is supposed to preserve. If we added one later it would narrate, never decide: not liability, not causality, not cost, not structural integrity. And it would be local, for the same reason nothing else here touches the network.
- **DEMO.** Generate the same report twice; show the identical total and the identical digest.

---

**Q15. "What does Office Kit actually contribute?"**

- **FACT.** It is the working route between phone and laptop: mirroring while iterating, APKs in, PDFs and captured audio out, clipboard for digests and stack traces.
- **TECHNICAL.** The concrete case worth naming: we copy a generated report's SHA-256 digest off the device by clipboard and recompute it on the laptop. That is a genuine verification step in our own integrity story, and Office Kit is how the value crosses.
- **DEMO.** Do exactly that, live.

---

**Q16. "What did you build yourself?"** → See Q4, and hand them the Layer-2 table from §7.

---

**Q17. "Which components are open source?"**

- **FACT.** YOLOv8 (Ultralytics), PyTorch Mobile Lite, CameraX, ML Kit text recognition, Room, ZXing, Android platform APIs. Datasets: seven Roboflow sources, **all CC BY 4.0 or Public Domain, none non-commercial** — enumerated with workspace, project, image count and license in §9.1 of this document.
- **TECHNICAL.** We can name every one, with its license, from a file in the repo.
- **DEMO.** Open `docs/HACKTRACKER_STRATEGY.md` §9.1. Most teams cannot answer this at all.

---

**Q18. "Which parts were written during the hackathon?"** and **Q19. "How do you prove this is not a pre-built product?"** ⚠

- **FACT — volunteer this, do not wait to be asked.** This project has pre-event work: dataset sourcing, model training, architecture and schema design, and initial UI. Written **today, during the event**: the deduction engine wired into the report generator with the rupee balance sheet printed on the PDF; the SHA-256 fingerprint; the manifest permission removal after discovering ML Kit was merging network permissions back in; the first validation metrics this project has ever had; the rebuilt report and walkthrough UI; and the venue AC IR pattern captured in this room.
- **TECHNICAL.** `git log` with timestamps. The commits are timestamped, messaged, and readable.
- **DEMO.** `git log --since="<event start>" --stat`. **And if you have not yet confirmed the pre-existing-code rule with an organiser, do that before judging, not during it.**

---

**Q20. "What happens if the model fails?"**

- **FACT.** It degrades to human review, not to a wrong answer.
- **TECHNICAL.** Three layers. Below 0.60 confidence nothing is priced. `SafetyGate` is deterministic and one-way — no model output can suppress a hazard rule, only raise one. And the vision channel is one of four: a missed crack can still be caught by tap response, and an appliance fault is detected by IR regardless of what the camera saw. If the segmenter fails to load entirely, the app still produces a signed report with the findings it does have.
- **DEMO.** If you have nerve and time: run the flow with the model deliberately unavailable and show that the report still generates. **Only show this if you have tested it first.**

---

## 14. HACKATHON SCORE OPTIMIZATION

Contribution ratings are **[INFERENCE]** from the published weights. There is no published formula and none is implied here. Priority is the operative column.

| Feature | End 30 | Nov 20 | Phone 15 | Depth 15 | OK 10 | Demo 10 | Impl. risk | Demo risk | **Priority** |
|---|---|---|---|---|---|---|---|---|---|
| **Rehearse full flow 3× timed + backup video** | High | — | Med | — | Low | **High** | **None** | **Eliminates** | **1** |
| **Confirm pre-existing-code rule with organiser** | — | — | — | — | — | — | None | None | **1 (equal)** |
| **Venue IR pattern capture** | High | **High** | Med | Med | — | **High** | Med | **High if skipped** | **2** |
| **Mirroring on + artefacts via Office Kit** | — | — | Low | — | **High** | Low | None | None | **3** |
| **Strip false claims from deck/README/script** | — | — | — | **High** | — | Med | None | **Eliminates** | **4** |
| **Exercise camera/mic every session (real work)** | Med | — | **High** | Low | — | — | None | None | **5** |
| Rupee balance sheet on PDF | **High** | Med | — | Med | — | **High** | — | — | **DONE** ✅ |
| Manifest permission removal + merged-manifest proof | Med | Med | — | **High** | — | Med | — | — | **DONE** ✅ |
| Validation metrics measured and quotable | — | — | — | **High** | — | — | — | — | **DONE** ✅ |
| On-device thermal benchmark (S1–S3) | Low | — | Med | **High** | — | — | Low | None | **6** |
| Reproducibility demo (same report twice) | Med | — | — | Med | — | Med | **Very low** | None | **7** |
| Single-class crack retrain | Med | — | — | Med | — | Low | **High** | **High** | **8 — only if 1–7 done** |
| NPU / HTP delegate | Low | Med | ? | Med | — | — | **Very high** | **Very high** | **CUT** ❌ |
| Local LLM / GenieX | Low | Med | ? | Med | — | Low | **Very high** | **Very high** | **CUT** ❌ |
| ARCore depth | Med | Med | Low | Med | — | Med | **Very high** | **Very high** | **CUT** ❌ |

**The strategic read.** Priorities 1–5 cost, in total, perhaps four hours and carry **almost no implementation risk**. They protect 40 points of End Product + Demo, bank most of the 25 telemetry points, and remove the single failure mode that most reliably destroys Technical Depth (a claim that collapses under one question). The three cut items cost many hours each, carry very high failure rates, and are already covered by honest fallback answers you have rehearsed.

### The biggest strategic question — Option A/B/C/D

| | A: Cloud-first | B: Laptop-first + phone demo | **C: Phone-first local AI** | D: Hybrid |
|---|---|---|---|---|
| Hackathon score | Poor | Poor | **Best** | Medium |
| Product quality | Medium | Medium | **Best for this product** | Medium |
| Technical depth | Low | Medium | **High** | Medium |
| Phone telemetry | Poor | Poor | **Best** | Medium |
| Reliability | Venue wifi dependent | Good | **Best — no network to fail** | Medium |
| Latency | Network-bound | n/a | Good | Medium |
| Privacy | **Worst** | Medium | **Best** | Medium |
| Demo strength | Weak | Weak | **Strongest** | Medium |
| Dev difficulty | Easiest | Easy | **Hardest — already paid** | Medium |

**Recommendation: C, and you are already there.** This is not a choice to make; it is a position to defend. The whole of §6.2 exists because you already built C. **Do not dilute it.** Adding a cloud fallback "just in case" would cost you the only claim in this project that no competitor can match.

---

## 15. TOP 10 RISKS

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| 1 | **Pre-existing-code rule voids the entry** | **Catastrophic** | Ask an organiser **now**. Disclose proactively. Make the checkpoint show in-event work. §11.1 |
| 2 | **Demo fails live** — 40 points on one run | **Critical** | Rehearse 3× timed. Record the backup video the moment a run succeeds. Control the scene |
| 3 | **Venue IR pattern never captured** — kills the differentiator | **Critical** | Capture it early. `WalkthroughScreen.kt:191` still prints "placeholder pattern" |
| 4 | **A false claim collapses under one question** (Hexagon NPU / GenieX / <30 ms / ARCore) | **High** | Strip all four from deck, README and spoken script. §16 |
| 5 | **Vision model misses the defect on stage** (recall 0.379) | **High** | Rehearse against one specific surface; confirm it fires repeatedly; bring your own prop. Keep `stain_mould` out entirely |
| 6 | **Telemetry never banked** because mirroring was off and building happened on the laptop | **Medium-High** | Mirroring on from now. Artefacts through Office Kit. Irrecoverable at hour 29 |
| 7 | **Demo phone heat-soaked from rehearsal** | Medium | Stop heavy work 10 minutes before presenting. Bind camera per capture, not continuously |
| 8 | Pitch runs long and never reaches the rupee figure | Medium | Demo by 0:40, rupee figure by 2:00. BATTLE_PLAN §6 |
| 9 | Someone attempts the NPU or LLM port at hour 20 because it sounds impressive | Medium | BATTLE_PLAN §9 anticipated this by name. **Do not.** |
| 10 | A banned statistic reaches a slide | Medium | ₹2,400 crore, 1.1 crore agreements, "38% of disputes" — **no source exists**. ">40%" must be labelled an industry estimate |

---

## 16. TOP 10 THINGS WE MUST NOT DO

1. **Do not game HackTracker.** No opening the camera for nothing, no fake inference loops, no idle mirroring left running to farm duration, no synthetic file transfers, no spoofing, no disabling or bypassing anything. Beyond the ethics: F4 says the package does not capture content, which means it likely cannot distinguish real work from theatre — and that is precisely what makes doing it dishonest rather than clever. Your product genuinely needs every sensor it touches. **Let the telemetry be a by-product of real work.**
2. **Do not claim the app runs on the Hexagon NPU.** [VERIFIED FALSE for the shipped build.] Assets contain `yolov8n_seg.ptl` and `acoustic_tap_model.json` only; the QNN bundle is in `ml/` and there is no ExecuTorch dependency. The honest line is BATTLE_PLAN's: *"Today the segmenter runs through PyTorch Lite on CPU. The QNN bundle is exported and in the repo; wiring the delegate is the next step, not a claim we're making now."*
3. **Do not claim GenieX, Llama, or any LLM.** [VERIFIED FALSE.] None exists in the app.
4. **Do not claim "<30 ms" inference, or any on-device latency you have not measured.** The only measured figure is 210.7 ms/image **on a laptop CPU**.
5. **Do not put ARCore or depth sensing in any diagram or sentence.** No dependency exists; availability on this hardware is recorded as unanswered.
6. **Do not quote ₹2,400 crore, 1.1 crore agreements, or "38% of disputes".** No source was ever found. ">40% of disputes" must be described as an industry estimate.
7. **Do not quote 86.7% acoustic accuracy without [0.621, 0.963] and n=15.** The interval is the credibility, not the caveat.
8. **Do not name prior hackathon winners as precedent.** Seven such claims failed verification 0-3. The stale digest at `~/.claude/skills/hackathon-pitch-deck/references/RESEARCH.md` §2 still asserts several of them as fact — **treat that file as retired**.
9. **Do not call the SHA-256 fingerprint a digital signature.** No keypair. It proves integrity, not authorship. The code comments already say this correctly — match them.
10. **Do not start the NPU port, the LLM, or a retrain** until §14 priorities 1–7 are complete. BATTLE_PLAN §9 predicted this temptation at roughly hour 20.

---

## 17. FINAL ARCHITECTURE

**Solid lines are shipping code, verified in this repository today. Bracketed italics are explicitly not built.**

```
                    iQOO 15 — NO INTERNET PERMISSION (removed at manifest level)
  ┌───────────────────────────────────────────────────────────────────────────┐
  │                                                                           │
  │  CAMERA ──────► CameraX 1.4.1 ──┬──► YOLOv8n-Seg (.ptl, 13.8 MB)          │
  │                                 │      PyTorch Mobile Lite → CPU          │
  │                                 │      mask mAP50 0.249 · crack AP50 0.05 │
  │                                 │                                         │
  │                                 └──► ML Kit OCR (on-device)               │
  │                                        lease terms · meter readings       │
  │                                                                           │
  │  MICROPHONE ──► 16 kHz tap capture                                        │
  │                   → Kotlin FFT → log-Mel → DCT/MFCC  (hand-written)       │
  │                   → logistic regression (JSON weights, ships with its CI) │
  │                       0.867 · 95% CI [0.621, 0.963] · n=15                │
  │                                                                           │
  │  SENSORS ─────► rotation vector → ArAlignmentTracker                      │
  │                   move-in ⇄ move-out viewpoint alignment (no ARCore)      │
  │                                                                           │
  │  IR EMITTER ──► ConsumerIrManager @ 38 kHz → appliance functional check   │
  │                                                                           │
  │         ▼                                                                 │
  │  ┌──────────────────────────────────────────────────────────────┐         │
  │  │  SafetyGate — deterministic, one-way: escalate only,          │         │
  │  │               no model output can suppress a hazard rule      │         │
  │  └──────────────────────────────────────────────────────────────┘         │
  │         ▼                                                                 │
  │  ┌──────────────────────────────────────────────────────────────┐         │
  │  │  DeductionEngine — deterministic rules + RepairTariff          │        │
  │  │  PRICING_CONFIDENCE_FLOOR = 0.60                               │        │
  │  │  below the floor ⇒ NOT PRICED ⇒ human review                   │        │
  │  └──────────────────────────────────────────────────────────────┘         │
  │         ▼                                                                 │
  │  HUMAN REVIEW ──► ReportGenerator (PdfDocument)                           │
  │                     deposit held · per-line basis · total · refund due    │
  │                     + honesty note printing the confidence floor          │
  │         ▼                                                                 │
  │  ReportFingerprint — SHA-256 over canonical record + file digest          │
  │         integrity, NOT a signature — no keypair                           │
  │         ▼                                                                 │
  │  Room / SQLite — property inspection timeline                             │
  │         ▼                                                                 │
  │  FileProvider — the only egress, user-initiated, one PDF at a time        │
  │                                                                           │
  │  SelfTest — on-device capability probe: SOC, ABIs, IR carriers,           │
  │             38 kHz support, per-camera focal lengths, degradation paths   │
  │                                                                           │
  │  ┈┈┈ [NOT BUILT: Hexagon/HTP delegate — .pte lives in ml/ only] ┈┈┈       │
  │  ┈┈┈ [NOT BUILT: local LLM / GenieX narration] ┈┈┈                        │
  │  ┈┈┈ [NOT BUILT: ARCore depth / metric dimensioning] ┈┈┈                  │
  └───────────────────────────────────────────────────────────────────────────┘
                              ▲
                              │  Office Kit — mirroring · file transfer · clipboard · remote
                              ▼
                     LAPTOP — build machine, analysis, digest verification
```

**Does this architecture give genuine reasons to use the phone continuously? Yes — and that was true before HackTracker existed.** Four physical sensing channels, all inference on-device, an artefact generated on-device, and no network path out. The phone is not the demo screen. It is the instrument.

---

## 18. FINAL RECOMMENDATION

**Ship exactly what you have, prove every word of it, and cut everything else.**

Concretely, in order, starting now:

1. **Ask an organiser about pre-existing code.** Ninety seconds. It is the only item that can void everything else.
2. **Capture the venue AC's IR pattern.** It gates your most differentiated claim and it is capturable only in this room.
3. **Mirroring on. Every artefact through Office Kit from here to the end.**
4. **Rehearse the full flow — airplane mode, cold start, three times, timed — and record the backup video the moment a run succeeds.**
5. **Strip Hexagon NPU, GenieX, <30 ms and ARCore from the deck, the README and the spoken script.**
6. Exercise camera, microphone and IR in every session, because the work requires it.
7. If hours genuinely remain: run S1–S3, then the reproducibility demo.
8. **Do not start the NPU, the LLM, or a retrain.**

---

## 19. ROADMAP — VIDEO-FIRST INGESTION (designed, not built)

A later proposal in the same session floated replacing the current single-shot capture flow with a video-sweep ingestion model: a 15-second guided pan per room, a gyroscope speed-gate that HUD-warns and buzzes if the pan is too fast, frame decimation via `MediaMetadataRetriever` every 500 ms, a Laplacian-variance blur gate, an ARCore-style wall-plane gate, batched Hexagon-NPU inference on the decimated frames, an IoU-based spatial deduplicator so one crack is logged once instead of once per frame, and a Llama 3.2 3B synthesis pass over the unified findings.

**Decision made explicitly, today, with 15+ hours still on the clock: do not build this now.** Not because the idea is weak — it is a genuinely stronger UX thesis than the checklist framing ("a natural sweep, not fifty individual photos" is a real novelty argument) — but because as specified it re-introduces every component §16 already told you to cut, and it replaces a working, tested, just-repaired capture pipeline with five new subsystems (video capture, frame decimation, blur/plane gating, NPU batching, LLM synthesis) that do not exist in this codebase in any form today. `CameraController.kt` is single-shot `ImageCapture`; there is no `VideoCapture`, no `MediaMetadataRetriever`, no ARCore, no ExecuTorch/HTP delegate, no LLM anywhere in `app/src/main/java`. A full rewrite the same day as judging is exactly the failure mode BATTLE_PLAN §5 and this document's §16 were written to prevent.

**What to do with it instead: narrative only, no code changes.** Use it as an explicitly-labelled roadmap slide or spoken line — the same honesty move already applied to the NPU delegate and GenieX elsewhere in this document (§6.3, §16 item 10): say what you built, say what you'd build next, and never blur the line between them.

**Suggested deck slide / spoken line — drop-in, do not modify without re-checking against §16:**

> **Today:** guided single-shot capture per room, wired and working — camera, alignment, acoustic tap, IR, and a deterministic pricing engine, entirely offline.
>
> **Next iteration:** a video-sweep capture mode — one guided pan per room instead of a shot list, gated in real time by the gyroscope so a too-fast pan gets corrected before the user finishes, then decimated and deduplicated offline. Designed. Not built. We'd rather ship the smaller thing that's true than the bigger thing that isn't.

**If a judge asks "why didn't you build the video version?"** — the honest answer, consistent with every other admission in §13: *"We scoped it, and decided a five-subsystem rewrite — video capture, frame decimation, NPU batching, an LLM synthesis pass — the same day as judging was the wrong bet against a working single-shot pipeline we'd already tested. We'd rather demo the smaller thing that's real."* That is a Technical Depth answer, not a concession.

**If you revisit this after the event**, with time to actually build and validate it, the individual pieces rank very differently on risk: the gyroscope speed-gate is cheap, self-contained UX polish that could sit on top of the *existing* single-shot flow without touching video, NPU, or LLM at all — see the "cherry-pick" option this session declined in favour of narrative-only. Frame decimation + blur/plane gating + IoU dedup is a substantial but bounded CV engineering task. NPU batching and LLM synthesis remain, as everywhere else in this document, the highest-risk, least-validated pieces, and should be attempted last, if at all.

---

## IF I WERE TRYING TO BEAT 100 STRONG TEAMS, I WOULD DO THIS

I would stop trying to be impressive and start being **unfalsifiable**.

Here is the actual competitive situation, without flattery. Your model is weak — mask mAP50 0.249, and a crack detector at AP50 0.052 is not a crack detector. Your acoustic result rests on fifteen taps. You have no LLM, no NPU path, and no depth sensing, and three of your loudest slide claims are false in code as of this morning. If you walk in and pitch SmartLease Edge as an AI product, a judge with an ML background will take it apart in two questions, and you will deserve it.

But that is not the product you have. The product you have is something rarer, and I do not think you have noticed how rare.

**You have a phone that cannot lie.** Not "offline" in the sense that ninety other teams mean it — airplane mode toggled on for three minutes. Offline in the sense that the operating system will refuse the syscall, because `INTERNET` is stripped at the manifest level, and you stripped it *after discovering that ML Kit was quietly merging it back in*. That is not a feature. That is a finding. It is the kind of thing you only discover by actually reading your own merged manifest, and there will not be five teams in that building who have read theirs.

**And you have an engine that refuses to bill people.** `PRICING_CONFIDENCE_FLOOR = 0.60`, printed on the report itself. Every team in that room will demo a system that produces an answer. You can demo a system that declines to. In a domain where the output is somebody's deposit, a refusal is not a limitation — it is the entire argument for why this should exist at all.

So here is what I would do with the remaining hours.

**I would make honesty the weapon, not the apology.** Open the technical Q&A with the bad number before anyone asks for it: *"Our detector gets mask mAP50 0.25 — and it used to say 0.66, until we hashed our own images and found 81% of the test set had leaked into training. That is not good enough to bill someone's deposit on. That is why the engine refuses to price anything below sixty percent confidence, and why that floor is printed on the report."* Watch what happens to the room. Judges spend all day being pitched at by people inflating, and the first team that hands them a real number and a real design response to it becomes the team they remember. If the research log's stack-ranking inference holds here — and it is an inference, not a rule — then being *memorable to several judges* beats being uniformly adequate, and nothing is more memorable at a hackathon than candour.

**I would build the entire demo around one image nobody else can produce:** a phone in airplane mode telling you the air conditioner works. Not a photo of an AC. Not a classification of an AC. A 38 kHz command leaving the emitter, the unit responding, the finding landing in the report, with a rupee figure at the end. Every competitor documents how a flat looks. You are the only team that tests whether anything in it functions. That is thirty seconds of demo and it is worth more than every remaining line of code you could write today.

**I would refuse, flatly, to touch the NPU or the LLM.** Not because they are bad engineering — because at this hour they are a bet where the upside is a slightly better sentence in your pitch and the downside is a dead demo. Your battle plan already told you this would tempt you around hour twenty. It was right. The honest fallback answer — *"the QNN bundle is exported and in the repo, wiring the delegate is the next step, not a claim we're making now"* — is a **better** answer than a half-wired delegate, because it demonstrates judgement, and judgement is what Technical Depth actually measures.

**I would bank the twenty-five points that require no talent whatsoever.** Mirroring on. Builds on the phone where feasible. Every artefact through Office Kit. Camera, microphone and IR exercised every session because the work genuinely needs them. That quarter of the scoreboard cannot be argued for at hour twenty-nine and cannot be recovered once it is gone, and it costs you nothing but the discipline to start now instead of later.

**And I would rehearse until it is boring.** Three timed runs, airplane mode, cold start, backup video recorded the moment one succeeds. Forty points ride on a single live run, and a polished deck in front of a broken demo is the single most reliably punished pattern in every judging source your own research reviewed.

The teams that beat you will have shinier models. Let them. You are not going to win the model contest today and you should stop trying to. **You win by being the only team in the room whose every claim survives being checked** — and by being the only one who tells a judge which of them wouldn't, before they ask.

That is not a consolation strategy. Against a hundred teams who are all overclaiming, it is the sharpest edge available.

---

*Ground truth: `docs/BATTLE_PLAN.md`, `docs/research/IQOO_CHENNAI_2026_RESEARCH_LOG.md`. Repository state verified directly on 2026-09-12 against `app/src/main/AndroidManifest.xml`, `app/src/main/assets/`, `gradle/libs.versions.toml`, and the `deduction/`, `report/`, `safety/`, `vision/`, `acoustic/`, `diagnostics/` packages. Official iQOO/Reskilll pages could not be fetched — see §0.*
