# Onboarding Guide — Android Developer (Member 2)

**Welcome to SmartLeaseEdge!** This guide gets you productive in 30 minutes.

---

## 🎯 Your Mission

Build the Android app UI/UX and hardware integrations for the iQOO Hackathon 2026 demo.

**Timeline:** Days 3–11 (Sep 3–11, 2026)
**Output:** Polished Android app with AR, camera, IR features

---

## ✅ Setup Checklist (Do This First)

### 1. Install Tools

- [ ] **Android Studio** (latest stable version)
  - Download: https://developer.android.com/studio
  - Install with SDK Manager: Android 14 (API 34)

- [ ] **Git**
  - Download: https://git-scm.com/downloads
  - Verify: `git --version`

- [ ] **GitHub Account**
  - Already have access to: https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai

### 2. Clone Repository

```bash
# Choose a location on your machine
cd C:\Projects  # Or your preferred directory

# Clone the repo
git clone https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai.git
cd smartlease-edge-chennai

# Create your feature branch
git checkout -b feature/android-ui
git push -u origin feature/android-ui
```

### 3. Open in Android Studio

```
File → Open → C:\Projects\smartlease-edge-chennai
```

**First-time setup:**
- Android Studio will sync Gradle automatically (wait 2–5 minutes)
- If prompted to update Gradle/AGP: decline (we're on 9.2.1, keep it)
- If SDK/build tools are missing: click "Install" in the error banner

### 4. Build the App

```bash
# In Android Studio terminal, or external terminal:
./gradlew :app:assembleDebug
```

**Expected output:**
```
BUILD SUCCESSFUL in 45s
Output: app/build/outputs/apk/debug/app-debug.apk (~56MB)
```

**If build fails:**
- Check `local.properties` exists and has: `sdk.dir=C:/Users/YourName/AppData/Local/Android/Sdk` (use forward slashes!)
- Check JDK: Android Studio → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK → Use "Android Studio's JBR"

### 5. Run on Device/Emulator

**Option A: Physical Device (Recommended)**
1. Enable Developer Options on your phone:
   - Settings → About Phone → Tap "Build Number" 7 times
2. Enable USB Debugging:
   - Settings → Developer Options → USB Debugging ON
3. Connect via USB
4. Android Studio → Run button (green triangle)

**Option B: Emulator**
1. Tools → Device Manager → Create Device
2. Choose: Pixel 7 Pro, API 34, x86_64
3. Click green "Run" button

**Expected result:**
- App launches
- Shows "SmartLease Edge" home screen
- Three tabs visible (Home, Walkthrough, Report)

---

## 📂 Project Structure (Your Territory)

```
app/src/main/
├── java/com/smartlease/edge/
│   ├── MainActivity.kt          # 🔧 Entry point - set up navigation
│   │
│   ├── ui/                      # 🎨 YOUR PRIMARY WORK AREA
│   │   ├── screens/
│   │   │   ├── HomeScreen.kt    # Home screen UI
│   │   │   ├── WalkthroughScreen.kt  # AR/camera/inspection flow
│   │   │   └── ReportScreen.kt  # PDF report viewer
│   │   └── theme/
│   │       ├── Color.kt         # App colors
│   │       └── Theme.kt         # Material3 theme
│   │
│   ├── camera/                  # 📷 Camera & AR alignment
│   │   ├── CameraController.kt  # CameraX wrapper
│   │   └── ArAlignmentTracker.kt  # SensorManager rotation tracking
│   │
│   ├── ir/                      # 📡 IR blaster
│   │   └── IrController.kt      # ConsumerIrManager wrapper
│   │
│   ├── acoustic/                # 🔊 Acoustic tap (Member 1 integrates model)
│   │   ├── AcousticTapClassifier.kt
│   │   └── Fft.kt
│   │
│   ├── vision/                  # 👁️ Vision segmenter (Member 1 integrates model)
│   │   └── DefectSegmenter.kt
│   │
│   ├── report/                  # 📄 PDF generator
│   │   ├── ReportGenerator.kt   # ⚠️ GenieX part: Member 1 owns
│   │   └── ReportModels.kt      # Data classes
│   │
│   ├── ocr/                     # 🔤 Text recognition
│   │   └── OcrEngine.kt         # ML Kit integration
│   │
│   ├── safety/                  # 🛡️ Safety gate
│   │   └── SafetyGate.kt
│   │
│   └── data/                    # 💾 Database (coordinate before changing)
│       ├── AppDatabase.kt
│       ├── InspectionEntity.kt
│       └── InspectionDao.kt
│
└── res/                         # 🎨 Resources
    ├── drawable/                # Icons, backgrounds
    ├── values/
    │   ├── strings.xml          # App text
    │   └── themes.xml           # XML themes
    └── mipmap/                  # App icons
```

**What you own:**
- Everything in `ui/` folder
- Camera and IR controllers
- Resource files (drawables, strings, themes)
- `MainActivity.kt` navigation setup

**What you coordinate on:**
- `data/` folder (database schema - ask before changing)
- Anything in `report/ReportGenerator.kt` related to GenieX (Member 1 owns)

**What you don't touch:**
- `training_data/` (Member 1 only)
- `docs/` (Member 3 only)
- ML training scripts (Member 1 only)

---

## 🚀 Your First Task (Days 3–5)

### Task 1: Implement AR Overlay UI (1–2 days)

**Goal:** Visual ghost-overlay of baseline photo on camera preview.

**Files to edit:**
- `app/src/main/java/com/smartlease/edge/ui/screens/WalkthroughScreen.kt`
- `app/src/main/java/com/smartlease/edge/camera/ArAlignmentTracker.kt`

**What to build:**
1. **Camera preview** with baseline photo overlay at 40% opacity
2. **Alignment indicator:**
   - Green border when aligned (pitch/roll delta <5°)
   - Yellow when close (5–10°)
   - Red when misaligned (>10°)
3. **Numeric feedback:**
   - Display pitch/roll delta in degrees
   - "ALIGNED" text when ready

**Example UI:**
```
┌──────────────────────────────┐
│ 📷 Camera Preview            │
│   (with ghost overlay)       │
│                              │
│   Pitch: +2.3°  Roll: -1.8° │
│   ✓ ALIGNED                  │
│                              │
│   [Capture Baseline]         │
└──────────────────────────────┘
```

**Test criteria:**
- [ ] Overlay renders over camera preview
- [ ] Opacity adjustable (40% default)
- [ ] Pitch/roll updates in real-time (<100ms lag)
- [ ] Alignment indicator changes color correctly
- [ ] Works in bright sunlight (6000-nit display should be legible)

**Commit when done:**
```bash
git add .
git commit -m "feat: add AR baseline alignment overlay

- Implemented ghost image overlay at 40% opacity
- Real-time pitch/roll delta display
- Color-coded alignment indicator (red/yellow/green)
- Tested on 3 different walls in various lighting

Related: #5"
git push
```

---

### Task 2: Build IR Transmit Interface (1 day)

**Goal:** Trigger AC on/off via IR blaster.

**Files to edit:**
- `app/src/main/java/com/smartlease/edge/ir/IrController.kt`
- `app/src/main/java/com/smartlease/edge/ui/screens/WalkthroughScreen.kt` (add IR test UI)

**What to build:**
1. **Pre-capture common AC IR codes** (before event day):
   - Voltas
   - Blue Star
   - LG
   - Daikin
   - Hitachi
   - Store in `IrController.CommonAcIrProfiles`

2. **IR transmit UI:**
   - Button: "Test AC Power"
   - Dropdown: Select AC brand
   - Transmit IR code when tapped
   - Show feedback: "IR signal sent ✓"

3. **Live capture UI** (for event day):
   - Button: "Learn IR Code from Remote"
   - Instructions: "Point AC remote at phone, press Power button"
   - Save learned pattern to local storage

**⚠️ Limitation:** Android's `ConsumerIrManager` can TRANSMIT IR, but NOT receive/decode IR signals. The "learn IR code" feature will need manual pattern entry or a workaround (use IR remote app to get hex codes).

**Test criteria:**
- [ ] Pre-captured IR codes stored in data class
- [ ] Transmit works on device with IR blaster (borrow one if needed)
- [ ] UI shows brand selection dropdown
- [ ] Feedback shown after transmit
- [ ] Works offline (no network calls)

**Commit:**
```bash
git commit -m "feat: add IR transmit interface for AC control

- Pre-captured IR codes for 5 AC brands (Voltas, Blue Star, LG, Daikin, Hitachi)
- IR transmit UI with brand selection
- Tested on [Device Name] with IR blaster
- Fallback for devices without IR: shows warning

Related: #8"
git push
```

---

### Task 3: Improve Camera UI (0.5 days)

**Goal:** Better camera controls for property inspection.

**What to add:**
- Tap-to-focus (tap preview to focus on that point)
- Exposure controls (slider: -2 to +2 EV)
- Zoom controls (pinch-to-zoom, or buttons for 1x/2x/10x)
  - 1x: Main 50MP camera
  - 2x: Digital zoom
  - 10x: Periscope telephoto (if hardware supports)

**Files:**
- `camera/CameraController.kt`
- `ui/screens/WalkthroughScreen.kt`

**Test:**
- [ ] Tap-to-focus works
- [ ] Exposure slider adjusts brightness
- [ ] Zoom works (1x minimum, 10x for small defects)

---

## 📖 Key Technologies You'll Use

### Jetpack Compose (UI Framework)

```kotlin
// Modern declarative UI
@Composable
fun WalkthroughScreen() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        CameraPreview(modifier = Modifier.weight(1f))
        
        AlignmentIndicator(
            pitchDelta = 2.3f,
            rollDelta = -1.8f
        )
        
        Button(onClick = { captureBaseline() }) {
            Text("Capture Baseline")
        }
    }
}
```

**Resources:**
- Official docs: https://developer.android.com/jetpack/compose
- Compose samples: https://github.com/android/compose-samples

### CameraX (Camera API)

```kotlin
// Modern camera API, easier than Camera2
val cameraController = CameraController(context)

cameraController.bindCamera(
    lifecycleOwner = this,
    onPreviewReady = { /* ... */ }
)

cameraController.takePicture { imageProxy ->
    // Process captured image
}
```

**Resources:**
- Docs: https://developer.android.com/training/camerax
- Codelab: https://developer.android.com/codelabs/camerax-getting-started

### Material 3 (Design System)

```kotlin
// Modern Material Design components
MaterialTheme {
    Button(
        onClick = { /* ... */ },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text("Action")
    }
}
```

**Resources:**
- Guidelines: https://m3.material.io/
- Compose Material3: https://developer.android.com/jetpack/compose/designsystems/material3

---

## 🐛 Troubleshooting

### Build Issues

**Problem:** "SDK location not found"
```
Solution:
1. Create local.properties file in project root
2. Add: sdk.dir=C:/Users/YourName/AppData/Local/Android/Sdk
   (Use forward slashes, even on Windows!)
```

**Problem:** "Unsupported Gradle version"
```
Solution:
Keep Gradle 9.4.1-bin (already configured).
Don't click "Update" if prompted.
```

**Problem:** Room compilation errors with suspend functions
```
Already fixed: We're using Room 2.7.2, not 2.6.1.
If you see "Continuation<Long> vs Continuation<? super Long>" errors,
verify gradle/libs.versions.toml has room = "2.7.2"
```

### Runtime Issues

**Problem:** Camera crashes on rotation
```
Solution:
Call cameraController.unbindCamera() in onPause()
Re-bind in onResume()
```

**Problem:** AR overlay not updating
```
Solution:
Check SensorManager is registered in onResume()
Unregister in onPause() to save battery
```

**Problem:** IR transmit does nothing
```
Check:
1. Device has IR blaster (check hardware docs)
2. ConsumerIrManager is not null
3. IR frequency is correct (38000 Hz for most ACs)
```

---

## 📞 Getting Help

**Stuck? Here's what to do:**

1. **Check existing code:**
   - Look at `ArAlignmentTracker.kt` — AR alignment example
   - Look at `CameraController.kt` — CameraX wrapper pattern

2. **Search Android docs:**
   - https://developer.android.com

3. **Ask in group chat:**
   - "I'm trying to [X] but getting [Y error]. I've tried [Z]. Any ideas?"
   - Include: error message, code snippet, what you've tried

4. **Create GitHub issue:**
   - If it's a bug or blocker

---

## ✅ Daily Workflow

**Morning:**
```bash
git checkout feature/android-ui
git pull origin master  # Sync with latest
```

**During day:**
```bash
# Work on your features
# Build and test frequently

git add .
git commit -m "feat: add exposure controls to camera"
git push
```

**End of day:**
```bash
git push  # Make sure everything is backed up
```

**Post in group chat:**
```
✅ Day 3 complete:
- Implemented AR overlay UI
- Alignment indicator working
- Tested on 3 walls

→ Tomorrow: IR transmit interface
⚠️ None
```

---

## 🎯 Success Metrics (Day 11)

By end of Day 11, you should have:

- [ ] AR overlay rendering smoothly (60 FPS)
- [ ] Alignment indicator color-coded and accurate
- [ ] IR transmit working with pre-captured codes
- [ ] Camera controls (focus, exposure, zoom) functional
- [ ] All UI screens implemented (Home, Walkthrough, Report)
- [ ] App runs in airplane mode (no network calls)
- [ ] Build runs on iQOO 15 or similar Snapdragon device
- [ ] No crashes during 20-minute walkthrough
- [ ] Vivo Office Kit screen mirroring tested

---

## 📚 Additional Resources

**Android Development:**
- Official guides: https://developer.android.com/guide
- Kotlin docs: https://kotlinlang.org/docs/home.html
- Compose tutorial: https://developer.android.com/courses/pathways/compose

**iQOO 15 Specific:**
- Official specs: https://www.iqoo.com/in/products/param/iqoo15
- Snapdragon 8 Elite: https://www.qualcomm.com/products/mobile/snapdragon/smartphones/snapdragon-8-series-mobile-platforms/snapdragon-8-elite-mobile-platform

**Project Docs:**
- `README.md` — Project overview
- `TEAM_STRUCTURE.md` — Your role details
- `GITHUB_WORKFLOW.md` — Git help
- `CONTRIBUTING.md` — Code style

---

**Welcome to the team! Let's build an amazing demo. 🚀**

**Questions?** Ask in group chat or create an issue.
