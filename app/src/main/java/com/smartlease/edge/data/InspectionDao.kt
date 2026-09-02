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
}
