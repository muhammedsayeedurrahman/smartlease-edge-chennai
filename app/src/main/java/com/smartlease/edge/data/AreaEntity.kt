package com.smartlease.edge.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "areas",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("propertyId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AreaEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val name: String, // e.g., "Hall", "Master Bedroom"
    val length: Double,
    val breadth: Double,
    val height: Double?,
    val moveInVideoPath: String? = null,
    val moveOutVideoPath: String? = null,
    val moveInFrames: List<com.smartlease.edge.ui.CapturedFrame> = emptyList(),
    val moveOutFrames: List<com.smartlease.edge.ui.CapturedFrame> = emptyList()
)
