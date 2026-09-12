from __future__ import annotations

from tests.conftest import API, report_payload


def test_get_report_returns_stored_record(client):
    payload = report_payload()
    client.post(f"{API}/reports", json=payload)

    response = client.get(f"{API}/reports/{payload['reportId']}")

    assert response.status_code == 200
    body = response.json()
    assert body["reportId"] == payload["reportId"]
    assert body["propertyRef"] == payload["propertyRef"]
    assert body["sessionType"] == payload["sessionType"]
    assert body["digestSha256"] == payload["digestSha256"]
    assert body["findingsCount"] == payload["findingsCount"]
    assert body["depositRupees"] == payload["depositRupees"]
    assert body["totalDeductionRupees"] == payload["totalDeductionRupees"]
    assert body["countersignatures"] == []


def test_get_unknown_report_is_404(client):
    response = client.get(f"{API}/reports/{'0' * 8}-0000-0000-0000-000000000000")

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "report_not_found"
