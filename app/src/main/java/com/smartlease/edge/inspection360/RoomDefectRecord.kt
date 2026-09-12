package com.smartlease.edge.inspection360

/**
 * Represents a defect found on one of the 4 walls during a 360-degree inspection.
 */
data class RoomDefectRecord(
    val wall: String,           // e.g., "WALL_1", "WALL_2"
    val defectClass: String,    // e.g., "water-stain", "possible discoloration/damage"
    val areaSqFt: Float,        // Estimated area in square feet
    val confidence: Float       // Model confidence (0.0 to 1.0)
)
