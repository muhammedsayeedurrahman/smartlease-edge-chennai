package com.smartlease.edge.data.drive

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles the offline-first app's backup mechanism to Google Drive via SAF.
 */
class GoogleDriveBackupHelper(private val context: Context) {
    private val TAG = "GoogleDriveBackup"

    private fun getGson(): com.google.gson.Gson {
        return com.google.gson.GsonBuilder()
            .registerTypeAdapter(
                object : com.google.gson.reflect.TypeToken<List<com.smartlease.edge.ui.CapturedFrame>>() {}.type,
                com.google.gson.JsonSerializer<List<com.smartlease.edge.ui.CapturedFrame>> { src, _, _ ->
                    val jsonStr = com.smartlease.edge.data.Converters().fromCapturedFrameList(src)
                    if (jsonStr != null) com.google.gson.JsonParser.parseString(jsonStr) else com.google.gson.JsonArray()
                }
            )
            .registerTypeAdapter(
                object : com.google.gson.reflect.TypeToken<List<com.smartlease.edge.ui.CapturedFrame>>() {}.type,
                com.google.gson.JsonDeserializer<List<com.smartlease.edge.ui.CapturedFrame>> { json, _, _ ->
                    com.smartlease.edge.data.Converters().toCapturedFrameList(json.toString()) ?: emptyList()
                }
            )
            .create()
    }

    /**
     * Zips the RoomDB, Rental Agreements, Videos, and AI Frames to the provided Uri.
     */
    suspend fun exportFullBackupToDrive(uri: Uri) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting full backup process to $uri...")
            
            val db = com.smartlease.edge.data.AppDatabase.get(context)
            val properties = db.propertyDao().getAllProperties()
            
            val gson = getGson()
            val dataObj = com.google.gson.JsonObject()
            val propsArray = com.google.gson.JsonArray()
            
            for (p in properties) {
                val pObj = gson.toJsonTree(p).asJsonObject
                val areas = db.propertyDao().getAreasForProperty(p.id)
                pObj.add("areas", gson.toJsonTree(areas))
                
                val agreement = db.propertyDao().getRentalAgreementForProperty(p.id)
                if (agreement != null) {
                    pObj.add("rentalAgreement", gson.toJsonTree(agreement))
                }
                propsArray.add(pObj)
            }
            dataObj.add("properties", propsArray)
            val jsonString = dataObj.toString()

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                java.util.zip.ZipOutputStream(outputStream).use { zos ->
                    // 1. Write JSON
                    val entry = java.util.zip.ZipEntry("data.json")
                    zos.putNextEntry(entry)
                    zos.write(jsonString.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                    
                    // 2. Write Media Files
                    val mediaDir = java.io.File(context.filesDir, "media")
                    if (mediaDir.exists()) {
                        mediaDir.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val relPath = "media/${file.name}"
                                val zipEntry = java.util.zip.ZipEntry(relPath)
                                zos.putNextEntry(zipEntry)
                                file.inputStream().use { fis -> fis.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "Backup successfully uploaded to Google Drive.")
        }
    }

    /**
     * Downloads the backup ZIP from the provided Uri and restores the local database and media.
     */
    suspend fun importBackupFromDrive(uri: Uri) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting restoration process from $uri...")
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                java.util.zip.ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "data.json") {
                            val jsonString = zis.readBytes().toString(Charsets.UTF_8)
                            val dataObj = com.google.gson.JsonParser.parseString(jsonString).asJsonObject
                            val propsArray = dataObj.getAsJsonArray("properties")
                            
                            val db = com.smartlease.edge.data.AppDatabase.get(context)
                            val gson = getGson()
                            
                            propsArray.forEach { pElem ->
                                val pObj = pElem.asJsonObject
                                val prop = gson.fromJson(pObj, com.smartlease.edge.data.PropertyEntity::class.java)
                                db.propertyDao().insertProperty(prop)
                                
                                val areas = pObj.getAsJsonArray("areas")
                                areas?.forEach { aElem ->
                                    val area = gson.fromJson(aElem, com.smartlease.edge.data.AreaEntity::class.java)
                                    db.propertyDao().insertArea(area)
                                }
                                
                                if (pObj.has("rentalAgreement")) {
                                    val agree = gson.fromJson(pObj.getAsJsonObject("rentalAgreement"), com.smartlease.edge.data.RentalAgreementEntity::class.java)
                                    db.propertyDao().insertRentalAgreement(agree)
                                }
                            }
                        } else if (entry.name.startsWith("media/")) {
                            val mediaDir = java.io.File(context.filesDir, "media")
                            mediaDir.mkdirs()
                            val destFile = java.io.File(context.filesDir, entry.name)
                            java.io.FileOutputStream(destFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            
            Log.d(TAG, "Data successfully restored from Google Drive.")
        }
    }
}
