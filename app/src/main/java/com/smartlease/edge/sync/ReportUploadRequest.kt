package com.smartlease.edge.sync

import kotlinx.serialization.Serializable

/**
 * Outgoing body for `POST /reports`. Deliberately carries only a digest and small counters --
 * there is no bitmap, byte-array, or file-path field on this class, so a caller cannot attach
 * photo/video/audio bytes to it even by accident. The server stores this shape and nothing
 * else; see the package-level note in [ReportSyncClient].
 */
@Serializable
data class ReportUploadRequest(
    val reportId: String,
    val propertyRef: String,
    val sessionType: SessionType,
    val createdAtEpochMs: Long,
    /** Lowercase hex SHA-256, 64 chars -- see [FindingsDigest]/[ReportFingerprint] upstream. */
    val digestSha256: String,
    val appVersion: String,
    val findingsCount: Int,
    val depositRupees: Int,
    val totalDeductionRupees: Int
)
