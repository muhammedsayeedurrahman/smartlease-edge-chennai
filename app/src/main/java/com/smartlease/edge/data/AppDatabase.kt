package com.smartlease.edge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        InspectionEntity::class, 
        CountersignatureEntity::class,
        PropertyEntity::class,
        AreaEntity::class,
        RentalAgreementEntity::class
    ],
    version = 6,
    exportSchema = false
)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inspectionDao(): InspectionDao
    abstract fun propertyDao(): PropertyDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // Local-only, on-device database — no cloud sync at any point in this pipeline.
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartlease.db"
                )
                    // v1 -> v2 added sessionType/propertyLabel (move-in/move-out baseline
                    // diffing). v2 -> v3 added the countersignatures table (QR joint-inspection
                    // handshake). v5 -> v6 added RentalAgreementEntity.dosAndDontsSource, so a
                    // report can print an honest "written by Gemma" vs "raw extracted text"
                    // credit for the lease summary, the same way every other report section
                    // already does. No migration path exists yet for whatever test rows are on
                    // a device from before this change -- there is no real tenant data at stake
                    // this early, so this drops old rows rather than leaving the app unable
                    // to open its own database on next launch.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
