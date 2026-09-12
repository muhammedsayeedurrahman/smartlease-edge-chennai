package com.smartlease.edge.sync

import kotlinx.serialization.Serializable

/** The `{"error": {"code": ..., "message": ...}}` envelope every non-2xx response uses. */
@Serializable
data class SyncErrorResponse(
    val error: SyncErrorDetail
)

@Serializable
data class SyncErrorDetail(
    val code: String,
    val message: String
)
