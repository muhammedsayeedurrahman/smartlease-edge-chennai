from __future__ import annotations

from tests.conftest import API, digest_for, report_payload


def test_verify_matching_digest(client):
    payload = report_payload()
    client.post(f"{API}/reports", json=payload)

    response = client.get(
        f"{API}/reports/{payload['reportId']}/verify",
        params={"digest": payload["digestSha256"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["reportId"] == payload["reportId"]
    assert body["matches"] is True


def test_verify_mismatched_digest(client):
    payload = report_payload()
    client.post(f"{API}/reports", json=payload)

    response = client.get(
        f"{API}/reports/{payload['reportId']}/verify",
        params={"digest": digest_for("some-other-findings")},
    )

    assert response.status_code == 200
    assert response.json()["matches"] is False


def test_verify_unknown_report_is_404(client):
    response = client.get(
        f"{API}/reports/{'0' * 8}-0000-0000-0000-000000000000/verify",
        params={"digest": digest_for("whatever")},
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "report_not_found"


def test_verify_malformed_digest_query_param_is_rejected(client):
    payload = report_payload()
    client.post(f"{API}/reports", json=payload)

    response = client.get(
        f"{API}/reports/{payload['reportId']}/verify",
        params={"digest": "not-hex"},
    )

    assert response.status_code == 422
