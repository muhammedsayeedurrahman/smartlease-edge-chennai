# PILOT_CHECKLIST.md

Two small captures, checked end to end, before you spend an hour on data you cannot re-shoot.

**Total cost: ~12 minutes of yours, ~5 of mine.** Both pilots can be sent at the same time.

---

## A · ACOUSTIC PILOT — 2 spots

### What to record

**Record the constructed pair from `RECORDING_PROTOCOL.md §3a` if you can make it in two
minutes.** It is the best possible pilot:

| | spot | label |
|---|---|---|
| 1 | a spare floor tile / granite offcut / ply sheet resting on two supports, ~15 mm gap, on a solid floor — **strike the centre of the span** | `hollow` |
| 2 | **the same tile**, laid flat on the **same floor**, full contact — **strike the same point** | `solid` |

Same object, same room, same mic distance, minutes apart. The only variable is the air gap.
That lets me answer a question no format check can: **does this handset's microphone see the
difference at all?**

**If you cannot build the pair:** 1 × hollow-core door centre panel (H1) and 1 × solid
external wall (S3), **in the same room**. Less clean, still useful.

Surface slug: `pilot_h8_tile_on_battens` and `pilot_s4_tile_flat`, so I can find them.

### How to send

**Export all (zip)** from the capture screen → Drive, email, or USB cable.

> **Not WhatsApp, not Telegram, not any chat app.** They transcode audio attachments. The
> pilot would then fail for a reason that has nothing to do with your recording, and — worse
> — it might *pass* while telling you nothing, because a transcoded file still opens.
> Zipping first protects the bytes if you must use a messaging app.

**Tell me:** which handset, and whether you used the in-app screen or a third-party recorder
(and which one, with its settings).

### What I check — `handoff/scripts/pilot_audio_probe.py`

**Format — is it actually what it claims**

| # | check | fails if |
|---|---|---|
| 1 | container is RIFF/WAVE, `fmt` tag **1 = PCM** | it is m4a / 3gp / ogg / Opus / IMA-ADPCM wearing a `.wav` name |
| 2 | **44100 Hz** exactly, **1 channel**, **16-bit**, ~8.0 s | anything else — 48 kHz and 16 kHz are both common defaults |
| 3 | **energy above 8 kHz** | it is empty. **This is the most important single check.** Voice codecs band-limit to ~8 kHz, and a 16 kHz Opus file upsampled to 44.1 still has nothing up there. It is the exact defect in the current training set, and it is invisible in the file header. |
| 4 | no brickwall below 22.05 kHz | a sharp cliff at 15/20/24 kHz means it was recorded lower and resampled |

**Processing — did the phone quietly "help"**

| # | check | how |
|---|---|---|
| 5 | **automatic gain control** | noise-floor RMS in the 300 ms *before* the first strike, in the longest inter-strike gap, and at the very end. More than ~6 dB of drift with no physical cause is gain riding. I also check the decay shape — AGC lifts a transient's tail back up instead of letting it fall exponentially, which destroys the decay-time feature the classifier leans on. |
| 6 | **noise suppression** | spectrum of a silent segment. A real room has broadband rumble below 200 Hz and 50 Hz mains harmonics. If those are gone, or the silence shows isolated narrow spectral spikes (musical noise), something is processing the signal. |
| 7 | **clipping** | count of samples at ±32767 — the in-app meter catches this, I confirm it |
| 8 | **DC offset** | a large offset skews every energy feature |

**Technique — did you strike it well**

| # | check | what I will tell you |
|---|---|---|
| 9 | transient count, per-strike peak dBFS, inter-strike gaps, measurable decay | "4–6 clean strikes with clear gaps" — or "too soft", "too fast, they overlap", "that is a scrape not a strike" |

**Signal — is there anything to learn**

| # | check | what I will tell you |
|---|---|---|
| 10 | the **36 production features** (`AcousticFeatureExtractor`'s mirror) extracted from both files, side by side, with the per-feature gap | which features separate hollow from solid on **your** handset — spectral centroid, decay time, high-frequency ratio — and by how much. |

### The answer you get back

**GO**, or **one specific thing to change**. If check 3 or 5 fails I will say exactly what to
change before you record 60 spots into it.

> **If check 10 shows no separation at all**, that is the most valuable ten minutes in this
> project: you would learn *before* the hour of tapping that the A56's microphone cannot
> resolve the difference, and T5 becomes "report the existing model honestly and do not
> foreground it" instead of an hour spent proving that.

---

## B · PHOTO PILOT — 14 images

### What to send

Exactly the final folder structure, so the pilot exercises the real path:

```
pilot_photos/
├── clean/
│   ├── painted_wall/        3 images
│   ├── tiled_floor_grout/   4 images   <- the highest false-positive risk surface
│   └── wood_door_frame/     3 images
└── indoor_defects/
    ├── crack/               2 images
    └── damp/                2 images
```

10 clean across **three different surfaces** (not ten of one wall), 4 defects across **two
classes**. Shoot them the way §"How to shoot" in `SHOOT_LIST.md` describes — 0.5–1.5 m,
mostly straight on, under the lighting you will actually use.

Send as a zip, any route. Photos survive chat apps better than audio, but zip anyway —
WhatsApp recompresses loose images and strips EXIF.

### What I check — `handoff/scripts/p1_ingest_clean.py --pilot`

**Will the pipeline even read them**

| # | check | fails if |
|---|---|---|
| 1 | **format is JPEG, not HEIC/HEIF** | OpenCV cannot open HEIC. **This is the most likely blocker and the cheapest to fix** — Camera → Settings → Picture format → JPEG / "Most compatible". Check this on the phone before you shoot the other 346. |
| 2 | EXIF orientation is read and **applied** | otherwise a third of your photos train sideways and you will never see it |
| 3 | colour channels sane, no CMYK, no 16-bit oddity | |

**Does the ingest do what it says**

| # | check |
|---|---|
| 4 | resize to longest side 1024, aspect preserved, JPEG q88 — I report before/after dimensions and file size |
| 5 | a `.txt` label file exists for every clean image and is **exactly 0 bytes** |
| 6 | **group assignment**: folder name becomes the group, and every image from one folder lands in **one** split |
| 7 | **holdout routing**: specificity-holdout images appear in **none** of train / valid / test |
| 8 | **indoor defects are excluded from the segmenter dataset entirely** — they have no masks, so an ingest bug that let them through as empty labels would teach the model that cracks are background. I assert their absence explicitly. |
| 9 | ultralytics' own scan reports them: `N images, N backgrounds, 0 corrupt` |

**Are the photos any good**

| # | check | what I will tell you |
|---|---|---|
| 10 | sharpness — variance of the Laplacian, per image | "image 4 is motion-blurred, hold steadier" |
| 11 | exposure — % of pixels clipped at 0 and at 255 | "the tile shots are blown out by the flash, turn it off" |
| 12 | near-duplicates — perceptual hash across the set | "three of your ten are the same square metre; move along the wall" |

### The answer you get back

**GO**, or **one specific thing to change** — most likely the picture format, the flash, or
the distance.

---

## C · Turnaround and order of play

| | |
|---|---|
| 1 | Build the tile pair, record 2 spots. Shoot 14 photos. **~12 min.** |
| 2 | Zip both, send both. Tell me the handset and the recorder. |
| 3 | I run both probes. **~5 min.** |
| 4 | GO, or one change each. |
| 5 | You capture the rest: **32 hollow spots first** (they are the constraint), then 32 solid, then the 360 clean and 80–120 defect photos. |

**Do the acoustic bulk run before the photo bulk run.** If the audio pilot turns up a format
problem, the fix might be installing a different app or a different handset, and you want to
discover that while you still have the evening.

---

## D · What these pilots do **not** tell you

Worth knowing so you do not over-read a GO.

- **A GO on the audio pilot does not predict the model's accuracy.** Two spots cannot. It
  says the bytes are right, the technique is right, and there is measurable separation on one
  controlled pair. The accuracy comes from the 56–64 spots and its interval comes from the
  spot count — `RECORDING_PROTOCOL.md §4` has the arithmetic, and even 64 spots needs ~91%
  observed to put the lower bound above 0.80.
- **A GO on the photo pilot does not predict specificity.** It says the ingest works and the
  photos are usable. Whether 360 negatives move a clean wall from 19% to 90% is what T3
  measures, and it may not get there — `TARGETS.md §2 M1` states that plainly.
- **Neither pilot checks the thing you cannot fix tonight**: that the source defect images are
  drone frames of building exteriors and yours are a flat in Chennai. That is what the 80–120
  indoor defect photos and the T2 shortcut probe are for.
