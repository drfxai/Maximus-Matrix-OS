package ai.drfx.maximus.matrixai.database.dao

import androidx.room.*
import ai.drfx.maximus.matrixai.database.entities.MatrixEdgeEntity
import ai.drfx.maximus.matrixai.database.entities.MatrixNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatrixGraphDao {
    @Query("SELECT * FROM matrix_nodes ORDER BY isHub DESC, label ASC")
    fun observeNodes(): Flow<List<MatrixNodeEntity>>

    @Query("SELECT * FROM matrix_nodes")
    suspend fun getAllNodes(): List<MatrixNodeEntity>

    @Query("SELECT * FROM matrix_edges")
    fun observeEdges(): Flow<List<MatrixEdgeEntity>>

    @Query("SELECT * FROM matrix_edges")
    suspend fun getAllEdges(): List<MatrixEdgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<MatrixNodeEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: MatrixNodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdges(edges: List<MatrixEdgeEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM matrix_nodes")
    suspend fun getNodeCount(): Int

    @Query("DELETE FROM matrix_nodes WHERE id = :id")
    suspend fun deleteNode(id: String): Int

    @Query("DELETE FROM matrix_nodes")
    suspend fun clearNodes(): Int

    @Query("DELETE FROM matrix_edges")
    suspend fun clearEdges(): Int
}
