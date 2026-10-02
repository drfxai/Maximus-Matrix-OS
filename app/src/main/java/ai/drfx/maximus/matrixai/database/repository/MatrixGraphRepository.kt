package ai.drfx.maximus.matrixai.database.repository

import ai.drfx.maximus.matrixai.database.dao.MatrixGraphDao
import ai.drfx.maximus.matrixai.database.entities.MatrixEdgeEntity
import ai.drfx.maximus.matrixai.database.entities.MatrixNodeEntity
import kotlinx.coroutines.flow.Flow

class MatrixGraphRepository(private val dao: MatrixGraphDao) {
    val nodes: Flow<List<MatrixNodeEntity>> = dao.observeNodes()
    val edges: Flow<List<MatrixEdgeEntity>> = dao.observeEdges()

    suspend fun getNodeCount(): Int = dao.getNodeCount()

    suspend fun saveNode(node: MatrixNodeEntity) = dao.insertNode(node)

    suspend fun saveNodes(nodes: List<MatrixNodeEntity>) = dao.insertNodes(nodes)

    suspend fun saveEdges(edges: List<MatrixEdgeEntity>) = dao.insertEdges(edges)

    suspend fun deleteNode(id: String) = dao.deleteNode(id)

    suspend fun clearGraph() {
        dao.clearEdges()
        dao.clearNodes()
    }
}
