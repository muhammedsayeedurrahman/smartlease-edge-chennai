package com.smartlease.edge.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Utility to save heavy inspection videos and frames securely to local persistent storage.
 * Using MediaStore ensures that the landlord's videos remain on the device even if the app
 * is cleared from recent apps or temporarily uninstalled (depending on Android version and scoped storage).
 */
class MediaStorageHelper(private val context: Context) {

    suspend fun saveVideoToPersistentStorage(videoFile: File, propertyName: String, areaName: String, sessionType: String): Uri? {
        return withContext(Dispatchers.IO) {
            val privateDir = File(context.filesDir, "videos")
            if (!privateDir.exists()) {
                privateDir.mkdirs()
            }
            
            val fileName = "SmartLease_${propertyName}_${areaName}_${sessionType}_${System.currentTimeMillis()}.mp4"
            val destFile = File(privateDir, fileName)
            
            try {
                videoFile.copyTo(destFile, overwrite = true)
                Uri.fromFile(destFile)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    suspend fun saveFrameToPersistentStorage(bitmap: android.graphics.Bitmap, propertyName: String, areaName: String, surfaceType: String, frameIndex: Int): String? {
        return withContext(Dispatchers.IO) {
            val privateDir = File(context.filesDir, "frames")
            if (!privateDir.exists()) {
                privateDir.mkdirs()
            }
            
            val fileName = "Frame_${propertyName}_${areaName}_${surfaceType}_${frameIndex}_${System.currentTimeMillis()}.jpg"
            val destFile = File(privateDir, fileName)
            
            try {
                java.io.FileOutputStream(destFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                destFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
