package com.smartlease.edge.sync

import kotlinx.serialization.Serializable

/** Wire values are the enum names themselves ("MOVE_IN" / "MOVE_OUT") -- see the backend contract. */
@Serializable
enum class SessionType {
    MOVE_IN,
    MOVE_OUT
}
