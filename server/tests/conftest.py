from __future__ import annotations

import hashlib
import uuid

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app

API = "/api/v1"

TEST_API_KEY = "test-api-key-not-a-real-secret"
# High enough that no ordinary test trips it; the rate-limit tests build their
# own app with a limit of 1 rather than making 60 requests to prove the point.
TEST_RATE_LIMIT_PER_MINUTE = 10_000


def build_settings(**overrides) -> Settings:
    values = {
        "server_version": "test",
        "database_path": ":memory:",
        "max_request_body_bytes": 16_384,
        "api_key": TEST_API_KEY,
        "rate_limit_per_minute": TEST_RATE_LIMIT_PER_MINUTE,
    }
    values.update(overrides)
    return Settings(**values)


def make_client(tmp_path, **setting_overrides) -> TestClient:
    """A TestClient with its own SQLite file and its own explicit Settings.

    The API key header is attached by default so the bulk of the suite tests
    behaviour rather than re-asserting authentication on every request; the
    tests in test_auth.py deliberately send requests without it.
    """
    app = create_app(
        database_path=str(tmp_path / "test.db"),
        settings=build_settings(**setting_overrides),
    )
    return TestClient(app, headers={"X-API-Key": TEST_API_KEY})


@pytest.fixture()
def client(tmp_path):
    """A TestClient backed by its own fresh SQLite file, so tests never share state."""
    with make_client(tmp_path) as test_client:
        yield test_client


def create_report(client, **overrides) -> tuple[dict, str]:
    """Create a report and return (response body, per-report token).

    Almost every test needs the token to do anything with the report it just
    created, so getting it here keeps the token handling explicit without
    repeating it in each test.
    """
    response = client.post(f"{API}/reports", json=report_payload(**overrides))
    assert response.status_code == 201, response.text
    body = response.json()
    return body, body["reportToken"]


def token_header(token: str) -> dict:
    return {"X-Report-Token": token}


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
