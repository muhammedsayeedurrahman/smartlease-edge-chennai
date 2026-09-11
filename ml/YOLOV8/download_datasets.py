"""
Re-download the five Roboflow source datasets into the directories the merge script expects.

The image sets are deliberately not tracked in git (9,043 files, a 118 MB archive), so this
is how a fresh clone gets them back.

Credentials
-----------
The API key is read from the environment and is never written to the repo:

    # PowerShell, current session only
    $env:ROBOFLOW_API_KEY = "<your key>"
    py -3 ml/YOLOV8/download_datasets.py

    # or persist it in a gitignored file at the repo root
    echo ROBOFLOW_API_KEY=<your key> > .env.local

Get the key from https://app.roboflow.com/settings/api (Private API Key). Treat it like a
password: it can read and write every project in your workspace. If it ever lands in a
commit, a screenshot or a chat log, revoke and regenerate it on that settings page.
"""

import argparse
import os
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

# (target directory relative to repo root, workspace, project, version)
DATASETS = [
    ("ml/YOLOV8/1",          "pothole-detection-h9muz",   "infrastructure-monitoring",       1),
    ("ml/YOLOV8/2",          "kamar",                     "structural-defects",              1),
    ("ml/YOLOV8/3",          "image-classification-jf9vm", "building-anomalies",             1),
    ("ml/YOLOV8/concrete",   "ammlworkspace",             "concrete-defects-gn6lu",          1),
    ("ml/YOLOV8/paint-peel", "image-classification-jf9vm", "detection-ghu6i",                1),
    # Used by the older pipeline, not by unified_defects.
    ("ml/data/dataset/internal-defect", "chew-poh-yee", "internal-wall-finishing-defects",   5),
]


def read_api_key() -> str:
    key = os.environ.get("ROBOFLOW_API_KEY", "").strip()
    if key:
        return key

    env_file = REPO_ROOT / ".env.local"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("ROBOFLOW_API_KEY=") and not line.startswith("#"):
                return line.split("=", 1)[1].strip().strip('"').strip("'")

    sys.exit(
        "ROBOFLOW_API_KEY is not set.\n\n"
        "  PowerShell:  $env:ROBOFLOW_API_KEY = \"<your key>\"\n"
        "  or write it to .env.local (gitignored) as ROBOFLOW_API_KEY=<your key>\n\n"
        "Key: https://app.roboflow.com/settings/api"
    )


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--format", default="yolov8",
                    help="Roboflow export format (yolov8 covers detection and instance seg)")
    ap.add_argument("--only", default=None,
                    help="Substring filter, e.g. --only concrete")
    ap.add_argument("--force", action="store_true",
                    help="Re-download even if the directory already has images")
    args = ap.parse_args()

    try:
        from roboflow import Roboflow
    except ImportError:
        sys.exit("roboflow SDK missing. Install it:  py -3 -m pip install roboflow")

    rf = Roboflow(api_key=read_api_key())

    for rel_dir, workspace, project, version in DATASETS:
        if args.only and args.only.lower() not in f"{rel_dir} {project}".lower():
            continue

        target = REPO_ROOT / rel_dir
        existing = len(list(target.rglob("*.jpg"))) if target.exists() else 0
        if existing and not args.force:
            print(f"[skip]  {rel_dir}  ({existing} images already present; --force to redo)")
            continue

        print(f"[get ]  {rel_dir}  <- {workspace}/{project} v{version}")
        try:
            ds = (
                rf.workspace(workspace)
                .project(project)
                .version(version)
                .download(args.format, location=str(target), overwrite=True)
            )
            count = len(list(Path(ds.location).rglob("*.jpg")))
            print(f"[ok  ]  {rel_dir}  {count} images")
        except Exception as exc:  # noqa: BLE001 - report and continue to the next dataset
            print(f"[FAIL]  {rel_dir}: {exc}")

    print(
        "\nNext:\n"
        "  py -3 ml/YOLOV8/merge_and_zip_datasets.py   # rebuild unified_defects/\n"
        "  py -3 ml/YOLOV8/balance_dataset.py          # class balancing"
    )


if __name__ == "__main__":
    main()
