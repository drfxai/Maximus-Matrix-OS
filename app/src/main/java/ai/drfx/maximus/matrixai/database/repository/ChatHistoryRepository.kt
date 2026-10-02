package ai.drfx.maximus.matrixai.database.repository

import ai.drfx.maximus.matrixai.database.dao.ChatDao
import ai.drfx.maximus.matrixai.database.entities.ChatMessageEntity
import ai.drfx.maximus.matrixai.database.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

class ChatHistoryRepository(private val dao: ChatDao) {
    val sessions: Flow<List<ChatSessionEntity>> = dao.observeSessions()

    fun observeMessages(sessionId: String = "default_session"): Flow<List<ChatMessageEntity>> {
        return dao.observeMessages(sessionId)
    }

    suspend fun getMessages(sessionId: String = "default_session"): List<ChatMessageEntity> {
        return dao.getMessages(sessionId)
    }

    suspend fun saveSession(session: ChatSessionEntity) {
        dao.insertSession(session)
    }

    suspend fun saveMessage(message: ChatMessageEntity) {
        dao.insertMessage(message)
    }

    suspend fun clearSession(sessionId: String = "default_session") {
        dao.deleteMessagesForSession(sessionId)
    }

    suspend fun clearAll() {
        dao.clearAllMessages()
    }
}
