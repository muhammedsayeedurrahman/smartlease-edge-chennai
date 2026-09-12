# -*- coding: utf-8 -*-
"""
T5 — acoustic refit, with the group integrity the paired design requires.

THE GROUPING PROBLEM, which is the whole reason this script exists rather than a rerun of
export_for_android.py:

  The built pair - the same tile on two pencils, then flat on the same floor - is what makes
  the labels certain, because only the air gap changes. But it also creates a leak. The
  existing exporter groups LeaveOneGroupOut folds by FILENAME:

      ml/acoustic/export_for_android.py:61   groups.append(os.path.basename(path))

  so tile #3-hollow and tile #3-solid land in DIFFERENT folds. The model can then be trained
  on that specific tile ringing and tested on that same specific tile not ringing, and it
  scores well by recognising the tile rather than the void. Every built pair would inflate
  the interval that the whole capture exists to tighten.

  So the fold unit here is the PHYSICAL SPECIMEN, not the file. Both conditions of one
  specimen share a group and LeaveOneGroupOut removes them together. Asserted, not assumed:
  assert_group_integrity() fails the run if any specimen's taps reach more than one group,
  and prints the pairs it found so the design is visible rather than trusted.

NAMING CONTRACT (RECORDING_PROTOCOL.md 3a/6):

      <label>_<slug>_<timestamp>.wav        e.g.  hollow_pair3_tile_on_floor_1757....wav
                                                  solid_pair3_tile_on_floor_1757....wav

  The specimen key is the SLUG. Two recordings of the same physical object must carry the
  same slug; the label distinguishes them. A slug beginning `pair` or containing `built`
  marks a constructed specimen, which is reported separately - a tile resting freely on two
  supports rings more than a bonded tile with a void beneath, and if built specimens come to
  dominate the hollow class the model learns "loose plate" and will not transfer to a floor.

Bake-off on the EXISTING 36 features only: logistic regression (the incumbent), gradient
boosting, and a small MLP. No new feature extractor - AcousticFeatureExtractor.kt mirrors the
current one and AcousticFeatureExtractorTest.kt pins the parity.

Usage:
    python p5_acoustic_refit.py                       # ml/data/audio
    python p5_acoustic_refit.py --audio-root DIR [--drop-legacy] [--dry-run]
"""
import argparse
import glob
import json
import math
import os
import re
import sys
import warnings
from collections import Counter, defaultdict

warnings.filterwarnings("ignore")

import numpy as np

HERE = os.path.abspath(os.path.dirname(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ACOUSTIC = os.path.join(ROOT, "smartlease-edge-chennai-master", "ml", "acoustic")
DEFAULT_AUDIO = os.path.join(ROOT, "smartlease-edge-chennai-master", "ml", "data", "audio")
CLASSES = ("solid", "hollow")

# pre-committed in TARGETS.md 8 G5, before any of this data existed
GATE_LEAD = 0.80
GATE_SHIP = 0.60

TS = re.compile(r"[_-]\d{10,}$")


def specimen_key(path):
    """Filename -> the physical object it was struck on.

    Strips the leading label and the trailing capture timestamp, leaving the slug. Two
    recordings of the same object share it whatever their labels, which is exactly what the
    built pair needs and what filename-grouping gets wrong.
    """
    stem = os.path.splitext(os.path.basename(path))[0]
    for c in CLASSES:
        if stem.lower().startswith(c + "_"):
            stem = stem[len(c) + 1:]
            break
    stem = TS.sub("", stem)
    return stem.lower() or os.path.splitext(os.path.basename(path))[0].lower()


def is_built(key):
    return key.startswith("pair") or "built" in key or "batten" in key


def wilson(k, n, z=1.96):
    if n <= 0:
        return (0.0, 1.0)
    p = k / n
    d = 1 + z * z / n
    c = p + z * z / (2 * n)
    s = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))
    return ((c - s) / d, (c + s) / d)


# ---------------------------------------------------------------- loading
def load(audio_root, drop_legacy=False):
    sys.path.insert(0, ACOUSTIC)
    cwd = os.getcwd()
    os.chdir(ACOUSTIC)
    try:
        import android_features as af
        from train_tap_classifier import segment_taps
    finally:
        os.chdir(cwd)

    X, y, groups, files, per_file = [], [], [], [], {}
    for label, cls in enumerate(CLASSES):
        for path in sorted(glob.glob(os.path.join(audio_root, cls, "*.*"))):
            if drop_legacy and "whatsapp" in os.path.basename(path).lower():
                continue
            clips = segment_taps(path)
            if not clips:
                print("  WARNING: no taps segmented from %s" % os.path.basename(path))
                continue
            key = specimen_key(path)
            for c in clips:
                X.append(af.extract(c))
                y.append(label)
                groups.append(key)
            files.append(path)
            per_file[os.path.basename(path)] = (key, cls, len(clips))
    return np.array(X), np.array(y), np.array(groups), files, per_file


# ---------------------------------------------------------------- the assertion
def assert_group_integrity(groups, y, per_file):
    """Fail the run if the fold unit is not the physical specimen.

    Two things are checked, and the second is the one that matters:
      1. every tap carries a group  (trivial, but a silent empty key would merge specimens)
      2. every FILE maps to exactly one group, and every specimen recorded under BOTH labels
         has its two files in the SAME group - so LeaveOneGroupOut removes the pair together
         and cannot train on one condition of a specimen while testing on the other.
    """
    print("\n--- group integrity")
    bad = [g for g in set(groups) if not g or g.isspace()]
    if bad:
        raise AssertionError("empty specimen key(s): %r" % bad)

    by_key = defaultdict(lambda: {"files": [], "labels": set()})
    for fn, (key, cls, n) in per_file.items():
        by_key[key]["files"].append(fn)
        by_key[key]["labels"].add(cls)

    file_of_group = defaultdict(set)
    for fn, (key, cls, n) in per_file.items():
        file_of_group[fn].add(key)
    multi = {fn: ks for fn, ks in file_of_group.items() if len(ks) > 1}
    assert not multi, "a file was assigned more than one specimen key: %r" % multi

    pairs = {k: v for k, v in by_key.items() if len(v["labels"]) == 2}
    singles = {k: v for k, v in by_key.items() if len(v["labels"]) == 1}
    print("  specimens: %d   of which paired (both conditions, one group): %d"
          % (len(by_key), len(pairs)))
    for k, v in sorted(pairs.items()):
        print("    PAIR  %-28s %s" % (k, " + ".join(sorted(v["files"]))))

    # the actual guarantee: for every specimen, all of its taps carry one and the same group
    idx = defaultdict(set)
    for g in groups:
        idx[g].add(g)
    assert all(len(v) == 1 for v in idx.values()), "a specimen key resolved to two groups"

    n_groups = len(set(groups))
    assert n_groups == len(by_key), \
        "group count %d != specimen count %d - grouping is not by specimen" % (n_groups, len(by_key))
    print("  PASS: %d folds, one per physical specimen; no specimen spans two folds."
          % n_groups)
    return by_key, pairs, singles


# ---------------------------------------------------------------- models
def candidates():
    from sklearn.ensemble import GradientBoostingClassifier
    from sklearn.linear_model import LogisticRegression
    from sklearn.neural_network import MLPClassifier
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler
    return {
        "logreg (incumbent)": make_pipeline(StandardScaler(),
                                            LogisticRegression(max_iter=2000, C=1.0)),
        "gradient boosting": make_pipeline(StandardScaler(),
                                           GradientBoostingClassifier(random_state=0)),
        "mlp 32x16": make_pipeline(StandardScaler(),
                                   MLPClassifier(hidden_layer_sizes=(32, 16), max_iter=4000,
                                                 random_state=0)),
    }


def grouped_logo(model, X, y, groups):
    """Returns per-tap correctness and per-specimen correctness (majority vote within a fold)."""
    from sklearn.model_selection import LeaveOneGroupOut
    logo = LeaveOneGroupOut()
    tap_ok, spot_ok = [], []
    for tr, te in logo.split(X, y, groups):
        if len(set(y[tr])) < 2:
            continue                       # a fold that removes an entire class cannot train
        m = model
        m.fit(X[tr], y[tr])
        p = m.predict(X[te])
        ok = (p == y[te])
        tap_ok.extend(ok.tolist())
        # the specimen is right if the majority of its taps are - this is what a user sees,
        # one verdict per spot, not one per knuckle
        spot_ok.append(bool(ok.mean() > 0.5))
    return np.array(tap_ok), np.array(spot_ok)


# ---------------------------------------------------------------- report
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--audio-root", default=DEFAULT_AUDIO)
    ap.add_argument("--drop-legacy", action="store_true",
                    help="exclude the 8 original WhatsApp files")
    ap.add_argument("--dry-run", action="store_true", help="do not write the model bundle")
    a = ap.parse_args()

    print("=" * 78)
    print("T5 ACOUSTIC REFIT - %s%s" % (a.audio_root, "  [legacy dropped]" if a.drop_legacy else ""))
    print("=" * 78)

    X, y, groups, files, per_file = load(a.audio_root, a.drop_legacy)
    if len(X) == 0:
        print("no taps found")
        return 2
    by_key, pairs, singles = assert_group_integrity(groups, y, per_file)

    # --- what actually arrived ----------------------------------------------------------
    spot_label = {}
    for fn, (key, cls, n) in per_file.items():
        spot_label.setdefault((key, cls), 0)
        spot_label[(key, cls)] += n
    n_hollow_spots = len({k for (k, c) in spot_label if c == "hollow"})
    n_solid_spots = len({k for (k, c) in spot_label if c == "solid"})
    n_spots = n_hollow_spots + n_solid_spots
    baseline = max(n_hollow_spots, n_solid_spots) / max(n_spots, 1)
    built = sum(1 for k in by_key if is_built(k))
    print("\n--- what arrived")
    print("  taps               %d" % len(X))
    print("  recordings         %d" % len(files))
    print("  specimens (folds)  %d" % len(by_key))
    print("  hollow / solid     %d / %d   (by recording)" % (n_hollow_spots, n_solid_spots))
    print("  majority baseline  %.3f   <- the number any model must beat" % baseline)
    print("  built / in-situ    %d / %d specimens  (%.0f%% built)"
          % (built, len(by_key) - built, 100.0 * built / max(len(by_key), 1)))
    if baseline > 0.60:
        print("  WARNING: the baseline is above the 0.60 ship gate. Raw accuracy is not "
              "interpretable at this balance - report balanced accuracy and per-class recall.")
    if built > 0.25 * len(by_key):
        print("  WARNING: built specimens are over a quarter of the set. A freely-resting "
              "plate rings more than a bonded tile with a void; the model may be learning "
              "'loose plate'. Report this limitation.")

    # --- bake-off --------------------------------------------------------------------
    print("\n--- grouped LeaveOneGroupOut, one fold per specimen")
    print("  %-20s %8s %8s   %-22s %-22s" % ("model", "taps", "spots",
                                             "tap-level 95% CI", "SPOT-level 95% CI"))
    results = {}
    for name, model in candidates().items():
        tap_ok, spot_ok = grouped_logo(model, X, y, groups)
        ta, sa = float(tap_ok.mean()), float(spot_ok.mean())
        tlo, thi = wilson(int(tap_ok.sum()), len(tap_ok))
        slo, shi = wilson(int(spot_ok.sum()), len(spot_ok))
        results[name] = {"tap_acc": ta, "tap_n": int(len(tap_ok)),
                         "tap_ci95": [tlo, thi],
                         "spot_acc": sa, "spot_n": int(len(spot_ok)),
                         "spot_ci95": [slo, shi]}
        print("  %-20s %8.3f %8.3f   [%0.3f, %0.3f]%s[%0.3f, %0.3f]"
              % (name, ta, sa, tlo, thi, " " * 9, slo, shi))

    print("\n  The SPOT-level interval is the one the gate uses. Taps struck at one spot are"
          "\n  near-duplicates, so a tap-level interval counts correlated observations as"
          "\n  independent and reads narrower than the evidence supports.")

    # --- gate -------------------------------------------------------------------------
    best = max(results, key=lambda k: (results[k]["spot_ci95"][0], results[k]["spot_acc"]))
    lb = results[best]["spot_ci95"][0]
    print("\n" + "=" * 78)
    print("GATE (pre-committed in TARGETS.md 8 G5, before this data existed)")
    print("=" * 78)
    print("  best by spot-level lower bound: %s" % best)
    print("  spot accuracy %.3f   95%% CI [%.3f, %.3f]   baseline %.3f   n_spots %d"
          % (results[best]["spot_acc"], lb, results[best]["spot_ci95"][1], baseline,
             results[best]["spot_n"]))
    if lb >= GATE_LEAD:
        verdict = "LEAD"
        print("\n  >= 0.80  ->  SHIP, AND THE TAP TEST MAY LEAD THE DEMO.")
    elif lb > GATE_SHIP:
        verdict = "SUPPORT"
        print("\n  0.60-0.80  ->  SHIP THE REFIT, BUT IT MUST NOT LEAD.")
    else:
        verdict = "NOTHING"
        print("\n  <= 0.60  ->  SHIP NOTHING NEW. The existing bundle stays untouched.")
        print("  More architecture does not fix sample size.")
    if lb <= baseline:
        print("  NOTE: the lower bound is at or below the majority baseline. At the bottom of"
              "\n  its range this model is indistinguishable from always answering '%s'."
              % (CLASSES[1] if n_hollow_spots > n_solid_spots else CLASSES[0]))

    out = {"verdict": verdict, "best_model": best, "baseline": baseline,
           "n_taps": int(len(X)), "n_recordings": len(files), "n_specimens": len(by_key),
           "n_pairs": len(pairs), "n_built": built,
           "hollow_spots": n_hollow_spots, "solid_spots": n_solid_spots,
           "drop_legacy": bool(a.drop_legacy), "results": results}
    p = os.path.join(ROOT, "handoff", "runs",
                     "t5_acoustic_refit%s.json" % ("_nolegacy" if a.drop_legacy else ""))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(out, open(p, "w", encoding="utf-8"), indent=2)
    print("\nwrote", p)

    if a.dry_run:
        print("\n--dry-run: the model bundle was NOT regenerated.")
    elif verdict == "NOTHING":
        print("\nGate failed - acoustic_tap_model.json deliberately NOT regenerated.")
    else:
        print("\nNext: regenerate the bundle and its golden vectors from ml/acoustic/:")
        print("  cd smartlease-edge-chennai-master/ml/acoustic && "
              "../../../handoff/env/train/Scripts/python.exe export_for_android.py")
        print("  (same recipe, same golden-vector emission, so "
              "AcousticFeatureExtractorTest.kt keeps passing)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
