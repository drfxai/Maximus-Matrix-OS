package ai.drfx.maximus.matrixai.database

import ai.drfx.maximus.matrixai.database.entities.MatrixNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DatabaseModuleTest {

    @Test
    fun appDatabaseConfiguration() {
        assertEquals("maximus_matrix.db", AppDatabase.DATABASE_NAME)
        val node = MatrixNodeEntity(
            id = "maximus",
            label = "MAXIMUS",
            groupName = "MATRIX CORE",
            x = 0f,
            y = 0f,
            isHub = true
        )
        assertNotNull(node)
        assertEquals("MAXIMUS", node.label)
    }
}
