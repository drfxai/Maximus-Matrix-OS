package ai.drfx.maximus.matrixai.database.repository

import ai.drfx.maximus.matrixai.agent.MatrixEvent
import ai.drfx.maximus.matrixai.database.dao.MatrixEventDao
import ai.drfx.maximus.matrixai.database.entities.MatrixEventEntity
import kotlinx.coroutines.flow.Flow

class MatrixEventRepository(private val dao: MatrixEventDao) {
    val recentEvents: Flow<List<MatrixEventEntity>> = dao.observeRecentEvents(100)

    suspend fun recordEvent(event: MatrixEvent) {
        val entity = MatrixEventEntity(
            id = event.id,
            missionId = event.missionId,
            type = event.type.name,
            sourceNode = event.sourceNode,
            targetNode = event.targetNode,
            message = event.message,
            timestampMs = event.timestampMs
        )
        dao.insertEvent(entity)
    }

    suspend fun getRecentEvents(limit: Int = 100): List<MatrixEventEntity> {
        return dao.getRecentEvents(limit)
    }

    suspend fun clearEvents() {
        dao.clearEvents()
    }
}

