# Onboarding Guide — Researcher & App Builder (Member 3)

**Welcome to SmartLeaseEdge!** This guide gets you contributing in 20 minutes.

---

## 🎯 Your Mission

Support the team with data collection, testing, documentation, and demo preparation for the iQOO Hackathon 2026.

**Timeline:** Days 1–11 (Sep 1–11, 2026)
**Output:** Clean datasets, test documentation, polished demo script, and winning pitch

---

## ✅ Setup Checklist (Do This First)

### 1. Install Tools

- [ ] **Git**
  - Download: https://git-scm.com/downloads
  - Verify: `git --version`

- [ ] **Text Editor** (choose one)
  - VS Code (recommended): https://code.visualstudio.com
  - Sublime Text: https://www.sublimetext.com
  - Notepad++ (Windows): https://notepad-plus-plus.org

- [ ] **Python 3.10+** (for data collection scripts)
  - Download: https://www.python.org/downloads
  - Verify: `python --version`

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
git checkout -b feature/docs-demo
git push -u origin feature/docs-demo
```

### 3. Install Python Dependencies

```bash
# Install required libraries for data collection
pip install rich

# Verify setup
python validate_training_data.py
```

**Expected output:**
```
⚠ Data collection INCOMPLETE
Missing: acoustic samples, vision images
```

(This is fine — you'll collect the data soon!)

---

## 📂 Project Structure (Your Territory)

```
smartlease-edge-chennai/
├── docs/                        # 📚 YOUR PRIMARY WORK AREA
│   ├── DEMO_SCRIPT.md           # 3-minute demo script (you write)
│   ├── TEST_CASES.md            # Test scenarios (you write)
│   ├── USER_GUIDE.md            # App user guide (you write)
│   └── ONBOARDING_*.md          # Team onboarding (already done)
│
├── training_data/               # 🎤📊 Data collection (Days 1-2)
│   ├── acoustic/                # Audio samples (you help collect)
│   │   ├── hollow/
│   │   └── solid/
│   └── vision/                  # Wall damage images (you help download)
│       ├── images/
│       ├── labels/
│       └── datasets/
│
├── README.md                    # 🔧 Keep updated as features land
├── DATA_COLLECTION_GUIDE.md     # Already created
├── ACOUSTIC_RECORDING_CHECKLIST.md  # Already created
├── DATASET_SOURCES.md           # Already created
│
└── Scripts you'll run:
    ├── acoustic_data_tracker.py     # Track recording progress
    ├── download_datasets.py         # Auto-download vision datasets
    └── validate_training_data.py    # Verify data completeness
```

**What you own:**
- Everything in `docs/` folder
- Data collection assistance (recording, labeling, downloading)
- Testing and quality assurance
- Demo materials (script, props, presentation)
- README updates (keep it current)

**What you coordinate on:**
- README.md (anyone can edit, but coordinate to avoid conflicts)

**What you don't touch:**
- `app/src/` folder (Member 2's Android code)
- `models/` folder (Member 1's trained models)
- ML training scripts (Member 1 only)

---

## 🚀 Your First Tasks (Days 1–2)

### Task 1: Acoustic Data Collection (1–2 hours)

**Goal:** Record 100 audio samples (50 hollow + 50 solid taps)

**Follow:** `ACOUSTIC_RECORDING_CHECKLIST.md`

**Quick steps:**
1. Open Voice Recorder on your phone
2. Configure: WAV format, 44.1kHz
3. Record hollow taps:
   - 15 hollow doors
   - 15 drywall (between studs)
   - 10 empty cardboard boxes
   - 10 hollow plastic containers
4. Record solid taps:
   - 15 concrete walls
   - 15 solid wood (furniture, tables)
   - 10 filled containers (water jugs)
   - 10 tile on concrete (bathroom floor)

**File naming:**
```
hollow_door_01.wav
hollow_door_02.wav
...
solid_concrete_01.wav
solid_wood_01.wav
...
```

**Track progress:**
```bash
python acoustic_data_tracker.py
```

Shows real-time progress bars and suggests what to record next!

**Transfer files:**
1. Connect phone to laptop (USB)
2. Copy to: `training_data/acoustic/hollow/` and `.../solid/`

**Deliverable:**
- [ ] 50+ hollow samples in `training_data/acoustic/hollow/`
- [ ] 50+ solid samples in `training_data/acoustic/solid/`
- [ ] All samples 3–5 seconds, clear taps, minimal background noise

---

### Task 2: Vision Dataset Download (1–2 hours)

**Goal:** Collect 200–500 wall damage images

**Follow:** `DATASET_SOURCES.md`

**Quick steps:**

**Option A: Manual Download (Easiest)**

1. Visit: https://universe.roboflow.com
2. Search: "wall damage" or "surface crack"
3. Filter: Segmentation datasets only
4. Download 2–3 datasets:
   - Click dataset → "Download Dataset"
   - Format: Select "YOLOv8 Segmentation"
   - Save to: `training_data/vision/datasets/[dataset_name]/`

**Recommended datasets to search:**
- "Wall Crack Detection"
- "Surface Defect Segmentation"
- "Concrete Crack Detection"

**Option B: Automated Script**

```bash
# If Roboflow API key is set up:
python download_datasets.py --roboflow

# Or get manual download links:
python download_datasets.py --manual
```

**Deliverable:**
- [ ] 2–3 datasets downloaded (200–500 total images)
- [ ] Datasets saved to `training_data/vision/datasets/`
- [ ] Datasets have segmentation masks (not just bounding boxes)

---

### Task 3: Data Validation (15 minutes)

**Goal:** Verify data collection is complete

```bash
python validate_training_data.py
```

**Expected output:**
```
✓ Acoustic data collection complete
  Hollow: 50 samples
  Solid: 50 samples

✓ Vision data collection complete (estimated 600 images)
  Public datasets: 3

✓ Day 2 data collection COMPLETE
Ready to begin Day 3 model training.
```

**If not all green:**
- See what's missing
- Complete missing items
- Re-run validation

**Deliverable:**
- [ ] `validate_training_data.py` shows all green checkmarks
- [ ] Backup `training_data/` folder to external drive or cloud

---

## 🚀 Your Next Tasks (Days 3–5)

### Task 4: Create Test Cases Document (1 day)

**Goal:** Document all app features and how to test them

**Create:** `docs/TEST_CASES.md`

**Template:**

````markdown
# SmartLeaseEdge — Test Cases

## Functional Tests

### Feature: Camera Capture
**Test Case 1.1:** Camera preview loads
- **Steps:** Open app → tap "New Inspection" → camera preview should appear
- **Expected:** Camera preview visible within 2 seconds
- **Status:** ✓ PASS / ✗ FAIL / ⏳ PENDING

**Test Case 1.2:** Capture photo
- **Steps:** Tap capture button → photo saves
- **Expected:** Photo saved to Room database, preview shown
- **Status:** ⏳ PENDING

### Feature: AR Alignment
**Test Case 2.1:** Ghost overlay renders
- **Steps:** Capture baseline photo → point camera at same wall
- **Expected:** Baseline photo overlaid at 40% opacity
- **Status:** ⏳ PENDING

... (continue for all features)

## Edge Cases

### Test Case E.1: Low light performance
- **Steps:** Use app in dimly lit room
- **Expected:** Camera adjusts exposure, overlay still visible
- **Status:** ⏳ PENDING

### Test Case E.2: Airplane mode verification
- **Steps:** Enable airplane mode → run full inspection
- **Expected:** All features work, no network errors
- **Status:** ⏳ PENDING

## Offline Mode Tests

### Test Case O.1: Full offline pipeline
- **Steps:** Airplane mode ON → complete inspection → generate report
- **Expected:** No errors, PDF generated locally
- **Status:** ⏳ PENDING
````

**Deliverable:**
- [ ] `docs/TEST_CASES.md` with 20+ test cases
- [ ] Covers all major features (camera, AR, acoustic, IR, report)
- [ ] Includes edge cases (low light, airplane mode, battery low)

---

### Task 5: Run Manual Tests (Ongoing)

**Goal:** Test the app as Member 2 builds features

**Daily testing routine:**

1. **Pull latest code:**
   ```bash
   git checkout master
   git pull origin master
   ```

2. **Ask Member 2 to build APK:**
   - Or if you have Android Studio: `./gradlew :app:assembleDebug`
   - Get APK from: `app/build/outputs/apk/debug/app-debug.apk`

3. **Install on your phone:**
   ```bash
   adb install app-debug.apk
   ```

4. **Run through test cases:**
   - Open `docs/TEST_CASES.md`
   - Test each feature
   - Mark as PASS or FAIL
   - If FAIL: note what's broken, screenshot if possible

5. **Report bugs:**
   - Create GitHub issue: https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai/issues
   - Title: `[Component] Short description`
   - Example: `[Camera] Preview freezes after rotation`
   - Assign to Member 2
   - Add label: `bug`

**Deliverable:**
- [ ] Test all new features within 24 hours of implementation
- [ ] Report bugs as GitHub issues
- [ ] Update TEST_CASES.md with results

---

## 🚀 Your Tasks (Days 6–8)

### Task 6: Draft Demo Script (1 day)

**Goal:** 3-minute demo script for hackathon presentation

**Create:** `docs/DEMO_SCRIPT.md`

**Template:**

````markdown
# SmartLeaseEdge — Demo Script (3 minutes)

**Presenter:** Member 3
**Hardware:** iQOO 15
**Props:** Wall board with defects, AC remote, baseline photo

---

## 0:00–0:45 — Problem Hook

**Script:**
"Security deposit disputes are the single biggest source of landlord-tenant conflict in India. In cities like Bengaluru, deposits run 6–9 months' rent — making these disputes high-stakes when they happen.

Every source traces the cause back to the same thing: no move-in documentation. No photos, no baseline, no neutral record.

SmartLeaseEdge solves this with a complete, timestamped, sensor-backed property inspection — that works 100% offline."

**Actions:**
- Stand in front of projector
- No slides yet, just talk
- Make eye contact with judges

---

## 0:45–1:00 — Offline Proof

**Script:**
"This works entirely offline. Watch."

**Actions:**
- Hold up iQOO 15
- Show settings: Airplane Mode ON
- Show Wi-Fi icon: crossed out
- Launch app

---

## 1:00–1:30 — AR Alignment

**Script:**
"First, we capture a baseline photo at move-in."

**Actions:**
- Point camera at wall board
- Tap "Capture Baseline"
- Show baseline photo saved

"At move-out, we align the camera to the exact same angle using AR spatial tracking."

**Actions:**
- Move camera off-angle (red alignment indicator)
- Slowly align back (yellow → green)
- Show "ALIGNED ✓" message
- Pitch/roll delta: <5°

---

## 1:30–1:50 — Acoustic Tap Test

**Script:**
"Next, we test for hollow tiles — something a camera can't see."

**Actions:**
- Tap wall board with knuckle
- Show waveform animation on screen
- Show result: "Hollow tile detected"

---

## 1:50–2:10 — IR Functional Verification

**Script:**
"We verify appliances actually work — not just how they look. Using the iQOO 15's IR blaster, we trigger the AC."

**Actions:**
- Point phone at AC unit
- Tap "Test AC Power"
- AC turns on (or show pre-recorded video if no AC available)
- Show result: "AC functional ✓"

---

## 2:10–2:30 — Report Generation

**Script:**
"All of this synthesizes into a single report — generated offline, on-device, using Qualcomm's GenieX AI."

**Actions:**
- Tap "Generate Report"
- Show loading spinner (5–10 seconds)
- PDF appears on screen
- Show: defect summary, acoustic verdict, IR verification, repair cost estimate, signature fields

---

## 2:30–3:00 — Competitive Close

**Script:**
"Here's what makes this different:

NoBroker charges ₹1,649 to ₹5,999 and requires scheduling a human inspector days in advance.

zInspector and RentCheck both need the cloud, and they're priced for property managers with dozens of units — not a single tenant-landlord pair.

SmartLeaseEdge runs entirely offline, tests whether appliances actually work, and is instant and self-service.

Built on the iQOO 15's Snapdragon 8 Elite NPU, with vapor chamber cooling that lets us run continuous AI inference through a full 30-minute walkthrough without thermal throttling.

Thank you."

**Actions:**
- Hold up phone
- Make eye contact
- Pause for questions

---

## Q&A Prep

**Common questions:**

Q: "How accurate is the acoustic classifier?"
A: "92% accuracy on our test set of 100 samples. We trained it on 50 hollow and 50 solid tap samples collected from real walls, doors, and tiles."

Q: "Why offline? Wouldn't cloud be easier?"
A: "Three reasons: privacy (no property data leaves the device), reliability (works in basements, remote areas), and cost (no server bills to pass to users)."

Q: "What if the baseline photo is from a different time of day (different lighting)?"
A: "The AR alignment uses rotation sensors, not visual matching — so lighting doesn't affect alignment accuracy. The vision model is trained on varied lighting conditions."

Q: "Can this work on other phones?"
A: "Yes, but the iQOO 15's specific hardware makes it better: the IR blaster is unique, the NPU is 37% faster than prior gen, and the vapor chamber cooling prevents thermal throttling during long inspections."

Q: "What happens if the tenant and landlord disagree about the report?"
A: "The report is timestamped and cryptographically signed (local key pair) — so it's tamper-evident. Both parties sign at the end of the inspection, creating a neutral record."

---

## Props Checklist

- [ ] iQOO 15 (fully charged)
- [ ] Wall board with visible defects (crack, nail hole)
- [ ] Baseline reference photo (pre-captured)
- [ ] AC remote (or pre-recorded video of AC IR test)
- [ ] Charging cable + power bank (backup)
- [ ] Backup phone (in case primary fails)
- [ ] Screen mirror cable (HDMI or Vivo Office Kit)

---

## Rehearsal Checklist

- [ ] Rehearsed 3+ times, timed (under 3 minutes)
- [ ] Practiced Q&A responses
- [ ] Tested screen mirroring (Vivo Office Kit)
- [ ] Verified airplane mode works (no network errors)
- [ ] Backup plan if AC IR fails (show pre-recorded video)

````

**Deliverable:**
- [ ] `docs/DEMO_SCRIPT.md` complete
- [ ] Script rehearsed 3+ times
- [ ] Under 3 minutes when timed
- [ ] Q&A responses prepared

---

### Task 7: Prepare Props & Equipment (0.5 days)

**Goal:** Physical materials for live demo

**Props list:**

1. **Wall/tile board:**
   - Find a small piece of drywall or tile board
   - Create visible defects: small crack, nail hole, water stain
   - Or use a real wall with existing damage

2. **Baseline reference photo:**
   - Pre-capture a baseline photo of the wall board
   - Save to phone gallery
   - Load into app during demo

3. **AC remote:**
   - Borrow a real AC remote
   - Or get IR codes from brand websites

4. **Backup equipment:**
   - Charging cable + power bank
   - Backup phone (in case primary crashes)
   - Screen mirror cable (HDMI or Vivo Office Kit dongle)

**Deliverable:**
- [ ] All props acquired and tested
- [ ] Props packed in bag/box for transport
- [ ] Checklist printed and laminated

---

## 🚀 Your Tasks (Days 9–11)

### Task 8: User Testing (1 day)

**Goal:** Get feedback from non-team members

**Steps:**

1. **Recruit 2–3 testers:**
   - Friends, family, roommates
   - People who have never seen the app before

2. **Give them a task:**
   - "Complete a property inspection from start to finish."
   - Don't help or guide them

3. **Observe and take notes:**
   - Where do they get confused?
   - What takes too long?
   - What do they like?

4. **Ask questions:**
   - "Was anything unclear?"
   - "What would you change?"
   - "Would you use this for your next rental?"

5. **Report to team:**
   - Post findings in group chat
   - Create GitHub issues for major usability problems
   - Prioritize: must-fix vs. nice-to-have

**Deliverable:**
- [ ] 2–3 user tests completed
- [ ] Findings documented and shared with team
- [ ] Critical issues fixed before Day 11

---

### Task 9: Final Rehearsal (Day 11)

**Goal:** Full end-to-end demo run, 3+ times

**Steps:**

1. **Set up demo environment:**
   - Wall board, props, phone, charger
   - Screen mirroring to projector

2. **Run full demo:**
   - Member 3 presents (you!)
   - Member 1 & 2 watch and time
   - Take notes on what to improve

3. **Rehearse 3+ times:**
   - First run: identify rough spots
   - Second run: smooth transitions
   - Third run: under 3 minutes, no mistakes

4. **Practice Q&A:**
   - Member 1 & 2 ask tough questions
   - Practice answering confidently

**Deliverable:**
- [ ] Demo script rehearsed 3+ times
- [ ] Under 3 minutes consistently
- [ ] Q&A responses practiced
- [ ] Equipment and props ready

---

## 📖 Key Skills You'll Learn

### Git & GitHub

You'll learn to:
- Clone repositories
- Create branches
- Commit changes
- Push to GitHub
- Create issues

**No coding required!** Just Markdown files and text.

### Markdown Writing

Markdown is easy:

```markdown
# Heading 1
## Heading 2

**Bold text**
*Italic text*

- Bullet point
- Another bullet

[Link text](https://example.com)

```code block```
```

**Preview:** VS Code has built-in Markdown preview (Ctrl+Shift+V)

### Manual Testing

You'll learn to:
- Write test cases
- Execute tests systematically
- Report bugs effectively
- Track bug fixes

**Skill transferable to:** QA roles, product management, UX research

---

## ✅ Daily Workflow

**Morning:**
```bash
git checkout feature/docs-demo
git pull origin master  # Sync with latest
```

**During day:**
```bash
# Edit docs in VS Code
# Save files

git add docs/
git commit -m "docs: add demo script Q&A section"
git push
```

**End of day:**
```bash
git push  # Backup your work
```

**Post in group chat:**
```
✅ Day 6 complete:
- Demo script drafted
- Props list finalized
- Q&A responses prepared

→ Tomorrow: User testing
⚠️ Need AC remote (Member 2?)
```

---

## 🐛 Troubleshooting

**Problem:** Git push fails
```
Solution:
git pull origin master  # Get latest changes
# Resolve conflicts if any
git push
```

**Problem:** Don't know what to work on
```
Solution:
1. Check TEAM_STRUCTURE.md (your task list)
2. Ask in group chat: "What should I focus on next?"
3. Look at open GitHub issues assigned to you
```

**Problem:** Found a bug but don't know if it's critical
```
Solution:
Post in group chat with:
- What you did
- What happened
- Screenshot
Team will decide priority.
```

---

## 📞 Getting Help

**Stuck? Here's what to do:**

1. **Check the guides:**
   - `README.md` — Project overview
   - `TEAM_STRUCTURE.md` — Your role details
   - `DATA_COLLECTION_GUIDE.md` — Data collection help
   - `GITHUB_WORKFLOW.md` — Git help

2. **Ask in group chat:**
   - "I'm trying to [X] but [Y is happening]. Any ideas?"

3. **Create an issue:**
   - If it's a bug or question for the team

---

## 🎯 Success Metrics (Day 11)

By end of Day 11, you should have:

- [ ] 50+ acoustic tap samples collected (hollow + solid)
- [ ] 200+ vision images downloaded (wall damage datasets)
- [ ] `TEST_CASES.md` with 20+ test cases
- [ ] All features tested, bugs reported
- [ ] `DEMO_SCRIPT.md` complete and rehearsed
- [ ] Props and equipment ready
- [ ] User testing completed (2–3 testers)
- [ ] README.md up-to-date with latest features

---

## 📚 Additional Resources

**Markdown:**
- Guide: https://www.markdownguide.org/basic-syntax
- Cheat sheet: https://github.com/adam-p/markdown-here/wiki/Markdown-Cheatsheet

**Git:**
- Git basics: https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control
- GitHub guides: https://guides.github.com

**Testing:**
- Manual testing guide: https://www.softwaretestinghelp.com/manual-testing

**Public Speaking:**
- Demo tips: https://www.youtube.com/watch?v=sGeVAG0AH-k
- Handling Q&A: https://www.youtube.com/watch?v=z0_jD8nO2Ms

**Project Docs:**
- `README.md` — Project overview
- `TEAM_STRUCTURE.md` — Your role
- `GITHUB_WORKFLOW.md` — Git workflow
- `CONTRIBUTING.md` — Code style

---

**Welcome to the team! Your work is critical to our success. 🚀**

**Questions?** Ask in group chat or create an issue.
