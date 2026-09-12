package com.smartlease.edge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One device-signed countersignature captured over a report's findings digest via the
 * two-phone QR handshake ([com.smartlease.edge.report.CountersignPayload]). [digestHex] is
 * stored alongside the signature (rather than trusted to still match the report at read
 * time) so a stale signature -- captured against an earlier version of a report that has
 * since been re-rendered with new findings -- can be detected and refused rather than
 * silently displayed as valid.
 *
 * This proves a specific device's hardware-backed key signed this digest. It does not
 * identify the person holding that device.
 */
@Entity(tableName = "countersignatures")
data class CountersignatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val digestHex: String,
    val signatureBase64: String,
    val certificateBase64: String,
    val hardwareBacked: Boolean,
    val capturedAtEpochMillis: Long
)
