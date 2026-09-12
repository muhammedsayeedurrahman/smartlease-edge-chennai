package com.smartlease.edge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "properties")
data class PropertyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val tenantName: String,
    val depositAmount: Double,
    val isFlat: Boolean,
    val creationEpochMillis: Long = System.currentTimeMillis()
)
