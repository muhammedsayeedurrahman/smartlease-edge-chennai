from __future__ import annotations

from tests.conftest import API, create_report, report_payload, token_header


def test_get_report_returns_stored_record(client):
    payload = report_payload()
    _, token = create_report(client, **payload)

    response = client.get(
        f"{API}/reports/{payload['reportId']}", headers=token_header(token)
    )

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
    """404 before 403: an unknown id is not a report whose token could be checked.

    This does mean a caller holding only the API key can distinguish "no such
    report" from "report exists, wrong token". That existence oracle is a
    deliberate, documented trade-off (see SECURITY.md) -- collapsing both into
    404 would make a genuinely lost token indistinguishable from a typo in the
    report id during a live handover.
    """
    response = client.get(
        f"{API}/reports/{'0' * 8}-0000-0000-0000-000000000000",
        headers=token_header("irrelevant"),
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "report_not_found"
