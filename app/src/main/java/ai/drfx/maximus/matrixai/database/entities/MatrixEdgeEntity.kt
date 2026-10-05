package ai.drfx.maximus.matrixai.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matrix_edges")
data class MatrixEdgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fromNodeId: String,
    val toNodeId: String,
    val relation: String,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

