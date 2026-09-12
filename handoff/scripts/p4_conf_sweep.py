"""Confidence sweep for the shipping model.

Two questions, measured rather than asserted:
  specificity -- of the clean images that should produce NO box, how many stay clean?
  recall      -- of the real defect instances, how many do we still find at that cutoff?

Raising SCORE_THRESHOLD buys the first by spending the second. The point of the sweep is
to show the exchange rate rather than argue about it.
"""
import glob, json, os
from ultralytics import YOLO

ROOT = "dataset/unified_v2"
WEIGHTS = r"C:\Users\HP\SmartLeaseEdge\ml\vision\handoff\models\best_candidate_A.pt"
CONFS = [0.25, 0.45, 0.55, 0.65, 0.80]

def backgrounds():
    """An image whose label file is empty is a declared negative: nothing is there."""
    out = []
    for split in ("valid", "test"):
        for lbl in glob.glob(f"{ROOT}/{split}/labels/*.txt"):
            if os.path.getsize(lbl) == 0:
                stem = os.path.splitext(os.path.basename(lbl))[0]
                hits = glob.glob(f"{ROOT}/{split}/images/{stem}.*")
                if hits:
                    out.append(hits[0])
    return out

model = YOLO(WEIGHTS)
bg = backgrounds()
print(f"clean images found: {len(bg)}")

results = {}
for c in CONFS:
    clean = 0
    boxes = 0
    for i in range(0, len(bg), 16):
        for r in model.predict(bg[i:i+16], conf=c, imgsz=640, verbose=False):
            n = 0 if r.boxes is None else len(r.boxes)
            boxes += n
            if n == 0:
                clean += 1
    m = model.val(data=f"{ROOT}/data.yaml", split="test", conf=c, imgsz=640,
                  verbose=False, plots=False)
    results[c] = {
        "specificity_pct": round(100.0 * clean / len(bg), 1),
        "fp_per_clean_image": round(boxes / len(bg), 2),
        "mask_recall": round(float(m.seg.mr), 4),
        "mask_precision": round(float(m.seg.mp), 4),
        "mask_map50": round(float(m.seg.map50), 4),
    }
    print(c, results[c], flush=True)

json.dump({"n_clean": len(bg), "weights": "candidate_A", "by_conf": results},
          open("conf_sweep_A.json", "w"), indent=2)
print("written conf_sweep_A.json")
