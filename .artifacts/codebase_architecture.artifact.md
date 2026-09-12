# SmartLease Edge — Codebase Architecture & Execution Guide

SmartLeaseEdge is an offline property verification and move-out inspection application built for Android (optimized for iQOO / Snapdragon 8 Elite hardware with Hexagon NPU). It operates **100% offline**, requiring zero cloud connectivity, making it ideal for basements, remote properties, and privacy-first security deposit settlements.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│  USER INTERFACE (Jetpack Compose Material 3)                │
│  HomeScreen · WalkthroughScreen · ReportScreen              │
└─────────────────────────────────────────────────────────────┘
                               │
         ┌─────────────────────┴─────────────────────┐
         ▼                                           ▼
┌─────────────────────────┐               ┌─────────────────────────┐
│ MULTI-MODAL SENSORS     │               │ ON-DEVICE AI / NPU      │
│ 📷 CameraX + YOLOv8-Seg │               │ 🧠 PyTorch Lite (Vision)│
│ 🎤 Mic + FFT / Acoustic │               │ 🤖 Qualcomm Genie JNI   │
│ 📡 IR Blaster Controls  │               │ 📝 ML Kit OCR           │
│ 🧭 IMU Heading / OIS    │               │                         │
└─────────────────────────┘               └─────────────────────────┘
         │                                           │
         └─────────────────────┬─────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ LOCAL PERSISTENCE & PROCESSING                              │
│ RoomDatabase (SQLite) · ReportGenerator (PDF) · SafetyGate  │
└─────────────────────────────────────────────────────────────┘
```

---

## Package Breakdown & Core Components

### 1. Root Package (`com.smartlease.edge`)
- **[MainActivity.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/MainActivity.kt)**: The single activity hosting the Jetpack Compose navigation graph (`HomeScreen` ➔ `WalkthroughScreen` ➔ `ReportScreen`).

### 2. Vision & 360° Walkthrough (`com.smartlease.edge.vision`, `com.smartlease.edge.camera`, `com.smartlease.edge.inspection360`)
- **[YoloSegDefectSegmenter.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/vision/YoloSegDefectSegmenter.kt)** / **[YoloSegDecoder.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/vision/YoloSegDecoder.kt)**: Runs on-device YOLOv8-Seg PyTorch Lite models to detect defects (wall cracks, water stains, mold), outputting bounding boxes and estimated surface area in square feet.
- **[CameraController.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/camera/CameraController.kt)**: CameraX wrapper capturing high-resolution frames with OIS stabilization.
- **[HeadingTracker.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/inspection360/HeadingTracker.kt)** & **[VideoInspectionCoordinator.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/inspection360/VideoInspectionCoordinator.kt)**: Manages 360-degree room walkthroughs by extracting frames across 4 walls (North, East, South, West) and running defect segmentation on each.

### 3. Acoustic Diagnostic Testing (`com.smartlease.edge.acoustic`)
- **[AcousticFeatureExtractor.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/acoustic/AcousticFeatureExtractor.kt)** & **[Fft.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/acoustic/Fft.kt)**: Computes Fast Fourier Transforms (FFT) and spectral features from microphone audio during appliance/wall tap tests.
- **[AcousticTapClassifier.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/acoustic/AcousticTapClassifier.kt)**: Classifies acoustic tap resonance to determine structural integrity (e.g., hollow drywall vs. solid masonry, normal vs. damaged pipe flow).

### 4. Infrared Appliance Testing (`com.smartlease.edge.ir`)
- **[IrController.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/ir/IrController.kt)**: Interfaces with consumer IR hardware to issue functional test commands to appliances (AC, TV, fans) during inspection.

### 5. OCR & Meter Reading (`com.smartlease.edge.ocr`)
- **[OcrEngine.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/ocr/OcrEngine.kt)**: Uses Google ML Kit Text Recognition to read utility meters (electricity/water) and appliance serial numbers offline.

### 6. LLM Summary & Report Generation (`com.smartlease.edge.llm`, `com.smartlease.edge.report`)
- **[LLMEngine.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/llm/LLMEngine.kt)** & **[native-lib.cpp](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/cpp/native-lib.cpp)**: Integrates with Qualcomm Genie / Llama-3.2 (via JNI C++) to generate dynamic, context-aware inspection summaries based on the walkthrough findings.
- **[ReportGenerator.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/report/ReportGenerator.kt)**: Assembles inspection findings into structured sections and compiles them into a signed, timestamped local PDF file using Android's native `PdfDocument` API.

### 7. Local Persistence (`com.smartlease.edge.data`)
- **[AppDatabase.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/data/AppDatabase.kt)**, **[InspectionDao.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/data/InspectionDao.kt)**, **[InspectionEntity.kt](file:///D:/hackathon/iq/smartlease-edge-chennai/app/src/main/java/com/smartlease/edge/data/InspectionEntity.kt)**: Room database managing offline session records, defect classifications, and sensor measurements.

---

## Key Data Flow

1. **Walkthrough Start**: User initiates an inspection session from `HomeScreen`.
2. **Data Capture**:
   - Camera captures frames/video analyzed by `YoloSegDefectSegmenter`.
   - Microphone records tap tests analyzed by `AcousticFeatureExtractor` and `AcousticTapClassifier`.
   - IR Controller tests appliances.
3. **Safety Evaluation**: `SafetyGate` checks findings against hazard thresholds.
4. **AI Synthesis**: Tapping "AI Summary" triggers `LLMEngine` to generate natural-language prose summarizing the findings.
5. **Report & PDF Export**: `ReportGenerator` compiles session findings into a structured report and exports a signed PDF locally to device storage.
