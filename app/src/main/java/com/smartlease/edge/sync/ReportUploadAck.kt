package com.smartlease.edge.sync

import kotlinx.serialization.Serializable

/** Success body for `POST /reports` (201, or 200 on an idempotent re-upload). */
@Serializable
data class ReportUploadAck(
    val reportId: String,
    val digestSha256: String,
    val serverReceivedAtEpochMs: Long
)
