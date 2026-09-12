# SmartLease Edge — Execution Plan

iQOO Hackathon 2026 · Chennai City Battle · Sep 12–13 2026 · consolidated 2026-09-12, mid-event

This is the single action-tracking document. It does not re-argue strategy — that's already
done in `docs/BATTLE_PLAN.md` (priorities and honesty position) and
`docs/HACKTRACKER_STRATEGY.md` (HackTracker/thermal/judge-defense deep dive). This file exists
to answer one question at a glance: **what's actually done, what's actually open, and who or
what closes it.**

Status verified directly against the repository at the commit noted per item — not assumed.
Re-check before trusting a ✅ that's more than a few hours old; code and reality can diverge.

---

## Status legend

- **✅ DONE** — verified in code/repo, with the file/commit that proves it.
- **🔧 CODE DONE, NEEDS ON-DEVICE VERIFICATION** — compiles/exists, hasn't been run on the real handset yet.
- **❌ OPEN — CODE-EXECUTABLE** — nothing physical required, just needs someone to write it.
- **⛔ OPEN — REQUIRES A HUMAN/PHYSICAL ACTION** — no amount of code closes this; needs a person, a room, or a conversation.
- **🚫 DELIBERATELY CUT** — considered and rejected for this event; do not start it.

---

## 1. Core product — jury-scored 75%

| Item | Status | Evidence / next step |
|---|---|---|
| Deduction engine wired into report, rupee balance sheet on PDF | ✅ DONE | `ReportGenerator.kt:73` calls `DeductionEngine.summarise`; PDF prints deposit/lines/total/refund + honesty note. Commit `94cc5ea3`. |
| IR-failure billing bug (tenant billed for phone's own IR failure) | ✅ DONE | Commit `75cb7878`, "stop billing the tenant for the phone's own IR failure." |
| Report screen layout bug (header swallowing screen) | ✅ DONE | Commit `356a2901`. |
| Manifest permission list matches actual build behaviour | ✅ DONE | Commit `c7a86287`; `INTERNET`/`ACCESS_NETWORK_STATE` explicitly removed via `tools:node="remove"`. |
| README competitor table "On-device NPU" claim | ✅ DONE | Corrected to "On-device CPU" this session, commit `6d330997`. |
| Vision segmenter (YOLOv8n-Seg via PyTorch Lite, CPU) | ✅ DONE, weak | mask mAP50 0.267, crack recall 0.379 — measured, not placeholder. `ml/YOLOV8/val_metrics.json`. |
| Acoustic tap classifier (logistic regression, ships own CI) | ✅ DONE, small-n | LOGO 0.867, 95% CI [0.621, 0.963], n=15 taps/8 recordings. |
| On-device self-test: inference latency benchmark (S1/S2-equivalent) | ✅ DONE — **extended this session** | `SelfTest.kt` already ran cold-load + 10 timed inference runs (mean/min/max) for both models. This session added a `PERFORMANCE` section: battery % and `PowerManager` thermal status, read before/after the warm-up loop. Compiles clean (`./gradlew compileDebugKotlin` — BUILD SUCCESSFUL). **Not yet run on a physical iQOO handset** — 🔧. |
| Venue AC IR pattern capture | ⛔ OPEN, candidate now testable in-app | `WalkthroughScreen.kt`'s scripted demo path is unchanged — still the honestly-labeled generic NEC placeholder. 2026-09-12: photographed the venue remote, model "YY-107A"; a third-party reseller lists it O General-compatible (unverified against the manufacturer); O General ACs commonly run the documented Fujitsu_AC protocol. Built a real port of the library's 7-byte short (toggle) frame — `FujitsuAcCandidate.kt` (encoder) + `FujitsuAcCandidateTiming` (timing constants), both sourced from IRremoteESP8266, not invented — and wired `IrController.transmitFujitsuCandidate()`. Exposed as two buttons on the **Self-Test screen** ("Send candidate ON/OFF"), clearly labeled UNVERIFIED and kept separate from the demo flow. Compiles clean. **Still not confirmed to work — the next and only remaining step is standing in front of the venue unit and pressing those two buttons to see if it reacts. If yes: promote it into the demo path and update every "not verified" disclosure. If no: it cost nothing, the placeholder demo path is untouched.** |
| Full-flow rehearsal, 3× timed, airplane mode, backup video | ⛔ OPEN | No video file or rehearsal log found in repo. Needs to actually happen. |

## 2. HackTracker telemetry — 25%

| Item | Status | Evidence / next step |
|---|---|---|
| Office Kit mirroring on from hour one | ⛔ OPEN — unverifiable from code | Behavioral. Only you know if it's happening. |
| Every artefact (APKs, PDFs, tap WAVs) routed through Office Kit file transfer | ⛔ OPEN — unverifiable from code | Same. |
| Camera/mic/IR exercised every working session (not just at demo time) | ⛔ OPEN — unverifiable from code | Same. This is free — it's just using the app you're building, honestly, as you build it. |
| Digest-verification clipboard use (copy SHA-256 off-device, recompute on laptop) | ❌ OPEN — CODE-EXECUTABLE, near-zero work | The mechanism (`ReportFingerprint`) already exists; this is a workflow habit, not a build task. Just do it once and remember it as a demo beat. |

## 3. Risk — highest severity first

| Item | Status | Next step |
|---|---|---|
| Pre-existing-code rule confirmed with an organizer | ✅ **RESOLVED 2026-09-12 — verbal confirmation, on-site** | Organiser asked directly, confirmed pre-existing code is allowed and judging looks at what's built on top of it during the event. Labelled [FIRST-PARTY, UNVERIFIED BY ME] per this doc's own discipline — a spoken confirmation, not a written rule — but treated as authoritative, same as every other organiser statement already relied on in `HACKTRACKER_STRATEGY.md`. Removes the one risk that could void everything else. Still keep committing with clear messages — the standard confirmed is literally "what you built on top," and `git log` is the evidence. |
| False claims stripped from deck (`.pptx`) | ✅ DONE | `docs/pitch/PITCH_DECK_PROMPT.md` carries a "SUPERSEDED IN PART — 2026-09-12" notice; both the `.md` and `.pptx` share an 11:37 timestamp today, evidence of an actual regeneration pass, not just a note. Hexagon NPU, GenieX/Llama, dual signatures, ARCore depth, inflated sample sizes — all corrected per that note. |
| False claims stripped from README | ✅ DONE | Verified clean via grep for Hexagon/NPU/GenieX/Llama/ARCore/`<30ms`/banned rupee-crore stats — all either absent or already correctly labelled "not currently used." One residual contradiction fixed this session (§1 above). |
| `AUDIT_CLAIMS.md` referenced by `PITCH_DECK_PROMPT.md` but not present in repo | ⚠️ noted, not urgent | Dangling reference — may have lived outside the repo. Doesn't block anything; just means you can't point back to it if asked what exactly was audited. |

## 4. Deliberately cut — do not start these

| Item | Why |
|---|---|
| 🚫 Hexagon NPU / ExecuTorch / QNN HTP delegate | Not wired into the APK. `.pte` + `libQnnHtp.so` exist only in `ml/YOLOV8/qnn_android_bundle/`, lowered for the wrong SoC (SM8650, not this device's), missing a required `.so`. High effort, high failure rate, already has an honest fallback answer. |
| 🚫 Local LLM / GenieX / Llama 3.2 3B | Doesn't exist anywhere in `app/src/main/java`. `synthesizeNarrative()` is rule-based templating. Cutting this is a *feature*, not a gap — see HACKTRACKER_STRATEGY.md §6.3 for the judge-facing argument. |
| 🚫 ARCore / depth sensing | No dependency in `gradle/libs.versions.toml`. Availability on this hardware is an unanswered research item — never claim it. |
| 🚫 Video-first ingestion (gyroscope speed-gate + frame decimation + IoU dedup + NPU batching + LLM synthesis) | Proposed this session, explicitly declined as a build task with 15+ hours left. Would replace a working, tested, just-repaired single-shot pipeline with five new subsystems. Logged as roadmap-only in `HACKTRACKER_STRATEGY.md` §19 — narrative use only, no code. |
| 🚫 Single-class crack model retrain | Would likely improve the weakest number (recall 0.379) but needs a training run, re-export, re-validation, re-integration — not worth the risk unless everything else here is done with hours to spare. |

---

## What actually needs a person right now, today

Everything in this plan that's marked ✅ or 🔧 is closed or nearly closed by code that already exists. The pre-existing-code risk is resolved. What's left that *matters* is not more code — it's:

1. **Capture the venue AC's real IR pattern**, in the room, on the actual unit. Now the single highest-priority open item.
2. **Rehearse the full flow three times, timed, airplane mode, and record the backup video the moment one succeeds.**
3. **Run the extended `SelfTestScreen` at least once on the actual iQOO handset** — it now reports battery and thermal status alongside inference latency, so this closes the "not measured on-device" gap in the Device Benchmark Plan cheaply. Screenshot it.
4. **Turn Office Kit mirroring on and leave it on.** Free telemetry, zero risk, zero code.
5. **Keep committing with clear messages.** The organiser's confirmation makes "what was built on top" the explicit standard — your `git log` is now literally the evidence being judged, not just a courtesy.

Nothing else on this list should get touched before those.

---

*Consolidates: `docs/BATTLE_PLAN.md`, `docs/HACKTRACKER_STRATEGY.md`, and direct repository verification performed 2026-09-12 (README, `AndroidManifest.xml`, `SelfTest.kt`, `WalkthroughScreen.kt`, `docs/pitch/`, git log). Superseded the moment any item above changes — re-verify, don't assume.*
