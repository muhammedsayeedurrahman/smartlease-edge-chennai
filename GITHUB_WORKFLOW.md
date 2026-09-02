# GitHub Workflow — Parallel Development

**Goal:** All 3 team members work simultaneously without conflicts

---

## 🌳 Branch Structure

```
master (main branch — always stable)
  ├── feature/ai-models (Member 1)
  ├── feature/android-ui (Member 2)
  └── feature/docs-demo (Member 3)
```

### Branch Naming Convention

```
feature/<description>     # New features
fix/<description>         # Bug fixes
docs/<description>        # Documentation only
test/<description>        # Testing improvements
```

**Examples:**
- `feature/acoustic-classifier`
- `feature/ar-overlay-ui`
- `docs/demo-script`
- `fix/camera-crash`

---

## 🚀 Initial Setup (Do This Once)

### 1. Clone the Repository

```bash
# Member 1 (you already have it)
cd C:\Users\HP\SmartLeaseEdge

# Member 2 & 3 (on their machines)
git clone https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai.git
cd smartlease-edge-chennai
```

### 2. Set Up Git Identity

```bash
# Each team member runs this on their machine
git config user.name "Your Name"
git config user.email "your.email@example.com"
```

### 3. Create Your Feature Branch

**Member 1 (AI/NPU Engineer):**
```bash
git checkout -b feature/ai-models
git push -u origin feature/ai-models
```

**Member 2 (Android Developer):**
```bash
git checkout -b feature/android-ui
git push -u origin feature/android-ui
```

**Member 3 (Research & Demo):**
```bash
git checkout -b feature/docs-demo
git push -u origin feature/docs-demo
```

---

## 📝 Daily Workflow

### Every Morning: Sync with Master

```bash
# 1. Make sure you're on your branch
git checkout feature/your-branch-name

# 2. Get latest changes from master
git fetch origin
git merge origin/master

# 3. If there are conflicts, resolve them (see Conflict Resolution below)
```

### During the Day: Make Commits

```bash
# 1. Check what files changed
git status

# 2. Add files you want to commit
git add path/to/file.kt
# OR add all changed files
git add .

# 3. Commit with a clear message
git commit -m "feat: add AR overlay ghost image rendering

- Implemented SensorManager rotation tracking
- Added pitch/roll delta visualization
- Ghost image opacity adjusts based on alignment accuracy

Related: #5"

# 4. Push to your branch
git push
```

### End of Day: Push Your Work

```bash
# Push all commits from today
git push origin feature/your-branch-name
```

---

## ✅ Commit Message Format

Use **Conventional Commits** format:

```
<type>: <short description>

<optional body>

<optional footer>
```

**Types:**
- `feat:` — New feature
- `fix:` — Bug fix
- `docs:` — Documentation only
- `test:` — Adding/updating tests
- `refactor:` — Code refactoring (no behavior change)
- `chore:` — Build, dependencies, config

**Examples:**

```bash
# Good commit messages
git commit -m "feat: add acoustic tap classifier integration"
git commit -m "fix: camera preview crash on rotation"
git commit -m "docs: update README with team structure"
git commit -m "test: add unit tests for IR controller"

# Bad commit messages (avoid these)
git commit -m "updates"
git commit -m "fix stuff"
git commit -m "WIP"
```

**Multi-line commit (detailed):**

```bash
git commit -m "$(cat <<'EOF'
feat: integrate YOLOv8-Seg vision model

- Loaded .pte model via ExecuTorch runtime
- Implemented Hexagon NPU delegate
- Added defect area calculation (square feet)
- Optimized INT8 quantization for 50ms inference

Performance:
- Inference: 48ms avg on Snapdragon 8 Elite
- Memory: 120MB peak
- Accuracy: 78% mAP on test set

Related: #12

Claude-Session: https://claude.ai/code/session_012Z8Rp7A8woU5MZxHKSy2Sb
EOF
)"
```

---

## 🔀 Merging Your Work Into Master

### Option A: Pull Request (Recommended)

**When to use:** Completed a major feature, want code review

**Steps:**

1. **Push your branch:**
   ```bash
   git push origin feature/your-branch-name
   ```

2. **Create Pull Request on GitHub:**
   - Go to: https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai
   - Click "Pull requests" → "New pull request"
   - Base: `master` ← Compare: `feature/your-branch-name`
   - Title: `feat: add acoustic classifier integration`
   - Description:
     ```markdown
     ## Summary
     - Trained acoustic classifier (92% accuracy)
     - Exported to TensorFlow Lite (.tflite)
     - Integrated into AcousticTapClassifier.kt
     
     ## Test Plan
     - [x] Model loads without crash
     - [x] Hollow tap detection works
     - [x] Solid tap detection works
     - [x] Inference under 100ms
     
     ## Screenshots
     (Attach if applicable)
     ```
   - Click "Create pull request"

3. **Request review from teammates:**
   - Assign reviewers: Member 2 and/or Member 3
   - Wait for approval (or address feedback)

4. **Merge when approved:**
   - Click "Merge pull request"
   - Delete branch after merge (optional)

### Option B: Direct Merge (Fast, but risky)

**When to use:** Small changes, docs only, urgent fix

**Steps:**

```bash
# 1. Switch to master
git checkout master

# 2. Pull latest changes
git pull origin master

# 3. Merge your branch
git merge feature/your-branch-name

# 4. Push to master
git push origin master

# 5. Go back to your branch
git checkout feature/your-branch-name
```

**⚠️ Warning:** Only do this if you're confident there are no conflicts.

---

## ⚔️ Conflict Resolution

**When do conflicts happen?**
- Two people edited the same file, same lines
- You try to merge master into your branch, or merge your branch into master

**How to resolve:**

### Example Conflict

```kotlin
<<<<<<< HEAD (your changes)
val threshold = 0.8f
=======
val threshold = 0.75f
>>>>>>> origin/master (their changes)
```

**Steps:**

1. **Open the file in your editor**
2. **Decide which version to keep (or combine both)**
   ```kotlin
   // Keep yours
   val threshold = 0.8f
   
   // OR keep theirs
   val threshold = 0.75f
   
   // OR combine
   val threshold = 0.8f // Updated from 0.75 after testing
   ```
3. **Remove conflict markers** (`<<<<<<<`, `=======`, `>>>>>>>`)
4. **Save the file**
5. **Stage and commit:**
   ```bash
   git add path/to/file.kt
   git commit -m "fix: resolve threshold conflict, keep higher value"
   git push
   ```

**If unsure:** Ask in group chat before resolving.

---

## 🛡️ Avoiding Conflicts (Best Practices)

### 1. File Ownership (See TEAM_STRUCTURE.md)

**Member 1 owns:** `training_data/`, `models/`, ML scripts
**Member 2 owns:** `app/src/main/java/.../ui/`, Android code
**Member 3 owns:** `docs/`, demo materials

→ Don't edit files you don't own without coordinating first.

### 2. Pull Before You Start Working

```bash
# Every morning, before writing code:
git checkout your-branch
git pull origin master
```

### 3. Push Frequently

```bash
# At least once per day, preferably more:
git push
```

### 4. Small, Focused Commits

**Good:**
```bash
git commit -m "feat: add tap sound recording UI"
git commit -m "feat: add file save functionality"
git commit -m "feat: add playback preview"
```

**Bad:**
```bash
git commit -m "feat: entire acoustic recording feature (500 lines changed)"
```

→ Smaller commits = easier to merge, easier to review.

---

## 📊 Track Progress with GitHub Issues

### Creating an Issue

1. Go to: https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai/issues
2. Click "New issue"
3. Title: `[Member 1] Train acoustic classifier`
4. Description:
   ```markdown
   **Goal:** Train a lightweight acoustic classifier for hollow vs. solid taps
   
   **Tasks:**
   - [ ] Prepare training data (50 hollow + 50 solid samples)
   - [ ] Train model (PyTorch or TFLite)
   - [ ] Achieve 90%+ accuracy on test set
   - [ ] Export to .tflite format
   - [ ] Benchmark inference speed (<100ms target)
   
   **Deadline:** Day 5 (Sep 5)
   **Assignee:** @Member1
   **Labels:** ai, high-priority
   ```
5. Assign to yourself
6. Add labels: `ai`, `high-priority`, `day-3-5`

### Linking Commits to Issues

```bash
git commit -m "feat: add acoustic model training script

Related: #12"
```

→ Typing `#12` in a commit message automatically links it to Issue #12.

### Closing Issues

```bash
git commit -m "feat: export acoustic classifier to TFLite

Closes #12"
```

→ When this commit is merged to master, Issue #12 auto-closes.

---

## 🚨 Emergency Scenarios

### Scenario 1: "I broke master!"

**Fix:**

```bash
# Option 1: Revert the bad commit
git checkout master
git log  # Find the bad commit hash (e.g., abc123)
git revert abc123
git push origin master

# Option 2: Reset to last good commit (DANGEROUS)
git checkout master
git reset --hard <last-good-commit-hash>
git push origin master --force  # ⚠️ Only if no one else has pulled!
```

**Prevention:** Always test before merging to master.

### Scenario 2: "I need to undo my last commit"

```bash
# If you haven't pushed yet:
git reset --soft HEAD~1  # Keeps changes, undoes commit
# OR
git reset --hard HEAD~1  # Deletes changes, undoes commit

# If you already pushed:
git revert HEAD  # Creates a new commit that undoes the last one
git push
```

### Scenario 3: "I accidentally committed to master instead of my branch"

```bash
# 1. Create a branch from current state
git branch feature/oops

# 2. Reset master to before your commits
git checkout master
git reset --hard origin/master

# 3. Switch to your branch and continue
git checkout feature/oops
```

---

## 📦 Release/Build Workflow

### Creating a Release (Day 11 - Demo-Ready Build)

**Member 2 (Android Developer) does this:**

1. **Build release APK:**
   ```bash
   ./gradlew :app:assembleRelease
   ```

2. **Tag the release:**
   ```bash
   git tag -a v1.0-demo -m "Demo build for iQOO Hackathon 2026"
   git push origin v1.0-demo
   ```

3. **Create GitHub Release:**
   - Go to: https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai/releases
   - Click "Create a new release"
   - Tag: `v1.0-demo`
   - Title: `SmartLeaseEdge Demo Build — Sep 11, 2026`
   - Description:
     ```markdown
     Demo-ready build for iQOO Hackathon 2026 Chennai City Battle.
     
     **Features:**
     - ✅ AR baseline alignment
     - ✅ Vision defect segmentation (YOLOv8-Seg)
     - ✅ Acoustic tap testing
     - ✅ IR-triggered AC verification
     - ✅ Offline PDF report generation
     
     **Hardware:** iQOO 15 (Snapdragon 8 Elite Gen 5)
     **Build:** app-release.apk (~60MB)
     ```
   - Upload: `app/build/outputs/apk/release/app-release.apk`
   - Click "Publish release"

---

## 📚 Quick Command Reference

```bash
# Setup (once)
git clone https://github.com/muhammedsayeedurrahman/smartlease-edge-chennai.git
git checkout -b feature/your-branch
git push -u origin feature/your-branch

# Daily workflow
git pull origin master        # Morning: sync with master
git add .                     # Stage changes
git commit -m "feat: ..."     # Commit with message
git push                      # Push to your branch

# Merging
git checkout master           # Switch to master
git pull origin master        # Get latest
git merge feature/your-branch # Merge your work
git push origin master        # Push to master

# Status checks
git status                    # What changed?
git log --oneline -5          # Recent commits
git branch -a                 # All branches
git diff                      # What's different?

# Undo/fix
git reset --soft HEAD~1       # Undo last commit, keep changes
git revert HEAD               # Undo last commit, create new commit
git stash                     # Save changes temporarily
git stash pop                 # Restore stashed changes
```

---

## 🎯 Team Workflow Summary

| Time | All Members Do |
|------|---------------|
| **Morning** | `git pull origin master` — Sync with latest |
| **During Day** | Work on your feature branch, commit often |
| **End of Day** | `git push` — Share your progress |
| **When Done** | Create Pull Request → Get review → Merge to master |

**Communication:** Post in group chat before merging to master, or when you need help with conflicts.

---

**Next:** See `CONTRIBUTING.md` for code style guidelines and review process
