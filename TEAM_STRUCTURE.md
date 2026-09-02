# SmartLeaseEdge — Team Structure & Roles

**Event:** iQOO Hackathon 2026 — Chennai City Battle
**Timeline:** Sep 2–13, 2026 (12-day prep + 2-day event)
**Current:** Day 2 of 12

---

## 👥 Team Composition

### Member 1: AI/NPU Engineer (You)
**Strengths:** ML/AI, Python, model training, NPU optimization
**Pre-Event Focus:** Train and export models (acoustic + vision); GenieX integration
**Event-Day Focus:** Integrate pre-tested models; tune to venue conditions

### Member 2: Android/App Developer
**Strengths:** Android development, Kotlin, UI/UX, hardware integration
**Pre-Event Focus:** AR overlay, IR transmit pipeline, UI screens, app architecture
**Event-Day Focus:** Live on-site IR capture; Vivo Office Kit Red Light sessions

### Member 3: Research & App Builder
**Strengths:** Research, curious about app building, documentation, testing
**Pre-Event Focus:** Data collection, testing, documentation, demo script, pitch prep
**Event-Day Focus:** Demo delivery, judge Q&A, presentation materials

---

## 📋 Detailed Role Breakdown

### 👨‍💻 Member 1: AI/NPU Engineer (You)

**Days 1–2 (Sep 1–2): Data Collection**
- ✅ Set up project structure (DONE)
- ✅ Create data collection guides (DONE)
- 🔄 Record acoustic tap samples (50 hollow + 50 solid)
- 🔄 Download/collect wall damage datasets (200–500 images)
- Validate data completeness

**Days 3–5 (Sep 3–5): Model Training**
- Train acoustic tap classifier
  - Input: Audio samples (WAV files)
  - Output: Hollow vs. solid classification model
  - Framework: PyTorch or TensorFlow Lite
  - Target: 90%+ accuracy on test set
- Train YOLOv8-Seg vision model
  - Input: Wall damage images with segmentation masks
  - Output: Defect segmentation model (cracks, holes, stains)
  - Framework: Ultralytics YOLOv8
  - Target: 75%+ mAP on test set

**Days 6–8 (Sep 6–8): Model Export & NPU Testing**
- Export vision model to ExecuTorch `.pte` format
- Test Hexagon NPU delegate on Snapdragon device
- Optimize INT8 quantization
- Benchmark inference speed (target: <100ms per frame)
- Fix delegate/op-support issues if they arise

**Days 9–10 (Sep 9–10): AI Integration**
- Wire acoustic classifier into `AcousticTapClassifier.kt`
- Wire vision model into `DefectSegmenter.kt`
- Integrate Qualcomm GenieX SDK for report generation
- End-to-end offline AI pipeline test
- Performance tuning

**Days 11 (Sep 11): Final Testing**
- Full pipeline test (camera → AI → report)
- Thermal/battery profiling
- Edge case testing

**Event Day (Sep 12–13):**
- Integrate models into live app
- Tune to venue lighting/acoustics
- Support Member 2 with technical issues

**Deliverables:**
- `acoustic_classifier.pth` or `.tflite` model
- `vision_segmenter.pte` ExecuTorch model
- GenieX integration code in `ReportGenerator.kt`
- Model performance report (accuracy, speed, size)

---

### 📱 Member 2: Android/App Developer

**Days 1–2 (Sep 1–2): Project Setup**
- ✅ Review existing Android codebase
- Set up Android Studio environment
- Familiarize with project structure
- Test build: `./gradlew :app:assembleDebug`
- Run on device/emulator

**Days 3–5 (Sep 3–5): Core App Features**
- Implement AR baseline alignment UI
  - Enhance `ArAlignmentTracker.kt`
  - Add ghost-overlay rendering on camera preview
  - Alignment accuracy indicator (pitch/roll delta display)
- Build IR transmit interface
  - Complete `IrController.kt` AC trigger logic
  - Pre-capture common AC IR codes (Voltas, Blue Star, LG, Daikin)
  - IR code library fallback system
- Implement camera UI improvements
  - Tap-to-focus
  - Exposure controls
  - Zoom for telephoto mode

**Days 6–8 (Sep 6–8): UI/UX Polish**
- Design and implement walkthrough screens
  - Onboarding flow (explain AR/acoustic/IR features)
  - Step-by-step property inspection guide
  - Progress tracker during walkthrough
- Build report preview screen
  - PDF viewer integration
  - Share/export functionality
  - Signature capture (tenant + landlord)
- Implement home screen
  - New inspection button
  - Inspection history list
  - Settings menu

**Days 9–10 (Sep 9–10): Integration & Testing**
- Integrate Member 1's AI models
  - Load `.pte` vision model via ExecuTorch
  - Load acoustic model via TensorFlow Lite
  - Wire GenieX report generation
- Hardware feature testing
  - Vapor chamber cooling performance (sustained load)
  - Battery life profiling (20–40 min walkthrough)
  - OIS camera stability
  - 6000-nit display outdoor legibility
- Bug fixes and edge cases

**Days 11 (Sep 11): Demo Prep**
- Vivo Office Kit setup (screen mirroring)
- Airplane mode end-to-end test
- Physical props setup (wall board, AC remote)
- Demo route rehearsal

**Event Day (Sep 12–13):**
- Live AC IR capture from demo unit
- Office Kit Red Light build sessions
- Real-time bug fixes
- Hardware troubleshooting

**Deliverables:**
- Polished Android app UI/UX
- AR overlay, IR control, camera features
- Integrated AI models (from Member 1)
- Demo-ready APK

---

### 🔬 Member 3: Research & App Builder

**Days 1–2 (Sep 1–2): Research & Data Collection**
- ✅ Review hackathon submission plan
- ✅ Understand competitive landscape
- Help collect acoustic tap samples
  - Record samples alongside Member 1
  - Label and organize audio files
  - Quality check recordings
- Assist with vision dataset curation
  - Download Roboflow/Kaggle datasets
  - Organize into training folders
  - Verify annotation quality
- Document data collection process

**Days 3–5 (Sep 3–5): Testing & Documentation**
- Manual testing of app features
  - Test camera capture flow
  - Test AR alignment on different walls
  - Test IR transmit with real AC remote
  - Test PDF report generation
- Create test cases document
  - Functional tests (each feature works)
  - Edge cases (low light, shaky camera, etc.)
  - Offline mode verification
- Update README and documentation
  - Keep README.md current with new features
  - Document API usage patterns
  - Create troubleshooting guide

**Days 6–8 (Sep 6–8): Demo Preparation**
- Draft demo script (3-minute version)
  - Problem hook (0:00–0:45)
  - Live demo (0:45–1:45)
  - Report generation (1:45–2:30)
  - Competitive close (2:30–3:00)
- Create pitch deck (if allowed)
  - Problem slide (deposit disputes)
  - Solution slide (offline multi-modal verification)
  - Competitive comparison table
  - Hardware utilization map
  - Roadmap slide
- Prepare demo props
  - Wall/tile board with defects
  - Baseline reference photos
  - AC remote (multiple brands as backup)
  - Charging cables, backup phone

**Days 9–10 (Sep 9–10): User Testing & Refinement**
- Run usability tests with non-team members
  - Can they complete a property inspection?
  - Where do they get confused?
  - What takes too long?
- Collect feedback and report to Member 2
- Write user guide / help text
- Test airplane mode end-to-end
- Verify no cloud dependencies

**Days 11 (Sep 11): Rehearsal & Final Prep**
- Run full demo 3+ times, timed
- Practice pitch delivery
- Prepare for judge Q&A
  - Common questions (How does NPU work? Why offline?)
  - Honest answers (what's stubbed, what's real)
  - Competitive positioning responses
- Pack all props and equipment
- Charge all devices

**Event Day (Sep 12–13):**
- Lead demo delivery and pitch
- Handle judge Q&A
- Capture photos/videos of demo
- Manage presentation materials
- Support team with non-technical tasks

**Deliverables:**
- Demo script (rehearsed, timed)
- Pitch deck (if applicable)
- Test cases document
- User guide
- Demo props and equipment

---

## 🔄 Parallel Work Coordination

### Daily Sync (15 minutes, end of day)

**Format:** Quick standup via chat or call

**Each member reports:**
1. What I completed today
2. What I'm working on tomorrow
3. Blockers / dependencies on other members

**Example:**
```
Member 1 (AI): 
  ✅ Trained acoustic classifier (92% accuracy)
  → Tomorrow: Start YOLOv8 training
  ⚠️ Need: Sample test images from Member 3

Member 2 (Android):
  ✅ Implemented AR overlay UI
  → Tomorrow: IR code pre-capture
  ⚠️ Need: Acoustic model from Member 1 by Day 8

Member 3 (Research):
  ✅ Collected 50 acoustic samples
  → Tomorrow: Download vision datasets
  ⚠️ None
```

### Weekly Review (30 minutes, end of week 1)

**When:** End of Day 7 (Sep 7)

**Agenda:**
- Review progress vs. 12-day plan
- Identify risks (what might not finish on time?)
- Adjust task assignments if needed
- Plan Week 2 priorities

---

## 📂 File Ownership (Avoid Conflicts)

### Member 1 (AI) owns:
```
training_data/
models/
scripts/train_*.py
scripts/export_*.py
app/src/main/java/com/smartlease/edge/report/ReportGenerator.kt (GenieX only)
```

### Member 2 (Android) owns:
```
app/src/main/java/com/smartlease/edge/ui/
app/src/main/java/com/smartlease/edge/camera/
app/src/main/java/com/smartlease/edge/ir/
app/src/main/res/
app/build.gradle.kts
```

### Member 3 (Research) owns:
```
docs/
DEMO_SCRIPT.md
PITCH_DECK.md (or .pptx)
TEST_CASES.md
USER_GUIDE.md
```

### Shared (coordinate before editing):
```
README.md
DATA_COLLECTION_GUIDE.md
app/src/main/java/com/smartlease/edge/data/ (database schema)
```

---

## ⚡ Quick Reference

| Day | Member 1 (AI) | Member 2 (Android) | Member 3 (Research) |
|-----|---------------|-------------------|-------------------|
| 1–2 | Data collection | Project setup | Data collection assist |
| 3–5 | Train models | Core app features | Testing & docs |
| 6–8 | Model export & NPU test | UI/UX polish | Demo prep |
| 9–10 | AI integration | Integration & testing | User testing |
| 11 | Final testing | Demo prep | Rehearsal |
| 12–13 | Event support | IR capture, builds | Demo delivery |

---

## 📞 Communication

**Primary channel:** (Choose one)
- WhatsApp group
- Slack channel
- Discord server

**Response time expectations:**
- Urgent (blockers): <1 hour
- Normal questions: <4 hours
- Code reviews: <8 hours

**Escalation:**
If stuck for >2 hours on a blocker → post in group immediately, don't wait

---

## 🎯 Success Metrics

**Pre-Event (Day 11 completion):**
- [ ] Both AI models trained and exported (Member 1)
- [ ] All app screens implemented (Member 2)
- [ ] Demo script rehearsed 3+ times (Member 3)
- [ ] Full offline pipeline test passes (All)
- [ ] Build runs on iQOO 15 or similar device (Member 2)

**Event Day:**
- [ ] Airplane mode demo successful (Eval Round 1)
- [ ] All four subsystems working (AR + vision + acoustic + IR)
- [ ] GenieX report generation live on-device
- [ ] Demo under 3 minutes, no technical failures
- [ ] Q&A handled confidently

---

**Read next:** `GITHUB_WORKFLOW.md` for parallel Git workflow setup
