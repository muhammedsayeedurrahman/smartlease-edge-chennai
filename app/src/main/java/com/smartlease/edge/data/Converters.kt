package com.smartlease.edge.data

import android.graphics.BitmapFactory
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartlease.edge.ui.CapturedFrame

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromCapturedFrameList(frames: List<CapturedFrame>?): String? {
        if (frames == null) return null
        
        // We do NOT want to serialize the actual Bitmap, just the filepath and metadata.
        // We create a lightweight DTO to serialize.
        val dtoList = frames.map { frame ->
            CapturedFrameDto(
                id = frame.id,
                surfaceType = frame.surfaceType,
                frameIndex = frame.frameIndex,
                timestampMs = frame.timestampMs,
                filePath = frame.filePath,
                defects = frame.defects,
                sharpness = frame.sharpness,
                isTrainedModel = frame.isTrainedModel
            )
        }
        val type = object : TypeToken<List<CapturedFrameDto>>() {}.type
        return gson.toJson(dtoList, type)
    }

    @TypeConverter
    fun toCapturedFrameList(framesString: String?): List<CapturedFrame>? {
        if (framesString == null) return null
        
        val type = object : TypeToken<List<CapturedFrameDto>>() {}.type
        val dtoList: List<CapturedFrameDto> = gson.fromJson(framesString, type)
        
        return dtoList.map { dto ->
            // Rehydrate the bitmap lazily if a file path exists
            val bitmap = if (dto.filePath != null) {
                BitmapFactory.decodeFile(dto.filePath) ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            } else {
                android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            }
            
            CapturedFrame(
                id = dto.id,
                surfaceType = dto.surfaceType,
                frameIndex = dto.frameIndex,
                timestampMs = dto.timestampMs,
                bitmap = bitmap,
                filePath = dto.filePath,
                defects = dto.defects,
                sharpness = dto.sharpness,
                isTrainedModel = dto.isTrainedModel
            )
        }
    }
}

data class CapturedFrameDto(
    val id: String,
    val surfaceType: String,
    val frameIndex: Int,
    val timestampMs: Long,
    val filePath: String?,
    val defects: List<com.smartlease.edge.vision.DefectSegmenter.Defect>,
    val sharpness: Double,
    val isTrainedModel: Boolean
)
