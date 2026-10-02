package ai.drfx.maximus.matrixai.database.dao

import androidx.room.*
import ai.drfx.maximus.matrixai.database.entities.MatrixEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatrixEventDao {
    @Query("SELECT * FROM matrix_events ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 100): Flow<List<MatrixEventEntity>>

    @Query("SELECT * FROM matrix_events WHERE missionId = :missionId ORDER BY timestampMs ASC")
    fun observeEventsForMission(missionId: String): Flow<List<MatrixEventEntity>>

    @Query("SELECT * FROM matrix_events ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 100): List<MatrixEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: MatrixEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<MatrixEventEntity>): List<Long>

    @Query("DELETE FROM matrix_events")
    suspend fun clearEvents(): Int
}
