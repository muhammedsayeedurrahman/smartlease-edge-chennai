package com.smartlease.edge.inspection360

import android.graphics.Bitmap
import com.smartlease.edge.vision.DefectSegmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomInspectionCoordinator(
    private val defectSegmenter: DefectSegmenter,
    private val onWallFrameProcessed: ((Quadrant, Bitmap, List<DefectSegmenter.Defect>, Float) -> Unit)? = null,
    private val onInspectionFinished: (List<RoomDefectRecord>) -> Unit
) {
    private val capturedRecords = mutableListOf<RoomDefectRecord>()

    fun onWallKeyframeAcquired(
        quadrant: Quadrant,
        bitmap: Bitmap,
        thumbnail: Bitmap,
        zDistance: Float,
        azimuth: Float = 0f
    ) {
        // Run inference in background coroutine pool
        CoroutineScope(Dispatchers.Default).launch {
            // Assume 120x160 inches for typical wall framing without AR depth
            val estimatedFrameWidthInches = 120f
            val estimatedFrameHeightInches = 160f

            val defects = defectSegmenter.segmentDefects(
                bitmap,
                estimatedFrameWidthInches,
                estimatedFrameHeightInches
            )

            synchronized(capturedRecords) {
                // If there are no defects, maybe we log a "CLEAN" record, but for now we log found defects.
                for (defect in defects) {
                    capturedRecords.add(
                        RoomDefectRecord(
                            wall = quadrant.name,
                            defectClass = defect.defectClass,
                            label = defect.label,
                            areaSqFt = defect.areaSqFtEstimate,
                            confidence = defect.confidence
                        )
                    )
                }
                
                // Also track that this quadrant is done, even if 0 defects found
                // We can use a special "NO_DEFECTS" tag to ensure the quadrant count increments 
                if (defects.isEmpty()) {
                     capturedRecords.add(
                        RoomDefectRecord(
                            wall = quadrant.name,
                            defectClass = RoomDefectRecord.CLEAR,
                            label = RoomDefectRecord.CLEAR,
                            areaSqFt = 0f,
                            confidence = 1.0f
                        )
                    )
                }
            }
            
            // Free the heavy inference bitmap; the lightweight thumbnail stays valid for UI display
            bitmap.recycle()

            withContext(Dispatchers.Main) {
                onWallFrameProcessed?.invoke(quadrant, thumbnail, defects, azimuth)
            }

            // If all 4 walls captured (NORTH, EAST, SOUTH, WEST), finalize
            val distinctWalls = capturedRecords.map { it.wall }.distinct()
            if (distinctWalls.size == 4) {
                withContext(Dispatchers.Main) {
                    onInspectionFinished(capturedRecords)
                }
            }
        }
    }
}
