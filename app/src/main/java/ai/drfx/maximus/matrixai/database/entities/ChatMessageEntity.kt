package ai.drfx.maximus.matrixai.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String = "default_session",
    val role: String,
    val content: String,
    val isError: Boolean = false,
    val fromVoice: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis(),
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null
)
