package com.smartlease.edge.inspection360

/**
 * Represents a defect found on one of the 4 walls during a 360-degree inspection.
 */
data class RoomDefectRecord(
    val wall: String,           // e.g., "WALL_1", "WALL_2"
    /**
     * Canonical model class, e.g. "crack" — the key the rate card is written in. The sentinel
     * [CLEAR] means the wall was inspected and nothing was flagged, which is a finding in its
     * own right and must not be confused with "not inspected".
     */
    val defectClass: String,
    /** Tenant-facing description, e.g. "crack in wall surface". Display only. */
    val label: String,
    val areaSqFt: Float,        // Estimated area in square feet
    val confidence: Float       // Model confidence (0.0 to 1.0)
) {
    companion object {
        const val CLEAR = "CLEAR"
    }
}
