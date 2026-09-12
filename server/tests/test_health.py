from __future__ import annotations

from tests.conftest import API


def test_health_returns_ok_and_version(client):
    response = client.get(f"{API}/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert isinstance(body["version"], str) and body["version"]
