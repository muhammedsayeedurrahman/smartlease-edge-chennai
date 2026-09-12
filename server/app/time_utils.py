"""Small, isolated time helper so it can be monkeypatched from tests."""

from __future__ import annotations

import time


def now_epoch_ms() -> int:
    return int(time.time() * 1000)
