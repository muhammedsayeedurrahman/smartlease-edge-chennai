package com.smartlease.edge.sync

/**
 * Uploads a signed findings report to the backend and nothing else.
 *
 * PRIVACY BOUNDARY, stated precisely because it is the whole reason this interface is worth
 * reviewing: every method below sends only a SHA-256 digest plus small integer/string
 * metadata. None of the request types in this package ([ReportUploadRequest],
 * [CountersignRequest]) has a bitmap, byte-array, or file field -- there is structurally
 * nothing to attach a photo, video clip, or audio sample to, even by mistake. Callers pass in
 * a digest computed elsewhere (see [com.smartlease.edge.report.FindingsDigest] /
 * [com.smartlease.edge.report.ReportFingerprint]); this package never touches the media that
 * digest was computed over.
 *
 * Implementations must never throw out of these methods -- every failure mode (offline,
 * timeout, tamper conflict, validation rejection, server error, malformed response) is a
 * [SyncResult] case, not an exception. All four methods are suspend functions intended to run
 * on `Dispatchers.IO`; callers do not need to switch dispatchers themselves.
 */
interface ReportSyncClient {

    /** `GET /health` -- a lightweight reachability check, safe to call before a real upload. */
    suspend fun checkHealth(): SyncResult<HealthStatus>

    /**
     * `POST /reports`. Idempotent on the server for a byte-identical re-upload of the same
     * [ReportUploadRequest.reportId]; a prior upload with a *different* digest for the same
     * id comes back as [SyncResult.TamperConflict], not [SyncResult.Success].
     *
     * [request] is validated locally first (see [ReportUploadRequest.validationError]); a
     * failing check returns [SyncResult.ValidationRejected] without making a network call.
     */
    suspend fun uploadReport(request: ReportUploadRequest): SyncResult<ReportUploadAck>

    /**
     * `POST /reports/{reportId}/countersign`. A role that already signed this report comes
     * back as [SyncResult.TamperConflict] (the backend's 409), not [SyncResult.Success].
     */
    suspend fun countersign(reportId: String, request: CountersignRequest): SyncResult<CountersignAck>

    /** `GET /reports/{reportId}/verify?digest=...` -- does the server's copy match [digestSha256]? */
    suspend fun verifyReport(reportId: String, digestSha256: String): SyncResult<VerifyOutcome>
}
