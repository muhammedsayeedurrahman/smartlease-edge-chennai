# SmartLease Edge judge walkthrough

Open **Demo** from the home screen. This self-contained route is designed for a concise,
reliable presentation before moving into the live inspection workflow.

## Presenter flow

1. **Problem** — “Deposit disputes rely on scattered photos and memory. SmartLease Edge
   turns property condition into a structured, checkable record.”
2. **Move-in condition** — Show the first evidence card. “A condition record establishes the
   reference point before occupancy.”
3. **Move-out condition** — Show the second card. “At move-out, visible damage is recorded
   for review; the app supports evidence collection, not an automatic legal decision.”
4. **Case file** — Tap **Review case file**. “The report presents the finding, any proposed
   deduction, and the privacy boundary in one readable record.”
5. **Live proof** — Return to the normal inspection flow to show camera capture, local report
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

## Evidence image sources

The fixed guided-demo reference images are bundled for offline use. They are not represented
as photographs of a specific tenancy.

- **Move-in:** [Elm-Place-Dry-Wall-Gypsum-Board-Interior-wall.jpg](https://commons.wikimedia.org/wiki/File:Elm-Place-Dry-Wall-Gypsum-Board-Interior-wall.jpg), by Marina-Shehata, CC0 1.0.
- **Move-out:** [Kerewan Cracked Wall.JPG](https://commons.wikimedia.org/wiki/File:Kerewan_Cracked_Wall.JPG), by Dcm250451, public domain.
