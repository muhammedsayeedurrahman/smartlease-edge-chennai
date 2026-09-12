"""Authentication and per-report authorization.

These tests exist because the first version of this service had neither. A
security review found that anyone who could reach the server could POST a
countersignature in a tenant's name -- the exact artefact the tamper-evidence
story rests on. Each test below pins one half of the fix in place.
"""

from __future__ import annotations

from fastapi.testclient import TestClient

from tests.conftest import (
    API,
    TEST_API_KEY,
    countersign_payload,
    create_report,
    make_client,
    report_payload,
    build_settings,
    token_header,
)
from app.main import create_app


def _no_key_client(tmp_path) -> TestClient:
    """A client that sends no X-API-Key at all."""
    client = make_client(tmp_path)
    client.headers.pop("X-API-Key", None)
    return client


def test_create_without_api_key_is_401(tmp_path):
    with _no_key_client(tmp_path) as client:
        response = client.post(f"{API}/reports", json=report_payload())

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "unauthorized"


def test_wrong_api_key_is_401(tmp_path):
    with make_client(tmp_path) as client:
        response = client.post(
            f"{API}/reports",
            json=report_payload(),
            headers={"X-API-Key": "not-the-configured-key"},
        )

    assert response.status_code == 401


def test_countersign_without_api_key_is_401(tmp_path):
    """The endpoint that matters most: no key, no signature, full stop."""
    with make_client(tmp_path) as client:
        report_id = create_report(client)[0]["reportId"]
        client.headers.pop("X-API-Key", None)
        response = client.post(
            f"{API}/reports/{report_id}/countersign", json=countersign_payload()
        )

    assert response.status_code == 401


def test_server_without_configured_key_refuses_report_traffic(tmp_path):
    """Fail closed, not open, when SMARTLEASE_API_KEY was never set.

    Serving openly would be the dangerous failure mode, so an unconfigured
    deployment returns 503 rather than accepting anonymous countersignatures.
    """
    app = create_app(
        database_path=str(tmp_path / "unconfigured.db"),
        settings=build_settings(api_key=None),
    )
    with TestClient(app) as client:
        response = client.post(
            f"{API}/reports",
            json=report_payload(),
            headers={"X-API-Key": "anything"},
        )

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "api_key_not_configured"


def test_health_is_reachable_without_api_key(tmp_path):
    """Liveness checks must not need the secret -- that is what they are for."""
    with _no_key_client(tmp_path) as client:
        response = client.get(f"{API}/health")

    assert response.status_code == 200


def test_create_issues_a_report_token_once(client):
    body, token = create_report(client)

    assert token
    # Token is a URL-safe secrets.token_urlsafe(32); never the report id.
    assert token != body["reportId"]
    assert len(token) >= 40


def test_resubmitting_the_same_report_does_not_reissue_the_token(client):
    payload = report_payload()
    first = client.post(f"{API}/reports", json=payload)
    second = client.post(f"{API}/reports", json=payload)

    assert first.status_code == 201
    assert first.json()["reportToken"]
    assert second.status_code == 200
    # Otherwise POSTing a known reportId twice would mint access to it.
    assert second.json()["reportToken"] is None


def test_get_report_without_report_token_is_403(client):
    report_id = create_report(client)[0]["reportId"]

    response = client.get(f"{API}/reports/{report_id}")

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "forbidden"


def test_get_report_with_another_reports_token_is_403(client):
    """A valid token for report A must not open report B."""
    first_id = create_report(client)[0]["reportId"]
    other_token = create_report(client)[1]

    response = client.get(
        f"{API}/reports/{first_id}", headers=token_header(other_token)
    )

    assert response.status_code == 403


def test_countersign_with_api_key_but_no_report_token_is_403(client):
    """The forgery path the security review found. It must stay closed."""
    body, token = create_report(client)
    report_id = body["reportId"]

    response = client.post(
        f"{API}/reports/{report_id}/countersign",
        json=countersign_payload(signerRole="TENANT"),
    )

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "forbidden"

    # Rejected, and no signature left behind: a 403 that still wrote the row
    # would be worse than no check at all.
    stored = client.get(f"{API}/reports/{report_id}", headers=token_header(token))
    assert stored.json()["countersignatures"] == []


def test_verify_needs_the_api_key_but_not_the_report_token(client):
    """Verify is the QR-scan path, so it is deliberately token-free.

    It returns one boolean about a digest the caller already holds and cannot
    change anything. The disclosure it does make -- that a given reportId
    exists -- is recorded in SECURITY.md.
    """
    payload = report_payload()
    create_report(client, **payload)

    response = client.get(
        f"{API}/reports/{payload['reportId']}/verify",
        params={"digest": payload["digestSha256"]},
    )

    assert response.status_code == 200
    assert response.json()["matches"] is True


def test_stored_token_is_hashed_not_plaintext(client, tmp_path):
    """A copy of the database must not hand over the tokens."""
    body, token = create_report(client)

    database_bytes = (tmp_path / "test.db").read_bytes()

    assert token.encode("utf-8") not in database_bytes
    assert body["reportId"].encode("utf-8") in database_bytes
