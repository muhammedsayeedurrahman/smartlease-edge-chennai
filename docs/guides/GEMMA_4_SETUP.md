# Putting Gemma 4 on the Phone

How to download a Gemma 4 build, push it to the demo handset, and find out whether this app can actually run it. Companion guide for edge LLM evaluation on the iQOO 15.

---

## Runtime Compatibility (Read This First)

This app's LLM pipeline targets MediaPipe `tasks-genai` (pinned at `0.10.35`). That runtime reads the `.task` container format.

Gemma 4 is published almost exclusively as `.litertlm` — the LiteRT-LM container. The two are not interchangeable:
* `0.10.35` is the latest `tasks-genai` on Google's Maven. There is no version bump that adds Gemma 4 support out of the box.
* LiteRT-LM is not published as a drop-in Android Maven artifact the way `tasks-genai` is, so "just swapping the runtime" is a dependency-architecture change, not a one-line fix.
* If a `.litertlm` file is pushed, the file locator will detect it, but the underlying MediaPipe runtime will reject it.
* **The honest expectation:** Pushing Gemma 4 to the phone is an experiment worth running, but **"failed to load"** is the expected result, and it is an architectural container incompatibility, not an ADB push error.

---

## Which Build to Take

All of these live on Hugging Face under `litert-community`, the official LiteRT org, and are ungated (no license click, no approval wait).

| Repository | File | Size | Notes |
| :--- | :--- | :--- | :--- |
| `litert-community/gemma-4-E2B-it-litert-lm` | `gemma-4-E2B-it.litertlm` | 2.59 GB | Smallest full build. |
| `litert-community/gemma-4-E4B-it-litert-lm` | `gemma-4-E4B-it.litertlm` | 3.66 GB | **Start here.** Largest that fits the demo phone comfortably. |
| `litert-community/gemma-4-12B-it-litert-lm` | `gemma-4-12B-it.litertlm` | 6.88 GB | Will likely be OOM-killed (see memory budget below). |
| `litert-community/gemma-4-26B-A4B-it-litert-lm` | `gemma-4-26B-A4B-it-gpu.litertlm` | Very large | Not a phone model. |

### Variant Suffixes
* **No suffix (`gemma-4-E4B-it.litertlm`)**: General build. Use this one.
* **`-gpu`**: GPU-delegate build.
* **`-web`**: WASM/browser build (`gemma-4-E4B-it-web.task` targets web runtimes, not Android).
* **SoC-specific builds (`_qualcomm_sm8750`, `_Google_Tensor_G5`, etc.)**: Compiled for specific chips. The iQOO 15 uses SM8850 (Snapdragon 8 Elite); an `sm8750` build will fail.

---

## RAM & Memory Budget (iQOO 15 / vivo I2501)

* Total RAM: 12 GB / 16 GB (~5 GB to 8.4 GB available depending on running background apps).
* Weights are memory-mapped (`mmap`), so file size is roughly the floor, and KV cache sits on top.
* **3.66 GB** against available RAM is feasible.
* **6.88 GB+** against available RAM will trigger Android's Low Memory Killer (`lmkd`) and kill the process before loading finishes.

Check available memory before running:
```powershell
& "D:\Android\Sdk\platform-tools\adb.exe" shell "cat /proc/meminfo | head -n 3; df -h /sdcard"
```

---

## Step-by-Step Execution Guide

### 1. Install Hugging Face CLI
```powershell
pip install -U huggingface_hub
```

### 2. Download Gemma 4 (3.66 GB)
```powershell
hf download litert-community/gemma-4-E4B-it-litert-lm gemma-4-E4B-it.litertlm --local-dir "$HOME/gemma4"
```
Verify the downloaded file size is ~3.7 GB and not a Git LFS pointer stub:
```powershell
Get-Item "$HOME/gemma4/gemma-4-E4B-it.litertlm" | Select-Object Length
```

### 3. Push to Device via ADB
Create the target directory and clean old models:
```powershell
& "D:\Android\Sdk\platform-tools\adb.exe" shell "mkdir -p /sdcard/Android/data/com.smartlease.edge/files/llm"
& "D:\Android\Sdk\platform-tools\adb.exe" shell "rm -f /sdcard/Android/data/com.smartlease.edge/files/llm/*.task /sdcard/Android/data/com.smartlease.edge/files/llm/*.litertlm"
```

Push the model:
```powershell
& "D:\Android\Sdk\platform-tools\adb.exe" push "$HOME/gemma4/gemma-4-E4B-it.litertlm" /sdcard/Android/data/com.smartlease.edge/files/llm/
```

Verify byte count:
```powershell
& "D:\Android\Sdk\platform-tools\adb.exe" shell "stat -c %s /sdcard/Android/data/com.smartlease.edge/files/llm/gemma-4-E4B-it.litertlm"
```

### 4. Verification & Diagnostic Logcat
Check how the runtime responds:
```powershell
& "D:\Android\Sdk\platform-tools\adb.exe" logcat -c
& "D:\Android\Sdk\platform-tools\adb.exe" logcat -d | Select-String -Pattern "llm_inference|mediapipe|litert|GemmaNarrator|NarratorFactory"
```

---

## What the Model Is and Is Not Allowed to Do

1. **Strict Guardrails (`NarrationAudit`)**:
   * An LLM must **never** compute or quote rupee amounts. Pricing belongs strictly to the deterministic deduction engine.
   * An LLM must **never** hallucinate defects not recorded by the physical sensors/YOLO model.
   * Output must be bounded to a single concise summary paragraph.
2. **Greedy Decoding (`maxTopK = 1`)**:
   * Ensures deterministic narration: regenerating a report over the same findings produces identical text.
3. **Honesty & Transparency**:
   * If no LLM loads, the app transparently prints rule-based templated narration and states so on the PDF.
