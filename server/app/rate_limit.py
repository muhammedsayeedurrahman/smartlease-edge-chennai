"""Per-client request rate limiting, as ASGI middleware.

Scope, honestly: the counters live in this process's memory. They do not
survive a restart and they are not shared across replicas, so this is a brake
on a runaway client or a casual scripted probe -- not a defence against a
distributed attacker. Anything stronger belongs at the reverse proxy.

It is applied to every route, including `/health`, because the project's
security rules require rate limiting on all endpoints and because an
unlimited health endpoint is still a free amplification target.
"""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass

from starlette.responses import JSONResponse
from starlette.types import ASGIApp, Receive, Scope, Send

WINDOW_SECONDS = 60.0
# Stop the bucket dict growing without bound on a long-lived process that sees
# many distinct clients; entries older than this are dropped on sweep.
IDLE_EVICTION_SECONDS = 300.0
_SWEEP_THRESHOLD = 1024
_UNKNOWN_CLIENT = "unknown"


@dataclass(frozen=True)
class _Window:
    started_at: float
    count: int


class RateLimiter:
    """Fixed-window counter per client key. Immutable windows, replaced not mutated."""

    def __init__(self, limit_per_minute: int) -> None:
        if limit_per_minute <= 0:
            raise ValueError("limit_per_minute must be positive")
        self._limit = limit_per_minute
        self._windows: dict[str, _Window] = {}
        self._lock = threading.Lock()

    @property
    def limit_per_minute(self) -> int:
        return self._limit

    def check(self, client_key: str, now: float | None = None) -> bool:
        """Return True if this call is allowed, False if the client is over the limit."""
        moment = time.monotonic() if now is None else now
        with self._lock:
            self._evict_idle(moment)
            window = self._windows.get(client_key)
            if window is None or moment - window.started_at >= WINDOW_SECONDS:
                self._windows[client_key] = _Window(started_at=moment, count=1)
                return True
            if window.count >= self._limit:
                return False
            self._windows[client_key] = _Window(
                started_at=window.started_at, count=window.count + 1
            )
            return True

    def _evict_idle(self, moment: float) -> None:
        if len(self._windows) < _SWEEP_THRESHOLD:
            return
        self._windows = {
            key: window
            for key, window in self._windows.items()
            if moment - window.started_at < IDLE_EVICTION_SECONDS
        }


def client_key_for(scope: Scope) -> str:
    """Identify the caller for rate-limiting purposes.

    The peer address, not `X-Forwarded-For`: that header is attacker-controlled
    unless a trusted proxy rewrites it, and trusting it here would let one
    client spoof an unlimited number of identities and defeat the limiter
    entirely. Behind a real proxy, the proxy should enforce the limit instead.
    """
    client = scope.get("client")
    if not client:
        return _UNKNOWN_CLIENT
    return str(client[0])


class RateLimitMiddleware:
    def __init__(self, app: ASGIApp, limiter: RateLimiter) -> None:
        self._app = app
        self._limiter = limiter

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self._app(scope, receive, send)
            return

        if not self._limiter.check(client_key_for(scope)):
            response = JSONResponse(
                status_code=429,
                content={
                    "error": {
                        "code": "rate_limited",
                        "message": (
                            "Too many requests from this client. The limit is "
                            f"{self._limiter.limit_per_minute} requests per minute."
                        ),
                    }
                },
                headers={"Retry-After": str(int(WINDOW_SECONDS))},
            )
            await response(scope, receive, send)
            return

        await self._app(scope, receive, send)
