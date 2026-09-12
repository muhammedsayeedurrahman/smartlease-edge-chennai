package com.smartlease.edge.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.withContext
import com.smartlease.edge.vision.DefectSegmenter
import com.smartlease.edge.vision.DefectSegmenterFactory
import com.smartlease.edge.vision.HeuristicDefectSegmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "AppViewModel"

data class CapturedFrame(
    val id: String = UUID.randomUUID().toString(),
    val surfaceType: String, // "Top", "Bottom", "Sides"
    val frameIndex: Int,
    val timestampMs: Long = System.currentTimeMillis(),
    val bitmap: Bitmap,
    val defects: List<DefectSegmenter.Defect> = emptyList(),
    val sharpness: Double = 0.0,
    // No default: overclaiming a trained model is a correctness bug in this project, so
    // every call site must state explicitly which backend produced this frame's defects.
    val isTrainedModel: Boolean
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

/**
 * Lifecycle of the on-device defect segmenter, session-scoped in [AppViewModel] so it is
 * loaded (asset copy + native TorchScript load + probe inference) exactly once per app
 * session rather than once per [com.smartlease.edge.ui.screens.VideoCaptureScreen] visit.
 */
sealed interface SegmenterState {
    data object Loading : SegmenterState
    data class Ready(val segmenter: DefectSegmenter) : SegmenterState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val properties = mutableStateListOf<Property>()
    val rooms = mutableStateListOf<Room>()

    private val dao = com.smartlease.edge.data.AppDatabase.get(application).propertyDao()

    // Off-main-thread home for the one-time vision-model bring-up. Cancelled in onCleared()
    // so no load work outlives this ViewModel.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        
        // Load properties and rooms from the database
        backgroundScope.launch {
            val dbProperties = dao.getAllProperties()
            val mappedProps = dbProperties.map {
                Property(it.id, it.name, it.address, it.tenantName, it.depositAmount.toString(), it.isFlat)
            }
            
            val allRooms = mutableListOf<Room>()
            for (p in dbProperties) {
                val dbRooms = dao.getAreasForProperty(p.id)
                allRooms.addAll(dbRooms.map {
                    Room(it.id, it.propertyId, it.name, 
                         sqFt = if (it.height != null) "${it.length * it.breadth * it.height}" else "${it.length * it.breadth}",
                         length = it.length.toString(), width = it.breadth.toString(), height = it.height?.toString() ?: "")
                })
            }
            withContext(Dispatchers.Main) {
                properties.clear()
                properties.addAll(mappedProps)
                rooms.clear()
                rooms.addAll(allRooms)
            }
        }
    }



    var segmenterState: SegmenterState by mutableStateOf(SegmenterState.Loading)
        private set

    private var segmenterLoadStarted = false

    /**
     * Kicks off the defect segmenter load exactly once per app session, off the main
     * thread. Safe to call from every VideoCaptureScreen visit -- the guard makes every
     * call after the first a no-op, so navigating Top/Bottom/Sides (and across rooms)
     * reuses the same loaded model instead of re-paying disk I/O + native load + a
     * 640x640 probe inference on every visit.
     */
    fun ensureSegmenterLoaded(appContext: Context) {
        if (segmenterLoadStarted) return
        segmenterLoadStarted = true
        backgroundScope.launch {
            val segmenter = try {
                DefectSegmenterFactory.create(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The factory itself already falls back to the heuristic segmenter for the
                // expected failure modes (missing asset, bad export). This catch only
                // guards against something unexpected (e.g. disk I/O failure copying the
                // asset) -- log it loudly and degrade to the honestly-labelled heuristic
                // segmenter rather than leaving the screen stuck on "Preparing...".
                Log.e(TAG, "Vision segmenter failed to initialize; falling back to heuristic", e)
                HeuristicDefectSegmenter()
            }
            segmenterState = SegmenterState.Ready(segmenter)
        }
    }

    override fun onCleared() {
        super.onCleared()
        backgroundScope.cancel()
    }

    fun addProperty(property: Property) {
        properties.add(property)
        backgroundScope.launch {
            dao.insertProperty(com.smartlease.edge.data.PropertyEntity(
                id = property.id,
                name = property.name,
                address = property.location,
                tenantName = property.tenantName,
                depositAmount = property.depositAmount.toDoubleOrNull() ?: 0.0,
                isFlat = property.isFlat
            ))
        }
    }

    fun addRoom(room: Room) {
        rooms.add(room)
        backgroundScope.launch {
            dao.insertArea(com.smartlease.edge.data.AreaEntity(
                id = room.id,
                propertyId = room.propertyId,
                name = room.type,
                length = room.length.toDoubleOrNull() ?: 0.0,
                breadth = room.width.toDoubleOrNull() ?: 0.0,
                height = room.height.toDoubleOrNull()
            ))
        }
    }

    fun updateRoom(updatedRoom: Room) {
        val index = rooms.indexOfFirst { it.id == updatedRoom.id }
        if (index != -1) {
            rooms[index] = updatedRoom
            backgroundScope.launch {
                dao.insertArea(com.smartlease.edge.data.AreaEntity(
                    id = updatedRoom.id,
                    propertyId = updatedRoom.propertyId,
                    name = updatedRoom.type,
                    length = updatedRoom.length.toDoubleOrNull() ?: 0.0,
                    breadth = updatedRoom.width.toDoubleOrNull() ?: 0.0,
                    height = updatedRoom.height.toDoubleOrNull()
                ))
            }
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

