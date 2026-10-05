package ai.drfx.maximus.matrixai.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_session_data")
data class UserSessionDataEntity(
    @PrimaryKey val key: String,
    val value: String,
    val category: String = "general",
    val updatedMs: Long = System.currentTimeMillis()
)

