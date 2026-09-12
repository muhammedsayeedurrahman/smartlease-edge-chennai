package com.smartlease.edge.sync

/**
 * Everything a [ReportSyncClient] call can hand back. Closed on purpose: nothing in this
 * package throws a raw exception out of the public API, and nothing swallows a failure --
 * every branch a caller needs to react to differently (tamper signal vs. plain outage vs.
 * "you sent bad data") is its own case, not a string stuffed into a generic failure.
 */
sealed interface SyncResult<out T> {

    data class Success<T>(val value: T) : SyncResult<T>

    /** No route to the backend at all -- offline, DNS failure, connection refused, etc. */
    data class NetworkUnavailable(val message: String) : SyncResult<Nothing>

    /** The request was sent but no response arrived within [SyncConfig]'s configured window. */
    data class Timeout(val message: String) : SyncResult<Nothing>

    /**
     * A 409 from the backend: it already holds a record for this reportId (or this signer
     * role) that disagrees with what was just sent -- e.g. the same reportId with a
     * *different* digest, which is a tamper signal, or a role that already countersigned.
     * Deliberately not folded into [ServerError]: callers need to distinguish "someone
     * altered this record" from "the server had a bad day".
     */
    data class TamperConflict(val message: String) : SyncResult<Nothing>

    /**
     * The outgoing payload failed local validation before anything was sent, or the backend
     * rejected it with 422. Both are "this data is malformed," so they share one case; the
     * message says which.
     */
    data class ValidationRejected(val message: String) : SyncResult<Nothing>

    /**
     * A 401: the backend refused this build's API key. Separate from [ServerError] because it
     * is never transient and retrying cannot fix it -- the build was shipped without a key,
     * or with the wrong one, and someone has to rebuild it.
     */
    data class Unauthenticated(val message: String) : SyncResult<Nothing>

    /**
     * A 403: the API key was accepted but the per-report token was missing or wrong for this
     * particular report. Distinct from [Unauthenticated] because the fix is different -- this
     * build is a legitimate client, it just does not hold the secret this report was created
     * under (most often because the report was uploaded by a different install, or the token
     * was never persisted). Never retried automatically: a retry loop against a countersign
     * endpoint is exactly what the token exists to stop.
     */
    data class NotAuthorized(val message: String) : SyncResult<Nothing>

    /** Any other non-2xx response the backend returned (5xx, or an unexpected 4xx). */
    data class ServerError(val httpStatusCode: Int, val message: String) : SyncResult<Nothing>

    /** The response body did not parse as the JSON shape the contract promises. */
    data class MalformedResponse(val message: String) : SyncResult<Nothing>
}

/** True for [SyncResult.Success] only -- a small readability helper for call sites. */
val SyncResult<*>.isSuccess: Boolean get() = this is SyncResult.Success

/** The success value, or null for every failure case. */
fun <T> SyncResult<T>.valueOrNull(): T? = (this as? SyncResult.Success)?.value
