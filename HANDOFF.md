# HANDOFF.md

Live status. Updated as work proceeds, not at the end.

**Repo:** `smartlease-edge-chennai-master/` · **HEAD:** `082af75` · **Tag:** `demo-known-good`
**Last verified build:** `84e4144` — `assembleDebug` + `testDebugUnitTest` green, **41/41**.
**Fallback tag:** `demo-known-good` → `84e4144`.

---

## Phase / gate status

| Phase | Item | Status |
|---|---|---|
| **0** | 0.1 Toolchain pin + tag | ✅ `082af75` |
| **0** | 0.2 APK archived outside `build/` | ✅ `demo/app-debug-KNOWN-GOOD-082af75.apk` |
| **0** | 0.3 Device reality check | ⛔ **BLOCKED — no device attached** |
| **C2-A** | Tap capture + `RECORDING_PROTOCOL.md` | ✅ `19bdf24` — **handed to you, waiting on recordings** |
| **C1** | QR digest | ✅ `a38a65d` · 41/41 · ⚠️ not yet scanned on hardware |
| — | `DECK_CORRECTIONS.md` | ✅ delivered — unblocks the deck owner |
| **CAL** | Threshold calibration | ⚠️ **PART 1 of 2.** Recall curve done; **no threshold shipped** — needs your clean-surface photos |
| **2.5** | Device & Model Check screen | ✅ `84e4144` · 41/41 |
| **5** | `VENUE_RUNBOOK.md` | ✅ delivered — 44 min, printable |
| **C2-B** | Re-export + decision gate | ⏳ blocked on your recordings |
| **C2-C** | UI reorder, haptic, tone, abstain | ⏳ gated on C2-B |
| **2** | Persist photos | not started — first to cut |
| **C6** | Confidence / abstain surfacing | not started — folded into C2-C |

### Blocked on you, in priority order

1. **Attach the A56** (accept the USB-debugging re-prompt) — unblocks GATE 0.
2. **Record taps** — `RECORDING_PROTOCOL.md`, ~40 min, 24–32 spots. Unblocks C2-B → C2-C.
3. **Shoot 20–30 clean-surface photos** — unblocks the FP half of CAL and the threshold decision.
4. **Scan the QR once on hardware** — C1 is verified by encode→decode unit tests, not by a camera.

---

## 30-second demo scripts

### C1 — "verify it yourself" (the strongest 20 seconds in the demo)

**Taps:** Walkthrough → record any 2–3 findings → **Generate Report** → the report screen
shows a QR above the hex.

**What the judge does:** opens their *own* phone's camera, points it at your screen. No app,
no network. It reads back a 64-character hex string.

**Then the beat.** Go back, add one more finding — or change one — regenerate, and ask them
to scan again. **Different code.**

**Say over it:**
> "That's a SHA-256 over exactly the findings in this report. You're holding it on your
> phone now, and I can't change it without you seeing a different code. It proves the
> findings are unaltered — it does not prove who recorded them. That's the next build, and
> I'd rather tell you the difference than let 'signed' do the work."

**Rehearsal status: NOT YET DONE ON HARDWARE.** No device has been attached this session, so
this has been verified by encode→decode round-trip in `QrCodeTest` (5 tests) rather than by a
camera. Zxing's own reader decodes every digest we generate, which is strong evidence the
symbol is well-formed — but *do* scan it once on the A56 before relying on it in front of a
judge. The two failure modes a unit test cannot catch are screen brightness and the 200dp
render size being too small for a particular camera.

### C2-A — tap capture (yours, not a demo)

Home → **Tap capture (debug)**. Debug builds only; the button and the route do not exist in
release. See `RECORDING_PROTOCOL.md`.

---

## C2-B decision rule — PRE-COMMITTED 2026-09-12, before any recording existed

Written down before I could see a result, so neither of us rationalises a bad number later.
Baseline to beat: **majority baseline 0.533**. Current shipped interval: **55–93%**, whose
lower bound *is* the baseline.

The test is the **lower bound of the grouped-LOGO 95% interval**, not the point estimate.

| New CI lower bound | Branch | What happens |
|---|---|---|
| **> 0.60** | **GREEN** | C2-C proceeds in full. The tap test leads the walkthrough. THE SENTENCE in `NOVELTY.md` is sayable without a caveat. |
| **0.533 – 0.60** | **AMBER** | C2-C ships the UI work — haptic, tone, interval shown, INCONCLUSIVE band — but the tap test **stays where it is in the flow**. It does not lead. The answer to "how good is it" stays the current honest one. |
| **≤ 0.533** | **RED** | **Stop.** Report it. No demo reorder around a capability whose interval contains chance, and no second recording round tonight. |

Both intervals — old and new — go into `METRICS.md` side by side whichever branch fires.

**Branch fired: _(pending recordings)_**

### To verify while in C2-B

`NOVELTY.md` §5 asserts the app already returns INCONCLUSIVE when the interval includes
chance. That needs checking against `TrainedTapClassifier.kt:46-50`, which abstains on a
**per-tap probability margin** (`|p − 0.5| < 0.15`) — that is not the same thing as
abstaining because the *model's* interval includes chance. The honest answer is likely
"per-prediction abstention yes, model-level abstention no." If so it becomes part of C2-C.

---

## ⛔ What is blocking GATE 0

`adb devices` returns an empty list. The phone is not connected.

```
$ adb devices -l
List of devices attached
                          <- empty
```

Windows still holds driver entries from an earlier session, which confirms the A56 *was*
attached (consistent with the 03:14 install) but is not now:

```
Status   FriendlyName
Unknown  SAMSUNG Mobile USB Composite Device
Unknown  SAMSUNG Android ADB Interface
```

**A note on a result that looks like a finding and is not.** Running the capability probe
with no device attached returns:

```
$ adb shell pm list features | grep consumerir
  (no output)
```

That is an **empty device list**, not a hardware answer. It would be wrong to record
"consumerir absent" from it, and precisely the class of silently-wrong result this whole
audit exists to catch. The IR verdict below is therefore marked *expected, unverified*
until the probe actually runs against a device.

### To unblock

1. Connect the Galaxy A56 by USB.
2. Unlock it and accept the **"Allow USB debugging"** prompt (it will re-prompt — the
   adb server was restarted, which invalidates the previous RSA grant).
3. Confirm: `adb devices` shows a serial with state `device` (not `unauthorized`, not `offline`).

---

## Device capabilities — 0.3

**Status: NOT YET RECORDED.** Fields stay empty until the probe runs. No value here is
inferred from the spec sheet.

| Property | Value |
|---|---|
| `ro.product.model` | *(pending)* |
| `ro.product.manufacturer` | *(pending)* |
| `ro.board.platform` | *(pending)* |
| `ro.soc.manufacturer` | *(pending)* |
| `ro.soc.model` | *(pending)* |
| `ro.build.version.release` / `sdk` | *(pending)* |
| `ro.product.cpu.abilist` | *(pending)* |
| `android.hardware.consumerir` | *(pending — expected ABSENT on Galaxy A56)* |

### The IR consequence, stated now because it does not depend on the probe

The Samsung Galaxy A56 (Exynos 1580) **has no IR emitter**. The iQOO 15 does.

The IR appliance trigger is the **only genuinely iQOO-specific hardware claim in the entire
submission** — it is the whole of the answer to `JUDGE_QA.md` **Q4** *("what does the iQOO 15
give you that a ₹15,000 phone doesn't?")*.

It follows that:

- **The IR path has never executed on hardware that has an emitter.** On the A56,
  `IrController.hasIrBlaster` is false, `transmit()` returns `Failure("Device reports no IR
  emitter hardware")`, and the button reads "No IR blaster detected". That branch is
  exercised; the *success* branch is not.
- **The app must be installed and rehearsed on an IR-equipped handset before judging.**
  Per the addendum, the iQOO 15 is a venue loaner with a short, possibly cable-free window
  — so this is the single highest-risk unrehearsed path in the demo, and `VENUE_RUNBOOK.md`
  (Phase 5) has to front-load it.
- `ConsumerIrManager` is **transmit-only**. No code can be captured with the phone at the
  venue. Either a real brand codeset is bundled in advance (Phase 5.6) or the transmit stays
  a generic NEC header and the finding string must keep saying *"pattern not verified
  against this unit"*.

---

## Toolchain forensics — 0.1, resolved

An IDE sync had left `gradle/` modified and uncommitted. Filesystem timestamps settle which
set actually produced the working APK:

| Time | Event |
|---|---|
| 03:05:40 | `gradle-9.4.1-bin` downloaded |
| 03:10 | `.gradle/9.4.1/` created — **9.4.1 ran** |
| **03:14:03** | **`app-debug.apk` written, 97 MB — the working artifact** |
| 03:35:11 | `gradle-9.6.0-bin` downloaded |
| 03:38 | `.gradle/9.6.0/` created — **21 min after the APK** |

**Verdict: the committed toolchain built the APK.** The upgrade never rebuilt it — the APK's
mtime predates the 9.6.0 run. Reverted `gradle/` and rebuilt from the committed set: green,
36/36, byte-identical APK size.

| | Committed (restored, pinned) | IDE's uncommitted upgrade (discarded) |
|---|---|---|
| AGP | `9.2.1` | `9.4.0` |
| Kotlin | `2.1.0` | `2.2.10` |
| KSP | `2.1.0-1.0.29` | `2.3.6` |
| Gradle | `9.4.1` | `9.6.0` |

**On the KSP concern raised in the previous handoff:** the restored pairing
`Kotlin 2.1.0 / KSP 2.1.0-1.0.29` is the correct `<kotlin>-<ksp>` form. The IDE's bare
`2.3.6` against Kotlin 2.2.10 was *never exercised by a successful `assembleDebug` here*, so
it is unproven rather than proven-broken. Worth knowing: the Gradle cache does contain a
correctly-paired `2.2.10-2.0.2`, so the upgrade is probably achievable — but not the night
before judging, and not untested.

`.idea/` and `gradle/gradle-daemon-jvm.properties` are now gitignored. The latter pins a
foojay-downloaded JDK 25 toolchain, which would make a build at the venue depend on network
access.

---

## Build environment (neither is on `PATH` by default)

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:/c/Users/shj08/AppData/Local/Android/Sdk/platform-tools:$PATH"
```

JDK: OpenJDK 25.0.3 (Android Studio JBR) · SDK: `C:\Users\shj08\AppData\Local\Android\Sdk`

```bash
cd "C:/Users/shj08/Downloads/project-1/IQOO Hackathon/smartlease-edge-chennai-master"
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

---

## adb commands

**Reinstall the known-good APK** (survives `gradlew clean` — it lives outside `build/`):

```bash
adb install -r "C:/Users/shj08/Downloads/project-1/IQOO Hackathon/demo/app-debug-KNOWN-GOOD-082af75.apk"
```

SHA-256 `dde8b06d1e3805971ee137a7275039a260a341bcc3f771c1886d24687a116708`
(verify with `sha256sum -c demo/app-debug-KNOWN-GOOD-082af75.apk.sha256`)

**Install the current build:**

```bash
adb install -r "C:/Users/shj08/Downloads/project-1/IQOO Hackathon/smartlease-edge-chennai-master/app/build/outputs/apk/debug/app-debug.apk"
```

**Roll the whole tree back to the known-good commit:**

```bash
git -C smartlease-edge-chennai-master checkout demo-known-good
```

**Launch / logs:**

```bash
adb shell am start -n com.smartlease.edge/.MainActivity
adb logcat -s YoloSegSegmenter DefectSegmenterFactory TrainedTapClassifier AcousticModelBundle
```

---

## Claims made stale by code changes — for the deck owner

Nothing from this session yet. `DECK_CORRECTIONS.md` (scheduled after Phase 1) will carry
the full hardware-mapping table. Standing items from the previous session are already
reflected in the deck.

**Advance warning, so it is not a surprise:** Phase 1.1 removes the unit "sq ft" from the
app UI and the PDF, replacing it with "covers N% of frame". Any slide or spoken line that
says the app reports square footage will become wrong the moment that lands. It is already
false today — the number is mask coverage × a hardcoded 48″×36″ frame and does not change
with distance — so this makes the app match what the deck was already corrected to say.
