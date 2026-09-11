"""
Run the trained tap classifier over a recording and print a per-tap verdict.

Use this to sanity-check the model against audio it has never seen -- in particular a
coin-tapped recording when the model was trained on knuckle taps. If confidence collapses
toward 50% or the verdicts disagree with ground truth, that is domain shift, not bad luck,
and the fix is retraining on the excitation you will actually demo with.

    py -3 ml/acoustic/predict_tap.py path/to/recording.ogg --expect hollow
"""

import argparse
import warnings
from pathlib import Path

warnings.filterwarnings("ignore")

import joblib
import numpy as np

from train_tap_classifier import extract_features, segment_taps  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("audio", help="Recording to classify (.ogg/.wav/.m4a)")
    ap.add_argument("--model", default="ml/models/acoustic_tap_clf.joblib")
    ap.add_argument("--expect", choices=["solid", "hollow"], default=None,
                    help="Ground truth, if known -- prints a hit/miss tally")
    args = ap.parse_args()

    # joblib.load unpickles, which is unsafe on untrusted input. This path only ever reads
    # an artifact this repo produced locally via train_tap_classifier.py -- it is a build
    # output, not user-supplied data. Do not point --model at a file from outside the repo.
    bundle = joblib.load(args.model)
    model = bundle["model"]
    classes = bundle["classes"]

    print("Model trained on %d taps, grouped CV %.1f%% (95%% CI %.1f-%.1f%%)" % (
        bundle["trained_on"]["n_taps"],
        bundle["grouped_logo_accuracy"] * 100,
        bundle["grouped_logo_ci95"][0] * 100,
        bundle["grouped_logo_ci95"][1] * 100,
    ))

    clips = segment_taps(args.audio)
    if not clips:
        print("No tap onsets detected in %s" % args.audio)
        return

    print("\n%d tap(s) detected in %s\n" % (len(clips), Path(args.audio).name))
    hits = 0
    for i, clip in enumerate(clips, start=1):
        feats = extract_features(clip).reshape(1, -1)
        pred = int(model.predict(feats)[0])
        label = classes[pred]
        if hasattr(model, "predict_proba"):
            conf = float(model.predict_proba(feats)[0][pred])
            conf_txt = "%5.1f%%" % (conf * 100)
        else:
            conf_txt = "  n/a"
        mark = ""
        if args.expect:
            ok = label == args.expect
            hits += int(ok)
            mark = "  OK" if ok else "  MISS"
        print("  tap %2d -> %-7s confidence %s%s" % (i, label, conf_txt, mark))

    if args.expect:
        print("\n%d/%d correct against expected '%s'" % (hits, len(clips), args.expect))
        if hits < len(clips):
            print("Misses on unseen audio are the signal that matters -- far more than the")
            print("training accuracy. If these were coin taps and the model saw only knuckle")
            print("taps, retrain on coin audio rather than tuning thresholds.")


if __name__ == "__main__":
    main()
