from __future__ import annotations

from tests.conftest import API, countersign_payload, create_report, digest_for, token_header


def _create_report(client) -> tuple[str, dict]:
    """Returns the new report's id and the header block that authorizes it."""
    body, token = create_report(client)
    return body["reportId"], token_header(token)


def test_countersign_happy_path(client):
    report_id, auth = _create_report(client)
    payload = countersign_payload(signerRole="TENANT")

    response = client.post(
        f"{API}/reports/{report_id}/countersign", json=payload, headers=auth
    )

    assert response.status_code == 201
    body = response.json()
    assert body["reportId"] == report_id
    assert body["signerRole"] == "TENANT"
    assert body["signatureSha256"] == payload["signatureSha256"]


def test_both_roles_can_countersign_independently(client):
    report_id, auth = _create_report(client)

    tenant_response = client.post(
        f"{API}/reports/{report_id}/countersign",
        json=countersign_payload(signerRole="TENANT"),
        headers=auth,
    )
    landlord_response = client.post(
        f"{API}/reports/{report_id}/countersign",
        json=countersign_payload(
            signerRole="LANDLORD", signatureSha256=digest_for("landlord-sig")
        ),
        headers=auth,
    )

    assert tenant_response.status_code == 201
    assert landlord_response.status_code == 201

    record = client.get(f"{API}/reports/{report_id}", headers=auth).json()
    roles = {cs["signerRole"] for cs in record["countersignatures"]}
    assert roles == {"TENANT", "LANDLORD"}


def test_same_role_cannot_countersign_twice(client):
    report_id, auth = _create_report(client)
    client.post(
        f"{API}/reports/{report_id}/countersign",
        json=countersign_payload(signerRole="LANDLORD"),
        headers=auth,
    )

    response = client.post(
        f"{API}/reports/{report_id}/countersign",
        json=countersign_payload(
            signerRole="LANDLORD", signatureSha256=digest_for("second-attempt")
        ),
        headers=auth,
    )

    assert response.status_code == 409
    assert response.json()["error"]["code"] == "already_countersigned"


def test_countersign_unknown_report_is_404(client):
    response = client.post(
        f"{API}/reports/{'0' * 8}-0000-0000-0000-000000000000/countersign",
        json=countersign_payload(),
        headers=token_header("irrelevant"),
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "report_not_found"


def test_countersign_malformed_signature_is_rejected(client):
    report_id, auth = _create_report(client)

    response = client.post(
        f"{API}/reports/{report_id}/countersign",
        json=countersign_payload(signatureSha256="short-and-invalid"),
        headers=auth,
    )

    assert response.status_code == 422


def test_countersign_invalid_role_is_rejected(client):
    report_id, auth = _create_report(client)

    response = client.post(
        f"{API}/reports/{report_id}/countersign",
        json=countersign_payload(signerRole="AGENT"),
        headers=auth,
    )

    assert response.status_code == 422


def test_countersign_extra_field_is_rejected(client):
    report_id, auth = _create_report(client)
    payload = countersign_payload()
    payload["photoBase64"] = "smuggled"

    response = client.post(
        f"{API}/reports/{report_id}/countersign", json=payload, headers=auth
    )

    assert response.status_code == 422
