package com.smartlease.edge.inspection360

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.smartlease.edge.vision.DefectSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoInspectionCoordinator(
    private val defectSegmenter: DefectSegmenter
) {

    /**
     * Processes a recorded 360-degree video, extracts 4 frames (representing 4 walls),
     * runs defect segmentation on each frame, and returns a combined list of defects.
     *
     * @param videoPath Local file path to the recorded .mp4 video.
     * @return List of RoomDefectRecord found across all 4 walls.
     */
    suspend fun analyzeVideo(videoPath: String): List<RoomDefectRecord> = withContext(Dispatchers.Default) {
        val capturedRecords = mutableListOf<RoomDefectRecord>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(videoPath)

            // Get video duration in milliseconds
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            if (durationMs < 4000L) {
                Log.w("VideoInspection", "Video is too short (${durationMs}ms) to extract 4 distinct walls safely.")
            }

            // Extract 4 evenly spaced frames to represent 4 walls.
            // Avoid extracting at exactly 0 or at the very end.
            val numFrames = 4
            val stepMs = durationMs / (numFrames + 1)

            for (i in 1..numFrames) {
                val timeMs = stepMs * i
                // timeMs is converted to microseconds for getFrameAtTime
                val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                if (bitmap != null) {
                    val wallName = "WALL_$i"
                    // Assume standard mobile phone camera dimensions and a default focal distance
                    // Since we lack ARCore depth in this offline video, we estimate the wall is 10 feet away.
                    // This gives a rough frame width/height in inches for area estimation.
                    val estimatedFrameWidthInches = 120f
                    val estimatedFrameHeightInches = 160f

                    val defects = defectSegmenter.segmentDefects(
                        bitmap = bitmap,
                        frameWidthInches = estimatedFrameWidthInches,
                        frameHeightInches = estimatedFrameHeightInches
                    )

                    for (defect in defects) {
                        capturedRecords.add(
                            RoomDefectRecord(
                                wall = wallName,
                                defectClass = defect.defectClass,
                                label = defect.label,
                                areaSqFt = defect.areaSqFtEstimate,
                                confidence = defect.confidence
                            )
                        )
                    }

                    // Free the bitmap memory early
                    bitmap.recycle()
                } else {
                    Log.e("VideoInspection", "Failed to extract frame at ${timeMs}ms")
                }
            }
        } catch (e: Exception) {
            Log.e("VideoInspection", "Error extracting frames from video", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }

        return@withContext capturedRecords
    }
}
