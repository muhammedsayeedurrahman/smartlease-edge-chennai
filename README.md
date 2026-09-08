# SmartLease Edge 🏠

> **100% Offline Property Verification · Powered by iQOO 15 Snapdragon 8 Elite**

[![iQOO Hackathon 2026](https://img.shields.io/badge/iQOO_Hackathon-2026-blueviolet?style=for-the-badge)](https://iqoo.com/in)
[![Smart Living Track](https://img.shields.io/badge/Track-Smart_Living-brightgreen?style=for-the-badge)](https://iqoo.com/in)
[![Chennai City Battle](https://img.shields.io/badge/City-Chennai-orange?style=for-the-badge)](https://iqoo.com/in)
[![Event Date](https://img.shields.io/badge/Event-Sep_12--13_2026-red?style=for-the-badge)](https://iqoo.com/in)

---

## 🏆 Team Track Record

**Proven hackathon winners · Not first-time participants**

- 🥇 **1st Place** — Lawtrix @ Sairam Engineering College
- 🏅 **SIH Finalist** — Smart India Hackathon (national competition)
- 🏅 **IOB Hackathon Top 10** — Mule Catch (fraud detection system)

We know how to execute under pressure, scope realistically, and deliver working demos when the clock runs out.

---

## 🎯 The Problem We're Solving

Security deposit disputes are **the single biggest source of landlord-tenant conflict in India**. Every source traces the cause back to the same thing: **no move-in documentation**.

In cities like Bengaluru, deposits run **6–9 months' rent** — making disputes high-stakes when they happen. 

**SmartLeaseEdge** closes this gap with a timestamped, sensor-backed property verification system that works **100% offline** — no cloud, no connectivity, no data leaving the device.

---

## ✨ Competitive Differentiation

**We researched 4 real competitors with real pricing — here's the gap we fill:**

| Feature | SmartLeaseEdge | NoBroker | TurboTenant | zInspector | RentCheck |
|---------|---------------|----------|-------------|------------|-----------|
| **Offline capable** | ✅ 100% offline | ❌ Needs inspector | ❌ Cloud-dependent | ❌ Cloud SaaS | ❌ Cloud SaaS |
| **Functional testing** | ✅ IR + acoustic | ❌ Visual only | ❌ Manual only | ❌ Visual only | ❌ Visual only |
| **AI defect detection** | ✅ On-device NPU | ❌ Human inspector | ❌ None | ✅ Cloud AI | ✅ Cloud AI |
| **Pricing** | ₹299–499 one-time | ₹1,649–5,999/inspection | Free (manual) | $0–115/mo | $0.60–1.10/unit/mo |
| **Target user** | Single tenant/landlord | Portfolio managers | DIY landlords | Property managers | Property managers |

### The Novelty

**No competitor combines both:**
1. **100% offline operation** (works in basements, remote areas, no data upload)
2. **Functional diagnostic testing** (verifies appliances actually work, not just how they look)

NoBroker requires scheduling a human inspector days in advance. zInspector and RentCheck need the cloud and are priced for property managers with dozens of units. TurboTenant is manual-only with no AI.

**SmartLeaseEdge is the only offline, on-device AI solution that tests function, not just appearance.**

---

## 🔧 How It Works

### Multi-Modal Verification Engine

```
┌─────────────────────────────────────────────────────────────┐
│  INPUT LAYER                                                │
│  📷 Camera (AR + vision) · 🎤 Mic (acoustic) · 📡 IR blaster│
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  ON-DEVICE PROCESSING (Snapdragon 8 Elite Gen 5 Hexagon NPU)│
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ AR Alignment│  │Vision Seg.  │  │Acoustic Test│         │
│  │(SensorMgr + │  │(YOLOv8-Seg) │  │(FFT + ML)   │         │
│  │OIS camera)  │  │             │  │             │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  DIAGNOSTIC SYNTHESIS                                       │
│  Defect area + Acoustic verdict + IR-verified appliance    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  REPORT LAYER (Qualcomm GenieX + Llama-3.2)                │
│  Plain-language repair/cost breakdown                      │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  OUTPUT: Signed, timestamped local PDF — 100% offline      │
└─────────────────────────────────────────────────────────────┘
```

---

## 📱 Hardware-First Design: iQOO 15 Feature Utilization

**8 specific iQOO 15 features mapped to 8 specific jobs — not just "uses the NPU"**

| iQOO 15 Hardware | Verified Spec | Specific Job in SmartLeaseEdge | Why It Matters |
|------------------|---------------|-------------------------------|----------------|
| **Snapdragon 8 Elite Gen 5 Hexagon NPU** | 37% faster AI vs. prior gen | Runs GenieX Llama-3.2 report synthesis + YOLOv8-Seg vision segmentation | On-device AI means no cloud upload → works offline in basements, remote areas |
| **8K Vapor Chamber Cooling** | Sustained thermal management under load | Enables one uninterrupted 30-min property walkthrough — continuous camera + NPU + mic | Prevents thermal throttling that would quietly degrade inference speed mid-demo on phones without this cooling |
| **Triple 50MP Camera System** | Sony IMX921 OIS + 50MP ultrawide + periscope telephoto (100x digital zoom) | **OIS:** Stabilizes AR overlay for pixel-accurate alignment<br>**Ultrawide:** Captures full wall in one frame<br>**Telephoto:** Inspects small cracks/nail holes from distance | OIS makes AR alignment accurate; telephoto inspects fragile surfaces without touching them |
| **6000-nit 2K LTPO Display** | Peak brightness rating | AR ghost-overlay legible even in bright, sunlit room during daytime inspection | Not just a dim-room demo trick — works in real inspection conditions |
| **7000mAh Battery + 100W Charging** | Large capacity, fast recharge | Survives full 20–40 min walkthrough without battery anxiety; recharges fast between Eval Round 1 and Round 2 | Critical for hackathon demo reliability across multiple evaluation rounds |
| **Sensing Hub** | Always-on, low-power sensor processing path | Runs lightweight "is there a tap sound" listener continuously | Doesn't pull power from main NPU — energy-efficient acoustic monitoring |
| **Dual Stereo Speakers** | 120% louder vs. prior gen | Audible confirmation tones synced to acoustic tap results | Makes tile-sounding test legible to a room of judges, not just the person holding the phone |
| **Vivo Office Kit** | Screen mirror, clipboard sync, remote control | Live demo screen-mirrored to projector so judges see phone screen clearly | Genuine phone-only build sessions; judges can watch real-time development |

**Why this mapping matters:** Most hackathon teams say "we used the NPU." Naming the exact hardware and the specific job it does is what separates generic claims from real technical depth under judge Q&A.

---

## 🚀 Current Status (Day 2 of 12) — Honest MVP Assessment

### ✅ What's Real and Working Right Now

We believe in honest execution, not vaporware. Here's what actually works today vs. what's coming:

| Subsystem | Implementation | Status |
|-----------|----------------|--------|
| **Camera capture + preview** | `camera/CameraController.kt` | ✅ Real CameraX wrapper, binds to device rear camera |
| **AR baseline alignment** | `camera/ArAlignmentTracker.kt` | ✅ Real SensorManager rotation-vector tracking, pitch/roll delta from baseline |
| **OCR** | `ocr/OcrEngine.kt` | ✅ Real ML Kit on-device text recognition, fully offline |
| **IR transmit** | `ir/IrController.kt` | ✅ Real ConsumerIrManager wrapper — transmits IR patterns |
| **Acoustic tap analysis** | `acoustic/AcousticTapClassifier.kt` + `Fft.kt` | ✅ Real AudioRecord capture, real FFT, amplitude/decay heuristic |
| **Safety gate** | `safety/SafetyGate.kt` | ✅ Real deterministic keyword-based rule engine |
| **PDF report generation** | `report/ReportGenerator.kt` | ✅ Real offline PDF rendering (Android PdfDocument API) |
| **Local storage** | `data/` | ✅ Real Room database, backup-excluded by design |

**Build verification:**
```bash
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk (~56MB)
# Installs and runs on device today
```

### 🔧 Honestly Stubbed — What We're Training Before the Event

| Subsystem | Why Stubbed | Our 12-Day Plan |
|-----------|-------------|----------------|
| **Vision defect segmenter** | Real version needs YOLOv8-Seg training on wall damage dataset + ExecuTorch export to Hexagon NPU | **Days 3–8:** Train on 200–500 collected images, export to `.pte` format, test NPU delegate |
| **GenieX report narrative** | Real version needs Qualcomm GenieX SDK integration for on-device Llama-3.2 | **Days 9–10:** Wire GenieX SDK into `ReportGenerator.kt`, test offline synthesis |
| **Real AC IR patterns** | Can only be captured from the actual on-stage demo AC unit | **Day 12 (on-site):** Live IR capture from venue AC — the one unavoidable live step |

**Placeholder implementations today:**
- `HeuristicDefectSegmenter`: Basic color/contrast region flagging (explicitly marked `isTrainedModel = false`)
- `synthesizeNarrative()`: Templated text generation (swaps to GenieX once wired)
- `CommonAcIrProfiles`: Carrier frequencies only (real patterns loaded Day 12)

**Why we're honest about this:** Under judge Q&A, claiming something works when it doesn't kills credibility. We'd rather show a clear de-risking plan that proves we know what's hard and have a schedule to solve it.

---

## 📦 Build & Run

### Prerequisites

- **JDK 17+** (Android Studio's bundled JBR recommended)
- **Android SDK** (API 34+)
- **Gradle 9.4.1** (wrapper included)

### Build APK

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (~56MB)

### Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Open in Android Studio

```bash
# Open this directory in Android Studio
# File → Open → C:\Users\HP\SmartLeaseEdge
```

---

## 📊 Data Collection (Day 2 Focus)

### Acoustic Tap Samples

**Target:** 30–50 samples each of hollow and solid taps

- **Hollow:** Doors, drywall, empty boxes, hollow plastic
- **Solid:** Concrete walls, solid wood, filled containers, tile on concrete

📖 **Guide:** [`ACOUSTIC_RECORDING_CHECKLIST.md`](./ACOUSTIC_RECORDING_CHECKLIST.md)

### Wall Damage Images

**Target:** 200–500 annotated images of wall defects

- **Public datasets:** Roboflow, Kaggle (pre-annotated)
- **Own photos:** Nail holes, cracks, stains, peeling paint

📖 **Guide:** [`DATA_COLLECTION_GUIDE.md`](./DATA_COLLECTION_GUIDE.md)
📖 **Dataset sources:** [`DATASET_SOURCES.md`](./DATASET_SOURCES.md)

### Validation

```bash
python validate_training_data.py
```

---

## 🗓️ 12-Day Hackathon Prep Timeline

| Days | Phase | Focus |
|------|-------|-------|
| **1–2** (Sep 1–2) | Data collection | Record tap samples, collect wall damage images |
| **3–5** (Sep 3–5) | Model training | Train acoustic classifier + YOLOv8-Seg vision model |
| **6–8** (Sep 6–8) | Export & hardware test | Export to ExecuTorch, test Hexagon NPU delegate |
| **9–10** (Sep 9–10) | Integration | Wire all subsystems, GenieX report generation |
| **11** (Sep 11) | Rehearsal | Full end-to-end demo run, 3+ times, timed |
| **12** (Sep 12) | Event check-in | Capture real AC IR pattern on-site |

---

## 🎤 Demo Script (3 minutes)

1. **0:00–0:45** — Problem hook: Deposit disputes as India's top landlord-tenant conflict
2. **0:45–1:45** — Live offline showcase: Airplane mode → AR alignment → acoustic tap → IR AC trigger
3. **1:45–2:30** — Report synthesis: GenieX generates offline report live on-device
4. **2:30–3:00** — Competitive close: Name the gap vs. NoBroker/zInspector/RentCheck

---

## 📈 Roadmap (Post-Hackathon)

### Phase 1: Maintenance Mode (2–4 months)
- Continuous appliance health checks (quarterly triggers)
- Turns single-use tool into regularly-opened app

### Phase 2: Insurance Assist (4–8 months)
- Same report pipeline, feeds insurance claims
- Differentiates on functional verification (not just visual)

### Phase 3: Buyer's Check (6–12 months)
- Self-service pre-purchase inspection
- Undercuts ₹10,000+ professional inspections

### Phase 4: B2B2C Licensing
- Property management companies, co-living operators
- Per-unit licensing, recurring revenue

📖 **Full platform vision:** [`SmartLease-Edge-Platform-Vision.pdf`](C:\Users\HP\SmartLease-Edge-Platform-Vision.pdf)

---

## 📄 Documentation

- **Hackathon submission plan:** [`SmartLease-Edge-Chennai-Battle-Plan.pdf`](C:\Users\HP\SmartLease-Edge-Chennai-Battle-Plan.pdf)
- **Platform vision:** [`SmartLease-Edge-Platform-Vision.pdf`](C:\Users\HP\SmartLease-Edge-Platform-Vision.pdf)
- **Data collection guide:** [`DATA_COLLECTION_GUIDE.md`](./DATA_COLLECTION_GUIDE.md)
- **Dataset sources:** [`DATASET_SOURCES.md`](./DATASET_SOURCES.md)
- **Acoustic recording checklist:** [`ACOUSTIC_RECORDING_CHECKLIST.md`](./ACOUSTIC_RECORDING_CHECKLIST.md)

---

## 🏆 Team Structure & Execution Strategy

**3-person team · Structured for parallel execution**

### Team Roles

| Role | Pre-Event Focus (Days 1–11) | Event-Day Focus (Sep 12–13) |
|------|----------------------------|----------------------------|
| **Member 1 — AI/NPU Engineer** | Train & export models (acoustic classifier, YOLOv8-Seg vision); GenieX SDK integration | Integrate pre-tested models into live app; tune to venue lighting/acoustics |
| **Member 2 — Android/Hardware Lead** | Build AR overlay UI, IR transmit pipeline, camera features; pre-capture AC IR codes | Live on-site IR capture from demo AC unit; Vivo Office Kit Red Light build sessions |
| **Member 3 — Research/Demo/Testing** | Data collection assist, testing, demo script, props, pitch prep | Rehearsed demo delivery (3-min timed); handle judge Q&A |

### Why This Structure Works

**File ownership prevents Git conflicts:**
- Member 1 owns: `training_data/`, `models/`, ML scripts
- Member 2 owns: `app/src/.../ui/`, Android code
- Member 3 owns: `docs/`, demo materials, test cases

**Daily 15-min sync:**
- What I completed today
- What I'm working on tomorrow
- Blockers / dependencies on other members

**Response time expectations:**
- Urgent (blockers): <1 hour
- Normal questions: <4 hours
- Code reviews: <8 hours

**12-day de-risking plan trains the hardest models BEFORE the event** — so the 30-hour clock is spent on integration and polish, not first-time R&D under pressure.

See [`TEAM_STRUCTURE.md`](./TEAM_STRUCTURE.md) for full breakdown.

---

## 📜 License

**Educational/Hackathon Project** — MIT License (TBD post-event)

This project was built for the **iQOO Hackathon 2026 — Chennai City Battle** (Smart Living track, Sep 12–13, 2026).

---

## 🙏 Acknowledgments

- **iQOO India** for the hackathon platform and iQOO 15 hardware access
- **Qualcomm** for the GenieX on-device LLM SDK
- **Roboflow / Kaggle** for public wall damage datasets
- **Chennai developer community** for battle-testing the offline-first approach

---

**Built with ❤️ for the Chennai City Battle — Sep 12–13, 2026**

> *"The only property inspection tool that works when the Wi-Fi doesn't."*

---

## 🔗 Quick Links

- **GitHub:** [smartlease-edge-chennai](https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai)
- **Hackathon submission:** [iQOO Hackathon Portal](https://iqoo.com/in)
- **Demo video:** (TBD after Day 11 rehearsal)
- **Live demo:** Sep 12–13, 2026 — Chennai

---

<p align="center">
  <img src="https://img.shields.io/badge/Made_for-iQOO_15-blueviolet?style=for-the-badge&logo=android" alt="Made for iQOO 15">
  <img src="https://img.shields.io/badge/Powered_by-Snapdragon_8_Elite-red?style=for-the-badge&logo=qualcomm" alt="Powered by Snapdragon 8 Elite">
  <img src="https://img.shields.io/badge/Built_in-Chennai-orange?style=for-the-badge&logo=googlemaps" alt="Built in Chennai">
</p>
