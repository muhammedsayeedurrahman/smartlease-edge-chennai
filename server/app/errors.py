"""Application-level errors and the envelope they render as.

Every error response from this API takes the shape
`{"error": {"code": "<machine_code>", "message": "<human message>"}}`.
Route handlers raise one of these instead of building a response directly,
so the mapping to an HTTP status code and machine-readable code lives in one
place (see `register_exception_handlers` in `app.main`).
"""

from __future__ import annotations


class AppError(Exception):
    """Base class for errors that map directly to a client-facing response."""

    status_code: int = 500
    code: str = "internal_error"

    def __init__(self, message: str) -> None:
        super().__init__(message)
        self.message = message

    def to_envelope(self) -> dict:
        return {"error": {"code": self.code, "message": self.message}}


class ReportNotFoundError(AppError):
    status_code = 404
    code = "report_not_found"


class DigestMismatchError(AppError):
    """A report with this id already exists but its stored digest differs.

    This is treated as a tamper signal, not an ordinary conflict: the same
    reportId should always carry the same findings digest.
    """

    status_code = 409
    code = "digest_mismatch"


class AlreadyCountersignedError(AppError):
    status_code = 409
    code = "already_countersigned"


class UnsupportedContentTypeError(AppError):
    status_code = 415
    code = "unsupported_content_type"


class PayloadTooLargeError(AppError):
    status_code = 413
    code = "payload_too_large"


class ApiKeyNotConfiguredError(AppError):
    """The deployment never set SMARTLEASE_API_KEY.

    This fails closed on purpose. An unauthenticated report-custody service is
    worse than an unavailable one: anyone who could reach it could forge a
    countersignature, which is the exact artefact this system exists to make
    trustworthy. Refusing to serve is the safe failure.
    """

    status_code = 503
    code = "api_key_not_configured"


class UnauthorizedError(AppError):
    """Missing or wrong X-API-Key."""

    status_code = 401
    code = "unauthorized"


class ForbiddenError(AppError):
    """Valid API key, but not the per-report token needed for THIS report.

    The API key says "a SmartLease client is calling". This says "the caller
    holds the secret issued when this specific report was created" -- without
    it, one key holder could read or countersign another tenancy's report.
    """

    status_code = 403
    code = "forbidden"


class RateLimitedError(AppError):
    status_code = 429
    code = "rate_limited"
