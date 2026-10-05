package ai.drfx.maximus.matrixai.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matrix_nodes")
data class MatrixNodeEntity(
    @PrimaryKey val id: String,
    val label: String,
    val groupName: String,
    val x: Float,
    val y: Float,
    val radius: Float = 16f,
    val colorHex: String = "#38C79B",
    val description: String = "",
    val relations: String = "",
    val isHub: Boolean = false,
    val isCustom: Boolean = false,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

