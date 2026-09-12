package com.smartlease.edge.sync

import kotlinx.serialization.Serializable

/** Wire values are the enum names themselves ("TENANT" / "LANDLORD") -- see the backend contract. */
@Serializable
enum class SignerRole {
    TENANT,
    LANDLORD
}
