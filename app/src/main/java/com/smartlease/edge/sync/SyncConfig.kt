package com.smartlease.edge.sync

/**
 * Everything that varies about talking to the backend, in one immutable place -- no base URL,
 * timeout or feature flag lives scattered across the sync package.
 *
 * [baseUrl] must end in `/` (Retrofit resolves each `@GET`/`@POST` path against it as a
 * relative reference, so a missing trailing slash silently drops the last path segment).
 * The default points at the hackathon reference deployment and is overridable per build --
 * nothing here is a secret, it is a routing detail.
 *
 * [syncEnabled] defaults to `false` -- OPT-IN, not opt-out -- for two reasons that both
 * matter more than the convenience of "it just works":
 *
 *  1. An inspection is the whole product. It must never depend on a network that may not
 *     be there -- a landlord and tenant standing in an empty flat with no signal still need
 *     a report, a PDF, and a QR code, all of which are produced entirely offline elsewhere
 *     in this app. Defaulting sync on would make that offline guarantee a lie for exactly
 *     the builds nobody remembered to configure.
 *  2. [DEFAULT_BASE_URL] is a placeholder domain that does not resolve. A build shipped
 *     with the default config would silently attempt an upload to a host that cannot
 *     answer, on every report, forever -- wasted battery and radio time in exchange for
 *     nothing, and in a demo, a confusing "failed to sync" message nobody asked to see.
 *     Overclaiming reach the app does not have is exactly the kind of correctness bug this
 *     codebase treats as a bug, not a rounding error.
 *
 * A build that wants sync turns it on explicitly, at the same time it points [baseUrl] at a
 * real deployment.
 */
data class SyncConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
    val writeTimeoutMs: Long = DEFAULT_WRITE_TIMEOUT_MS,
    /** Master switch. Callers must check this before invoking [ReportSyncClient] methods. */
    val syncEnabled: Boolean = DEFAULT_SYNC_ENABLED,
    /**
     * The backend's shared API key, sent as `X-API-Key` on every request.
     *
     * Null or blank means "this build has no credential". That is not treated as an error
     * here -- the request still goes out and the server answers 401 -- because pretending a
     * build is configured when it isn't would hide the misconfiguration rather than surface
     * it. It is supplied by the caller (see `BuildConfig.SMARTLEASE_API_KEY`), never
     * defaulted to a literal: a key committed to this file would be a key published to
     * GitHub.
     *
     * It is worth stating what this key does not do. It ships inside the APK and is
     * extractable by anyone willing to unzip it, so it raises the cost of anonymous abuse
     * and is not user authentication. Per-report authorization is [the report token]
     * [ReportUploadAck.reportToken]. See server/SECURITY.md.
     */
    val apiKey: String? = null
) {
    init {
        require(baseUrl.endsWith("/")) { "baseUrl must end with '/', was: $baseUrl" }
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be positive" }
        require(readTimeoutMs > 0) { "readTimeoutMs must be positive" }
        require(writeTimeoutMs > 0) { "writeTimeoutMs must be positive" }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://sync.smartlease-edge.example/api/v1/"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
        const val DEFAULT_READ_TIMEOUT_MS = 15_000L
        const val DEFAULT_WRITE_TIMEOUT_MS = 15_000L
        const val DEFAULT_SYNC_ENABLED = false

        val DEFAULT = SyncConfig()
    }
}
