# SmartLease Edge judge walkthrough

Open **Demo** from the home screen. This route runs the actual app pipeline — vision,
deduction, narration, PDF rendering — against three bundled move-in/move-out room pairs, so a
judge sees the real architecture execute rather than a scripted mock of it.

## Presenter flow

1. **Problem** — "Deposit disputes rely on scattered photos and memory. SmartLease Edge turns
   property condition into a structured, checkable record."
2. **Evidence stage** — Scroll through the three rooms (Living Room, Kitchen, Bedroom). Point
   out that each move-out photo is the *same photograph* as its move-in pair with one small
   defect added — a crack, a stain, a scuff — because that is the realistic case a move-out
   inspection exists to catch, not two different rooms standing in for "before/after".
3. **Run the pipeline** — Tap "Run the real pipeline on this evidence". Everything after this
   point is computed fresh, on this device, from these six photographs — nothing is
   precomputed or faked.
4. **Vision stage** — The on-device segmenter's actual findings for each room's move-out photo,
   with the trained-model flag shown honestly (falls back to a heuristic segmenter if no
   exported model is bundled on the build).
5. **Deduction stage** — The same rate-card pricing engine a live move-out report uses, applied
   to the findings above against a demo deposit.
6. **Narration stage** — Which narrator actually ran on this device (template or on-device
   Gemma) and the exact paragraph it wrote from the findings — nothing added beyond what the
   findings say.
7. **Report stage** — A real PDF written to the app's own storage this run, with its own
   SHA-256 fingerprint. Tap "View the generated PDF" to open it.
8. **Live proof** — Return to the normal inspection flow to show camera capture, local report
   generation, QR fingerprinting, and PDF sharing. Only show backend sync if the deployed
   HTTPS endpoint is configured and working.

## Claims to make precisely

- Camera analysis, report generation, local storage, PDF creation, and SHA-256 fingerprinting
  run on the handset.
- Media does not enter the backend sync API. Optional sync carries only a digest and small
  report metadata.
- A digest detects changed report findings; it is not proof of a person's identity or a legal
  signature.
- The operator reviews findings and remains responsible for any deduction decision.
- If the demo pipeline seems to stall or the app restarts mid-run, it is almost always device
  thermal throttling (heavy builds/installs beforehand heat the SoC) rather than a code fault —
  let the phone sit for a minute and retry.

## Evidence image sources

The fixed guided-demo reference images are bundled for offline use. They are not represented
as photographs of a specific tenancy. Each room's move-out photo is a synthesized-defect
version of its own move-in photo (crack/stain/scuff composited on with Pillow), not a separate
photograph — so "before" and "after" are the same room, the way a real move-out dispute is.

- **Living Room — move-in & move-out base:** [Elm-Place-Dry-Wall-Gypsum-Board-Interior-wall.jpg](https://commons.wikimedia.org/wiki/File:Elm-Place-Dry-Wall-Gypsum-Board-Interior-wall.jpg), by Marina-Shehata, CC0 1.0. Move-out adds a synthesized crack.
- **Kitchen — move-in & move-out base:** [A well-organized kitchen.jpg](https://commons.wikimedia.org/wiki/File:A_well-organized_kitchen.jpg), by Shixart1985, CC BY 2.0. Move-out adds a synthesized crack and stain.
- **Bedroom — move-in & move-out base:** [Bed & Nightstand (49875912823).jpg](https://commons.wikimedia.org/wiki/File:Bed_%26_Nightstand_(49875912823).jpg), by Ajay Suresh from New York, NY, USA, CC BY 2.0. Move-out adds a synthesized scuff and crack.
