package com.smartlease.edge.sync

import kotlinx.serialization.Serializable

/** Success body for `POST /reports` (201, or 200 on an idempotent re-upload). */
@Serializable
data class ReportUploadAck(
    val reportId: String,
    val digestSha256: String,
    val serverReceivedAtEpochMs: Long,
    /**
     * The per-report secret, issued by the server exactly once -- in the 201 that first
     * stored this report. It authorizes the later countersign and fetch calls for THIS
     * report and no other.
     *
     * Null on a 200 idempotent re-upload: the server keeps only a hash of the token, so it
     * cannot reissue one, and a server that could would be a server that hands any caller
     * access to any report. A build that loses the token can still produce the local PDF,
     * QR and countersignature -- only the server-side copy of the countersignature is lost,
     * which is why sync is best-effort and never on the critical path.
     */
    val reportToken: String? = null
)
