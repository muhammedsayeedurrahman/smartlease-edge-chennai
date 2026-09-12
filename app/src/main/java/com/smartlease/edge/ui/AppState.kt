package com.smartlease.edge.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.smartlease.edge.vision.DefectSegmenter
import java.util.UUID

data class CapturedFrame(
    val id: String = UUID.randomUUID().toString(),
    val surfaceType: String, // "Top", "Bottom", "Sides"
    val frameIndex: Int,
    val timestampMs: Long = System.currentTimeMillis(),
    val bitmap: Bitmap,
    val defects: List<DefectSegmenter.Defect> = emptyList(),
    val sharpness: Double = 0.0,
    val isTrainedModel: Boolean = true
)

data class Property(
    val id: String,
    val name: String,
    val location: String,
    val tenantName: String,
    val depositAmount: String,
    val isFlat: Boolean
)

data class Room(
    val id: String,
    val propertyId: String,
    val type: String, // Hall, Room, Kitchen, etc.
    val sqFt: String = "",
    val height: String = "",
    val width: String = "",
    val length: String = "",
    val damages: String = "",
    val topRecorded: Boolean = false,
    val bottomRecorded: Boolean = false,
    val sidesRecorded: Boolean = false,
    val frames: List<CapturedFrame> = emptyList()
)

class AppViewModel : ViewModel() {
    val properties = mutableStateListOf<Property>()
    val rooms = mutableStateListOf<Room>()

    fun addProperty(property: Property) {
        properties.add(property)
    }

    fun addRoom(room: Room) {
        rooms.add(room)
    }

    fun updateRoom(updatedRoom: Room) {
        val index = rooms.indexOfFirst { it.id == updatedRoom.id }
        if (index != -1) {
            rooms[index] = updatedRoom
        }
    }
    
    fun addFramesToRoom(roomId: String, newFrames: List<CapturedFrame>) {
        val index = rooms.indexOfFirst { it.id == roomId }
        if (index != -1) {
            val existing = rooms[index]
            val updatedFrames = existing.frames + newFrames
            rooms[index] = existing.copy(frames = updatedFrames)
        }
    }
    
    fun getRoomsForProperty(propertyId: String): List<Room> {
        return rooms.filter { it.propertyId == propertyId }
    }
}

