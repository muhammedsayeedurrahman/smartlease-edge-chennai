# SmartLease Edge

**On-device property verification for rental move-outs — the inspection runs with no network, and photos never leave the phone**

iQOO Hackathon 2026 · Smart Living Track · Chennai City Battle · Sep 12–13, 2026

---

## Team Background

Three-person team with previous hackathon experience:
- 1st Place: Lawtrix (Sairam Engineering College)
- SIH Finalist (Smart India Hackathon)
- IOB Hackathon Top 10: Mule Catch project

---

## Problem

Security deposit disputes are the primary source of landlord-tenant conflict in India, consistently traced to lack of move-in documentation. In Bengaluru, deposits typically run 6–9 months' rent, making documentation failures expensive.

SmartLeaseEdge provides timestamped, sensor-backed property verification. The entire inspection — capture, the vision model, the acoustic tap test, costing, the PDF and its SHA-256 — runs on the handset with no network, so it works in basements and dead spots.

An optional, **off-by-default** sync (`com.smartlease.edge.sync`, server in `server/`) uploads a signed findings digest plus rupee totals so a report can be held in custody and countersigned. Inspection photos, video and audio are never uploaded, and this is structural rather than a promise: the sync wire types carry no bitmap or byte-array field, the server schema has no binary column, and the API rejects unknown fields outright.

---

## Competitive Analysis

Researched existing solutions with verified pricing:

| Feature | SmartLeaseEdge | NoBroker | TurboTenant | zInspector | RentCheck |
|---------|---------------|----------|-------------|------------|-----------|
| Offline operation | Yes (inspection needs no network) | No (human inspector) | No (cloud) | No (SaaS) | No (SaaS) |
| Functional testing | IR + acoustic | Visual only | Manual | Visual only | Visual only |
| AI detection | On-device CPU | Human | None | Cloud AI | Cloud AI |
| Pricing | ₹299–499 one-time | ₹1,649–5,999/inspection | Free (manual) | $0–115/mo | $0.60–1.10/unit/mo |
| Target user | Single lease | Portfolios | DIY | Property managers | Property managers |

**Key differentiation:** No existing solution combines a network-independent inspection with functional diagnostic testing. 

- NoBroker: Requires scheduling human inspector, several days lead time, ₹1,649–5,999 per inspection
- zInspector/RentCheck: Cloud-dependent SaaS, per-unit pricing model unsuitable for single-lease use case
- TurboTenant: Manual documentation only, no automated defect detection

SmartLeaseEdge targets the gap: the inspection is network-independent, it tests appliance function (not just visual appearance), and it is priced for single tenant-landlord transactions.

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
│  ON-DEVICE PROCESSING (host CPU — see Current Status)       │
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
│  REPORT LAYER (rule-based synthesis, host CPU)             │
│  Plain-language repair/cost breakdown                      │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  OUTPUT: Timestamped local PDF + SHA-256 — all on-device    │
└─────────────────────────────────────────────────────────────┘
```

---

## iQOO 15 Hardware Utilization

Specific hardware features mapped to implementation requirements:

| Hardware Component | Specification | Implementation Use Case |
|--------------------|---------------|------------------------|
| Snapdragon 8 Elite Gen 5 Hexagon NPU | 37% faster AI inference vs. prior generation | **Not currently used.** All three models run on the CPU. The ExecuTorch INT8 `.pte` is compiled and in the repo (`ml/YOLOV8/qnn_android_bundle/`) but is not wired into the APK — see Current Status below |
| 8K Vapor Chamber Cooling | Sustained thermal management | Sustained CPU inference across a long walkthrough without throttling |
| Triple 50MP Camera (Sony IMX921 OIS, ultrawide, periscope) | OIS, multi-focal lengths | OIS steadies handheld capture. The app binds the default rear camera via CameraX; it does not currently select ultrawide or telephoto |
| 6000-nit 2K LTPO Display | High peak brightness | Keeps the live camera preview usable in bright sunlit rooms. *(No AR overlay is rendered — pose delta is shown as text.)* |
| 7000mAh Battery + 100W Charging | Extended capacity, fast charging | Supports 20–40 minute walkthrough; rapid recharge between hackathon evaluation rounds |
| Stereo mics | 44.1 kHz PCM via `AudioRecord` | Captures the knuckle-tap transient on a button press. **Not** the Sensing Hub: its always-on audio path has no third-party API |
| 2K LTPO display | High refresh, high brightness | Keeps the camera preview and findings log legible in a sunlit flat. *(No audio confirmation tone is implemented.)* |
| Vivo Office Kit | Screen mirroring, remote control | Live screen projection for evaluation; phone-only development workflow |

The honest summary of this table: **the IR blaster is the only component here that a commodity phone does not have and that this app actually uses.** Everything else is camera, mic and sensors. The NPU is headroom we have not taken up.

---

## 🚀 Current Status (Sep 12, judging eve) — Honest MVP Assessment

Audited against the source on 2026-09-12; every claim below was checked against code, not
against an earlier version of this file. See `AUDIT_CLAIMS.md` and `AUDIT_GAPS.md` at the
workspace root for the full ledger, and `METRICS.md` for the first recorded vision metrics.

### ✅ Real and running

| Subsystem | Implementation | Status |
|-----------|----------------|--------|
| **Camera capture + preview** | `camera/CameraController.kt` | ✅ CameraX, default rear camera |
| **Pose baseline** | `camera/ArAlignmentTracker.kt` | ✅ Rotation-vector pitch/roll delta, ±4° tolerance. In memory for the session — **not** a stored move-in reference, and no photo is kept |
| **OCR** | `ocr/OcrEngine.kt` | ✅ ML Kit bundled Latin recognizer, genuinely offline |
| **IR transmit** | `ir/IrController.kt` | ✅ Real `ConsumerIrManager`. Transmit only — Android cannot receive IR, so a trigger is logged as *sent*, never as *verified* |
| **Vision segmentation** | `vision/YoloSegDefectSegmenter.kt` | ✅ 4-class YOLOv8n-Seg (`yolov8n_seg.ptl`), **on the CPU** via PyTorch Lite. Falls back to a labelled colour heuristic if the model fails a load-time contract probe |
| **Acoustic tap** | `acoustic/` | ✅ 36-feature logistic regression, CPU. Reported with its grouped cross-validated accuracy and 95% CI |
| **Safety gate** | `safety/SafetyGate.kt` | ✅ Deterministic keyword rules, 8 unit tests. Can only escalate, never suppress |
| **Report** | `report/ReportGenerator.kt` | ✅ Offline `PdfDocument`, with a **SHA-256 of exactly its findings on every page** |
| **Share** | `ui/screens/ReportScreen.kt` | ✅ `FileProvider` + share sheet, scoped to `filesDir/reports/` |
| **Storage** | `data/` | ✅ Room, local only. `allowBackup=false`, every backup domain excluded |

**Build:** `./gradlew :app:assembleDebug` — arm64-v8a only, ~14 MB of assets.

### ❌ What is claimed nowhere, because it does not exist

| | Why it matters |
|---|---|
| **Hexagon NPU / HTP execution** | The only inference dependency is `org.pytorch:pytorch_android_lite:1.13.0`, a CPU interpreter. The ExecuTorch INT8 `.pte` is compiled and in the repo (`ml/YOLOV8/qnn_android_bundle/`) but is **not in the APK**, was lowered for **SM8650 (Snapdragon 8 Gen 3)**, and is missing the `libQnnHtpV##Skel.so` its own build script lists as required |
| **On-device LLM / GenieX / Llama 3.2** | `synthesizeNarrative()` is string templating. No LLM dependency exists |
| **Dual-party signature** | Not implemented. The SHA-256 proves the findings are unaltered; it does **not** identify who recorded them, and the timestamp is `System.currentTimeMillis()`, which a user can move |
| **Pinhole area in sq ft** | The app reports mask coverage × a hardcoded 48″×36″ frame. There is no focal length and no depth on device, so the number does not change with distance. The real formula lives in `ml/vision/predict.py` and never runs on the phone |
| **Sensing Hub always-on capture** | No third-party API exists for it. Capture is `AudioRecord` on a button press |
| **Move-in ↔ move-out diff** | No stored baseline, no photo retention, no phone-to-phone transport, no diff engine, no wear-and-tear rules, no fixture inventory. This is a **single-session condition record**, and that is what it should be pitched as |

### ⚠️ Known weaknesses we will raise before a judge does

- **Vision mAP50 (mask) is 0.2145**, and the number went *down* on purpose. The previous
  0.659 was measured on a split whose test set was 81% leaked into training — a memorisation
  score, not an accuracy. The shipping model is retrained on a leak-free rebuild, and 0.2145
  is the pooled val+test estimate over all 686 held-out images (`handoff/METRICS_MODELS.md`),
  which is the most reliable point estimate available. The 268-image test split alone reads
  0.2488; quote the pooled figure.
- **Per-class accuracy is not quotable from our test split.** It holds only 46 crack
  instances, which is too few to read an AP from. We know crack is the weak class and we say
  so; we do not attach a decimal to it.
- **False positives on clean surfaces are measured on 63 held-out backgrounds.** At the
  threshold the app ships (0.45) it leaves 39.7% of clean images completely alone, against
  19.0% at the Ultralytics-default 0.25 — bought for 0.7 of a point of recall. The model
  previously on the phone left **3.2%** alone. Caveat we state before anyone asks: those 63
  negatives are drone photographs of building exteriors, not flat interiors.
  `docs/guides/SHOOT_LIST.md` is the indoor shoot that would let us state this properly.
- **The acoustic model is trained on 15 taps from 8 recordings.** 80% grouped-LOGO accuracy,
  **95% CI 55–93%**, against a **53.3%** majority baseline — so the bottom of the interval is
  chance, and the model is not yet distinguishable from guessing at the low end. The app
  prints this interval on screen rather than a point estimate.
  `docs/guides/RECORDING_PROTOCOL.md` is the 32-spot recording that would close it.
- **No on-device latency measurement exists.** 21.8 ms/image on a desktop CPU is a bound,
  not an app number.

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

📖 **Guide:** [`ACOUSTIC_RECORDING_CHECKLIST.md`](./docs/guides/ACOUSTIC_RECORDING_CHECKLIST.md)

### Wall Damage Images

**Target:** 200–500 annotated images of wall defects

- **Public datasets:** Roboflow, Kaggle (pre-annotated)
- **Own photos:** Nail holes, cracks, stains, peeling paint

📖 **Guide:** [`DATA_COLLECTION_GUIDE.md`](./docs/guides/DATA_COLLECTION_GUIDE.md)
📖 **Dataset sources:** [`DATASET_SOURCES.md`](./docs/guides/DATASET_SOURCES.md)

### Validation

```bash
python tools/validate_training_data.py
```

---

## 🗓️ 12-Day Hackathon Prep Timeline

| Days | Planned | What actually happened |
|------|---------|------------------------|
| **1–2** | Data collection | 8 tap recordings (15 taps). Vision data taken from 4 public Roboflow datasets instead of collected |
| **3–5** | Model training | ✅ Both models trained. Acoustic: grouped-LOGO evaluated. Vision: trained, **not** evaluated until Sep 12 — see `METRICS.md` |
| **6–8** | Export & NPU test | ⚠️ ExecuTorch INT8 `.pte` exported and in the repo. **The HTP test never happened and the delegate is not in the APK** |
| **9–10** | GenieX report generation | ❌ Not started. The narrative is rule-based templating |
| **11** | Rehearsal | — |
| **12** | Capture real AC IR pattern on-site | Outstanding. The shipped burst is an unverified NEC-family header |

---

## 🎤 Demo Script (3 minutes)

1. **0:00–0:45** — Problem hook: Deposit disputes as India's top landlord-tenant conflict
2. **0:45–1:45** — Live offline showcase: Airplane mode → AR alignment → acoustic tap → IR AC trigger
3. **1:45–2:30** — Report synthesis: findings render to a PDF on-device, carrying a SHA-256 of exactly those findings on every page; share sheet to the laptop
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
- **Data collection guide:** [`DATA_COLLECTION_GUIDE.md`](./docs/guides/DATA_COLLECTION_GUIDE.md)
- **Dataset sources:** [`DATASET_SOURCES.md`](./docs/guides/DATASET_SOURCES.md)
- **Acoustic recording checklist:** [`ACOUSTIC_RECORDING_CHECKLIST.md`](./docs/guides/ACOUSTIC_RECORDING_CHECKLIST.md)

---

## 🏆 Team Structure & Execution Strategy

**3-person team · Structured for parallel execution**

### Team Roles

| Role | Pre-Event Focus (Days 1–11) | Event-Day Focus (Sep 12–13) |
|------|----------------------------|----------------------------|
| **Member 1 — AI Engineer** | Train & export models (acoustic classifier, YOLOv8-Seg vision); ExecuTorch INT8 lowering | Integrate models into the live app; tune to venue lighting/acoustics |
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

See [`TEAM_STRUCTURE.md`](./docs/guides/TEAM_STRUCTURE.md) for full breakdown.

---

## 📜 License

**Educational/Hackathon Project** — MIT License (TBD post-event)

This project was built for the **iQOO Hackathon 2026 — Chennai City Battle** (Smart Living track, Sep 12–13, 2026).

---

## 🙏 Acknowledgments

- **iQOO India** for the hackathon platform and iQOO 15 hardware access
- **Roboflow Universe** contributors for the four CC BY 4.0 defect datasets: `infrastructure-monitoring`, `structural-defects`, `building-anomalies`, `concrete-defects`
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
