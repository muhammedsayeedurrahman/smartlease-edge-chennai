package com.smartlease.edge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One inspection event (a single Tier-1 finding, OCR read, or IR verification result)
 * within a walkthrough session. Local-only — see AndroidManifest's backup exclusion rules.
 */
@Entity(tableName = "inspection_findings")
data class InspectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestampEpochMillis: Long,
    val findingType: FindingType,
    val label: String,
    val detailJson: String,
    val severity: Severity,
    val latitude: Double? = null,
    val longitude: Double? = null
)

enum class FindingType {
    VISUAL_DEFECT,
    ACOUSTIC_TAP,
    IR_APPLIANCE_CHECK,
    OCR_TEXT_READ,
    AR_BASELINE_ALIGNMENT
}

enum class Severity {
    INFO,
    NOTABLE,
    STOP_ESCALATE
}
