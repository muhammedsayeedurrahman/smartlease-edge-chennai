package com.smartlease.edge.sync

import kotlinx.serialization.Serializable

/** Success body for `GET /health`. */
@Serializable
data class HealthStatus(
    val status: String,
    val version: String
)
