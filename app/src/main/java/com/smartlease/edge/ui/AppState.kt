package com.smartlease.edge.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

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
    val sidesRecorded: Boolean = false
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
    
    fun getRoomsForProperty(propertyId: String): List<Room> {
        return rooms.filter { it.propertyId == propertyId }
    }
}
