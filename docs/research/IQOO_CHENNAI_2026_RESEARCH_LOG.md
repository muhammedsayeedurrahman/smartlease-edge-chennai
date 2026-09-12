# Research Log — iQOO Hackathon 2026, Chennai City Battle

Run date: 2026-09-12 (event day). Method: 5-angle fan-out web search, 26 sources fetched, claims extracted and put to a 3-vote adversarial verification panel (2 of 3 refutes kills a claim).

**Run outcome: partial.** 79 of 108 agents completed; 29 failed against a session limit, including the final synthesis step. There is therefore no auto-generated cited report — this log is the hand-assembled record of what survived verification, what did not, and what was never answered.

Run ID `wf_e5e44946-90e`. Per-agent transcripts: `.claude/projects/.../subagents/workflows/wf_e5e44946-90e/journal.jsonl`.

---

## 1. Confirmed claims

| # | Claim | Vote | Source |
|---|---|---|---|
| C1 | The iQOO 15 ships an **infrared blaster** as a listed hardware sensor — the `ConsumerIrManager` 38 kHz appliance demo is hardware-feasible on the target device | 2-0 | iqoo.com official spec page |
| C2 | Qualcomm's on-site edge-AI rubric is 100 points: **Technical Implementation 40, Use-Case & Innovation 25, Local Processing & Privacy 15, Deployment & Accessibility 10, Presentation & Documentation 10** | 3-0 | Qualcomm Official Rules PDF (Edge AI Developer Hackathon, New York) |
| C3 | Qualcomm rules **forbid closed-source pre-existing code**; a pre-existing proposal must be "significantly modified to add the AI models"; screening scores whether scope is achievable in the build window | 3-0 | same PDF |
| C4 | MLH's recommended winner selection is **stack ranking**, not additive scoring: each judge nominates a top three worth 3/2/1 points; winners recur across judges' lists | 2-1 | MLH judging guide |

### Why each matters here

- **C1** was the single binary go/no-go in the whole plan. Had the iQOO 15 lacked an IR emitter, an entire demo leg and a rubric differentiator would have died. It is confirmed present.
- **C2** is not this event's rubric, but it is the same sponsor family and shows where silicon vendors put weight: engineering evidence far above pitch polish. iQOO's published weights (End Product 30 / Novelty 20 / Creative Phone Use 15 / Technical Depth 15 / Office Kit 10 / Demo 10) rhyme with it.
- **C3** is the most actionable constraint found. This project carries roughly eleven days of pre-event work in a public repo. Qualcomm's wording would require significant in-event modification. **iQOO/Reskilll's specific rule was not verified** — confirm with an organiser on arrival.
- **C4** changes pitch strategy: if judges nominate a top three rather than score every criterion, being *memorable to several judges* beats being uniformly competent. One vivid, working moment outperforms five half-features.

## 2. Refuted claims — do not cite these

All of the following failed verification 0-3 and must not appear in a deck, README, or spoken answer.

| Claim | Note |
|---|---|
| Qualcomm Bengaluru 2025 Grand Prize went to a project because it showed a complete end-to-end on-device pipeline | Refuted 0-3 |
| GameSense (Tech Mahindra) ran Llama 3.2 3B on Hexagon via Qualcomm AI Runtime + GENIE | Refuted 0-3 |
| All five Qualcomm Korea 2025 winners were end-user application products | Refuted 0-3 |
| Korea winners composed off-the-shelf open models rather than training custom ones | Refuted 0-3 |
| Llama-3.2-3B-Instruct was used by two of five Korea winning teams | Refuted 0-3 |
| Winners marketed NPU-local execution as the product value proposition | Refuted 0-3 |
| MLH budgets ~4 minutes per project and enforces a hard 3-minute cap | Refuted 0-3 |

**Important:** several of these appear as asserted fact in `~/.claude/skills/hackathon-pitch-deck/references/RESEARCH.md` (section 2). That digest was written 2026-09-06 and its prior-winner claims did not survive re-checking. Whether the sources genuinely fail to support them or the pages were unreachable during verification, the consequence is identical: **do not name prior hackathon winners as precedent.** Staking credibility on an unverifiable claim in front of a sponsor's own engineers is a pure downside bet.

## 3. Contested (1-2 and 2-1 splits — treat as weak)

- Qualcomm's 40-point Technical Implementation is scored on NPU utilisation, SLM optimisation, latency and energy sub-dimensions (1-2).
- Fully on-device execution is a separately scored 15-point line item (1-2).
- The Windows on Snapdragon hackathon included an explicit "best use of Qualcomm AI Hub models" sponsor-stack criterion (1-2).
- MLH requires a submission video showing a working demo rather than slides (1-2).

Directionally consistent with C2; not solid enough to quote with a number attached.

## 4. Unanswered — killed by the session limit

The entire device-and-toolchain angle died before voting. None were recovered:

- ARCore device certification and **ARCore Depth API availability on vivo/iQOO** hardware
- Whether Ultralytics ships a first-party QNN exporter, and whether that path yields a `.pte`
- YOLOv8 **Segment** task support on the QNN/ExecuTorch path
- Snapdragon 8 Elite HTP v79 specifics
- Whether Qualcomm publishes a pre-optimised YOLOv8-seg
- iQOO 15 chipset confirmation beyond the spec page

**Assessed as non-blocking.** Every one sits on the NPU or ARCore branch. The shipping app uses neither: `ArAlignmentTracker` is SensorManager rotation-vector only with no ARCore dependency, and vision inference runs through PyTorch Lite on CPU. Resuming the run to answer them would spend tokens on work that is not happening inside the event window.

## 5. Also unanswered — no sources found

- **Bengaluru (Aug 29-30) and Pune (Sep 5-6) City Battle winners.** These had already happened and would have been the most directly relevant precedent available. Nothing surfaced.
- Chennai venue specifics, Grand Finale prize split.

## 6. How to resume, if ever wanted

```
Workflow({
  scriptPath: ".../workflows/scripts/deep-research-wf_e5e44946-90e.js",
  resumeFromRunId: "wf_e5e44946-90e"
})
```

Completed agents replay from cache; only the 29 failures and synthesis re-run. Session limit resets 01:40 IST. **Recommendation: do not.** See section 4.

---

## 7. Local evidence generated during this session

Not from the web — measured here, and more decision-relevant than anything the search returned.

### 7.1 Vision model validation (new)

`best.pt` validated against the 203-image held-out `unified_defects` validation split. Results committed to `ml/YOLOV8/val_metrics.json`.

| Class | Images | Instances | Box mAP50 | Mask mAP50 | Mask mAP50-95 |
|---|---|---|---|---|---|
| **all** | 203 | 785 | 0.302 | 0.267 | 0.136 |
| crack | 104 | 298 | 0.322 | 0.276 | 0.099 |
| peeling | 41 | 120 | 0.168 | 0.148 | 0.085 |
| spalling | 85 | 242 | 0.585 | 0.553 | 0.326 |
| stain_mould | 58 | 125 | 0.132 | 0.092 | 0.034 |

Crack precision 0.452, **recall 0.379**. Inference 210.7 ms/image on laptop CPU.

Reproduce with an absolute-path data yaml (the committed `data.yaml` uses `path: .`, which ultralytics resolves relative to the wrong directory):

```
python -c "from ultralytics import YOLO; YOLO('best.pt').val(data='<abs>/unified_defects.yaml', imgsz=640, split='val', device='cpu')"
```

**Reading of the result.** The merged six-dataset corpus spans bridge concrete, pavement and interior walls — domains that do not share visual statistics — and the model is diluted across them. `spalling` is the only strong class and the least relevant to a rental flat. `stain_mould` at AP50 0.092 is effectively non-functional and must stay out of the live demo path. `crack`, the hero class, misses roughly six of every ten instances. The `crack-seg` source alone carried 3,717 images and would likely have produced a far stronger single-class detector; there is no time to retrain inside the event.

This number is why `DeductionEngine` refuses to price any detection below 60% confidence or any heuristic hit. Billing a tenant against a 0.267 mAP50 detector is the attack an ML-literate judge would run, and the architecture should answer it before it is asked.

### 7.2 Acoustic model (previously recorded)

`ml/models/acoustic_report.json`: LeaveOneGroupOut accuracy 0.867 (LogisticRegression), **95% CI [0.621, 0.963]**, majority baseline 0.533 — over **15 taps from 8 source recordings**. Honest phrasing: beats baseline, sample far too small for a confidence claim. Do not quote 86.7% without the interval.

### 7.3 Repo state versus the plan document

Audited against `SmartLease_Edge_Complete_App_Structure_and_Team_Matrix.pdf`. Build green (`assembleDebug`, exit 0, 114 MB APK).

| Plan claim | Actual | Status |
|---|---|---|
| YOLOv8n-Seg INT8 on Hexagon NPU, <30 ms | Ships as `yolov8n_seg.ptl` via **PyTorch Lite on CPU**. `.pte` + `libQnnHtp.so` exist in `ml/YOLOV8/qnn_android_bundle/` but are not in app assets and there is no ExecuTorch dependency | **False** |
| Llama 3.2 3B via GenieX | `ReportGenerator.synthesizeNarrative()` is a commented placeholder; no GenieX in app code | **False** |
| SHA-256 fingerprint | Absent; file comment states it is not a cryptographic signature | **Was false — being fixed** |
| ₹2,400 deducted from ₹90,000 | No rupee logic anywhere in the codebase | **Was false — being fixed** |
| Dual on-screen signatures | No signature canvas | **False** |
| ARCore depth for area math | SensorManager rotation vector only | **False** |
| Acoustic tap classifier | Real, trained, wired at `WalkthroughScreen.kt:54` | **True** |
| IR transmit | Real `ConsumerIrManager` wrapper, wired; pattern still placeholder pending on-site capture | **Partly true** |
| ML Kit OCR, CameraX, Room, offline PDF | Real and wired | **True** |
| No INTERNET permission | Confirmed absent from manifest | **True** |

Also found: `ACCESS_FINE_LOCATION` requested but never used (no reader anywhere in the tree), and a dead second package `app/src/main/java/com/iqoo/multimodal/` carrying its own `MainActivity`.

## Changelog

- 2026-09-12: initial log. Partial run (79/108 agents, synthesis failed on session limit). Four confirmed claims; seven prior-winner claims from the 2026-09-06 skill digest refuted 0-3 and retired from use. Added local evidence: first-ever vision validation metrics, acoustic interval, and full claim-versus-code audit.
