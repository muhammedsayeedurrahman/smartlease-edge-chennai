# FIXED.md — Budget 1 execution log

**Date:** 2026-09-12 · **Judging:** 2026-09-13 · **Repo:** `smartlease-edge-chennai-master/`
**Baseline commit:** `281e35b` (repo exactly as received) · **Head:** `3a4af66`
**Net:** 26 files changed, 864 insertions, 738 deletions, 15 commits.

---

## ⚠️ Read this first: nothing here has been compiled

There is **no JDK, no Android SDK and no `~/.gradle`** on this machine. `./gradlew` cannot
run, so the hard rule "build and run the unit tests after each commit" could not be
satisfied for any item. You chose "write all of Tier A+B, you compile."

**What I substituted, per item class:**

| Check | Applies to | What it catches | What it misses |
|---|---|---|---|
| XML parse (`xml.dom.minidom`) | manifest, backup rules, `file_paths.xml` | malformed XML | wrong attribute semantics |
| Brace/paren parity | every touched `.kt` | truncated edits, unbalanced blocks | type errors, unresolved symbols |
| Import-presence grep | new API use | missing imports | signature mismatches |
| **Algorithm port to Python** | `FindingsDigest` | **real logic bugs — it caught one** | Kotlin syntax |
| `python-pptx` reopen | the deck | corrupted archive | layout overflow |
| `ultralytics val` | `data.yaml` | a genuinely broken dataset path | — |

**Every commit is independently revertible and no commit depends on a later one.**
To drop any single item: `git revert <sha>`.

**Highest compile risk, in order** — check these first when you build:
1. `A9` `78fc6e7` — `LaunchedEffect` + `withContext` reshuffle in `WalkthroughScreen.kt`
2. `C2` `66821e6` — `logFinding` became `suspend`; `WalkthroughScreen`'s callback signature changed to `(InspectionReport) -> Unit`
3. `A7` `f5bd2e0` — new `FindingsDigest.kt`, new fields on `InspectionReport`
4. `A8` `ad2840e` — `FileProvider` + `ACTION_SEND`

---

## 🚨 Not my change — an IDE upgraded your toolchain mid-session

Between my baseline commit and now, something (an Android Studio-family IDE — `.idea/`
appeared) rewrote your build config. **I did not touch `gradle/` and I have left these
uncommitted**, but you need to decide about them before you build:

```
gradle/libs.versions.toml
-  agp    = "9.2.1"              +  agp    = "9.4.0"
-  kotlin = "2.1.0"              +  kotlin = "2.2.10"
-  ksp    = "2.1.0-1.0.29"       +  ksp    = "2.3.6"

gradle/wrapper/gradle-wrapper.properties
-  gradle-9.4.1-bin.zip          +  gradle-9.6.0-bin.zip

new, untracked: gradle/gradle-daemon-jvm.properties   (pins toolchainVersion=25)
new, untracked: .idea/
```

**The line to look at is `ksp = "2.3.6"`.** KSP versions are conventionally pinned to a
Kotlin version — the old value `2.1.0-1.0.29` is `<kotlin>-<ksp>`. A bare `2.3.6` alongside
Kotlin `2.2.10` is exactly the shape of mismatch that fails the Room annotation processor at
configuration time. Room is a hard dependency here (`AppDatabase`, `InspectionDao`), so if
KSP fails, nothing builds — and it will look like one of my commits did it.

**Recommended:** restore the toolchain you had, build, and only then consider upgrading.

```
git -C smartlease-edge-chennai-master checkout -- gradle/
```

If you keep the upgrade and the build fails on KSP, that is the first thing to revert — and
it is independent of every commit below.

---

## Status table

| ID | Item | Status | Commit | Verify with |
|---|---|---|---|---|
| **P0-1** | `allowBackup` | **fixed** | `ce9989f` | `grep allowBackup app/src/main/AndroidManifest.xml` → `"false"` |
| **P0-2** | Home-screen placeholder card | **fixed** | `b44f14f` | `grep -ri placeholder app/src/main/java/com/smartlease/edge/ui/` → 0 hits |
| **P0-3** | Deck `[Team name]` placeholders | **partial** | `da552fd` | see ⚠️ below — **you must fill 6 fields** |
| **P0-4** | Slide 12 false ✓ | **fixed** | `da552fd`, `698d11c` | open slide 12 — GenieX is `NEXT:`, SHA-256 is `✓` |
| **P0-5** | Capture crash on denied camera | **fixed** | `0dc402c` | fresh install → Deny camera → button reads "Camera permission needed", disabled |
| **P0-6** | "placeholder pattern" in the PDF | **fixed** | `b44f14f` | tap Trigger AC, read the findings log line |
| **P0-7** | "ran on the Hexagon NPU" | **fixed** | `da552fd` | `grep -n "Hexagon NPU" docs/pitch/PITCH_DECK_PROMPT.md` → only the corrected line |
| **P0-8** | PDF cannot be shared | **fixed** | `ad2840e` | generate → "Share report (PDF)" → sheet opens |
| **P0-9** | Not tamper-evident | **fixed** | `f5bd2e0` | `grep -rl MessageDigest app/src/main` → `report/FindingsDigest.kt`; digest prints on every PDF page |
| **P0-10** | Main-thread freeze | **fixed** | `78fc6e7` | `grep -c "withContext(Dispatchers" .../WalkthroughScreen.kt` → 5 |
| **P0-11** | "View Past Reports" dead button | **fixed** (deleted) | `182b956` | `grep -r "View Past Reports" app/src/` → 0 hits |
| **P0-12** | No photo/audio evidence retained | **skipped** | — | out of scope (Budget 2, 4 h) |
| **P0-13** | Move-in ↔ move-out not addressed | **skipped (reframed)** | `3a4af66` | `README.md` + `problem.md` now name the gap explicitly |
| **P1-1** | Hardcoded 48″×36″ frame | **partial** | `da552fd`, `3a4af66` | docs/deck now say "share of the frame"; **the app still prints "sq ft"** |
| **P1-2** | `vision_best.ptl` wrong model, dead weight | **fixed** | `546386c` | `ls app/src/main/assets` → 2 files, 14 MB (was 4 files, 26.4 MB) |
| **P1-3** | Zero hard negatives | **skipped (documented)** | `3a4af66` | `METRICS.md` — ultralytics prints `0 backgrounds` itself |
| **P1-4** | Unused permissions | **fixed** | `a2ad9ae` | `grep -c uses-permission app/src/main/AndroidManifest.xml` → 2 |
| **P1-5** | No release signing config | **skipped** | — | out of scope; debug APK only |
| **P1-6** | `data.yaml` absolute `D:\` path | **fixed** | — (outside repo) | `cd YOLOV8 && yolo segment val model=best.pt data=unified_defects/data.yaml split=test` runs |
| **P1-7** | No vision metric exists | **fixed** | — (outside repo) | `cat METRICS.md`; raw run in `YOLOV8/runs_audit/` |
| **P1-8** | Acoustic n=15 | **partial** | `da552fd` | slide 7 now says "15 taps from 8 recordings". **Model not retrained** |
| **P1-9** | `com.iqoo.multimodal` unreachable | **fixed** | `546386c` | `find app/src -name "*.kt" -path "*iqoo*"` → 0 |
| **P1-10** | PyTorch Mobile EOL | **skipped (answer ready)** | — | `JUDGE_QA.md` Q1 |
| **P1-11** | `detailJson = "{}"` | **skipped** | — | out of scope (Budget 2) |
| **P1-12** | `SafetyGate` untested | **fixed** | `9e5c814` | `app/src/test/java/com/smartlease/edge/safety/SafetyGateTest.kt` — 8 tests |
| **P1-13** | Report/insert race | **fixed** | `66821e6` | `grep -n "suspend fun logFinding" .../WalkthroughScreen.kt` |
| **P1-14** | `NOTABLE` unreachable | **fixed** | `9e5c814` | `grep -n "Severity.NOTABLE" .../WalkthroughScreen.kt` |
| **P1-15** | 32-bit session id | **fixed** | `f5bd2e0` | `grep -n "UUID.randomUUID" .../WalkthroughScreen.kt` → no `.take(8)` |
| **P1-16** | README frozen at "Day 2 of 12" | **fixed** | `3a4af66` | `grep -c "Day 2 of 12" README.md` → 0 |
| **P2-1** | CC BY 4.0 attribution missing | **fixed** | `da552fd`, `3a4af66` | slide 17 + README credits |
| **P2-2** | `best.pt` not in the repo | **skipped** | — | out of scope (touch list) |
| **P2-4** | `-wal`/`-shm` not excluded | **fixed** | `ce9989f` | moot — every domain now excluded |
| **P2-5** | Empty proguard / `exportSchema` | **skipped** | — | deliberate hackathon calls |
| **P2-6** | Calibration ≠ inference preprocessing | **skipped** | — | out of scope (`ml/`), and the `.pte` isn't loaded |

**Totals: 20 fixed · 4 partial · 10 skipped.**

## Test count

| | Before | After |
|---|---:|---:|
| Test files | 2 | 4 |
| Test methods | 15 | **36** |

New: `FindingsDigestTest` (13), `SafetyGateTest` (8).

---

## ⚠️ Six fields you must fill by hand

I did not invent names. These are still literal placeholders in the shipping deck:

| Slide | Shape | Current |
|---|---|---|
| 1 | `sh=14` | `TEAM [Team name] · [Member 1] · [Member 2] · [Member 3]` |
| 1 | `sh=15` | `[Student / Professional bucket]` |
| 14 | `sh=10` / `16` / `22` | `[Member 1]` / `[Member 2]` / `[Member 3]` |
| 15 | `sh=20` | `[email]` |

Also: **slides 1 and 4 product images are mockups.** I destaled the captions ("Target UI —
representative mockup, not a screenshot"). Replace the images with real screenshots once the
app builds — A9 makes it presentable enough to screenshot.

---

## Still open, ranked — and what to say

**1. The vision model is weak, and now you have the number.**
Mask mAP50 **0.287**; `stain_mould` **0.065**; zero hard negatives; splits leak video frames.
> *"Mask mAP50 is 0.287 on our test split, and I'd discount even that — our splits share video frames across train and test. Spalling works at 0.59, crack is weak at 0.25, and stain_mould is broken at 0.07 because we merged corrosion, damp and efflorescence into one class that isn't one thing. And there's not a single background image in the dataset, so none of this measures false positives on a clean wall — which is the number that actually matters here."*

**2. No move-in ↔ move-out comparison exists.**
> *"What we built is a single-session condition record — 60 seconds, timestamped, offline, multi-modal — which solves the thing every one of these disputes starts from: neither party has an objective record of day one. The diff, the baseline handoff and the wear-and-tear policy are V2, and the hard part there is the trust model, not the code."*

**3. Nothing runs on the NPU.**
> *"All three run on the CPU. The ExecuTorch INT8 lowering is done and in the repo — `qnn_android_bundle/yolov8n_seg_htp.pte` — what we didn't finish is wiring the HTP delegate into the APK. And I'll add: it was lowered for SM8650, which is 8 Gen 3, not the 8 Elite, and the bundle is missing the Skel library our own script says it needs. So it couldn't have run even if we'd wired it."*

**4. The report is hashed, not signed.**
> *"Every page carries a SHA-256 over exactly these findings — change one character and it stops matching. It is not a signature: nothing identifies who recorded them, and the timestamp is the device clock. Two signature pads below the digest is the next step, and the ordering matters — a signature above unhashed text binds nothing."*

**5. "Sq ft" is still printed in the app.**
The docs and deck now say "share of the frame"; the app UI and PDF still say sq ft. **This is the one place code and docs still disagree.**
> *"That number is mask coverage times an assumed 4×3 ft frame — it doesn't change if I step back, because there's no depth or focal length on device. Read it as a percentage of frame. Relabelling the UI is a 30-minute change we didn't get to."*

**6. Acoustic model is still 15 taps from 8 WhatsApp recordings.**
The deck now says so. Recording 40–60 real taps and re-running `export_for_android.py` is ~2 h and turns your best engineering into your best number.

**7. No photo or audio clip is retained.** Still the biggest product gap (P0-12, 4 h).
> *"We don't keep photos yet. The report is findings plus a digest over them. Photos hashed into the same digest is the next build, and it's what turns this from an assertion into a document."*

**8. Release APK is unsigned; PyTorch Mobile 1.13.0 is EOL; `detailJson` is still `"{}"`.**
Low stakes. Answers in `JUDGE_QA.md`.

---

## What changed that you should re-read before pitching

- `README.md` — Current Status, hardware map, 12-day timeline (now "planned vs what actually happened"), demo script
- `problem.md` — now separates the original objective from what was built, and names the move-in/move-out gap
- `solution1.md`, `summary.md` — NPU, Sensing Hub, speakers, "Encrypted", signed PDF, dual entry points
- `METRICS.md` — **new**, the first recorded vision evaluation
- The deck — 74+11+8+8 paragraph replacements across 16 of 17 slides
