from __future__ import annotations

import hashlib
import uuid

import pytest
from fastapi.testclient import TestClient

from app.main import create_app

API = "/api/v1"


@pytest.fixture()
def client(tmp_path):
    """A TestClient backed by its own fresh SQLite file, so tests never share state."""
    db_path = str(tmp_path / "test.db")
    app = create_app(database_path=db_path)
    with TestClient(app) as test_client:
        yield test_client


def digest_for(seed: str) -> str:
    """A deterministic, valid-looking 64-hex-char digest derived from `seed`."""
    return hashlib.sha256(seed.encode("utf-8")).hexdigest()


def new_report_id() -> str:
    return str(uuid.uuid4())


def report_payload(**overrides) -> dict:
    payload = {
        "reportId": new_report_id(),
        "propertyRef": "flat-12b",
        "sessionType": "MOVE_IN",
        "createdAtEpochMs": 1_700_000_000_000,
        "digestSha256": digest_for("findings-v1"),
        "appVersion": "1.0.0",
        "findingsCount": 3,
        "depositRupees": 50_000,
        "totalDeductionRupees": 0,
    }
    payload.update(overrides)
    return payload


def countersign_payload(**overrides) -> dict:
    payload = {
        "signerRole": "TENANT",
        "signatureSha256": digest_for("signature-v1"),
        "signedAtEpochMs": 1_700_000_100_000,
    }
    payload.update(overrides)
    return payload
