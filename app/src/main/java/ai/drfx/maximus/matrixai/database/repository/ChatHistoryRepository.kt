package ai.drfx.maximus.matrixai.database.repository

import ai.drfx.maximus.matrixai.database.dao.ChatDao
import ai.drfx.maximus.matrixai.database.entities.ChatMessageEntity
import ai.drfx.maximus.matrixai.database.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ai.drfx.maximus.matrixai.database.ChatContentCipher

class ChatHistoryRepository(private val dao: ChatDao, private val cipher: ChatContentCipher? = null) {
    val sessions: Flow<List<ChatSessionEntity>> = dao.observeSessions()

    fun observeMessages(sessionId: String = "default_session"): Flow<List<ChatMessageEntity>> {
        return dao.observeMessages(sessionId).map { messages -> messages.map(::decode) }
    }

    suspend fun getMessages(sessionId: String = "default_session"): List<ChatMessageEntity> {
        return dao.getMessages(sessionId).map(::decode)
    }

    suspend fun getSession(sessionId: String): ChatSessionEntity? = dao.getSession(sessionId)

    suspend fun renameSession(sessionId: String, title: String) {
        require(title.isNotBlank() && title.length <= 200) { "Conversation title must contain 1–200 characters" }
        dao.renameSession(sessionId, title.trim(), System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: String) = dao.deleteSessionWithMessages(sessionId)

    suspend fun saveSession(session: ChatSessionEntity) {
        dao.insertSession(session)
    }

    suspend fun saveMessage(message: ChatMessageEntity) {
        dao.insertMessage(message.copy(content = cipher?.encrypt(message.content) ?: message.content))
    }

    suspend fun updateMessageContent(sessionId: String, timestampMs: Long, role: String, content: String): Int =
        dao.updateMessageContent(sessionId, timestampMs, role, cipher?.encrypt(content) ?: content)

    suspend fun clearSession(sessionId: String = "default_session") {
        dao.deleteMessagesForSession(sessionId)
    }

    private fun decode(message: ChatMessageEntity): ChatMessageEntity {
        if (cipher == null) return message
        return try { message.copy(content = cipher.decrypt(message.content)) }
        catch (_: Exception) { message.copy(content = "Encrypted message unavailable on this device", isError = true) }
    }

    suspend fun clearAll() {
        dao.clearAllMessages()
    }
}

