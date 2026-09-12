"""ASGI middleware enforcing the core privacy constraint of this service.

The server must NEVER receive or store inspection photos, video, or audio --
only a digest plus small metadata. Two independent backstops enforce that:

1. Any request body that isn't declared as `application/json` is rejected
   outright (415), before it is ever parsed. Photos/video/audio never arrive
   as bare JSON, so this alone rules out the common cases (multipart form
   uploads, raw image bytes, etc).
2. Any body larger than a small configurable ceiling is rejected (413).
   Legitimate report/countersign payloads are a few hundred bytes; anything
   large is not a valid request regardless of its declared content type.

This is deliberately a blunt, cheap check that runs before pydantic parsing.
Pydantic's `extra="forbid"` models are the second line of defense, rejecting
any unexpected field (e.g. a smuggled base64 image) that *does* arrive as
valid, small JSON.
"""

from __future__ import annotations

from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.types import ASGIApp, Receive, Scope, Send

_ALLOWED_CONTENT_TYPE = "application/json"
_BODY_BEARING_METHODS = frozenset({"POST", "PUT", "PATCH"})


class JsonOnlyBodyGuardMiddleware:
    def __init__(self, app: ASGIApp, max_body_bytes: int) -> None:
        self._app = app
        self._max_body_bytes = max_body_bytes

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http" or scope["method"] not in _BODY_BEARING_METHODS:
            await self._app(scope, receive, send)
            return

        request = Request(scope, receive=receive)
        content_type = request.headers.get("content-type", "")
        media_type = content_type.split(";")[0].strip().lower()

        if media_type != _ALLOWED_CONTENT_TYPE:
            await _reject(
                scope,
                receive,
                send,
                status_code=415,
                code="unsupported_content_type",
                message=(
                    "Only application/json request bodies are accepted. "
                    "This server never accepts image, video, or audio payloads."
                ),
            )
            return

        content_length_header = request.headers.get("content-length")
        if content_length_header is not None:
            try:
                content_length = int(content_length_header)
            except ValueError:
                content_length = None
            if content_length is not None and content_length > self._max_body_bytes:
                await _reject(
                    scope,
                    receive,
                    send,
                    status_code=413,
                    code="payload_too_large",
                    message=(
                        "Request body exceeds the maximum size for digest-only "
                        "report metadata."
                    ),
                )
                return

        await self._app(scope, receive, send)


async def _reject(
    scope: Scope,
    receive: Receive,
    send: Send,
    *,
    status_code: int,
    code: str,
    message: str,
) -> None:
    response = JSONResponse(
        status_code=status_code,
        content={"error": {"code": code, "message": message}},
    )
    await response(scope, receive, send)
