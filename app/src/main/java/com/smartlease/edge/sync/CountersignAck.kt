package com.smartlease.edge.sync

/**
 * Success outcome of a countersign call. The backend contract does not specify a response
 * body shape for this endpoint (only the 201 status), so this is built locally from the
 * request that was just accepted rather than parsed from JSON -- there is nothing to
 * misparse if the body is empty or the server ever adds fields to it.
 */
data class CountersignAck(
    val reportId: String,
    val signerRole: SignerRole,
    val signedAtEpochMs: Long
)
