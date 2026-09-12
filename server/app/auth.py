"""Authentication and per-report authorization.

Two distinct checks, because they answer two different questions:

* **API key** (`X-API-Key`) -- "is this a SmartLease client at all?" One shared
  secret for the deployment. It stops anonymous traffic.
* **Report token** (`X-Report-Token`) -- "does this caller hold the secret that
  was issued when *this particular* report was created?" Without it, any holder
  of the API key could read or countersign any other tenancy's report, which is
  a real problem given the API key ships inside an APK.

## What this does NOT do, stated plainly

The API key is extractable from the app by anyone willing to unzip it. It raises
the cost of abuse; it is not user authentication and must never be described as
such. There is no user identity here, and a countersignature is not
cryptographically bound to a person -- `signatureSha256` is a value the client
computes and the server stores, so the server can prove a signature has not
*changed*, not that a specific human produced it. Binding a countersignature to
a keypair held by the signer is the fix, and it is not built. See SECURITY.md.
"""

from __future__ import annotations

import hashlib
import hmac
import secrets

from fastapi import Header, Request

from app.errors import ApiKeyNotConfiguredError, ForbiddenError, UnauthorizedError

REPORT_TOKEN_BYTES = 32


def issue_report_token() -> str:
    """A fresh per-report secret. Returned to the creator exactly once."""
    return secrets.token_urlsafe(REPORT_TOKEN_BYTES)


def hash_token(token: str) -> str:
    """Only the hash is ever stored, so a database copy does not yield tokens."""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def require_api_key(request: Request, x_api_key: str | None = Header(default=None)) -> None:
    """Reject anything that is not a configured SmartLease client.

    Reads the key from `app.state.settings` rather than the process-wide
    `get_settings()` cache so that a differently-configured app instance (a
    test, a second deployment in one process) is actually honoured instead of
    silently inheriting whichever config was constructed first.

    Fails closed when unconfigured: an unauthenticated custody service would
    let anyone forge a countersignature, so refusing to serve is safer than
    serving openly.
    """
    settings = request.app.state.settings
    if settings.api_key is None:
        raise ApiKeyNotConfiguredError(
            "This server has no SMARTLEASE_API_KEY configured, so it refuses "
            "report traffic rather than serving it unauthenticated."
        )
    if not x_api_key or not hmac.compare_digest(x_api_key, settings.api_key):
        raise UnauthorizedError("Missing or invalid X-API-Key header.")


def require_report_token(request: Request, report_id: str, expected_hash: str | None) -> None:
    """Authorize access to one specific report.

    `hmac.compare_digest` rather than `==` so a token cannot be recovered a
    character at a time by timing the response.
    """
    supplied = request.headers.get("X-Report-Token")
    if expected_hash is None:
        # A report stored before tokens existed. Fail closed rather than
        # silently granting access to legacy rows.
        raise ForbiddenError(
            f"Report {report_id} predates per-report tokens and cannot be "
            "accessed through this endpoint."
        )
    if not supplied or not hmac.compare_digest(hash_token(supplied), expected_hash):
        raise ForbiddenError(
            "Missing or invalid X-Report-Token for this report. This token is "
            "issued once, in the response that created the report."
        )
