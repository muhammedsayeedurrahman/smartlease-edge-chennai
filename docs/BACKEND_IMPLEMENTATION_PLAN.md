# Backend Implementation Plan — Report Custody Server

Status as of 2026-09-13. This file documents the backend as it actually exists in this
repo (verified by reading `server/app/**` and running the test suite), then lays out what
is missing before it can serve a real deployment, and the exact steps to close each gap.

It does not repeat everything in `server/README.md` and `server/SECURITY.md` — those two
are the source of truth for the API contract and the security model, respectively, and
this plan defers to them. This file exists to answer a different question: **the backend
code is done and tested — what does it take to actually stand it up and wire the Android
app to it?**

---

## 1. What already exists (verified)

The FastAPI service at `server/` is fully built and tested, not a stub:

- **55 tests pass**, ~99% statement coverage (`python -m pytest -q` from `server/`,
  confirmed 2026-09-13).
- **No known unhandled crash paths.** The `create_report` race condition described in an
  earlier draft plan (`f:/IQ/New folder/.../reports.py`) — an uncaught `sqlite3.IntegrityError`
  on a concurrent duplicate insert producing a `500` — is **already fixed** in this repo.
  `server/app/routes/reports.py:118-137` wraps `repo.insert_report` in
  `try/except sqlite3.IntegrityError`, re-reads the row, and returns the correct `200`
  (idempotent) or `409 digest_mismatch` outcome. No action needed there.
- **`list_countersignatures` ordering is fine.** `server/app/storage/db.py:31-38` defines
  `countersignatures.id` as an explicit `INTEGER PRIMARY KEY AUTOINCREMENT` column, so
  `ORDER BY id` in `report_repository.py:142` is ordering a real column, not relying on
  implicit `rowid` aliasing.

### 1.1 Architecture

```
server/
  app/
    main.py            # create_app(): wires middleware, routes, exception handlers
    config.py           # Settings, sourced from SMARTLEASE_* env vars, no secret defaults
    auth.py              # X-API-Key (deployment-wide) + X-Report-Token (per-report)
    body_guard.py          # rejects non-JSON (415) and oversized (413) bodies pre-parse
    rate_limit.py            # fixed-window, per-peer-IP, in-process
    errors.py                 # AppError hierarchy -> {"error": {"code","message"}} envelope
    time_utils.py
    models/
      report.py               # pydantic v2, extra="forbid" everywhere
      countersign.py
    routes/
      health.py
      reports.py               # create / get / countersign / verify handlers
    storage/
      db.py                     # SQLite schema + idempotent column migrations
      report_repository.py       # ReportRecord/CountersignatureRecord, thread-safe
  tests/                          # 55 tests, one fresh SQLite file per test
  requirements.txt
```

Request flow for `POST /api/v1/reports`:

1. `RateLimitMiddleware` checks the caller's IP against a 60-req/min fixed window → `429`.
2. `JsonOnlyBodyGuardMiddleware` rejects non-`application/json` (`415`) or bodies over
   16 KB (`413`) before any parsing happens.
3. `require_api_key` (a route dependency) checks `X-API-Key` with `hmac.compare_digest`
   against `Settings.api_key` → `503` if the server has none configured, `401` if wrong.
4. Pydantic validates the body (`ReportCreateRequest`, `extra="forbid"`) → `422` on any
   unknown field, bad UUID, non-hex digest, or `totalDeductionRupees > depositRupees`.
5. The route checks for an existing `reportId`: same digest → idempotent `200`, different
   digest → `409 digest_mismatch`. New id → `issue_report_token()`, insert, `201`.

### 1.2 Data model (SQLite)

```sql
reports (
  report_id TEXT PRIMARY KEY,
  property_ref, session_type, created_at_epoch_ms, digest_sha256, app_version,
  findings_count, deposit_rupees, total_deduction_rupees,
  server_received_at_epoch_ms,
  access_token_sha256 TEXT   -- SHA-256 of the per-report token; never the token itself
)

countersignatures (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  report_id TEXT REFERENCES reports(report_id),
  signer_role TEXT, signature_sha256 TEXT, signed_at_epoch_ms INTEGER,
  UNIQUE(report_id, signer_role)   -- one signature per role per report
)
```

No column anywhere can hold binary media — that is the enforced privacy boundary, not
just a policy statement (see `server/README.md` "Privacy constraint").

### 1.3 Auth model (two independent secrets)

| Secret | Header | Scope | Answers |
|---|---|---|---|
| `SMARTLEASE_API_KEY` | `X-API-Key` | Whole deployment, one shared value | "Is this a SmartLease client at all?" |
| Per-report token | `X-Report-Token` | One report, issued once at creation, stored only as its SHA-256 | "Does this caller hold the secret minted for *this* report?" |

Both comparisons use `hmac.compare_digest` (timing-safe). Neither is user identity —
`server/SECURITY.md` is explicit that a countersignature is not cryptographically bound
to a person. Do not describe it as one in the pitch or the PDF.

### 1.4 API contract (condensed — full detail in `server/README.md`)

Base path `/api/v1`. Every `/reports` route needs `X-API-Key`; `GET /reports/{id}` and
`POST /reports/{id}/countersign` also need `X-Report-Token`.

| Route | Success | Key failure modes |
|---|---|---|
| `GET /health` | `200 {status, version}` | — |
| `POST /reports` | `201` (first) / `200` (idempotent replay) | `409 digest_mismatch`, `422 validation_error`, `415`, `413` |
| `GET /reports/{id}` | `200` full record + countersignatures | `404 report_not_found`, `403 forbidden` |
| `POST /reports/{id}/countersign` | `201` | `404`, `409 already_countersigned`, `422`, `403` |
| `GET /reports/{id}/verify?digest=` | `200 {reportId, matches}` | `404`, `422` |

### 1.5 Android side (already wired, but pointed nowhere real)

- `app/src/main/java/com/smartlease/edge/sync/*` (21 files, ~930 lines) implements the
  full client: `HttpReportSyncClient`, `SyncApi` (Retrofit), `SyncCoordinator`, request
  mappers, and a `SyncResult` sealed hierarchy that already distinguishes
  `MalformedResponse` from network/auth/server failures.
- `SyncConfig.syncEnabled` **defaults to `false`** — this is an intentional design
  decision (`SyncConfig.kt:14-27`), not a bug: an inspection must complete fully offline,
  and a build with no real backend configured must not silently attempt uploads.
- `SyncConfig.DEFAULT_BASE_URL = "https://sync.smartlease-edge.example/api/v1/"` — a
  **placeholder domain that does not resolve.** No build has ever pointed at a real,
  reachable server.
- `buildTimeSyncConfig()` turns sync on **only** if `BuildConfig.SMARTLEASE_API_KEY` is
  non-blank, which is threaded from `-PsmartleaseApiKey=...` / `gradle.properties` /
  `SMARTLEASE_API_KEY` env var in `app/build.gradle.kts:40-53`. There is **no equivalent
  mechanism for the base URL** — it is a hardcoded constant in `SyncConfig.kt`. This is
  the concrete piece of app code that must change to point at a real deployment (Phase 2
  below).

---

## 2. Gap analysis — what's missing before this goes live

| # | Gap | Why it matters | Severity |
|---|---|---|---|
| G1 | **Never deployed anywhere.** No live URL exists. `DEFAULT_BASE_URL` doesn't resolve. | Sync is fully built and fully untested against a real network. | Blocking for a live sync demo |
| G2 | **No way to point a build at a real host** without editing `SyncConfig.kt` source. | Every environment change requires a code edit + rebuild, no `-P` override like the API key has. | Blocking for repeatable deploys |
| G3 | **No Dockerfile / container image.** | Most PaaS options (Render, Railway, Fly.io) work fine without one for a plain FastAPI app, but a container is needed for anything self-hosted (a VPS, k8s). | Medium |
| G4 | **No CI for the Python backend.** `.github/workflows/android.yml` only builds/tests the app; `pytest` never runs in CI. | A backend regression ships silently until someone runs `pytest` by hand. | Medium |
| G5 | **No TLS termination in this service.** It speaks plain HTTP and expects a reverse proxy / PaaS edge to add HTTPS (`server/SECURITY.md`, "There is no TLS termination here"). | Android's default network security config blocks cleartext HTTP; the deployed URL must be HTTPS. | Blocking |
| G6 | **SQLite on disk with no persistence plan.** Several free-tier PaaS platforms wipe the local filesystem on every redeploy (though not on restart). | A redeploy could silently discard all stored reports/tokens. | Medium — must pick a host/plan that persists a volume, or accept the reset for a hackathon demo |
| G7 | **Key rotation is a hard cutover** (documented, accepted trade-off in `SECURITY.md`) — no overlap window, no second accepted key. | Rotating the key instantly breaks every build still holding the old one. | Low for a hackathon; real gap for anything longer-lived |
| G8 | **No structured logging / request metrics** beyond default `uvicorn` access logs. | Hard to debug a live demo failure ("why did that countersign 403?") without shell access to the host. | Low |
| G9 | **Countersignatures are not cryptographically bound to a signer identity** (explicitly a non-goal today, per `SECURITY.md`). | Anyone claiming this proves who signed is making a false claim — must not appear in the pitch/PDF. | Out of scope for this plan; tracked for completeness |

G1, G2, G5 are what block getting a real demo working today. G3, G4, G6–G9 are hardening
for anything beyond the hackathon.

---

## 3. Implementation plan

### Phase 1 — Get a live, HTTPS backend running (closes G1, G5, partially G6)

Pick one option. Both give you HTTPS automatically and need no Dockerfile.

**Option A — Render.com (recommended: free web service, zero Docker, persistent disk on paid tier)**

1. Push `server/` to GitHub (already true — it's part of this repo).
2. In Render: **New → Web Service**, connect this repo, set:
   - Root directory: `server`
   - Build command: `pip install -r requirements.txt`
   - Start command: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
3. Environment variables (Render dashboard → Environment):
   ```
   SMARTLEASE_API_KEY=<generate below>
   SMARTLEASE_RATE_LIMIT_PER_MINUTE=60
   SMARTLEASE_DATABASE_PATH=/opt/render/project/src/smartlease_reports.db
   ```
   Generate the key once, locally:
   ```bash
   python -c "import secrets; print(secrets.token_urlsafe(32))"
   ```
4. On the free tier the disk resets on every deploy (not on restart/sleep) — acceptable
   for a hackathon demo; note it in the runbook so a redeploy right before judging doesn't
   surprise anyone. For anything that must survive redeploys, add a Render **persistent
   disk** mounted at the `SMARTLEASE_DATABASE_PATH` directory (paid tier).
5. Render terminates TLS for you — the public URL is already `https://<service>.onrender.com`.

**Option B — Fly.io (works on the free allowance, gives a real persistent volume)**

```bash
cd server
fly launch --no-deploy          # creates fly.toml, pick a region near the venue
fly volumes create data --size 1   # 1 GB persistent volume
fly secrets set SMARTLEASE_API_KEY=$(python -c "import secrets; print(secrets.token_urlsafe(32))")
```
Add to `fly.toml`:
```toml
[mounts]
  source = "data"
  destination = "/data"

[env]
  SMARTLEASE_DATABASE_PATH = "/data/smartlease_reports.db"
```
Fly needs a `Dockerfile` (closes G3 at the same time) — minimal one:
```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
```
```bash
fly deploy
```
Fly also terminates TLS automatically at `https://<app>.fly.dev`.

**Verify the deployment (either option), before touching the Android app:**
```bash
BASE=https://<your-host>
KEY=<the SMARTLEASE_API_KEY you set>

curl -s $BASE/api/v1/health
# {"status":"ok","version":"0.1.0"}

curl -s -X POST $BASE/api/v1/reports \
  -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"reportId":"'$(python -c "import uuid; print(uuid.uuid4())")'","propertyRef":"smoke-test","sessionType":"MOVE_IN","createdAtEpochMs":1700000000000,"digestSha256":"'$(python -c "print('a'*64)")'","appVersion":"0.1.0","findingsCount":0,"depositRupees":1000,"totalDeductionRupees":0}'
# expect 201 with a reportToken
```
If both calls succeed, Phase 1 is done — a real, reachable, HTTPS backend exists.

### Phase 2 — Wire the Android app to it (closes G2)

The API key already has a build-time override mechanism
(`app/build.gradle.kts:40-53`). The base URL does not; add the matching pattern rather
than hand-editing `SyncConfig.kt` per build:

1. In `app/build.gradle.kts`, next to the existing `smartleaseApiKey` block, add:
   ```kotlin
   val smartleaseBaseUrl = (project.findProperty("smartleaseBaseUrl") as String?)
       ?: System.getenv("SMARTLEASE_BASE_URL")
       ?: ""
   buildConfigField("String", "SMARTLEASE_BASE_URL", "\"$smartleaseBaseUrl\"")
   ```
2. In `SyncConfigDefaults.kt`, use it when non-blank, falling back to the existing
   placeholder default otherwise (so a build with no override still fails safe, per
   `SyncConfig`'s existing "opt-in" design):
   ```kotlin
   fun buildTimeSyncConfig(): SyncConfig = SyncConfig(
       baseUrl = BuildConfig.SMARTLEASE_BASE_URL.ifBlank { SyncConfig.DEFAULT_BASE_URL },
       syncEnabled = BuildConfig.SMARTLEASE_API_KEY.isNotBlank(),
       apiKey = BuildConfig.SMARTLEASE_API_KEY.ifBlank { null },
   )
   ```
   Remember `SyncConfig.baseUrl` must end in `/` (enforced by an `init` check) — pass
   `https://<your-host>/api/v1/`, not the bare host.
3. Build with both values supplied:
   ```bash
   ./gradlew :app:assembleDebug \
     -PsmartleaseApiKey=<the same key set on the server> \
     -PsmartleaseBaseUrl=https://<your-host>/api/v1/
   ```
4. Install on the demo device (iQOO 15) and run one full inspection through to report
   generation — confirm the sync indicator in `SyncUiState` reaches a success state, then
   confirm the report is fetchable with `GET /reports/{id}` using the same `X-Report-Token`
   the app received.

### Phase 3 — Hardening after the demo (closes G3, G4, G7, G8)

Not needed for today's demo; do these once the hackathon judging is over, in order of
value:

1. **Server CI** — add a `.github/workflows/server.yml` mirroring `android.yml`'s
   structure: checkout, `setup-python@v5`, `pip install -r server/requirements.txt`,
   `python -m pytest --cov=app --cov-report=term-missing` from `server/`, fail if
   coverage regresses below ~95%.
2. **Dockerfile in the repo** (not just in a PaaS-specific config) — the one sketched in
   Phase 1 Option B, checked in at `server/Dockerfile`, so any future host (a VPS,
   Kubernetes, a different PaaS) has a reproducible build.
3. **Key rotation with an overlap window** — `SECURITY.md` documents today's hard-cutover
   rotation as an accepted hackathon trade-off. Post-hackathon, change `Settings.api_key`
   to `frozenset[str]` (accept N valid keys) and `require_api_key` to check membership,
   so a new key can be added before the old one is retired.
4. **Structured logging** — replace default `uvicorn` access logs with a small
   `logging.Filter` that emits one JSON line per request (method, path, status, client
   key, latency) so a live-demo failure can be diagnosed from host logs alone.
5. **Signer-bound countersignatures** — the real fix for the gap `SECURITY.md` calls out
   explicitly as unbuilt: each signer holds a keypair, signs the digest, and the server
   verifies against a public key registered in advance, rather than storing a client-
   computed hash it cannot attribute to a person. This is a schema change
   (`countersignatures` needs a `public_key` reference or embedded key) plus a new
   verification step in `countersign_report` — scope it as its own plan before starting.

---

## 4. Rollout checklist

- [ ] Generate a production `SMARTLEASE_API_KEY` (never the value used in any local test run)
- [ ] Deploy `server/` to a host with HTTPS (Phase 1)
- [ ] Confirm `GET /health` and a smoke-test `POST /reports` succeed against the live URL
- [ ] Add `smartleaseBaseUrl` build wiring (Phase 2, step 1–2)
- [ ] Rebuild the APK with matching `smartleaseApiKey` + `smartleaseBaseUrl`
- [ ] Run one full inspection on the demo device and confirm sync succeeds end-to-end
- [ ] Note the chosen host's persistence behavior (does a redeploy wipe the DB?) in the
      demo-day runbook so nobody redeploys mid-judging
- [ ] Post-hackathon: work through Phase 3 in order

## References

- `server/README.md` — full API contract, setup, and test instructions
- `server/SECURITY.md` — the auth model and, importantly, what it does *not* prove
- `app/src/main/java/com/smartlease/edge/sync/SyncConfig.kt` — why sync defaults off
- `app/build.gradle.kts:36-53` — the existing `-P`/env-var override pattern to mirror for the base URL
