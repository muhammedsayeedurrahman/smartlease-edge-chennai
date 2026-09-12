"""Rate limiting.

The limiter is in-process and per-peer-address, which is a real constraint
rather than a detail: it does not survive a restart and does not coordinate
across replicas. These tests pin the behaviour it does provide.
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from tests.conftest import API, TEST_API_KEY, build_settings, report_payload
from app.main import create_app
from app.rate_limit import (
    IDLE_EVICTION_SECONDS,
    WINDOW_SECONDS,
    _SWEEP_THRESHOLD,
    RateLimiter,
    client_key_for,
)


def _limited_client(tmp_path, limit: int) -> TestClient:
    app = create_app(
        database_path=str(tmp_path / "limited.db"),
        settings=build_settings(rate_limit_per_minute=limit),
    )
    return TestClient(app, headers={"X-API-Key": TEST_API_KEY})


def test_requests_over_the_limit_are_429(tmp_path):
    with _limited_client(tmp_path, limit=1) as client:
        first = client.get(f"{API}/health")
        second = client.get(f"{API}/health")

    assert first.status_code == 200
    assert second.status_code == 429
    assert second.json()["error"]["code"] == "rate_limited"


def test_rate_limit_response_tells_the_client_when_to_retry(tmp_path):
    with _limited_client(tmp_path, limit=1) as client:
        client.get(f"{API}/health")
        response = client.get(f"{API}/health")

    assert response.headers["Retry-After"] == str(int(WINDOW_SECONDS))


def test_rate_limit_applies_to_report_endpoints_too(tmp_path):
    with _limited_client(tmp_path, limit=1) as client:
        client.get(f"{API}/health")
        response = client.post(f"{API}/reports", json=report_payload())

    assert response.status_code == 429


def test_limiter_runs_before_the_body_is_parsed(tmp_path):
    """An over-limit client is rejected without its payload being read.

    A malformed body would normally be a 422; here the 429 must win, which is
    what proves the limiter sits outside the parsing layers.
    """
    with _limited_client(tmp_path, limit=1) as client:
        client.get(f"{API}/health")
        response = client.post(f"{API}/reports", json={"nonsense": True})

    assert response.status_code == 429


def test_window_resets_after_sixty_seconds():
    limiter = RateLimiter(limit_per_minute=2)

    assert limiter.check("client-a", now=0.0) is True
    assert limiter.check("client-a", now=1.0) is True
    assert limiter.check("client-a", now=2.0) is False
    # A fresh window starts once the previous one has fully elapsed.
    assert limiter.check("client-a", now=WINDOW_SECONDS + 0.1) is True


def test_clients_are_counted_separately():
    limiter = RateLimiter(limit_per_minute=1)

    assert limiter.check("client-a", now=0.0) is True
    assert limiter.check("client-a", now=0.1) is False
    assert limiter.check("client-b", now=0.2) is True


def test_limit_must_be_positive():
    with pytest.raises(ValueError):
        RateLimiter(limit_per_minute=0)


def test_forwarded_for_header_cannot_be_used_to_dodge_the_limit(tmp_path):
    """X-Forwarded-For is attacker-controlled, so the limiter ignores it.

    If it keyed on that header, one client could present a new address per
    request and never be limited at all.
    """
    with _limited_client(tmp_path, limit=1) as client:
        client.get(f"{API}/health", headers={"X-Forwarded-For": "10.0.0.1"})
        response = client.get(f"{API}/health", headers={"X-Forwarded-For": "10.0.0.2"})

    assert response.status_code == 429


def test_client_key_falls_back_when_the_peer_is_unknown():
    assert client_key_for({"client": None}) == "unknown"
    assert client_key_for({"client": ("203.0.113.4", 51234)}) == "203.0.113.4"


def test_idle_clients_are_swept_so_the_bucket_map_stays_bounded():
    """Without this, a long-running process leaks one dict entry per client seen."""
    limiter = RateLimiter(limit_per_minute=5)
    for index in range(_SWEEP_THRESHOLD + 1):
        limiter.check(f"client-{index}", now=0.0)

    assert len(limiter._windows) > _SWEEP_THRESHOLD

    limiter.check("late-arrival", now=IDLE_EVICTION_SECONDS + 1.0)

    # Everything from the first burst has aged out; only the new caller remains.
    assert set(limiter._windows) == {"late-arrival"}
