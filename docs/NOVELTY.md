# NOVELTY.md — assessment and feature selection

**Date:** 2026-09-12 · **Code state:** `082af75`, tag `demo-known-good`, build green, 36/36 tests.
**Source of truth:** the code. The deck is not evidence and was not consulted.

---

## 0. Two things to read before the scores

**The four skills you named are not installed.** `~/.claude/skills/` contains only
`impeccable`; `Skill(hackathon-idea-evaluator)` returns `Unknown skill`. I have not pretended
to run a scoring instrument I do not have. What follows applies **the rubric you specified in
the prompt** — Novelty, Feasibility, Scalability, Impact, Demo-ability, Domain Fit, with your
anti-inflation rule (unimplemented = 0, not partial) — as my own analysis, and the
inversion / collision / simplification passes as reasoning I performed rather than tools I
loaded. Judge it as such.

**I found and fixed an error of mine that changes this assessment.** The acoustic confidence
interval I put in the deck, `METRICS.md`, and five other docs was **62–96%**. That number came
from `acoustic_report.json`'s `best_accuracy_95ci` — a *different fit*. The interval the
shipped bundle carries, and the one `TrainedTapClassifier.kt:54-57` actually renders on screen,
is:

```
acoustic_tap_model.json → groupedAccuracyCi95 = [0.548, 0.930]     → the app prints "55-93%"
acoustic_report.json    → majority_baseline   =  0.533
```

**The lower bound of the interval is the baseline.** At 55% against 53.3%, the shipped acoustic
model is statistically indistinguishable from guessing at the bottom of its range. I have
corrected `AUDIT_CLAIMS.md`, `AUDIT_GAPS.md`, `JUDGE_QA.md`, `ROADMAP.md`, `solution1.md` and
`README.md`. **Slide 7 of the deck still says 62–96% and is now wrong** — this is in
`DECK_CORRECTIONS.md` for the deck owner.

This single number decides the answer to your hypothesis. It is the difference between "the
novelty is under-sold" and "the novelty is under-sold *and* under-evidenced".

---

## 1. Baseline score — SmartLease Edge as it stands

Anti-inflation applied: a capability that is specified but not implemented scores 0. A
capability that is implemented but whose measured confidence interval includes "no better
than chance" is scored as **demonstrated engineering, undemonstrated function**.

| Dimension | Score | Reasoning from code |
|---|---:|---|
| **Novelty** | **3 / 10** | Offline is a constraint, not an invention. Vision is stock YOLOv8n on four public Roboflow sets at mask mAP50 **0.287** with `stain_mould` at **0.065** — commodity, and weak commodity. IR "functional verification" scores **0**: `IrController.kt:11-13` states the loop is open and it has never run on hardware with an emitter. The two genuinely uncommon things are the **percussive tap test** (under-evidenced) and the **SHA-256 commitment over findings** (`FindingsDigest.kt`, real, tested, shipped). |
| **Feasibility** | **8 / 10** | It builds, installs, launches, 36/36 unit tests pass, 97 MB APK archived. Demonstrated, not asserted. Held below 9 only by the 2022 `pytorch_android_lite` runtime and an unproven toolchain upgrade sitting one `git checkout` away. |
| **Scalability** | **4 / 10** | Single session, single room, single phone. No baseline storage, no transport, no identity, no multi-party state. The ₹299–499/report model in the deck has no billing, no accounts and no second party in the code. Scales as a tool, not as a product. |
| **Impact** | **5 / 10** | The problem is real and large. The delivered artefact is a PDF listing sentences about a wall, with **no photograph in it** (P0-12). High ceiling, low floor. |
| **Demo-ability** | **6 / 10** | 60 seconds, offline, on a phone, and the report now shares. But four landmines: no photo, IR cannot fire on the A56, the vision model has **zero hard negatives** so a clean wall may light up, and the area number is indefensible under a two-metre step back. |
| **Domain Fit** | **7 / 10** | Squarely "Smart Living": phone-first, sensor-led, on-device, airplane-mode-true. Weakened because the NPU claim is false and the one iQOO-exclusive component is the one that cannot be tested before the venue. |

**Composite: a competent, unusually honest, well-tested app with a weak novelty claim.**
The engineering discipline is real — grouped cross-validation, golden-vector parity tests, a
length-prefixed digest that survived a collision I found by porting it to Python. None of that
is *novelty*; it is craft. A judge scores craft under End Product Quality, not under Novelty.

---

## 2(a) What does this do that the comparators do not?

| Comparator | What it does | What it cannot do |
|---|---|---|
| **Zillow / Housing.com move-in checklists** | Structured room-by-room photo checklist, cloud-stored | Purely visual. No material diagnosis. Vendor holds the evidence. |
| **RentCheck** | Guided capture, AI-assisted damage detection, cloud | Purely visual, and cloud-dependent by architecture — the comparison happens on their server. |
| **Properly** | Checklist + photo verification, cloud | Purely visual. Same trust model: you trust the vendor. |
| **Landlord + phone camera + WhatsApp** | Free, universal, what almost everyone actually does | Undated in any provable sense, unstructured, trivially selective, and disputed precisely because either party can curate it. |
| **Paid inspector, ₹1,649–5,999** | Does everything, including **sounding/percussion** and moisture metering, and is authoritative | Costs 100×, must be scheduled, and does not exist at 9pm on move-out day. |

**Three things fall out of that table.**

1. **Percussive material diagnosis is the only sensing capability here that no consumer app
   offers.** Every software comparator is a camera and a form. A camera answers *what does this
   surface look like*. A tap answers *what is behind it* — hollow tile, debonded plaster,
   drywall void. In Indian rentals, hollow tiles and debonded plaster are exactly the
   high-value line items a deposit argument turns on, and they are **invisible to every
   comparator in the list except the ₹5,999 human**.
2. **No third party holds the evidence.** This is the real argument for offline, and it is not
   the argument the deck makes. The deck says "the flat has no wifi", which is weak and
   invites "so cache and sync". The strong form is structural: *a cloud inspection product
   cannot offer this, because the vendor is a third party both sides must trust.* A local
   hash that both parties can verify independently is a different trust model, not a
   degraded one.
3. **Where it is worse:** no photographs retained at all, no baseline comparison, and a vision
   model weaker than RentCheck's. Two of those three are fixable in the window.

## 2(b) The strongest true sentence available **today**, before any new build

> *"It tests what is behind the surface, not just what it looks like — and commits the findings
> to a hash neither party can alter."*

Both halves are literally true of `082af75`. Both are weak in practice: the tap model's interval
bottoms out at chance, and the hash is 64 hex characters in a PDF footer that no judge will
ever actually verify. **The claim is true and undemonstrable** — which in a 20%-weighted
Novelty category scores closer to the 3 above than to what it could be.

---

## 3. Your hypothesis, tested

> *"The acoustic tap test is the novel element and it is being under-sold. If that holds up,
> the novelty problem is a positioning problem, not a feature problem."*

**Verdict: the first sentence is right. The second is wrong, and the way it is wrong matters.**

**What holds up.**
- No consumer inspection app in the comparator set does percussive diagnosis. Confirmed by the
  table above.
- It answers a question a camera structurally cannot. That is a *category* difference, not a
  quality difference, and category differences are what Novelty scores.
- It is genuinely under-sold in the product: it is the **third** button (`WalkthroughScreen.kt:270`,
  after Set Baseline and Capture), it produces no sound and no haptic, and its accuracy is
  buried in a log line rather than shown where it lands.

**What does not hold up.**
- **Tap testing is not novel as a technique.** Hammer sounding and chain-drag delamination
  detection are long-standing professional practice with an ASTM standard behind them. A judge
  with a civil-engineering background will say "we have done this for a century," and if the
  pitch has claimed invention, that lands badly. The novelty is **not the method — it is that
  the method currently requires a site visit and now requires a phone you already own.** Claim
  the delivery, never the technique.
- **The implementation does not yet support the claim.** 15 taps, 8 WhatsApp voice notes,
  grouped-LOGO 80%, **95% CI 55–93%** against a **53.3%** baseline. Training audio is Opus at
  16 kHz with noise suppression and AGC; serving audio is raw 44.1 kHz PCM decimated to 22.05.
  Tiny *n* on top of a train/serve domain shift.

**Therefore: it is a positioning problem AND a feature problem — but they are wildly
asymmetric.** The positioning fix is free. The feature fix is roughly two hours of recording
and re-export. Doing the positioning without the data would mean promoting a coin-flip to the
front of the demo and putting a confidence interval next to it that a careful judge will read.
That is the precise failure mode this project has spent two sessions correcting. **Do not
foreground the tap test until the data behind it is real.**

### The reframe that is worth more than any single feature

Running the inversion on *"an inspection is a photo record"* gives *"an inspection is a
commitment two adversaries both accept."* Under that inversion the photo is merely one input,
and the properties that matter become: neither party can selectively omit, neither can alter
after the fact, neither can angle-shop the framing, and no third party holds the result.

That reframe costs nothing, is true of the code today, and it converts "offline" from an
apology into the thesis. It also collapses the candidate list — see §5.

---

## 4. Candidate scoring

Filters applied first; anything failing one is deleted, not scored.
Ranked by **Novelty × Demo-ability × Domain Fit**, with Feasibility as a hard gate.

| # | Candidate | F1 ≤4h | F2 ≤30s | F3 closes gap | F4 offline | F5 no iQOO | Nov | Demo | Fit | **Product** |
|---|---|:--:|:--:|:--:|:--:|:--:|--:|--:|--:|--:|
| **C2** | Lead with tap test + 50 real taps | ✅ 3h | ✅ | ✅ P1-8 | ✅ | ✅ | 8 | 9 | 9 | **648** |
| **C7** | Tap-grid map (6 points → solid/hollow map) | ✅ 3h | ✅ | ⚠️ | ✅ | ✅ | 8 | 9 | 8 | **576** |
| **C1** | Digest as scannable QR | ✅ 1.5h | ✅ | ✅ P0-9 | ✅ | ✅ | 6 | 10 | 7 | **420** |
| **C4** | Wear-and-tear rules table | ✅ 3h | ✅ | ✅ Q21 | ✅ | ✅ | 6 | 8 | 8 | **384** |
| C5 | Baseline digest reference | ✅ 2h | ❌ | ⚠️ | ✅ | ✅ | 8 | 5 | 9 | ~~360~~ |
| C3 | Refuse-to-record alignment gate | ✅ 2h | ✅ | ⚠️ | ✅ | ✅ | 7 | 7 | 6 | **294** |
| C8 | Two-party acknowledgement tap | ✅ 2h | ✅ | ⚠️ Q14 | ✅ | ✅ | 4 | 6 | 7 | **168** |
| C6 | Confidence / abstain surfacing | ✅ 1.5h | ✅ | ✅ Q10/Q12 | ✅ | ✅ | 3 | 6 | 7 | **126** |

### Rejects, with the filter each failed

- **C5 — baseline digest reference. REJECTED on F2, and more seriously on the spirit of F3.**
  It needs a pre-seeded move-in session and ideally two handsets, so it is not a cold 30-second
  demo. Worse: it *sounds* like it solves move-in ↔ move-out and does not. A judge hears
  "references baseline `a3f1…`" and immediately asks "so show me the diff" — and there is no
  diff. It manufactures exactly the unbacked claim F3 exists to prevent. **This is the most
  dangerous item on the list and I recommend against it even though it scores 360.**
- **C8 — two-party acknowledgement. REJECTED on novelty and on honesty drift.** A tap that
  records "I have read this" is not a signature, and the gravitational pull to call it one is
  strong. We have spent two sessions removing that exact word.
- **C7 — conditionally rejected, then folded.** A six-point hollow/solid *map* built on a model
  whose interval reaches chance presents six unreliable readings with the authority of a
  spatial diagram. It **fails F3 as a standalone** ("creates a new unbacked claim"). It becomes
  excellent the moment C2's retrain puts the interval clear of baseline — so it is folded into
  C2 as a gated stretch, not a separate item.

### Simplification cascade — six candidates are two capabilities

- **C1 + C3 + C5 + C8** all express one idea: *evidence that neither party can game.*
- **C2 + C6 + C7** all express one idea: *say only what the sensors actually know.*

Two deep capabilities outscore six shallow features, and they map exactly onto the two halves
of THE SENTENCE below. C6 is not a feature at all — it is hygiene that belongs inside C2's
work, and I have priced it there rather than as a line item.

---

## 5. THE SENTENCE

> **"SmartLease Edge tests what is behind a surface, not just what it looks like — and commits
> every finding to a hash both parties can verify offline, with any phone."**

27 words. True of the code after C2 + C1 ship. Neither half is true of any comparator.

**Hostile follow-up 1 — "Tap testing is a hundred years old. What did you invent?"**
> "The technique is old, and that is exactly why I trust it — it is ASTM-standard sounding, the
> same thing a professional does with a hammer. What is new is the delivery. Today that
> diagnosis requires a site visit at ₹1,649 to ₹5,999 and three days' notice. We put it in a
> phone that is already in the room, and bound the result to a hash so neither side can edit it
> afterwards. I am not claiming the physics. I am claiming the distribution."

**Hostile follow-up 2 — "How much data is that model trained on?"**
> *(Only sayable after C2's recording.)* "N taps recorded on this handset, grouped
> cross-validated by recording so sibling taps cannot leak between folds, and the app prints the
> interval next to every verdict. When that interval includes chance, the app returns
> INCONCLUSIVE instead of guessing — which it does today, and which is why we recorded more."
>
> **Before C2 lands, the honest answer is "fifteen, and the interval bottoms out at chance."
> That answer loses the Novelty point entirely.** This is why C2's data half is mandatory.

**Hostile follow-up 3 — "Why does offline matter? Just cache and sync."**
> "Caching solves connectivity. It does not solve trust. In a deposit dispute the two parties
> do not trust each other, and a cloud product asks them both to trust a third — the vendor who
> holds the evidence and can change it. A hash computed on the phone and verified on the other
> party's phone needs no server and no trusted third party. That is a different trust model,
> not a degraded one."

---

## 6. Recommended build order, and what I would cut

**Build, in this order:**

| | Item | Time | What it buys |
|---|---|---|---|
| 1 | **C2** — reorder tap test to first, add haptic + audible confirmation, surface accuracy and interval in the UI, **and record ~50 real taps on the A56 and re-export** | 3 h | Turns the one category-differentiating capability from an assertion into a demonstration. Without the data half this is negative value. |
| 2 | **C1** — QR of the findings digest on the report screen and the last PDF page | 1.5 h | The best 20 seconds in the whole demo: the judge scans it with *their own* phone, you change one finding, regenerate, they scan again and the code is different. They verify your tamper-evidence claim with their own hands and no app. |
| — | *(stretch, gated)* **C7** tap-grid map — only if C2's retrain puts the CI lower bound clear of the 53.3% baseline | 3 h | Turns a binary into a spatial finding. Do not build it on the current model. |

**Then the existing plan, reordered:**

| Phase | Keep / cut | Why |
|---|---|---|
| **Phase 2** — persist photos | **KEEP, highest priority after C1** | Still the biggest product gap (P0-12) and it *compounds* C1: once photo hashes are in the digest, the QR commits to the images too, and "show me the photo" stops being an apology. |
| **Phase 2.5** — on-device self-test | **KEEP** | Highest value per hour in the whole plan. It is the only way you learn at the venue which lens CameraX bound, and it converts Q3 from "no on-device measurement" into a number on a screen. |
| **Phase 5** — venue runbook | **KEEP** | IR has never run on hardware with an emitter. This is the only mitigation. |
| **Phase 1.2** — clean-surface FP harness | **KEEP, but shrink to 45 min** | Do not build a CSV harness. Just run the walkthrough against 20 surfaces and tally. The number matters; the tooling does not. |
| **Phase 3** — hard negatives + retrain | **CUT the retrain. Keep the measurement.** | 4 h to move a commodity capability from mAP 0.287 to perhaps 0.35 — still not good, and vision is not where the novelty is. Spend those hours on C2's acoustic data instead, where the same effort moves the *differentiating* capability from chance to credible. |
| **Phase 4** — metric depth | **CUT entirely** | 8 h, high risk, gated on a 2022 runtime loading a torch-2.x trace. It buys precision on a number (area in ft²) that contributes nothing to the novelty claim — Phase 1.1's percentage already answers Q6 honestly. |

**What cutting costs, stated plainly.** Cutting Phase 3's retrain means mask mAP50 stays at
0.287 and `stain_mould` stays broken at 0.065, so Q8 and Q10 keep their current answers — which
are already written, already honest, and already rehearsed. Cutting Phase 4 means the app never
reports square feet, so the deck's pinhole formula stays a roadmap item. Both are answers you
can give. Neither is a contradiction. That is the trade: **fewer capabilities, zero new
liabilities.**

## 7. Is the right answer to build nothing?

I considered it seriously, because you invited it. **No — but it is closer than it looks.**

The positioning work (§3's reframe, THE SENTENCE, the three follow-ups) is free and is worth
more than any single feature on the list. If the window collapsed to one hour, I would take the
sentence and the reframe and build nothing.

The reason to build C2 anyway is not that it adds a feature. It is that **the sentence is not
honestly sayable without it.** "Tests what is behind a surface" against a 55–93% interval is a
claim whose own confidence interval contains "does not." Two hours of tapping a tile board
converts the strongest sentence available to this project from something that needs a caveat
into something that does not.

C1 earns its 90 minutes for a different reason: it is the only item in the list that lets a
judge *test* a claim rather than believe it, and a tested claim scores in a category that a
believed one does not.

---

## 8. Waiting on you

**Nothing has been built.** Per Step 3 I have stopped here.

Also still outstanding and unrelated to this phase: **GATE 0 has not passed** — `adb devices`
is empty, so the device capability probe (0.3) has not run. C2's tap recording needs that phone
attached anyway.
