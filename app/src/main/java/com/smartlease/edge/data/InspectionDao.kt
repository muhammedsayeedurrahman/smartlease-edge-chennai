package com.smartlease.edge.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {
    @Insert
    suspend fun insert(finding: InspectionEntity): Long

    @Query("SELECT * FROM inspection_findings WHERE sessionId = :sessionId ORDER BY timestampEpochMillis ASC")
    fun findingsForSession(sessionId: String): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspection_findings WHERE sessionId = :sessionId ORDER BY timestampEpochMillis ASC")
    suspend fun findingsForSessionOnce(sessionId: String): List<InspectionEntity>

    /** The most recent session of [sessionType] recorded for this property, if any. */
    @Query(
        "SELECT sessionId FROM inspection_findings WHERE propertyLabel = :propertyLabel " +
            "AND sessionType = :sessionType ORDER BY timestampEpochMillis DESC LIMIT 1"
    )
    suspend fun mostRecentSessionId(propertyLabel: String, sessionType: SessionType): String?
}
