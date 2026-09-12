# SmartLease Edge — Winnable Deck: Master Prompt, Content Spec, and Script

Generated 2026-09-06 for the iQOO Hackathon 2026, Chennai City Battle (Sep 12–13), Smart Living track.
Companion files: `SmartLease-Edge-iQOO-Hackathon-2026.pptx` (17 slides, speaker notes on every slide) and `build/` (regenerates the deck: `python build_deck.py assets out.pptx`).

> **SUPERSEDED IN PART — 2026-09-12.** The `.pptx` was corrected against the shipping code
> (see `AUDIT_CLAIMS.md` at the workspace root). The slide specs in §3 below still describe
> the pre-correction deck on these points and must **not** be used to regenerate it:
> Hexagon NPU / HTP execution (all three models run on the CPU via PyTorch Lite; the
> ExecuTorch `.pte` is compiled and in the repo but not in the APK), on-device Llama 3.2 3B /
> GenieX (the narrative is rule-based templating), SHA-256 and dual signatures (not
> implemented), ARCore depth (no depth source exists), Sensing Hub always-on capture (no
> third-party API for it), mic-verified IR (`ConsumerIrManager` is transmit-only), the
> confidence-hiding and dismiss behaviour (not implemented), "580+ images" (2,106) and the
> class list (`crack, peeling, spalling, stain_mould`), and "100+ own recordings" (15 taps
> from 8 recordings). The **script in §5 is current** — it has been corrected in place.

---

## 1. What the research found (why this deck looks the way it does)

**The rubric you are actually scored on (iQOO 2026, published by Reskilll/iQOO):**

| Criterion | Weight | Measured by |
|---|---|---|
| End product quality | 30% | Jury, demo seen cold at the end |
| Novelty and impact | 20% | Jury |
| Creative phone use (camera, voice, on-device AI) | 15% | HackTracker device telemetry |
| Technical depth | 15% | Jury |
| Office Kit usage | 10% | HackTracker device telemetry |
| Demo and presentation | 10% | Jury, 3–5 min pitch on the iQOO phone |

25% of the score comes from device data. Cloud-only or web-only builds forfeit it. "A local or open-source model at the core earns brownie points." Round 1 (Sat 19:00–22:00) is a scored checkpoint; top 10 per bucket pitch on Sunday from 13:45. No PPT template or slide cap is published for idea screening.

**What previous winners' decks did that typical decks did not (SIH 2022/2024 winners, Qualcomm Edge-AI winners 2025–26, ExecuTorch hackathon, TreeHacks/Cal Hacks grand prizes, EY Techathon):**
1. Opened with the pain in one sentence, one statistic, one visual, and a named person.
2. Used the product name or a claim as the headline, never "Idea Title".
3. Showed one or two hero numbers that are hardware-relative (ms, FPS, tokens/s, power).
4. Had a "why only on this hardware / why local" slide with an explicit NPU/CPU/peripheral division of labour (GameSense, EdgeFit Coach).
5. Reached the demo within 90 seconds and showed one flow to its punchline; recorded backup.
6. One architecture diagram, not a service maze.
7. A concrete user story on the impact slide (Cannon Crew, SIH 2024).
8. Honest build status ("80% built", "simulated data, real sensors next") and stated limitations.
9. Competitor matrix with checkmarks and a narrow, defensible novelty sentence.
10. Sentence headlines (assertion-evidence), dark high-contrast slides, ≤3–4 colours, real screenshots in device frames, no stock photos or clip art.

**Where your previous decks (e.g. Aegis) lost points:** light Gamma template with 14 pt body text, AI comic illustrations instead of product visuals, no hero numbers, no competitor matrix, no hardware-to-feature map, no rubric mapping, no sources.

**Color theory applied:** navy ground `#14142B` (not pure black), off-white text `#F1EEFA`, one warm accent `#F5A623` used at ~10% (echoes iQOO's gold without becoming an ad), semantic green `#2E9E5B` / amber / red `#D64545` used identically on every slide. Segoe UI throughout (present on every Windows machine; python-pptx cannot embed fonts). Headlines 26–34 pt, body 12–16 pt, sources 9.5 pt. Keep a light variant only if the venue projector is weak.

---

## 2. The master prompt (paste into Claude, Gamma, Canva Magic Design, or the `hackathon-pitch-deck` skill)

```
You are designing the idea-submission and final-pitch deck for SmartLease Edge, a phone-first, fully offline
property-inspection app for the iQOO Hackathon 2026 (Chennai City Battle, Sep 12–13, Smart Living track).
Judging: End product quality 30%, Novelty & impact 20%, Creative phone use 15% (device telemetry),
Technical depth 15%, Office Kit usage 10% (telemetry), Demo & presentation 10%. Final pitch is 3–5 minutes
with a live demo on the iQOO 15 (Snapdragon 8 Elite Gen 5, Hexagon NPU, IR blaster, Sensing Hub).

Build 15 content slides + 2 appendix slides, 16:9, dark navy ground (#14142B), off-white text (#F1EEFA),
single orange accent (#F5A623), semantic green/amber/red, Segoe UI. Every headline is a full-sentence claim.
Tag each content slide top-right with the rubric criterion it serves. Put speaker notes with timing on
every slide. Use only sourced numbers; put every source on the last slide. Label UI mockups "target UI"
until real screenshots exist. Never claim something is built unless it is in the repo.

Slide order and required content:
1  Title: "SmartLease Edge — Your phone is the property inspector." Track, city, dates, team, bucket,
   repo link, two phone screens, pills: 100% offline · on-device Llama 3.2 3B · Snapdragon 8 Elite Gen 5.
2  Problem: "Move-out day in India is a memory contest, with up to ten months' rent at stake."
   Three hero numbers: 6–10 months' rent deposits in Bengaluru vs the Model Tenancy Act 2021 2-month cap;
   >40% of landlord–tenant disputes are about the deposit (industry estimate); NoBroker human inspection
   ₹1,649–5,999 booked days ahead. Named user story (Priya, Anna Nagar, ₹90,000 deposit, 12 WhatsApp
   photos). Root cause box: no timestamped move-in documentation. Full-bleed darkened cracked-wall photo.
3  Why on-device, why now: three cards (offline is the use case / private by physics / real-time or
   useless) and a "why now" strip: Hexagon NPU 37% faster; Llama 3.2 3B ~10 tok/s W4A16 via Qualcomm
   GenieX; YOLOv8n-Seg INT8 <30 ms per frame (target).
4  Solution: "Three sensors. One signed report. Zero internet." Three device-framed screens (vision scan,
   tap test, signed report) with one-sentence captions, plus the IR functional-check line: the report
   records "works", not just "looks fine".
5  Live demo flow: five timestamped steps (set baseline 0:00, vision scan 0:15, tap test 0:30, IR appliance
   check 0:45, signed report 1:00), each naming the sensor/runtime it exercises; backup recording note.
6  Vision model: "Cracks become square feet, not opinions." YOLOv8n-Seg on 580+ labelled images; export
   torch.export → PT2E (QnnQuantizer 8a8w) → ExecuTorch .pte on Hexagon HTP, ~6 MB; area = pixels × Z²/(fx·fy);
   confidence thresholds; latency chart NPU vs CPU fallback; honest-status card (heuristic today, model Days 3–8).
7  Acoustic model: "Tap a tile. The phone hears what a surveyor's hammer would." Spectrogram figure
   hollow vs solid; 150 ms crop → 64-band log-Mel → 3-layer CNN ~150 KB INT8 <3 ms + Random Forest;
   Sensing Hub trigger; decay-time heuristic fallback already shipping; training set of own recordings.
8  Architecture: one diagram, input layer (IMX921 camera, stereo mics, IR blaster, rotation/depth) →
   three lanes (Hexagon NPU: segmenter, tap CNN, Llama 3.2 3B via GenieX/QAIRT; Host CPU: NMS, area,
   Mel, PDF, SHA-256, Room DB; IR state machine: capture → store → replay → verify) → diagnostic synthesis
   JSON → signed PDF (timestamp, session ID, SHA-256, dual signature). Badge: media never
   leaves the device — structurally, not by promise (the upload type has no field that can
   carry image bytes). Do NOT badge "no INTERNET permission": the app declares it for report
   sync as of 2026-09-12, and the badge would be false.
9  iQOO 15 hardware map: table of ≤7 rows, hardware / verified spec / exact job (NPU, triple 50 MP with
   OIS + ultrawide + periscope, IR blaster, stereo mics + Sensing Hub, 6000-nit display, 7000 mAh + 8K
   vapor chamber). Caveat: vivo Q3 co-processor not targeted (no public SDK).
10 Models + fallbacks + Red Light plan: table (model, architecture, size, runtime, latency, fallback) and
   three cards: Red Light phone-only work via Office Kit remote control, Green Light export/builds,
   Office Kit in the pitch (mirror, file transfer, clipboard).
11 Competition: "AI inspection apps exist. None run offline, and none test whether things actually work."
   Matrix: NoBroker, TurboTenant, zInspector, RentCheck, ClaimSnap vs SmartLease Edge on offline /
   functional test / defect area / on-device AI / price for one lease. Narrow novelty sentence beneath.
12 Working MVP, honestly: three columns — real and running now (CameraX, AR alignment, ML Kit OCR, IR
   transmit, acoustic heuristic, safety gate, Room + PDF); shipping before the battle (segmenter training
   and export, tap CNN, GenieX narrative, SHA-256 + signatures, rehearsals); live at the venue (real AC IR
   capture, Round-1 vertical slice by Sat 19:00, threshold tuning, Sun 09:00 airplane-mode run).
13 Impact & scalability: who it serves; ₹299–499 per signed report vs ₹1,649–5,999 human inspection;
   scales without new engines (new class = 200 images, new language = one prompt, any Snapdragon 8 Gen 2+);
   four-phase roadmap (move-out verify → maintenance mode + insurance assist → buyer's check → B2B2C).
14 Team + scorecard: three roles with zero overlap; table mapping all six rubric criteria to how the
   project scores them.
15 Close: "Every Indian renter deserves a fair move-out." Five checkmarks, "We are not pitching an idea.
   We are showing you a working system.", repo and contact.
16 Appendix Q&A: false positives, area accuracy, other phones, privacy, ChatGPT + photo, why YOLOv8 nano,
   trained vs built live, business model.
17 Appendix sources: one line per number used.
```

---

## 3. Fill-ins and claim-to-code checklist before you submit

- [ ] Team name, member names, student/professional bucket, contact email (slides 1, 14, 15).
- [ ] SHA-256 fingerprint: the current `ReportGenerator.kt` writes a timestamp and session ID only. Add a `MessageDigest("SHA-256")` over the PDF bytes and print it on the last page (about 10 lines) so slides 4, 8 and 12 stay true.
- [ ] Replace phone mockups with real screenshots when the UI lands (Days 6–10); keep the "target UI" caption until then.
- [ ] Replace the illustrative spectrogram with a real hollow/solid pair from your recordings after Day 5.
- [ ] Latency numbers (<30 ms, <3 ms, ~15 s) are targets; after the Day 6–8 NPU tests, replace with measured values and drop the word "target".
- [ ] Confirm no slide claims the manifest lacks INTERNET — it declares it for report sync
      (slide 8 badge, slide 15 checkmark both need the media-cannot-leave wording instead).
- [ ] Sources slide: the ">40% of disputes" figure is an industry estimate from rental portals; say so if challenged. Do not reuse the earlier draft's ₹2,400 crore / 1.1 crore agreements / 38% figures; no source was found for them.
- [ ] Keep the deck under 10 MB; export a PDF copy alongside the PPTX.

---

## 4. Three-minute pitch script (slides 1, 2, 4, 5, 8, 9, 11, 12, 15)

- **0:00–0:10 (slide 1)** "SmartLease Edge turns the iQOO 15 into a property inspector. Point the camera, tap the wall, point the IR blaster at the AC, and in sixty seconds you have a timestamped condition report. No network permission, no backup, nothing leaves the phone."
- **0:10–0:45 (slide 2)** Priya's story, then the three numbers, then: "Every source traces the dispute to the same thing: no timestamped move-in record."
- **0:45–1:05 (slide 4)** Three sensors, one report. "The IR blaster is the check a camera cannot do — today it proves the command was sent; the mic listen-back that closes the loop is the next step."
- **1:05–2:35 (slide 5 as backdrop, LIVE DEMO)** Airplane Mode on, Office Kit mirror on. Baseline → scan → tap → IR → generate. One presenter narrates, one drives.
- **2:35–2:50 (slides 8–9)** "Everything you saw ran on the CPU. The ExecuTorch INT8 lowering is done and in the repo — `qnn_android_bundle/yolov8n_seg_htp.pte` — what we didn't finish is wiring the HTP delegate into the APK. The IR blaster is why this is an iQOO app."
- **2:50–3:05 (slides 11–12)** "zInspector and RentCheck have AI; neither works offline or tests function. What you saw is in the repo today; the two models were trained before this weekend."
- **3:05–3:20 (slide 15)** "Every Indian renter deserves a fair move-out. We are not pitching an idea." Stop. Invite questions.

---

## 5. How to regenerate or audit later

- Regenerate: `cd docs/pitch/build && python build_deck.py assets ../SmartLease-Edge-iQOO-Hackathon-2026.pptx`
- Audit any deck against the winning-deck rules: `python ~/.claude/skills/hackathon-pitch-deck/scripts/audit_pptx.py deck.pptx --render`
- In Claude Code, type `/hackathon-pitch-deck` or ask to "build/audit a hackathon deck"; the skill holds the research digest, design system, slide templates and checklist so no re-research is needed.
