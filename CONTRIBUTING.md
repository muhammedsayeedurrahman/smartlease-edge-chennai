# Contributing to SmartLeaseEdge

**Welcome to the team!** This guide helps you get started and maintain code quality.

---

## 🚀 Quick Start for New Team Members

### 1. Clone and Setup

```bash
# Clone the repository
git clone https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai.git
cd smartlease-edge-chennai

# Create your feature branch (see docs/guides/TEAM_STRUCTURE.md for your role)
git checkout -b feature/your-branch-name
git push -u origin feature/your-branch-name
```

### 2. Install Dependencies

**For Android Development (Member 2):**
```bash
# Open in Android Studio
# File → Open → C:\path\to\smartlease-edge-chennai

# Build to verify setup
./gradlew :app:assembleDebug

# Run on device/emulator
# Click green "Run" button in Android Studio
```

**For AI/ML Work (Member 1):**
```bash
# Install Python dependencies
pip install -r requirements.txt

# Verify setup
python tools/validate_training_data.py
```

**For Documentation (Member 3):**
```bash
# No special dependencies
# Any text editor works (VS Code, Sublime, Notepad++)
```

### 3. Read the Guides

- `docs/guides/TEAM_STRUCTURE.md` — Your role and responsibilities
- `docs/guides/GITHUB_WORKFLOW.md` — How to use Git for parallel work
- `README.md` — Project overview
- This file — Code standards

---

## 📝 Code Style Guidelines

### Kotlin (Android Code)

**Follow Android/Kotlin conventions:**

```kotlin
// ✅ Good - Clear naming, immutable by default
data class InspectionResult(
    val defectArea: Float,
    val acousticVerdict: Boolean,
    val irFunctional: Boolean
)

fun calculateDefectArea(segments: List<Segment>): Float {
    return segments
        .filter { it.isDefect }
        .sumOf { it.area }
        .toFloat()
}

// ❌ Bad - Mutable, unclear naming
var result = 0f
fun calc(s: List<Segment>): Float {
    for (seg in s) {
        if (seg.isDefect) {
            result += seg.area
        }
    }
    return result
}
```

**Key principles:**
- Use `val` (immutable) by default, `var` only when necessary
- Prefer `data class` for DTOs
- Use meaningful names (`defectArea` not `da`)
- Keep functions small (<50 lines)
- Extract magic numbers to constants

**Formatting:**
- 4 spaces indentation (not tabs)
- Max line length: 120 characters
- Opening brace on same line: `fun foo() {`

### Python (ML/Training Scripts)

**Follow PEP 8:**

```python
# ✅ Good - Type hints, clear structure
from pathlib import Path
from typing import List, Tuple

def load_audio_samples(
    data_dir: Path,
    sample_rate: int = 44100
) -> Tuple[List[np.ndarray], List[int]]:
    """Load acoustic tap samples from directory.
    
    Args:
        data_dir: Path to hollow/solid sample directories
        sample_rate: Target sample rate in Hz
        
    Returns:
        Tuple of (audio_arrays, labels)
    """
    samples = []
    labels = []
    
    for class_dir in data_dir.iterdir():
        label = 1 if class_dir.name == "hollow" else 0
        for audio_file in class_dir.glob("*.wav"):
            samples.append(load_wav(audio_file, sample_rate))
            labels.append(label)
    
    return samples, labels

# ❌ Bad - No types, unclear, no docstring
def load(d):
    s = []
    l = []
    for c in d.iterdir():
        for f in c.glob("*.wav"):
            s.append(load_wav(f))
            l.append(1 if c.name == "hollow" else 0)
    return s, l
```

**Key principles:**
- Always use type hints (`def func(x: int) -> str:`)
- Write docstrings for all functions
- Use `pathlib.Path` not string paths
- Prefer list comprehensions when readable
- Constants in UPPER_CASE

**Formatting:**
- Run `black` before committing: `black scripts/`
- Max line length: 88 characters (black default)

### Markdown (Documentation)

**Clear structure:**

```markdown
# Main Title (H1 - only one per file)

Brief intro paragraph.

## Section (H2)

Content here.

### Subsection (H3)

More content.

**Bold** for emphasis
*Italic* for terms
`code` for inline code
```

**Code blocks with language:**

````markdown
```python
def example():
    return "Always specify language"
```
````

---

## ✅ Before You Commit

### 1. Self-Review Checklist

- [ ] Code compiles/runs without errors
- [ ] No `TODO` or `FIXME` comments (create an issue instead)
- [ ] No hardcoded values (use constants or config)
- [ ] No console.log / println (use proper logging)
- [ ] No commented-out code (delete it, Git keeps history)
- [ ] File names follow convention (PascalCase for classes, kebab-case for docs)

### 2. Test Your Changes

**Android (Member 2):**
```bash
# Build succeeds
./gradlew :app:assembleDebug

# Run on device - verify your feature works
# Test in airplane mode (offline requirement!)
```

**Python (Member 1):**
```bash
# Script runs without errors
python scripts/train_acoustic_classifier.py

# If you added tests, run them
pytest tests/
```

**Documentation (Member 3):**
- Read through your doc — does it make sense?
- Check all links work
- Spell-check

### 3. Commit Message Format

Use Conventional Commits:

```
<type>: <short summary (50 chars max)>

<optional body - explain WHY, not WHAT>

<optional footer - issue refs>
```

**Types:**
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation only
- `test:` Tests
- `refactor:` Code restructure (no behavior change)
- `chore:` Build, dependencies, config

**Examples:**

```bash
# Simple feature
git commit -m "feat: add AR ghost overlay rendering"

# Bug fix with context
git commit -m "fix: prevent camera crash on device rotation

CameraController was not releasing preview when activity rotated.
Now properly releases and re-initializes camera session.

Fixes #23"

# Documentation
git commit -m "docs: add acoustic recording troubleshooting section"
```

---

## 🔍 Code Review Process

### When to Request Review

**Always request review for:**
- Merging to master
- Large features (>200 lines changed)
- Changes to shared files (database schema, core models)
- Anything you're unsure about

**Optional (but encouraged) for:**
- Small bug fixes
- Documentation updates
- Your own feature branch work

### How to Request Review

1. **Create Pull Request** (see docs/guides/GITHUB_WORKFLOW.md)
2. **Assign reviewers:**
   - At least 1 other team member
   - For critical code, assign 2
3. **Describe changes:**
   ```markdown
   ## What changed
   - Added AR alignment accuracy indicator
   - Pitch/roll delta displayed in real-time
   
   ## Why
   Users need visual feedback when aligning camera to baseline
   
   ## Test plan
   - [x] Tested on 3 different walls
   - [x] Works in bright and dim lighting
   - [x] No performance impact (<5ms overhead)
   
   ## Screenshots
   [Attach screenshot showing alignment indicator]
   ```

### How to Review Code

**When someone requests your review:**

1. **Pull their branch:**
   ```bash
   git fetch origin
   git checkout feature/their-branch
   ```

2. **Test it:**
   - Build and run the code
   - Try to break it (edge cases)
   - Check airplane mode works (offline requirement)

3. **Read the code:**
   - Is it readable?
   - Does it follow our style guide?
   - Are there obvious bugs?
   - Is it too complex?

4. **Leave feedback:**
   - **Approve** if looks good
   - **Request changes** if issues found
   - **Comment** for questions/suggestions

**Feedback examples:**

```markdown
# ✅ Constructive feedback
"Consider extracting this 50-line function into smaller helpers for readability."

"This looks good! One suggestion: move this magic number (0.85) to a constant."

"Minor: variable name `d` is unclear - maybe `defectArea`?"

# ❌ Unhelpful feedback
"This is bad."
"Rewrite this."
"I don't like this approach."
```

### Review Response Time

- **Urgent (blockers):** <2 hours
- **Normal PRs:** <24 hours
- **Documentation:** <48 hours

If you can't review soon, comment: "I'll review this tomorrow morning"

---

## 🚫 Things to Avoid

### Don't Commit:

- [ ] Secrets (API keys, passwords) → Use environment variables
- [ ] Large binaries (>10MB) → Use Git LFS or external storage
- [ ] Build artifacts (`*.apk`, `*.class`, `__pycache__`)
- [ ] Personal IDE settings (`.idea/workspace.xml`) → Keep only `.idea/runConfigurations`
- [ ] Temporary files (`*.tmp`, `*.swp`, `.DS_Store`)

→ Check `.gitignore` is configured correctly

### Don't Push:

- [ ] Code that doesn't compile
- [ ] Code that crashes on launch
- [ ] Incomplete features (use feature flags if needed)
- [ ] Commented-out code

→ Push "work in progress" to your branch, but not to master

### Don't Merge to Master:

- [ ] Without testing
- [ ] Without code review (unless docs-only or trivial)
- [ ] If CI/CD fails (when we set it up)
- [ ] During teammate's critical work period

→ Coordinate in group chat before merging

---

## 📂 Project Structure

```
smartlease-edge-chennai/
├── app/                          # Android app (Member 2)
│   ├── src/main/
│   │   ├── java/com/smartlease/edge/
│   │   │   ├── MainActivity.kt   # Entry point (launcher activity)
│   │   │   ├── ui/screens/       # HomeScreen, WalkthroughScreen, ReportScreen
│   │   │   ├── ui/theme/         # Colors, typography
│   │   │   ├── camera/           # Camera & AR alignment
│   │   │   ├── acoustic/         # Acoustic tap classifier
│   │   │   ├── vision/           # Vision defect segmenter
│   │   │   ├── ir/               # IR transmit controller
│   │   │   ├── ocr/              # Text recognition (ML Kit)
│   │   │   ├── report/           # PDF report generator
│   │   │   ├── safety/           # Safety gate
│   │   │   └── data/             # Room database
│   │   ├── java/com/iqoo/multimodal/   # PyTorch model bench (non-launcher)
│   │   └── assets/               # vision_best.ptl, acoustic_cnn.ptl
│   └── build.gradle.kts
├── ml/                           # Python training project (Member 1)
│   ├── acoustic/                 # Log-Mel/MFCC features, CNN+RF training
│   ├── vision/                   # YOLOv8-Seg training
│   ├── report/                   # PDF report templates
│   ├── export/                   # ExecuTorch/ONNX export
│   ├── data/audio/               # Real recorded taps (tracked, small)
│   ├── data/dataset/             # Roboflow dataset (NOT tracked -- see ml/README.md)
│   ├── app.py                    # Gradio demo UI
│   └── config.py                 # Hyperparameters & paths
├── tools/                        # Data-collection helper scripts
│   ├── acoustic_data_tracker.py
│   ├── download_datasets.py
│   └── validate_training_data.py
├── docs/
│   ├── guides/                   # Team, workflow & onboarding docs
│   └── pitch/                    # Hackathon deck + generator
├── training_data/                # Raw capture staging (not tracked)
├── gradle/libs.versions.toml     # Dependency version catalog
├── README.md                     # Project overview
└── CONTRIBUTING.md               # This file
```

---

## 🐛 Reporting Bugs

**Use GitHub Issues:**

1. Go to: https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai/issues
2. Click "New issue"
3. Title: `[Component] Short description` (e.g., `[Camera] Preview freezes on rotation`)
4. Description:
   ```markdown
   **What happened:**
   Camera preview froze when rotating device from portrait to landscape.
   
   **Expected behavior:**
   Preview should rotate smoothly.
   
   **Steps to reproduce:**
   1. Open app
   2. Start camera preview
   3. Rotate device
   
   **Device:**
   - Model: Samsung Galaxy S23
   - Android: 14
   - App version: Debug build from commit abc123
   
   **Logs/Screenshots:**
   [Attach if available]
   ```
5. Assign to person who owns that component (see docs/guides/TEAM_STRUCTURE.md)
6. Add label: `bug`, `high-priority` (if urgent)

---

## ❓ Getting Help

**Stuck? Here's how to get unstuck:**

1. **Check the docs:**
   - `README.md` — Project overview
   - `docs/guides/TEAM_STRUCTURE.md` — What you should be working on
   - `docs/guides/GITHUB_WORKFLOW.md` — Git help
   - `docs/guides/DATA_COLLECTION_GUIDE.md` — Data collection
   - `docs/guides/ACOUSTIC_RECORDING_CHECKLIST.md` — Recording help

2. **Search GitHub Issues:**
   - Maybe someone already solved this?

3. **Ask in group chat:**
   - Post your question
   - Include: what you're trying to do, what error you get, what you've tried

4. **Create an issue:**
   - If it's a bug or feature request

**When asking for help, include:**
- What you're trying to do
- What you expected to happen
- What actually happened
- Error messages (full text)
- Steps you've already tried

---

## 🎉 Recognition

**Great contributors:**
- Write clear code
- Help teammates when stuck
- Test their changes thoroughly
- Review PRs promptly
- Communicate blockers early
- Document as they go

**We'll track this for:**
- GitHub contribution graph (commits, reviews)
- Hackathon team evaluation
- Post-event recognition

---

## 📞 Contact

**Questions about:**
- **AI/ML models:** Ask Member 1 (AI Engineer)
- **Android code:** Ask Member 2 (Android Developer)
- **Demo/docs:** Ask Member 3 (Research & Demo)
- **Git/GitHub:** Check docs/guides/GITHUB_WORKFLOW.md or ask anyone
- **General:** Post in group chat

---

**Welcome aboard! Let's build something amazing. 🚀**
