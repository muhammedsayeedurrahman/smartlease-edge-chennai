# On-device report narration (Gemma)

The inspection report's section prose can be written by a Gemma model running on the phone.
It is **off unless you put the weights on the device** — there is no build flag, no setting,
and no toggle. The app looks for a model file at report time; if it finds one it narrates with
it, and if it does not it falls back to rule-based templating. Both outcomes are printed on the
PDF under the paragraph, so a reader always knows which one wrote what they are reading.

**Nothing here needs a network at inspection time.** Inference is local. The download below is
a one-off setup step on your workstation.

## Why the model is not in the APK

Two reasons, both hard:

- **Size.** The smallest usable build is around 550 MB; the Gemma 3n E2B build is about 3 GB.
  An APK carrying either is not something a judge or a tenant will wait to install.
- **Licence.** Gemma weights are distributed under Google's Gemma Terms of Use, which you
  accept on Hugging Face. Bundling them in this repo would redistribute them.

## 1. Get access

The LiteRT Gemma repositories are gated. On huggingface.co, while signed in:

1. Open the model page and accept the Gemma licence — this is the step that ungates it:
   - `litert-community/Gemma3-1B-IT` (recommended for the demo)
   - `google/gemma-3n-E2B-it-litert-preview` (better prose, ~3 GB)
2. Create a **read** token at <https://huggingface.co/settings/tokens>.
3. Log in locally: `hf auth login` and paste the token when prompted.

> Do not paste the token into a chat, a commit, or this file. Hugging Face scans public
> surfaces and revokes tokens it finds, so a leaked token usually stops working — which is the
> good outcome. Rotate any token that has been shared.

## 2. Download

```bash
hf download litert-community/Gemma3-1B-IT gemma3-1b-it-int4.task --local-dir ./gemma
```

Which file to take:

| File | Size | Notes |
|---|---|---|
| `gemma3-1b-it-int4.task` | ~0.55 GB | **Start here.** Loads on every device, fast first token. |
| `gemma-3n-E2B-it-int4.task` | ~3 GB | Noticeably better prose; slower to load, from the `google/` repo above. |
| `Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm` | ~0.5 GB | Built for the demo phone's exact SoC (SM8850, iQOO 15). Needs a LiteRT-LM runtime, **not** the MediaPipe runtime this app pins — do not expect it to load here today. |

## 3. Push it to the phone

```bash
adb push gemma/gemma3-1b-it-int4.task \
  /sdcard/Android/data/com.smartlease.edge/files/llm/
```

That path is the app's private external directory: `adb push` can write it without root, and
the app can read it without a storage permission.

`/data/local/tmp` is **not** searched, deliberately. It is world-readable scratch space, and
treating it as a model source would mean any process on the device could swap the weights that
write sentences onto a legal document.

If the directory does not exist yet, launch the app once first, or:

```bash
adb shell mkdir -p /sdcard/Android/data/com.smartlease.edge/files/llm
```

## 4. Confirm the app picked it up

Open **Self-test** in the app. The `MODELS` section prints one of:

```
report narrative    on-device Gemma - gemma3-1b-it-int4.task (528 MB)
report narrative    rule-based templating - no model file in /data/... or /storage/...
report narrative    rule-based templating - gemma3-1b-it-int4.task (528 MB) failed to load
```

Those three are distinct on purpose. "Not set up" and "set up and broken" need different
things from you.

## What the model is and is not allowed to do

The narrative is the only part of the report that is written rather than computed. Everything
else on the page — the digest, the measured areas, the confidences, the rupee arithmetic — is
derived from data. So the model's output is checked against the findings before it is printed
(`NarrationAudit`), and rejected text falls back to the template silently. It is rejected for:

- describing a hazard no finding recorded, **or** omitting one that a finding did record;
- stating a number of findings that is not the real number;
- quoting any rupee amount — pricing belongs to the deduction engine, which prints its own
  balance sheet on the same page;
- being empty, or running away past a paragraph.

The prompt also forbids recommending repairs, assigning blame, and inventing observations. The
audit is what enforces it, because a prompt is a request and an audit is a check.

Decoding is greedy (`maxTopK = 1`), so regenerating a report over the same findings produces
the same paragraph. A report that reworded itself each time would make "we regenerated it and
it says something different" a real conversation to have in front of a judge.

## Honest limits

- **The audit is deterministic and shallow.** It catches contradictions with the structured
  record. It cannot tell whether a paragraph is *well* written, and it will not catch a
  plausible-sounding sentence that happens to be vague rather than false.
- **A 1B model writes plainly.** That is largely a feature on this document, but do not oversell
  it as analysis. It is summarisation of a list you can read directly above it.
- **Latency is real.** First token after model load takes a few seconds on the demo phone. The
  model is loaded per report and closed afterwards, because holding it pins hundreds of
  megabytes between inspections.
- **No narration ran in the demo build as shipped.** If no weights are on the phone, do not
  say the app narrates with Gemma — the PDF will say otherwise, in print, under the paragraph.
