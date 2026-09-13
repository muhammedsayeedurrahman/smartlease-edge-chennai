# Putting Gemma 4 on the phone

How to download a Gemma 4 build, push it to the demo handset, and find out whether this app can
actually run it. Companion to [GEMMA_SETUP.md](GEMMA_SETUP.md), which covers the Gemma 3 path
the app was built against.

Read [Runtime compatibility](#runtime-compatibility-read-this-first) before you spend an hour on
a download.

---

## Runtime compatibility (read this first)

**Gemma 4 now runs.** The app loads `.litertlm` weights through Google's **LiteRT-LM** runtime
(`com.google.ai.edge.litertlm:litertlm-android`, pinned in `gradle/libs.versions.toml`). Gemma 4
is published as the `.litertlm` container, and LiteRT-LM is the runtime that reads it.

> Older note, kept for history: the app used to load models only through MediaPipe `tasks-genai`
> `0.10.35`, which reads the `.task` container and **rejects** `.litertlm` at tokenizer-init with
> a SentencePiece parse error (`sentencepiece_processor.cc(264) ParseFromString`). That runtime
> is still in the build for `.task` models, but narration now goes through LiteRT-LM, so a Gemma 4
> `.litertlm` loads instead of falling back to templating.

### This was tested. It loads and narrates.

Clean-installed on the demo phone (iQOO 15, SM8850), with a `.litertlm` model pushed byte-exact,
the LiteRT-LM path loads on the **GPU backend** and narrates end to end:

```
LOCATE: found <model>.litertlm
GemmaNarrator: Loaded <model>.litertlm on GPU
LOAD:   usingModel=true
LOAD:   status=on-device Gemma - <model>.litertlm (N MB)
GENERATE: source=GEMMA
AUDIT:  Accepted
RESULT: MODEL NARRATED
```

Reproduce with `GemmaLoadDeviceTest`:

```bash
adb shell am instrument -w \
  -e class com.smartlease.edge.narration.GemmaLoadDeviceTest \
  com.smartlease.edge.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d | grep -E "GemmaLoadTest|GemmaNarrator"
```

### If you build the app yourself: two gotchas we hit

LiteRT-LM `0.17.0` is compiled against Kotlin **2.4.0** and needs `kotlin-stdlib:2.4.0` +
`kotlin-reflect:2.4.0` **at runtime** — the reflect artifact references
`kotlin.jvm.internal.KotlinGenericDeclaration`, a class that exists only in the 2.4.0 stdlib.
This project's Kotlin compiler is older, so:

1. `app/build.gradle.kts` **forces the Kotlin runtime libraries up to 2.4.0** (not down) and sets
   `-Xskip-metadata-version-check`. Forcing the stdlib *down* compiles fine but crashes at model
   load with `NoClassDefFoundError: kotlin.jvm.internal.KotlinGenericDeclaration`. This is already
   configured in the repo; do not undo it.
2. **Do a full uninstall + clean install when you change the runtime**, not an incremental
   install. An incremental install can leave stale dex on the device, so the app runs old code and
   crashes with the class-not-found error even though the fresh APK contains the class:
   ```bash
   adb uninstall com.smartlease.edge.test
   adb uninstall com.smartlease.edge
   ./gradlew :app:installDebug :app:installDebugAndroidTest
   ```
   Note that uninstalling **also deletes the model** from the app's external dir, so re-push it
   (Step 3) after a clean install.

Just installing the pre-built APK to *use* the app needs neither of these — they only matter when
you compile.

---

## Which build to take

All of these live on Hugging Face under `litert-community`, the official LiteRT org, and are
**ungated** — no licence click, no access request, no approval wait. The `google/gemma-3n-*`
repos, by contrast, are `gated: manual` and need a human at Google to approve you.

| Repo | File | Size | Notes |
|---|---|---|---|
| `litert-community/gemma-4-E2B-it-litert-lm` | `gemma-4-E2B-it.litertlm` | 2.59 GB | Smallest full build. |
| `litert-community/gemma-4-E4B-it-litert-lm` | `gemma-4-E4B-it.litertlm` | **3.66 GB** | **Start here.** Largest that fits the demo phone comfortably. |
| `litert-community/gemma-4-12B-it-litert-lm` | `gemma-4-12B-it.litertlm` | 6.88 GB | Will likely be OOM-killed — see below. |
| `litert-community/gemma-4-26B-A4B-it-litert-lm` | `gemma-4-26B-A4B-it-gpu.litertlm` | very large | Not a phone model. |

Variant suffixes:

- **no suffix** (`gemma-4-E4B-it.litertlm`) — the general build. Use this one.
- **`-gpu`** — GPU-delegate build.
- **`-web`** — WASM/browser build (e.g. `gemma-4-E4B-it-web.task`). Targets the web runtime, not
  Android — do not use it here. On device it is rejected with `Unable to open zip archive`.
- **`_qualcomm_sm8750`, `_Google_Tensor_G5`, `_intel_LNL`** — NPU builds compiled for one exact
  SoC. The demo phone is **SM8850**; an `sm8750` build is a *different* chip and will not load.

### Will it fit in RAM?

The demo phone (iQOO 15, vivo I2501, SM8850) has **15.6 GB total, ~8.4 GB available**.

Weights are memory-mapped, so the file size is roughly the floor and the KV cache sits on top.
3.66 GB against 8.4 GB available is comfortable. 6.88 GB against 8.4 GB is not — Android's low
memory killer will most likely take the process before the model finishes loading.

Check before downloading a large one:

```bash
adb shell cat /proc/meminfo | head -3
adb shell df -h /sdcard
```

---

## 1. Get the Hugging Face CLI

```bash
pip install -U huggingface_hub
```

The `litert-community` Gemma 4 repos are ungated, so **no token is required** for the download
below. If you need one for a gated repo, authenticate properly:

```bash
hf auth login
```

> Paste the token at that prompt only — never into a chat, a commit, a file, or a command line.
> Hugging Face scans public surfaces and revokes tokens it finds. If a token has been exposed,
> delete it at <https://huggingface.co/settings/tokens> and issue a new one. A revoked token is
> the *good* outcome; the bad one is a live token in someone else's hands.

## 2. Download

```bash
hf download litert-community/gemma-4-E4B-it-litert-lm \
  gemma-4-E4B-it.litertlm \
  --local-dir ~/gemma4
```

Verify you got real weights and not a Git LFS pointer stub — a pointer is a few hundred bytes
and fails in a way that looks like a runtime problem:

```bash
ls -l ~/gemma4/gemma-4-E4B-it.litertlm    # expect ~3.7 GB, not ~200 bytes
```

The first bytes of a real file are the ASCII marker `LITERTLM`; a stub is text starting with
`version https://git-lfs...`. If you only got a stub, delete it and re-run the download.

> **It downloads slowly on an unauthenticated connection** (HF rate-limits anonymous pulls, and
> a 3.66 GB file can take 30+ minutes). `hf download` resumes if interrupted — just re-run the
> same command and it picks up where it stopped. A HF token (`hf auth login`) makes it faster,
> but the `litert-community` repos need no token to access.

## 3. Push it to the phone

```bash
adb push ~/gemma4/gemma-4-E4B-it.litertlm \
  /sdcard/Android/data/com.smartlease.edge/files/llm/
```

That path is the app's private external directory. `adb push` can write it without root, and the
app reads it without a storage permission.

Three things that will bite you:

**Clear the directory first.** `GemmaModelLocator` picks the **largest** file it finds
(`maxByOrNull { it.length() }`). Leave two models there and the bigger one wins — so a Gemma 4
build that fails to load will silently mask a working Gemma 3, and the app falls back to
templating while you stare at a directory that visibly contains a model.

```bash
adb shell "rm -f /sdcard/Android/data/com.smartlease.edge/files/llm/*.task \
                 /sdcard/Android/data/com.smartlease.edge/files/llm/*.litertlm"
```

**Create the directory if the app has never run.**

```bash
adb shell mkdir -p /sdcard/Android/data/com.smartlease.edge/files/llm
```

**Verify the transfer was byte-exact.** A truncated multi-gigabyte push presents as a mysterious
load failure:

```bash
stat -c %s ~/gemma4/gemma-4-E4B-it.litertlm
adb shell stat -c %s /sdcard/Android/data/com.smartlease.edge/files/llm/gemma-4-E4B-it.litertlm
```

Those two numbers must match. At ~28 MB/s over USB, 3.66 GB takes about two to three minutes.

`/data/local/tmp` is **not** searched, deliberately. It is world-readable scratch space, and
treating it as a model source would mean any process on the device could swap the weights that
write sentences onto a legal document.

## 4. Confirm what the app actually did

Open the app, tap **ⓘ** on the home screen to reach **Device & Model Check**, and read the
`MODELS` section. It prints exactly one of three lines, and they mean different things:

| Line | Meaning | What to do |
|---|---|---|
| `on-device Gemma - <file> (N MB)` | Loaded. Narration is running on the model. | Nothing. Generate a report. |
| `rule-based templating - no model file in ...` | The push did not land where the app looks. | Re-check the destination path in Step 3. |
| `rule-based templating - <file> (N MB) failed to load` | Found, but the runtime refused it. | Truncated push, a `-web`/wrong-SoC build, or (if you built the app) the stale-dex issue in the Runtime section. |

"Not set up" and "set up and broken" need different things from you, which is why the app
distinguishes them rather than printing one vague failure.

For the real cause of a load failure:

```bash
adb logcat -c    # clear, then reproduce by generating a report
adb logcat -d | grep -iE "litert|GemmaNarrator|NarratorFactory|KotlinGenericDeclaration"
```

A successful load logs `GemmaNarrator: Loaded <file> on GPU` (or `on CPU`). `KotlinGenericDeclaration`
in the trace means the stale-dex / Kotlin-version issue — see the Runtime section above.

## 5. Measure what it costs

Drive a real report render rather than a synthetic prompt — `ReportRenderDeviceTest` runs the
actual pipeline end to end:

```bash
adb shell am instrument -w \
  -e class com.smartlease.edge.report.ReportRenderDeviceTest \
  com.smartlease.edge.test/androidx.test.runner.AndroidJUnitRunner
```

> Use `am instrument`, **not** `./gradlew connectedDebugAndroidTest`. The Gradle task uninstalls
> both APKs when it finishes, which wipes app data — including the model you just spent three
> minutes pushing.

Capture alongside it:

```bash
adb shell dumpsys meminfo com.smartlease.edge     # peak RSS
adb shell top -n 1 | grep smartlease              # CPU share
adb shell dumpsys thermalservice                  # throttling, before and after
```

Record per model: load time, first-token latency, tokens/sec, peak RSS, which delegate actually
ran (from logcat, not from assumption), and whether `NarrationAudit` accepted or rejected the
output.

---

## What the model is and is not allowed to do

Unchanged from the Gemma 3 path, and it applies to any model you put here. The narrative is the
only part of the report that is *written* rather than *computed* — the digest, the measured
areas, the confidences and the rupee arithmetic are all derived from data. So the model's output
is checked against the findings before it is printed (`NarrationAudit`), and rejected text falls
back to the template silently. It is rejected for:

- describing a hazard no finding recorded, **or** omitting one that a finding did record;
- stating a number of findings that is not the real number;
- quoting any rupee amount — pricing belongs to the deduction engine, which prints its own
  balance sheet on the same page;
- being empty, or running away past a paragraph.

Decoding is greedy (`maxTopK = 1`), so regenerating a report over the same findings produces the
same paragraph.

## Honest limits

- **A bigger model is not a better report.** The narrative summarises a list the reader can see
  directly above it. Going from 1B to E4B buys fluency, not insight, and the audit constrains
  both equally.
- **Latency is real and scales with size.** The model is loaded per report and closed afterwards,
  because holding it pins gigabytes between inspections. A 3.66 GB model costs seconds of load
  time on every report, not once per session.
- **Do not claim narration that did not happen.** If no model loaded, the PDF says so in print
  under the paragraph. Check the Self-test `MODELS` row before telling anyone the app narrates
  with Gemma 4.
