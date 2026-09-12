package com.smartlease.edge.sync

import kotlinx.serialization.Serializable

/** Outgoing body for `POST /reports/{reportId}/countersign`. */
@Serializable
data class CountersignRequest(
    val signerRole: SignerRole,
    /** Lowercase hex SHA-256, 64 chars. */
    val signatureSha256: String,
    val signedAtEpochMs: Long
)
