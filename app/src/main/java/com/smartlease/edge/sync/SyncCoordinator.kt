package com.smartlease.edge.sync

import com.smartlease.edge.report.InspectionReport

/**
 * Orchestrates the two after-the-fact, best-effort sync calls this app makes: uploading a
 * generated report, and recording a captured countersignature. Both functions here are the
 * single place that decides whether a network call happens at all -- see [SyncConfig.syncEnabled]
 * -- so a caller never needs to duplicate that check, and a disabled config is guaranteed to
 * never construct or send a request.
 *
 * Neither function throws: [ReportSyncClient] itself never throws out of its public methods
 * (see its class doc), and the one piece of local work these functions add --
 * [sha256HexOfBase64] in [syncCountersign] -- is wrapped so a malformed signature never
 * escapes as an exception either. Both are plain suspend functions with no Android
 * dependency, so they are unit-testable on the JVM against a fake [ReportSyncClient].
 *
 * Neither function is ever on the critical path of the offline flow: report generation, PDF
 * rendering, QR rendering and local persistence must all already be complete by the time
 * either of these is called. Callers should launch them from a coroutine scope that keeps
 * running independently of anything the screen does next (e.g. `rememberCoroutineScope()`
 * inside a `LaunchedEffect`/button handler), never from a scope the local flow itself is
 * waiting on.
 */

/**
 * Uploads [report] via [client], short-circuiting to [SyncUiState.Disabled] without touching
 * [client] at all when [config] has sync turned off.
 */
suspend fun syncReportUpload(
    report: InspectionReport,
    propertyRef: String,
    appVersion: String,
    config: SyncConfig,
    client: ReportSyncClient
): ReportUploadOutcome {
    if (!config.syncEnabled) return ReportUploadOutcome(SyncUiState.Disabled, reportToken = null)
    val request = report.toReportUploadRequest(propertyRef, appVersion)
    val result = client.uploadReport(request)
    val uiState = result.toSyncUiState { ack ->
        "Uploaded -- the server recorded this report's digest at ${ack.serverReceivedAtEpochMs}."
    }
    return ReportUploadOutcome(uiState, reportToken = result.valueOrNull()?.reportToken)
}

/**
 * What an upload attempt produced: the line to render, and the per-report token if the server
 * issued one.
 *
 * A separate type rather than an extra field on [SyncUiState.Uploaded] because the token is
 * not a UI concern at all -- no screen should ever display it -- and because [SyncUiState] is
 * shared with the countersign path, which has no token to carry. Keeping them apart means the
 * type system never offers a screen a secret it has no business rendering.
 *
 * [reportToken] is null whenever the server did not issue one: sync disabled, upload failed,
 * or an idempotent 200 re-upload of a report whose token was issued (once) on a previous
 * attempt.
 */
data class ReportUploadOutcome(
    val uiState: SyncUiState,
    val reportToken: String?
)

/**
 * Records a countersignature server-side via [client], short-circuiting to
 * [SyncUiState.Disabled] without touching [client] at all when [config] has sync turned off.
 *
 * @param signatureBase64 the raw device-key signature captured off the QR handshake --
 * hashed here (never sent as-is) to build [CountersignRequest.signatureSha256]. A value that
 * is not valid base64 is treated as a validation failure, not a crash.
 * @param reportToken the per-report secret returned by [syncReportUpload] for this report
 * ([ReportUploadAck.reportToken]). Null when this report was never uploaded from this
 * install, or the upload was an idempotent re-send that issued no token; the call still goes
 * out and comes back 403, which is reported honestly rather than hidden.
 */
suspend fun syncCountersign(
    reportId: String,
    signerRole: SignerRole,
    signatureBase64: String,
    signedAtEpochMs: Long,
    reportToken: String?,
    config: SyncConfig,
    client: ReportSyncClient
): SyncUiState {
    if (!config.syncEnabled) return SyncUiState.Disabled
    val signatureSha256 = try {
        sha256HexOfBase64(signatureBase64)
    } catch (e: IllegalArgumentException) {
        return SyncUiState.Failed(
            "Could not prepare this signature for sync (${e.message ?: "malformed signature"}). " +
                "The countersignature is saved on this phone and has not been lost."
        )
    }
    val request = CountersignRequest(
        signerRole = signerRole,
        signatureSha256 = signatureSha256,
        signedAtEpochMs = signedAtEpochMs
    )
    val result = client.countersign(reportId, reportToken, request)
    return result.toSyncUiState { ack ->
        "Countersignature synced for ${ack.signerRole.name.lowercase()} at ${ack.signedAtEpochMs}."
    }
}
