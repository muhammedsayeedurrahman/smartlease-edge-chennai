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
            val resolver = context.contentResolver
            val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val fileName = "SmartLease_${propertyName}_${areaName}_${sessionType}_${System.currentTimeMillis()}.mp4"

            val videoDetails = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SmartLease")
                }
            }

            val videoUri = resolver.insert(videoCollection, videoDetails)

            videoUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(videoFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    videoDetails.clear()
                    videoDetails.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, videoDetails, null, null)
                }
            }
            videoUri
        }
    }
}
