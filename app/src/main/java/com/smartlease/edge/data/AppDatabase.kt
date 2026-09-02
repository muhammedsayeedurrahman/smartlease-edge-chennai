package com.smartlease.edge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [InspectionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inspectionDao(): InspectionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // Local-only, on-device database — no cloud sync at any point in this pipeline.
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartlease.db"
                ).build().also { instance = it }
            }
    }
}
