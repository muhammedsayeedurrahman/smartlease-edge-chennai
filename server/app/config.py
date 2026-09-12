"""Runtime configuration, sourced from environment variables with safe defaults.

No secrets live here: this service only ever stores a digest and small
metadata, never inspection media, so there is nothing sensitive to configure
beyond where the SQLite file lives and how the server describes itself.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache

DEFAULT_SERVER_VERSION = "0.1.0"
DEFAULT_DATABASE_PATH = "smartlease_reports.db"
# Report-custody payloads are tiny (a digest plus a handful of small fields).
# Anything meaningfully larger than this is almost certainly not a valid
# request and is rejected outright -- this is a defense-in-depth backstop
# against accidentally attaching image/video/audio payloads, on top of the
# schema-level `extra="forbid"` validation.
DEFAULT_MAX_REQUEST_BODY_BYTES = 16_384

API_V1_PREFIX = "/api/v1"


@dataclass(frozen=True)
class Settings:
    server_version: str
    database_path: str
    max_request_body_bytes: int


@lru_cache
def get_settings() -> Settings:
    return Settings(
        server_version=os.environ.get("SMARTLEASE_SERVER_VERSION", DEFAULT_SERVER_VERSION),
        database_path=os.environ.get("SMARTLEASE_DATABASE_PATH", DEFAULT_DATABASE_PATH),
        max_request_body_bytes=int(
            os.environ.get(
                "SMARTLEASE_MAX_REQUEST_BODY_BYTES", DEFAULT_MAX_REQUEST_BODY_BYTES
            )
        ),
    )
