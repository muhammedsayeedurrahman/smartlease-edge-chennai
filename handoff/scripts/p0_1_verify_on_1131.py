# -*- coding: utf-8 -*-
"""
P0.1, second half — the only proxy for the phone that exists on this machine.

Runs under torch 1.13.1 CPU (handoff/env/export). The Android runtime is
org.pytorch:pytorch_android_lite:1.13.0, whose native library is built from the same
1.13 source tree, so if this loads and runs a forward pass, the phone almost certainly can.

What this catches that the torch-2.x half cannot: **operator coverage**. Matching the
bytecode version number only proves the container format is readable. A model traced under
torch 2.12 can still reference an operator that does not exist in the 1.13 runtime, or one
whose schema changed — and that fails at load or at first forward, on the phone, in front of
a judge. This is the check for that.

Usage:
    handoff/env/export/Scripts/python.exe handoff/scripts/p0_1_verify_on_1131.py <file.ptl> [...]

Defaults to the toy artifact from p0_1_export_contract.py plus the known-good model.
"""
import json
import os
import sys
import tempfile

import torch

HERE = os.path.abspath(os.path.dirname(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
KNOWN_GOOD = os.path.join(
    ROOT, "smartlease-edge-chennai-master", "app", "src", "main", "assets", "yolov8n_seg.ptl"
)
TOY = os.path.join(tempfile.gettempdir(), "slx_p0", "toy_backported.ptl")
REPORT = os.path.join(HERE, "p0_1_result_1131.json")


def version_of(path):
    # Space-in-path bug again — a handle sidesteps it on every torch version.
    from torch.jit.mobile import _get_model_bytecode_version
    with open(path, "rb") as fh:
        return _get_model_bytecode_version(fh)


def check(path, example):
    out = {"path": path, "exists": os.path.exists(path)}
    if not out["exists"]:
        out["verdict"] = "MISSING"
        return out
    out["bytes"] = os.path.getsize(path)
    try:
        out["bytecode_version"] = version_of(path)
    except Exception as e:
        out["bytecode_version_error"] = "%s: %s" % (type(e).__name__, e)

    try:
        with open(path, "rb") as fh:
            m = torch.jit.mobile._load_for_lite_interpreter(fh)
        out["load_ok"] = True
    except Exception as e:
        out["load_ok"] = False
        out["load_error"] = "%s: %s" % (type(e).__name__, str(e)[:400])
        out["verdict"] = "FAIL(load)"
        return out

    try:
        with torch.no_grad():
            y = m(example)
        out["forward_ok"] = True
        out["is_tuple"] = isinstance(y, (tuple, list))
        out["out_shapes"] = [list(t.shape) for t in (y if isinstance(y, (tuple, list)) else [y])]
        out["verdict"] = "PASS"
    except Exception as e:
        out["forward_ok"] = False
        out["forward_error"] = "%s: %s" % (type(e).__name__, str(e)[:400])
        out["verdict"] = "FAIL(forward)"
    return out


def main():
    print("torch", torch.__version__, "| python", sys.version.split()[0])
    r = {"torch": torch.__version__, "python": sys.version.split()[0], "checks": []}

    targets = sys.argv[1:]
    if not targets:
        targets = [TOY, KNOWN_GOOD]

    for t in targets:
        # The toy takes 64x64; the real segmentation model takes 640x640.
        ex = torch.randn(1, 3, 640, 640) if "yolov8n_seg" in os.path.basename(t) else torch.randn(1, 3, 64, 64)
        c = check(t, ex)
        r["checks"].append(c)
        print("\n--", os.path.basename(t), "--")
        for k, v in c.items():
            if k != "path":
                print("   %-20s %s" % (k, v))

    verdicts = [c.get("verdict") for c in r["checks"] if c.get("verdict") != "MISSING"]
    r["overall"] = "PASS" if verdicts and all(v == "PASS" for v in verdicts) else "FAIL"
    json.dump(r, open(REPORT, "w", encoding="utf-8"), indent=2)
    print("\nOVERALL (torch 1.13.1 proxy):", r["overall"])
    print("wrote", REPORT)
    return 0 if r["overall"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
