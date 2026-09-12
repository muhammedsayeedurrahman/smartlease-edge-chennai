package com.smartlease.edge.sync

import kotlinx.serialization.Serializable

/** Success body for `GET /reports/{reportId}/verify`. */
@Serializable
data class VerifyOutcome(
    val reportId: String,
    val matches: Boolean
)
