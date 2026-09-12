# Security model of the report-custody service

This document exists because the first version of this service had no authentication at all.
A security review found it, and the finding was correct: anyone who could reach the server
could `POST /reports/{id}/countersign` and write a signature attributed to `TENANT` or
`LANDLORD` — the exact artefact the tamper-evidence story rests on. This file records what
was built in response, and, at least as importantly, what was not.

## What the service holds

A SHA-256 digest of a report's findings, plus small metadata: property reference, session
type, timestamps, app version, a findings count, and two rupee figures. Countersignatures are
stored as a role, a SHA-256 hex string, and a timestamp.

No photo, video, or audio ever reaches this service. Three independent mechanisms enforce
that: `JsonOnlyBodyGuardMiddleware` rejects any non-JSON body (415) and any body over 16 KB
(413) before parsing; every request model sets `extra="forbid"`, so a smuggled `photoBase64`
field is a 422 rather than an ignored key; and the schema in `app/storage/db.py` has no column
capable of holding bytes.

## The two checks

**`X-API-Key`** — a single shared secret for the deployment, required on every `/reports`
route. It answers "is this a SmartLease client at all?" and nothing more. It comes from the
`SMARTLEASE_API_KEY` environment variable and has **no default**: a server started without it
returns `503 api_key_not_configured` on every report route rather than serving anonymous
traffic. Failing closed is deliberate — an unauthenticated custody service is worse than an
unavailable one.

**`X-Report-Token`** — a per-report secret, generated with `secrets.token_urlsafe(32)` and
returned exactly once, in the `201` that first stores a report. It answers "does this caller
hold the secret issued when *this particular* report was created?" It is required by
`GET /reports/{id}` and `POST /reports/{id}/countersign`.

Both comparisons use `hmac.compare_digest`, so a token cannot be recovered a character at a
time by timing responses. Only `sha256(token)` is stored, so a stolen copy of the database
does not yield working tokens — `test_stored_token_is_hashed_not_plaintext` asserts the raw
token does not appear in the database file.

Rate limiting is per-peer-address, fixed-window, applied to every route including `/health`.

## What this does NOT do

This section matters more than the one above.

**The API key ships inside the APK.** It is set at build time via
`buildConfigField("String", "SMARTLEASE_API_KEY", ...)` and is extractable by anyone willing
to unzip the APK and read the constant pool. It raises the cost of anonymous abuse. It is not
user authentication and must never be described as one.

**There is no user identity anywhere in this system.** No accounts, no login, no sessions. The
server cannot tell you *who* called it, only that the caller held a key and, for
report-scoped routes, a token.

**A countersignature is not cryptographically bound to a person.** `signatureSha256` is a
value the client computes and the server stores. The server can prove that a stored signature
has not *changed*; it cannot prove that a particular human produced it. A tenant who later
disputes "I never signed this" is not refuted by anything in this database. The fix is to have
each signer hold a keypair and sign the digest with it, so the server stores a signature it
can verify against a public key it was given in advance. **That is not built.** Any claim that
this system proves who signed would be false, and the PDF and the pitch must not make it.

**The `/verify` endpoint takes the API key but not a report token,** because it is the
endpoint the QR-scan flow calls and the token is not in the QR code. It returns one boolean
about a digest the caller already holds and cannot mutate anything. It does confirm that a
given `reportId` exists, which is a disclosure, recorded here rather than waved away.

**`GET /reports/{id}` returns 404 before 403 for an unknown id.** A caller holding only the
API key can therefore distinguish "no such report" from "report exists, wrong token".
Collapsing both into 404 would hide a genuinely mistyped report id during a live handover, so
the existence oracle is the accepted cost.

**Rate limiting is in-process.** The counters live in one process's memory: they do not
survive a restart and are not shared across replicas, so this is a brake on a runaway client
or a scripted probe, not a defence against a distributed attacker. `X-Forwarded-For` is
deliberately ignored — keying on an attacker-controlled header would let one client present a
new identity per request and defeat the limiter entirely. Behind a real proxy, the proxy
should enforce the limit.

**A lost token is unrecoverable.** The server keeps only the hash, so it cannot reissue one,
and a re-POST of the same report returns `200` with `reportToken: null` rather than minting
fresh access to a report whose id someone happens to know. Losing the token costs only the
server-side copy of the countersignature; the local PDF, QR code, and signature on the phone
are unaffected, which is why sync is best-effort and never on the critical path.

**There is no TLS termination here.** `SyncConfig` requires an HTTPS base URL in practice, but
this service speaks plain HTTP and expects to sit behind something that does not.

## Rotating the key

The key lives in the environment, never in source. To rotate: set a new `SMARTLEASE_API_KEY`
on the server, restart it, and rebuild the app with the matching value
(`./gradlew :app:assembleDebug -PsmartleaseApiKey=<new key>`, or via the `SMARTLEASE_API_KEY`
environment variable). There is no key list and no overlap window, so rotation is a hard
cutover: old builds get `401` immediately. For a hackathon deployment that is the right
trade; for anything longer-lived it is not, and a second accepted key during rotation would
be the first thing to add.

## Running it

```bash
export SMARTLEASE_API_KEY="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
python -m uvicorn app.main:app --port 8077
```

Without that variable the server starts and answers `/health`, and every report route returns
`503`. That is the intended behaviour, not a bug.
