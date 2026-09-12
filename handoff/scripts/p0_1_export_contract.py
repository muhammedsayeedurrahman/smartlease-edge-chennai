# -*- coding: utf-8 -*-
"""
P0.1 — export contract gate. Run this before anything else; it can invalidate the plan.

The app pins org.pytorch:pytorch_android_lite:1.13.0, so everything we ship must be a
bytecode version that runtime accepts. This script:

  1. reads N from the known-good artifact already running on the phone
  2. traces a toy conv net under torch 2.x and saves it for the lite interpreter
  3. records the version torch 2.x emits by default
  4. backports to N and confirms
  5. reloads the backported file and runs a forward pass

Step 5 under torch 2.x proves the file is well-formed. It does NOT prove 1.13.0 accepts it —
a newer runtime reads older bytecode by design, so this direction is the easy one. The real
proxy is the export/ venv on torch 1.13.1: see p0_1_verify_on_1131.py.

WINDOWS BUG, worked around here and worth knowing about:
torch's C++ PytorchStreamReader fails on any path containing a SPACE — including an absolute
one — with "PytorchStreamReader failed reading zip archive: failed finding central
directory ... high likelihood that your checkpoint file is corrupted". The file is NOT
corrupted; the workspace is "IQOO Hackathon". Passing an open file handle works, and so does
a space-free path. Every torch file operation below runs inside a space-free temp directory.
"""
import json
import os
import shutil
import sys
import tempfile

import torch
import torch.nn as nn
from torch.jit.mobile import (
    _backport_for_mobile,
    _get_model_bytecode_version,
    _load_for_lite_interpreter,
)
from torch.utils.mobile_optimizer import optimize_for_mobile

HERE = os.path.abspath(os.path.dirname(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
KNOWN_GOOD = os.path.join(
    ROOT, "smartlease-edge-chennai-master", "app", "src", "main", "assets", "yolov8n_seg.ptl"
)
REPORT = os.path.join(HERE, "p0_1_result.json")

# Space-free scratch: see the module docstring.
WORK = os.path.join(tempfile.gettempdir(), "slx_p0")
os.makedirs(WORK, exist_ok=True)
TOY_NATIVE = os.path.join(WORK, "toy_native.ptl")
TOY_BACKPORTED = os.path.join(WORK, "toy_backported.ptl")


def bytecode_version(path):
    """Robust against the space-in-path bug: a file handle always works."""
    with open(path, "rb") as fh:
        return _get_model_bytecode_version(fh)


class Toy(nn.Module):
    """Two convs and a tuple return — same output-shape family as the real segmentation model."""

    def __init__(self):
        super().__init__()
        self.a = nn.Conv2d(3, 8, 3, stride=2, padding=1)
        self.b = nn.Conv2d(8, 4, 3, stride=2, padding=1)
        self.act = nn.SiLU()

    def forward(self, x):
        h = self.act(self.a(x))
        h = self.act(self.b(h))
        return h.flatten(2), h


def main():
    r = {"torch": torch.__version__, "workspace_has_space": " " in ROOT}
    print("torch", torch.__version__)

    if not os.path.exists(KNOWN_GOOD):
        print("FATAL: known-good artifact missing:", KNOWN_GOOD)
        return 2

    n = bytecode_version(KNOWN_GOOD)
    r["N_known_good"] = n
    r["known_good_bytes"] = os.path.getsize(KNOWN_GOOD)
    print("N (known-good yolov8n_seg.ptl) =", n)

    m = Toy().eval()
    ex = torch.randn(1, 3, 64, 64)
    with torch.no_grad():
        traced = torch.jit.trace(m, ex)
    traced = optimize_for_mobile(traced)
    traced._save_for_lite_interpreter(TOY_NATIVE)
    native = bytecode_version(TOY_NATIVE)
    r["native_version_torch2x"] = native
    print("torch 2.x emits bytecode version:", native)

    if native == n:
        r["backport_needed"] = False
        shutil.copyfile(TOY_NATIVE, TOY_BACKPORTED)
        print("no backport needed")
    else:
        r["backport_needed"] = True
        try:
            ok = _backport_for_mobile(TOY_NATIVE, TOY_BACKPORTED, n)
            r["backport_returned"] = bool(ok)
            print("_backport_for_mobile ->", ok)
        except Exception as e:
            r["backport_error"] = "%s: %s" % (type(e).__name__, e)
            r["verdict_under_torch2x"] = "FAIL"
            print("BACKPORT FAILED:", e)
            json.dump(r, open(REPORT, "w", encoding="utf-8"), indent=2)
            return 1

    got = bytecode_version(TOY_BACKPORTED)
    r["backported_version"] = got
    r["version_matches_N"] = got == n
    print("backported file version:", got)

    # torch.jit._load_for_lite_interpreter was moved to torch.jit.mobile in torch 2.x.
    with open(TOY_BACKPORTED, "rb") as fh:
        lite = _load_for_lite_interpreter(fh)
    with torch.no_grad():
        out = lite(ex)
    r["forward_ok"] = True
    r["forward_is_tuple"] = isinstance(out, (tuple, list))
    r["forward_out_shapes"] = [list(t.shape) for t in out]
    print("forward OK, outputs:", r["forward_out_shapes"], "tuple:", r["forward_is_tuple"])

    r["verdict_under_torch2x"] = "PASS" if (r["version_matches_N"] and r["forward_ok"]) else "FAIL"
    r["artifact_for_1131_check"] = TOY_BACKPORTED
    r["note"] = (
        "Loading under torch 2.x proves well-formedness, not 1.13.0 acceptance. "
        "Run p0_1_verify_on_1131.py in the export/ venv for the real proxy."
    )
    json.dump(r, open(REPORT, "w", encoding="utf-8"), indent=2)
    print("\nVERDICT (torch 2.x half):", r["verdict_under_torch2x"])
    print("toy artifact for the 1.13.1 check:", TOY_BACKPORTED)
    print("wrote", REPORT)
    return 0 if r["verdict_under_torch2x"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
