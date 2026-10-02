package ai.drfx.maximus.matrixai.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matrix_events")
data class MatrixEventEntity(
    @PrimaryKey val id: String,
    val missionId: String,
    val type: String,
    val sourceNode: String,
    val targetNode: String? = null,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
)
