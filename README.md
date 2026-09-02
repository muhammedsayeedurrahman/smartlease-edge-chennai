# SmartLease Edge 🏠

> **Offline, phone-first property verification for the real world**

[![iQOO Hackathon 2026](https://img.shields.io/badge/iQOO_Hackathon-2026-blueviolet?style=for-the-badge)](https://iqoo.com/in)
[![Smart Living Track](https://img.shields.io/badge/Track-Smart_Living-brightgreen?style=for-the-badge)](https://iqoo.com/in)
[![Chennai City Battle](https://img.shields.io/badge/City-Chennai-orange?style=for-the-badge)](https://iqoo.com/in)
[![Event Date](https://img.shields.io/badge/Event-Sep_12--13_2026-red?style=for-the-badge)](https://iqoo.com/in)

---

## 🎯 The Problem

Security deposit disputes are **the single biggest source of landlord-tenant conflict in India**. Every source traces the cause back to the same thing: **no move-in documentation**.

In cities like Bengaluru, deposits run **6–9 months' rent** — making disputes high-stakes when they happen. SmartLeaseEdge closes this gap with a timestamped, sensor-backed baseline record that works **100% offline**.

---

## ✨ What Makes This Different

| Feature | SmartLeaseEdge | NoBroker | TurboTenant | zInspector | RentCheck |
|---------|---------------|----------|-------------|------------|-----------|
| **Offline capable** | ✅ 100% offline | ❌ Needs inspector | ❌ Cloud-dependent | ❌ Cloud SaaS | ❌ Cloud SaaS |
| **Functional testing** | ✅ IR + acoustic | ❌ Visual only | ❌ Manual only | ❌ Visual only | ❌ Visual only |
| **AI defect detection** | ✅ On-device NPU | ❌ Human inspector | ❌ None | ✅ Cloud AI | ✅ Cloud AI |
| **Pricing model** | ₹299–499 one-time | ₹1,649–5,999/inspection | Free (manual) | $0–115/mo | $0.60–1.10/unit/mo |
| **Target user** | Single tenant/landlord | Portfolio managers | DIY landlords | Property managers | Property managers |

**The novelty:** Fully offline, on-device AI with **functional diagnostic testing** — no competitor does both.

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

## 📱 iQOO 15 Hardware Utilization

| Hardware | Spec | Job in SmartLeaseEdge |
|----------|------|----------------------|
| **Snapdragon 8 Elite Gen 5 NPU** | 37% faster AI vs. prior gen | Runs GenieX Llama-3.2 + YOLOv8-Seg vision segmenter |
| **8K Vapor Chamber Cooling** | Sustained thermal management | Enables one uninterrupted property walkthrough — continuous camera + NPU + mic without thermal throttling |
| **Triple 50MP Camera** | Sony IMX921 OIS + ultrawide + periscope | OIS stabilizes AR overlay; ultrawide captures full wall; telephoto zooms on small defects |
| **6000-nit 2K LTPO Display** | Peak brightness | AR ghost-overlay legible in bright sunlit rooms during daytime inspections |
| **7000mAh Battery + 100W Charging** | Large capacity, fast recharge | Survives 20–40 min walkthrough; recharges between hackathon eval rounds |
| **Sensing Hub** | Always-on low-power sensor processing | Runs lightweight "is there a tap sound" listener continuously |
| **Dual Stereo Speakers** | 120% louder vs. prior gen | Audible confirmation tones synced to acoustic tap results for judges |
| **Vivo Office Kit** | Screen mirror, clipboard sync | Live demo screen-mirrored to projector for judges |

---

## 🚀 Current Status (Day 2 of 12)

### ✅ What's Real and Working

| Subsystem | Implementation | Status |
|-----------|----------------|--------|
| Camera capture + preview | `camera/CameraController.kt` | ✅ Real CameraX wrapper |
| AR baseline alignment | `camera/ArAlignmentTracker.kt` | ✅ Real SensorManager rotation tracking |
| OCR | `ocr/OcrEngine.kt` | ✅ Real ML Kit on-device text recognition |
| IR transmit | `ir/IrController.kt` | ✅ Real ConsumerIrManager wrapper |
| Acoustic tap analysis | `acoustic/AcousticTapClassifier.kt` + `Fft.kt` | ✅ Real AudioRecord + FFT + heuristic |
| Safety gate | `safety/SafetyGate.kt` | ✅ Real deterministic keyword-based rules |
| PDF report generation | `report/ReportGenerator.kt` | ✅ Real offline PDF rendering |
| Local storage | `data/` | ✅ Real Room database |

### 🔧 Honestly Stubbed (Training in Progress)

| Subsystem | Why Stubbed | Timeline |
|-----------|-------------|----------|
| Vision defect segmenter | Needs YOLOv8-Seg training + ExecuTorch export | Days 3–8 |
| GenieX report narrative | Needs Qualcomm GenieX SDK integration | Days 9–10 |
| Real AC IR patterns | Needs live capture from demo unit | Day 12 (on-site) |

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

## 🏆 Team

**3-person team for iQOO Hackathon 2026 — Chennai City Battle**

| Role | Pre-Event Focus | Event-Day Focus |
|------|----------------|-----------------|
| **Member 1 — AI/NPU Engineer** | Train & export models (acoustic + vision); GenieX testing | Integrate pre-tested models; tune to venue conditions |
| **Member 2 — Android/Hardware Lead** | AR overlay, IR transmit, pre-capture AC IR codes | Live on-site IR capture; Office Kit Red Light sessions |
| **Member 3 — Product/Demo Pitcher** | Report UI, pricing logic, source citations, draft pitch | Rehearsed demo delivery, judge Q&A |

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

- **GitHub:** (this repository)
- **Hackathon submission:** [iQOO Hackathon Portal](https://iqoo.com/in)
- **Demo video:** (TBD after Day 11 rehearsal)
- **Live demo:** Sep 12–13, 2026 — Chennai

---

<p align="center">
  <img src="https://img.shields.io/badge/Made_for-iQOO_15-blueviolet?style=for-the-badge&logo=android" alt="Made for iQOO 15">
  <img src="https://img.shields.io/badge/Powered_by-Snapdragon_8_Elite-red?style=for-the-badge&logo=qualcomm" alt="Powered by Snapdragon 8 Elite">
  <img src="https://img.shields.io/badge/Built_in-Chennai-orange?style=for-the-badge&logo=googlemaps" alt="Built in Chennai">
</p>
