# SmartLease Edge — Report Custody Server

A small FastAPI service that provides **report custody and countersignature
verification** for the SmartLease Edge Android app.

## Privacy constraint (by design)

This server **never receives or stores inspection photos, video, or audio**.
It stores only a SHA-256 digest of a report's findings plus a handful of
small metadata fields (property reference, session type, timestamps, deposit
figures, findings count). This is enforced, not just documented:

- Every POST body must be `application/json` — anything else (e.g.
  `image/jpeg`, `multipart/form-data`) is rejected with `415` before it is
  parsed (`app/body_guard.py`).
- Request bodies are capped at a small size ceiling (16 KB by default,
  `SMARTLEASE_MAX_REQUEST_BODY_BYTES`) — far larger than any legitimate
  digest/metadata payload, so an oversized body is rejected with `413`.
- Every request schema uses pydantic's `extra="forbid"`, so an unexpected
  field (e.g. a smuggled base64 image under some field name) is rejected
  with `422` rather than silently accepted.
- The database schema (`app/storage/db.py`) has no column or table capable
  of holding binary media — only text/integer fields for the digest and
  metadata.

The digest itself is computed on-device (see
`app/src/main/java/com/smartlease/edge/report/FindingsDigest.kt` and
`CountersignPayload.kt` in the Android app) as a SHA-256 hex string over the
findings; this server treats it as an opaque 64-character lowercase hex
string and never recomputes or reinterprets it.

## Stack

- Python 3.12, FastAPI, pydantic v2
- SQLite (stdlib `sqlite3`), one file per deployment
- pytest + FastAPI's `TestClient` for tests

## Project layout

```
server/
  app/
    main.py               # app factory, exception handlers, middleware wiring
    config.py              # env-driven settings
    errors.py               # AppError hierarchy -> error envelope
    body_guard.py            # rejects non-JSON / oversized bodies
    time_utils.py
    models/
      report.py             # ReportCreateRequest/Response, ReportRecordResponse, VerifyResponse
      countersign.py         # CountersignRequest/Response
    routes/
      health.py
      reports.py             # create/get/countersign/verify handlers
    storage/
      db.py                  # SQLite schema + connection setup
      report_repository.py   # immutable ReportRecord/CountersignatureRecord + repository
  tests/
    conftest.py               # isolated TestClient fixture (fresh SQLite file per test) + payload factories
    test_health.py
    test_reports_create.py
    test_reports_get.py
    test_countersign.py
    test_verify.py
  requirements.txt
  pytest.ini
  README.md
```

## Setup

From `server/`:

```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate

pip install -r requirements.txt
```

(All required packages — `fastapi`, `uvicorn`, `pydantic`, `httpx`, `pytest`
— were already present in the environment this was built and tested in; the
venv step above is for a clean machine.)

## Running the server

From `server/`:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Then, e.g.:

```bash
curl http://127.0.0.1:8000/api/v1/health
```

### Configuration (environment variables, all optional)

| Variable | Default | Purpose |
|---|---|---|
| `SMARTLEASE_SERVER_VERSION` | `0.1.0` | Version string returned by `/health` |
| `SMARTLEASE_DATABASE_PATH` | `smartlease_reports.db` | SQLite file path |
| `SMARTLEASE_MAX_REQUEST_BODY_BYTES` | `16384` | Hard cap on request body size |

## Running the tests

From `server/`:

```bash
python -m pytest
```

With coverage:

```bash
python -m pytest --cov=app --cov-report=term-missing
```

**Actual result when this was built** (28 tests, 99% statement coverage on
`app/`):

```
28 passed, 1 warning in 2.73s

Name                               Stmts   Miss  Cover   Missing
----------------------------------------------------------------
app\__init__.py                        0      0   100%
app\body_guard.py                     33      2    94%   62-63
app\config.py                         16      0   100%
app\errors.py                         24      0   100%
app\main.py                           37      2    95%   73-74
app\models\countersign.py             15      0   100%
app\models\report.py                  53      0   100%
app\routes\health.py                   7      0   100%
app\routes\reports.py                 54      0   100%
app\storage\db.py                     10      0   100%
app\storage\report_repository.py      52      0   100%
app\time_utils.py                      4      0   100%
----------------------------------------------------------------
TOTAL                                305      4    99%
```

The two uncovered branches are: an invalid (non-numeric) `Content-Length`
header in the body guard, and the catch-all 500 handler for truly unexpected
exceptions — both are defensive code paths, not part of the documented
contract.

Each test gets its own fresh SQLite file (via pytest's `tmp_path`), so tests
are fully isolated and can run in any order.

## API

Base path: `/api/v1`.

Every error response uses the envelope:

```json
{"error": {"code": "<machine_code>", "message": "<human message>"}}
```

Success responses return the bare object described below.

### `GET /health`

`200`: `{"status": "ok", "version": "<server version>"}`

### `POST /reports`

Request:

```json
{
  "reportId": "5c1a1e0e-....-....-....-............",
  "propertyRef": "flat-12b",
  "sessionType": "MOVE_IN",
  "createdAtEpochMs": 1700000000000,
  "digestSha256": "<64 lowercase hex chars>",
  "appVersion": "1.0.0",
  "findingsCount": 3,
  "depositRupees": 50000,
  "totalDeductionRupees": 0
}
```

- `201` on first creation: `{"reportId", "digestSha256", "serverReceivedAtEpochMs"}`.
- `200` (idempotent) if `reportId` already exists with the **same** `digestSha256` — returns the original record's response, including the original `serverReceivedAtEpochMs`.
- `409 digest_mismatch` if `reportId` already exists with a **different** `digestSha256` (treated as a tamper signal).
- `422 validation_error` if `totalDeductionRupees > depositRupees`, if any field is malformed (bad UUID, digest not 64 lowercase hex chars, negative counts, unknown `sessionType`, etc.), or if the body contains any field not in this contract.
- `415 unsupported_content_type` if the request is not `application/json`.
- `413 payload_too_large` if the body exceeds the configured size ceiling.

### `GET /reports/{reportId}`

- `200`: the stored record, including any countersignatures:
  ```json
  {
    "reportId": "...", "propertyRef": "...", "sessionType": "MOVE_IN",
    "createdAtEpochMs": 0, "digestSha256": "...", "appVersion": "...",
    "findingsCount": 0, "depositRupees": 0, "totalDeductionRupees": 0,
    "serverReceivedAtEpochMs": 0,
    "countersignatures": [
      {"signerRole": "TENANT", "signatureSha256": "...", "signedAtEpochMs": 0}
    ]
  }
  ```
- `404 report_not_found` if unknown.

### `POST /reports/{reportId}/countersign`

Request: `{"signerRole": "TENANT"|"LANDLORD", "signatureSha256": "<64 hex>", "signedAtEpochMs": <int>}`

- `201`: `{"reportId", "signerRole", "signatureSha256", "signedAtEpochMs"}`.
- `404 report_not_found` if the report is unknown.
- `409 already_countersigned` if that role has already countersigned this report.
- `422 validation_error` for malformed input (bad signature hex, invalid role, unexpected field).

### `GET /reports/{reportId}/verify?digest=<64 hex>`

- `200`: `{"reportId", "matches": true|false}`.
- `404 report_not_found` if unknown.
- `422 validation_error` if `digest` is not 64 lowercase hex characters.

## Deviations from the contract as given

None functionally. Two implementation choices worth calling out explicitly
since the contract didn't fully specify them:

1. **Idempotent duplicate-POST response shape.** The contract says a
   duplicate POST with an identical digest "returns 200 and the existing
   record". This implementation returns the same shape as the `201` create
   response (`reportId`, `digestSha256`, `serverReceivedAtEpochMs` — with
   the *original* receive timestamp, not a new one), rather than the fuller
   record shape used by `GET /reports/{reportId}`. Use `GET
   /reports/{reportId}` if the fuller record (including countersignatures)
   is needed.
2. **`reportId` format.** The contract says "uuid"; this is validated
   strictly via `uuid.UUID(...)` (any RFC 4122 UUID string, any version) at
   the pydantic boundary, rejecting non-UUID strings with `422`.
