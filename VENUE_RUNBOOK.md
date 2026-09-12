# VENUE_RUNBOOK.md — iQOO 15 bring-up

**Print this.** Assume: no internet, possibly no laptop, possibly no cable, short window.
**Total: 44 min.** Ordered so the highest unknowns resolve first — if you run out of time,
everything below step 4 is optional.

Carry: the APK on a **USB-C drive** *and* in your phone's Downloads, a USB-C cable, and a
**tile board or a hollow door you have already tested**.

---

## 1 · Install — 5 min ⚠️ nothing else happens until this works

**File:** `demo/app-debug-KNOWN-GOOD-<sha>.apk` (also verify `.sha256` if you have a laptop).

**Without a cable (expect this):**
1. Plug the USB-C drive in → **Files** app → open the APK.
2. FunTouch blocks it: *"For your security, you can't install unknown apps from this source."*
   Tap **Settings** on that dialog → enable **Allow from this source** for **Files**.
   *(Long path: Settings → Apps → Special app access → Install unknown apps → Files.)*
3. Back → tap the APK again → **Install**.
4. FunTouch may then show a second scan dialog — **Install anyway**.

**If it still refuses:** the build is debug-signed, which some FunTouch builds treat as
higher risk. Try installing from **Downloads** rather than the USB drive (copy it across
first). Failing that, use the cable:
```
adb install -r app-debug-KNOWN-GOOD-<sha>.apk
```

**If nothing installs → go to step 8 (fallback) now. Do not burn the window.**

---

## 2 · Stop FunTouch killing the app — 6 min

vivo/iQOO builds are aggressive. Being killed mid-walkthrough on stage is a failure mode
nobody rehearses. Do all four:

- **Settings → Battery → Background power consumption management** → SmartLease Edge →
  **Allow high background power consumption**.
- **Settings → Apps → SmartLease Edge → Battery** → **Unrestricted** / disable optimisation.
- **Recents** → swipe down on the SmartLease card → **lock** it (padlock).
- **Settings → Apps → SmartLease Edge → Permissions** → turn OFF
  **"Remove permissions if app is unused"**. Camera and mic must not be auto-revoked.

Then **grant Camera and Microphone** on first launch. Nothing else is requested — there is
no location or storage prompt, and if you see one, something is wrong.

---

## 3 · Device & Model Check — 3 min ⚠️ everything downstream depends on this

Home → **Device & Model Check**. It runs automatically. **Screenshot the whole thing**
(scroll, screenshot twice), then **Copy** → paste into a note.

Read these four lines before you move on:

| Line | What you need to see | If not |
|---|---|---|
| `FEATURE_CONSUMER_IR` | **PRESENT** | IR is dead — step 8 |
| `38 kHz supported` | **yes** | the app transmits at 38 kHz; if the range excludes it, drop the IR beat |
| `CameraX will bind` + focal lengths | main sensor, **not** the ultrawide | framing is wrong; note it, do not "fix" it live |
| `warm inference` | a real number in ms | **this is your answer to Q3** — write it on your hand |

That inference number is the only on-device latency this project has ever had. It also fills
the blank in `DECK_CORRECTIONS.md` §4.

---

## 4 · IR, for real, for the first time — 5 min ⚠️ highest unknown in the submission

**This has never executed on hardware with an emitter.** The A56 has none, so only the
failure branch has ever run.

1. Walkthrough → point the phone's IR window (top edge) at the venue AC, **≤ 3 m, clear line
   of sight**.
2. Tap **Trigger AC (IR)**.
3. Read the finding line.

| What you see | What it means | What you say |
|---|---|---|
| `IR command transmitted — Voltas, 38 kHz (pattern not verified against this unit)` | The emitter fired. The AC almost certainly does nothing. | *"That's the honest state: we sent a command and logged that we sent it. Android can't receive IR, so we can't confirm the unit responded — that's the open loop, and it's the next build."* |
| `No IR blaster detected` | Self-test lied, or the feature is gated. | Drop IR from the demo. Step 8. |
| `IR transmit failed: …` | Emitter present, transmit rejected. | Read the reason aloud. It is still a real finding. |

**The AC will probably not react, and that is fine** — the claim in the app and the report is
only that the command was sent. Do not promise a reacting appliance.

---

## 5 · Layout on 2K — 5 min

The A56 is FHD+; the iQOO 15 is 2K with a different aspect. Compose will not crash, it will
just look broken. Open each and look for clipped text or a button below the fold:

- Home — is **Start Walkthrough** visible without scrolling?
- Walkthrough — is **Generate Report** reachable? Does the findings log scroll?
- Report — is the **QR fully visible**, and is **Share report** reachable?
- Device & Model Check — does the monospace block wrap rather than clip?

---

## 6 · Clean-wall recon — 10 min ⚠️ this is what pre-empts Q10

The model has **zero hard negatives** — not one of its 2,106 training images is free of
defects, so it has no concept of a clean surface.

Walk the judging area. **Capture + Analyze** on each: the wall behind you, the table, the
floor, a switch plate, any exposed brick. **Write down what fires.**

Then either demo on a surface you verified, or open with:

> *"Before you point it at that wall — we have zero clean-surface images in our training set,
> so I expect false positives on grout lines, shadows and sockets. It's the first thing we'd
> fix, and it's why every detection shows its confidence."*

**Pre-empting this scores. Being ambushed by it does not.**

---

## 7 · One timed rehearsal — 10 min

Airplane mode ON. Baseline → Capture → Tap Test → IR → Generate Report → **scan the QR with
a second phone** → Share. Time it. Twice if the clock allows.

---

## 8 · Fallback — if the loaner never works

Demo on the **A56**. Everything works except IR.

**Drop entirely:** every IR claim. Do not describe IR as if it ran.

**Say this, unprompted, at the hardware slide:**

> *"I'm demoing on a Galaxy A56, so the IR appliance check can't run — that hardware isn't in
> this phone. It's the one part of this that's genuinely iQOO-specific, and I'd rather show
> you the parts I can stand behind than describe one you can't see. Everything else here —
> the segmenter, the tap test, the hashed report — is the same code on either handset."*

Then lead with the **QR verification beat** (`HANDOFF.md`), which needs no special hardware
and is the only thing in the demo a judge can test with their own phone.

---

## Appendix · IR codesets — decided in advance, and the answer is "don't"

`ConsumerIrManager` is **transmit-only**, so no code can be captured at the venue. The only
option is bundling one in advance. Licences, checked:

| Source | Licence | Verdict |
|---|---|---|
| [**irdb**](https://github.com/probonopd/irdb) | **Custom, not permissive.** Requires opening a GitHub issue to notify them, an attribution notice in the product, and **making up to three fully licensed units of your product available free on request**. | **Do not bundle.** That third clause is a live obligation for a product pitched at ₹299–499. |
| [**LIRC** remotes db](https://en.wikipedia.org/wiki/LIRC) | **GPL** | **Do not bundle.** GPL data shipped inside a commercially-pitched APK is a licence question you do not want asked on stage. |
| [**Flipper-IRDB**](https://github.com/Lucaslhm/Flipper-IRDB) | **Not clearly stated.** | **Do not bundle.** Unstated provenance is the worst option of the three. |

**Recommendation: ship the generic NEC header with its existing caveat.** The finding string
already reads *"pattern not verified against this unit"* — that is true, it costs nothing,
and it is defensible. A real Voltas power code would buy you an AC that beeps; it would not
buy you a *verified* appliance, because there is still no feedback channel. The honest
caveat is worth more than the beep.

**Never label a code "captured".** The app cannot capture one, and claiming an artifact that
cannot exist is the exact failure this audit has spent three sessions removing.
