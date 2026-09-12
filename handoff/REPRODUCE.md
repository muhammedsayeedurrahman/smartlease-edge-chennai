# REPRODUCE.md

Clean machine to shipped `.ptl`. Every number in `METRICS_MODELS.md` comes out of these
commands. Windows, `uv`, one command per stage.

**Total: ~75 min, most of it the two trainings.**

---

## 0 · Environments — 15 min

Two venvs, because they cannot coexist: training needs CUDA 12.8 wheels on Python 3.12, and
the export check needs torch 1.13.1, which has **no cp312 wheels at all**.

```bash
cd "IQOO Hackathon/handoff/env"

# training: CUDA 12.8 for Blackwell sm_120
uv venv train --python 3.12
uv pip install --python train/Scripts/python.exe torch torchvision --index-url https://download.pytorch.org/whl/cu128
uv pip install --python train/Scripts/python.exe -r train-requirements.lock

# export verification: the same torch the Android native library is built from
uv venv export --python 3.10          # uv fetches 3.10 automatically
uv pip install --python export/Scripts/python.exe -r export-requirements.lock
```

**Verify CUDA before writing anything else.** A silent CPU fallback wastes hours:

```bash
handoff/env/train/Scripts/python.exe -c "import torch;print(torch.__version__, torch.version.cuda, torch.cuda.get_device_capability())"
# expect: 2.11.0+cu128 12.8 (12, 0)      <- (12,0) is sm_120. Anything else, stop.
```

> **uv trap:** if an earlier `uv` process is still alive it holds the cache lock, and uv then
> prints `error: Failed to acquire lock` **while exiting 0** — installing nothing, silently.
> If a package seems missing after an apparently successful install:
> `Get-Process uv | Stop-Process -Force` then clear `%LOCALAPPDATA%\uv\cache\**\*.lock`.

---

## 1 · Export contract gate — 2 min, run it FIRST

This can invalidate everything downstream, so it runs before any training.

```bash
handoff/env/train/Scripts/python.exe  handoff/scripts/p0_1_export_contract.py
handoff/env/export/Scripts/python.exe handoff/scripts/p0_1_verify_on_1131.py
```

Expect `N = 8`, `torch 2.x emits 8` (so no backport needed), and `OVERALL: PASS` — both the toy
net and the shipping model loading under torch 1.13.1.

---

## 2 · Rebuild the dataset — 1 min

```bash
handoff/env/train/Scripts/python.exe handoff/scripts/p1_rebuild_dataset.py
# with clean photos, once they exist:
handoff/env/train/Scripts/python.exe handoff/scripts/p1_rebuild_dataset.py --clean path/to/clean_photos
```

Deterministic — `--seed 0` by default. Expect 226 groups, 2201 images, 154 backgrounds, and
`leak check: every one of 226 groups sits in exactly one split`.

---

## 3 · Train — ~25 min per candidate on a 5070 Ti

```bash
handoff/env/train/Scripts/python.exe handoff/scripts/p2_train.py --run A --epochs 100 --batch 24
handoff/env/train/Scripts/python.exe handoff/scripts/p2_train.py --run B --epochs 100 --batch 24
handoff/env/train/Scripts/python.exe handoff/scripts/p2_train.py --run C --epochs 100 --batch 24   # 800px, auto-halves batch
```

`seed=0`, `deterministic=True`. Runs land in `handoff/runs/<name>/`; TensorBoard logs are
written there, local only.

> First run downloads a checkpoint for ultralytics' AMP check. With no internet, pass
> `amp=False` in `p2_train.py` and record that in the run config — it changes the numbers.

---

## 4 · Evaluate — ~8 min per candidate on CPU

```bash
handoff/env/train/Scripts/python.exe handoff/scripts/p2_evaluate.py \
    --weights handoff/runs/A_yolov8n_640/weights/best.pt --tag A --device cpu
```

Emits per-class mask AP50/AP50-95 on the grouped-split test set **and** the clean-image
false-positive table at 0.25/0.35/0.45/0.55. Use `--imgsz 800` for candidate C.

CPU on purpose: it frees the GPU for the next training, and these are small sets.

---

## 5 · Export the winner — 2 min

```bash
handoff/env/train/Scripts/python.exe handoff/scripts/p3_export.py \
    --weights handoff/runs/A_yolov8n_640/weights/best.pt
handoff/env/export/Scripts/python.exe handoff/scripts/p0_1_verify_on_1131.py \
    handoff/models/vision/yolov8n_seg.ptl
```

The export asserts shapes against `CONTRACTS.md`, asserts bytecode ≤ 8, and asserts
`max |eager − lite| < 1e-3`. It refuses to write the file if any of those fail.

> **Do not add `optimize_for_mobile`.** It silently corrupted the output — 524 max abs diff on
> box coordinates, with correct shapes and a clean load. The assertion above exists because of
> that. `ml/YOLOV8/export_torchscript_lite.py:66-69` warns about the same class of problem.

---

## 6 · Acoustic refit — blocked on recordings

```bash
cd smartlease-edge-chennai-master/ml/acoustic
../../../handoff/env/train/Scripts/python.exe export_for_android.py
```

Must run **from** `ml/acoustic/` — it hardcodes `audio_root = "../data/audio"` at `:66`.
Regenerates `acoustic_tap_model.json` including the golden vectors, so the Kotlin parity test
keeps passing without intervention. See `RECORDING_PROTOCOL.md`.

---

## Known environment traps

| trap | symptom | fix |
|---|---|---|
| **Space in the workspace path** | `PytorchStreamReader failed reading zip archive … your checkpoint file is corrupted` — on a file that is fine | Pass an open file handle, or work under a space-free directory. Every script here does. |
| `torch.jit._load_for_lite_interpreter` | `AttributeError` on torch 2.x | Moved to `torch.jit.mobile` |
| uv cache lock | `error: Failed to acquire lock`, **exit code 0**, nothing installed | Kill stray `uv`, clear `*.lock` |
| torch 1.13.1 on Python 3.12 | no matching distribution | No cp312 wheels exist. Use Python 3.10 — `uv` fetches it. |
| `shutil.rmtree` on the dataset | `WinError 32 … used by another process` | A shell `cd`'d inside it, or a dataloader still holds it. Leave the directory and kill stray python. |

## Determinism

`seed=0`, `deterministic=True`, fixed split seed. Exact float reproduction across different
GPUs or CUDA versions is not guaranteed — cuDNN kernel selection varies. The **split** is fully
deterministic and reproduces byte-for-byte anywhere.
