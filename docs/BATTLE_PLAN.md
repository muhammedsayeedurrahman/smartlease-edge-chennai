# SmartLease Edge — Chennai Battle Plan

iQOO Hackathon 2026 · Chennai City Battle · Sep 12-13 2026 · Smart Living track · 30 hours

Companion: `docs/research/IQOO_CHENNAI_2026_RESEARCH_LOG.md` (evidence and provenance for everything asserted here).

---

## 1. Where you actually stand

Three things are true at once, and the plan follows from holding all three.

**You have a genuinely strong premise.** Four different sensors — camera, microphone, IR emitter, OCR — feeding one signed artifact, entirely offline, with no `INTERNET` permission in the manifest. Most teams at a phone-first hackathon will ship a cloud API with a mobile front end. You are the opposite of that, and it is verifiable rather than claimed.

**Your headline model is weak.** Mask mAP50 0.267 across four classes; crack recall 0.379. The detector misses roughly six of every ten cracks. This is now measured, not suspected.

**Several of your loudest claims are currently false in code.** The Hexagon NPU path, GenieX/Llama 3.2 3B, SHA-256, the rupee deduction, dual signatures, ARCore depth. Two of those are being fixed today; the rest cannot be built inside the window.

The winning line is therefore **not** to build more. It is to make a narrower set of claims all of which are true, and to land one live offline run that reaches a rupee figure without stumbling.

## 2. Decoding the rubric

| Criterion | Weight | What actually moves it |
|---|---|---|
| End product | 30 | One complete flow that works when a judge watches it cold |
| Novelty & impact | 20 | Functional testing, not visual documentation — "does the AC work", not "does it look fine" |
| Creative phone use | 15 | **Device telemetry.** Accrues from using camera / mic / IR, not from arguing |
| Technical depth | 15 | Honest numbers with provenance; the ability to answer "how do you know" |
| Office Kit usage | 10 | **Device telemetry.** Mirror, clipboard, file transfer, phone-first builds |
| Demo | 10 | Reaching the punchline inside the time, on the phone |

**Two structural facts.**

*Twenty-five points are telemetry, not opinion.* Creative Phone Use plus Office Kit accrue mechanically from how you work during the 30 hours. This is the cheapest quarter of the scoreboard available and needs discipline, not cleverness. Mirror on from hour one. Build on the phone where feasible. Exercise camera, mic and IR in every session rather than only at the end.

*Forty points ride on one live run.* End Product plus Demo is judged from a flow seen cold. A polished deck in front of a broken demo is the single most reliably punished pattern in every judging source reviewed.

**One inference from the research.** MLH's published method is stack ranking — judges nominate a top three rather than score each criterion additively (confirmed 2-1). If any of that logic operates here, being *memorable to several judges* beats being uniformly adequate. One vivid working moment outperforms five half-features. Design the demo around a single thing nobody else will show: a phone that tells you an appliance works, while in airplane mode.

## 3. Constraints and risks to clear

**Pre-existing code — RESOLVED, verbally, on-site (2026-09-12).** Qualcomm's rules for its own edge-AI events forbid closed-source pre-existing code and require a prior proposal to be "significantly modified" during the event (confirmed 3-0) — that was always a different event, cited only as a directional signal. **iQOO/Reskilll's own organisers were asked directly and confirmed verbally that pre-existing code is allowed; judging looks at what is built on top of it during the event window.** This is [FIRST-PARTY, UNVERIFIED BY ME — an organiser's spoken word, not a written rule] but is treated as authoritative per the same standard applied to every other organiser statement in this project's research. **Consequence: keep committing, and keep the Round-1 checkpoint (Sat 19:00-22:00) showing substantial in-event work** — not because the rule demands it as a pass/fail gate, but because "what was built on top" is now explicitly the thing being judged, and your commit history is the evidence for that.

**The IR pattern is your one irreplaceable on-site dependency.** The iQOO 15's IR blaster is confirmed present (2-0, official spec page), so the hardware is not in doubt — but the venue AC's actual timing pattern can only be captured in the room. Do it early, not at hour 28. `CommonAcIrProfiles` currently carries carrier frequencies only, and `WalkthroughScreen.kt:191` still prints "placeholder pattern".

**The vision model will miss things on camera.** With recall 0.379 you cannot point the phone at an arbitrary wall and trust it. Control the scene: rehearse against one specific surface, confirm it fires repeatedly, and bring your own prop if the venue wall does not trigger it. Keep `stain_mould` (AP50 0.092) out of the demo path entirely.

**Do not name prior winners.** Seven prior-winner claims from the earlier research digest failed verification 0-3. Citing them in front of a sponsor's own engineers is a pure downside bet.

**Numbers you may not use.** ₹2,400 crore, 1.1 crore agreements, 38% of disputes — no source was ever found. The ">40% of disputes" figure is an industry estimate and must be described as one if challenged. Do not quote 86.7% acoustic accuracy without its interval [0.621, 0.963] over 15 taps.

## 4. Priority order for the 30 hours

Strictly ordered. Do not start an item before everything above it is done.

**1 — Make the money moment real.** The demo currently has no ending: there is no rupee logic anywhere in the codebase, yet "₹2,400 deducted from ₹90,000" is the punchline of every slide. Wire `DeductionEngine` into `ReportGenerator`, persist structured `detailJson`, print the balance sheet on the PDF. Nothing else on this list matters as much.

**2 — Bank the telemetry 25%.** Office Kit mirroring on from the start. Phone-first build sessions. Exercise every sensor in every session.

**3 — Capture the venue AC's IR pattern.** Early. It gates your most differentiated claim.

**4 — Rehearse one flow, timed, at least three times.** Airplane mode on, baseline, scan, tap, IR, report. One presenter narrates, one drives. Record a backup video the moment a run succeeds.

**5 — Strip every false claim.** Delete "<30 ms" and "Hexagon NPU" and "GenieX" from deck, README and spoken script unless wired. The people scoring Technical Depth are the most likely to ask, and a claim that collapses under one question costs more than the claim ever earned.

**6 — Only then: NPU or GenieX.** Highest effort, highest failure rate, and both are already covered by an honest fallback story. Attempt only with hours to spare.

## 5. The honesty position — and why it scores

Judges reward candour and punish polished-deck-thin-code; this is the most consistent finding across every judging source reviewed. You have unusually good material for this.

Say plainly: *"Our detector gets mask mAP50 0.27 across four classes on mixed public data. That is not good enough to bill someone's deposit on, so the engine refuses to price anything below 60% confidence or anything the colour heuristic flagged — those go to human review. What we assert is the measurement and the signed record, not an infallible detector."*

That answer converts your weakest number into evidence of engineering judgement. `DeductionEngine.PRICING_CONFIDENCE_FLOOR` exists in code precisely so this answer is demonstrable rather than rhetorical.

Same move on the fingerprint: it is a SHA-256 integrity digest, **not** a digital signature. There is no keypair, so it proves the record has not changed, not who made it. Saying so before being asked is worth more than the claim you would be defending.

## 6. Pitch structure (3 minutes, hard stop)

| Time | Content |
|---|---|
| 0:00-0:20 | One named person, one sentence of pain. "Priya, Anna Nagar, ₹90,000 deposit, twelve WhatsApp photos, no move-in record." |
| 0:20-0:40 | The gap: existing tools document how a flat *looks*. None test whether anything *works*, and none work offline. |
| 0:40-2:20 | **Live demo, airplane mode visible.** Baseline, scan, knuckle tap, IR at the AC, signed report with the balance sheet. Reach the rupee figure by 2:00. |
| 2:20-2:40 | Why it can only be this phone: NPU, IR emitter, sensing hub, no network permission in the manifest. |
| 2:40-3:00 | Honest status and close. Stop talking and invite questions. |

Reach the demo by 40 seconds. Every source reviewed agrees that the demo, not the framing, is what is scored — and a pitch that runs long gets cut before the punchline.

## 7. Q&A preparation

| Question | Answer |
|---|---|
| What is your accuracy? | Mask mAP50 0.267 across four classes, crack recall 0.379, measured on a 203-image held-out split; `ml/YOLOV8/val_metrics.json`. Which is why the pricing engine refuses low-confidence detections. |
| Is this a legal document? | It is a tamper-evident record both parties can verify by recomputing the digest. Not a digital signature — no keypair, so it proves integrity, not authorship. |
| Why not just use ChatGPT and a photo? | It needs network, it uploads a tenant's home to a third party, and it cannot tell you whether the AC works. |
| Does it run on the NPU? | Today the segmenter runs through PyTorch Lite on CPU. The QNN bundle is exported and in the repo; wiring the Hexagon delegate is the next step, not a claim we are making now. |
| Where do the repair rates come from? | Team-compiled Chennai contractor estimates, printed as estimates. `RepairTariff.kt` documents the provenance; real deployment replaces them with quotes. |
| Only 15 tap samples? | Correct, and the interval is wide: 0.867 with 95% CI [0.621, 0.963] against a 0.533 baseline. It beats chance; it is not yet a product claim. |

## 8. Engineering changes made on event day

| Change | Status |
|---|---|
| `deduction/DeductionModels.kt`, `RepairTariff.kt`, `FindingDetail.kt`, `DeductionEngine.kt` | Written |
| `report/ReportFingerprint.kt` — SHA-256 over canonical record plus file digest | Written |
| `report/ReportModels.kt` — carries deductions and digest | Edited |
| Wire into `ReportGenerator` and `WalkthroughScreen` | **Outstanding** |
| Remove unused `ACCESS_FINE_LOCATION` | **Outstanding** |
| Delete dead `com/iqoo/multimodal/` package | **Outstanding** |
| First-ever vision validation metrics | Done, `ml/YOLOV8/val_metrics.json` |

Design note: `RepairTariff` uses a per-sq-ft rate with a minimum call-out floor, so a small crack costs ₹1,500 because a contractor charges for the visit — the plan document's figure emerges from real logic instead of being hardcoded.

## 9. What to hold on to

You will be tempted, somewhere around hour 20, to start wiring the NPU because it sounds impressive. Do not. A working offline flow that ends in a rupee figure, defended with honest numbers, beats a half-wired Hexagon delegate that fails on stage — and the rubric's own weights say so: End Product 30, Technical Depth 15.

The one thing no other team will show is a phone that proves an appliance works while in airplane mode. Build the whole demo around that.
