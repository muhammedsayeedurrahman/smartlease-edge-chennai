from __future__ import annotations

import sqlite3

from tests.conftest import API, digest_for, report_payload


def test_create_report_happy_path(client):
    payload = report_payload()

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 201
    body = response.json()
    assert body["reportId"] == payload["reportId"]
    assert body["digestSha256"] == payload["digestSha256"]
    assert isinstance(body["serverReceivedAtEpochMs"], int)
    assert body["serverReceivedAtEpochMs"] > 0


def test_duplicate_post_with_identical_digest_is_idempotent(client):
    payload = report_payload()

    first = client.post(f"{API}/reports", json=payload)
    second = client.post(f"{API}/reports", json=payload)

    assert first.status_code == 201
    assert second.status_code == 200
    assert second.json()["reportId"] == payload["reportId"]
    assert second.json()["digestSha256"] == payload["digestSha256"]
    # Idempotent: the original receipt time is preserved, not overwritten.
    assert second.json()["serverReceivedAtEpochMs"] == first.json()["serverReceivedAtEpochMs"]


def test_duplicate_post_with_different_digest_is_a_tamper_signal(client):
    payload = report_payload()
    client.post(f"{API}/reports", json=payload)

    tampered = dict(payload, digestSha256=digest_for("a-different-set-of-findings"))
    response = client.post(f"{API}/reports", json=tampered)

    assert response.status_code == 409
    body = response.json()
    assert body["error"]["code"] == "digest_mismatch"


def test_racing_duplicate_create_is_idempotent_instead_of_a_500(client, monkeypatch):
    """A row inserted after the route's first read must be treated as a retry."""
    payload = report_payload()
    repository = client.app.state.report_repository
    original_insert = repository.insert_report

    def concurrent_winner(record):
        original_insert(record)
        raise sqlite3.IntegrityError("UNIQUE constraint failed: reports.report_id")

    monkeypatch.setattr(repository, "insert_report", concurrent_winner)

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 200
    assert response.json()["reportId"] == payload["reportId"]
    assert response.json()["digestSha256"] == payload["digestSha256"]
    assert response.json()["reportToken"] is None


def test_deduction_exceeding_deposit_is_rejected(client):
    payload = report_payload(depositRupees=10_000, totalDeductionRupees=10_001)

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 422
    assert "error" in response.json()


def test_deduction_equal_to_deposit_is_allowed(client):
    payload = report_payload(depositRupees=10_000, totalDeductionRupees=10_000)

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 201


def test_malformed_digest_is_rejected(client):
    payload = report_payload(digestSha256="not-a-valid-digest")

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "validation_error"


def test_uppercase_digest_is_rejected(client):
    payload = report_payload(digestSha256=digest_for("x").upper())

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 422


def test_non_uuid_report_id_is_rejected(client):
    payload = report_payload(reportId="not-a-uuid")

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 422


def test_missing_required_field_is_rejected(client):
    payload = report_payload()
    del payload["propertyRef"]

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 422


def test_negative_findings_count_is_rejected(client):
    payload = report_payload(findingsCount=-1)

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 422


def test_invalid_session_type_is_rejected(client):
    payload = report_payload(sessionType="MOVE_SIDEWAYS")

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 422


def test_unexpected_extra_field_is_rejected(client):
    """Guards the no-photos-ever contract: unknown fields (e.g. a smuggled
    image payload) are refused rather than silently accepted."""
    payload = report_payload()
    payload["photoBase64"] = "not-actually-a-photo-but-should-still-be-rejected"

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 422


def test_non_json_content_type_is_rejected(client):
    payload = report_payload()

    response = client.post(
        f"{API}/reports",
        content=b"binary-looking-payload",
        headers={"content-type": "image/jpeg"},
    )

    assert response.status_code == 415
    assert response.json()["error"]["code"] == "unsupported_content_type"


def test_oversized_payload_is_rejected(client):
    huge_property_ref = "x" * 100_000
    payload = report_payload(propertyRef=huge_property_ref)

    response = client.post(f"{API}/reports", json=payload)

    assert response.status_code == 413
    assert response.json()["error"]["code"] == "payload_too_large"
