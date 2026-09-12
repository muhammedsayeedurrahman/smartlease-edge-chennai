"""Runtime configuration, sourced from environment variables with safe defaults.

The service stores only a digest and small metadata, never inspection media,
so the stored data is not sensitive. Access to it is: anyone who can call
`POST /reports/{id}/countersign` can forge the signature that the whole
tamper-evidence story rests on. `SMARTLEASE_API_KEY` is therefore the one
genuine secret here, it has no default, and the report endpoints fail closed
without it -- see `app.auth`.
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
# Deliberately generous: a real inspection uploads one report and up to two
# countersignatures, so anything near this ceiling is abuse or a broken retry
# loop, not a user.
DEFAULT_RATE_LIMIT_PER_MINUTE = 60

API_V1_PREFIX = "/api/v1"


@dataclass(frozen=True)
class Settings:
    server_version: str
    database_path: str
    max_request_body_bytes: int
    # None means "not configured", which makes every /reports route return 503
    # rather than serving anonymous traffic. There is no default on purpose.
    api_key: str | None
    rate_limit_per_minute: int


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
        api_key=(os.environ.get("SMARTLEASE_API_KEY") or "").strip() or None,
        rate_limit_per_minute=int(
            os.environ.get("SMARTLEASE_RATE_LIMIT_PER_MINUTE", DEFAULT_RATE_LIMIT_PER_MINUTE)
        ),
    )
