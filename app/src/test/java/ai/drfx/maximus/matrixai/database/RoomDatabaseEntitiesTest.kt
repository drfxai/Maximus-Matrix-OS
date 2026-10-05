package ai.drfx.maximus.matrixai.database

import ai.drfx.maximus.matrixai.database.entities.ChatMessageEntity
import ai.drfx.maximus.matrixai.database.entities.ChatSessionEntity
import ai.drfx.maximus.matrixai.database.entities.MatrixEdgeEntity
import ai.drfx.maximus.matrixai.database.entities.MatrixEventEntity
import ai.drfx.maximus.matrixai.database.entities.MatrixNodeEntity
import ai.drfx.maximus.matrixai.database.entities.UserSessionDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDatabaseEntitiesTest {

    @Test
    fun matrixNodeEntityProperties() {
        val node = MatrixNodeEntity(
            id = "test_node",
            label = "Test Node",
            groupName = "TEST GROUP",
            x = 100f,
            y = 200f,
            radius = 18f,
            colorHex = "#38C79B",
            description = "Test description",
            relations = "Related Node",
            isHub = true,
            isCustom = true
        )
        assertEquals("test_node", node.id)
        assertEquals("Test Node", node.label)
        assertEquals("TEST GROUP", node.groupName)
        assertTrue(node.isHub)
        assertTrue(node.isCustom)
    }

    @Test
    fun matrixEdgeEntityProperties() {
        val edge = MatrixEdgeEntity(
            fromNodeId = "node_a",
            toNodeId = "node_b",
            relation = "ORCHESTRATES"
        )
        assertEquals("node_a", edge.fromNodeId)
        assertEquals("node_b", edge.toNodeId)
        assertEquals("ORCHESTRATES", edge.relation)
    }

    @Test
    fun chatSessionAndMessageEntities() {
        val session = ChatSessionEntity(
            id = "sess_01",
            title = "Session 1",
            provider = "OPENAI",
            model = "gpt-4o",
            agentId = "executive"
        )
        assertEquals("sess_01", session.id)
        assertEquals("OPENAI", session.provider)

        val message = ChatMessageEntity(
            sessionId = session.id,
            role = "user",
            content = "Hello Matrix",
            fromVoice = false,
            attachmentName = "doc.pdf",
            attachmentMimeType = "application/pdf"
        )
        assertEquals("sess_01", message.sessionId)
        assertEquals("user", message.role)
        assertEquals("Hello Matrix", message.content)
        assertEquals("doc.pdf", message.attachmentName)
    }

    @Test
    fun matrixEventAndUserSessionEntities() {
        val event = MatrixEventEntity(
            id = "evt_01",
            missionId = "miss_01",
            type = "MISSION_ACCEPTED",
            sourceNode = "executive",
            targetNode = "tools",
            message = "Mission started"
        )
        assertEquals("evt_01", event.id)
        assertEquals("MISSION_ACCEPTED", event.type)

        val sessionData = UserSessionDataEntity(
            key = "selected_theme",
            value = "DARK",
            category = "preferences"
        )
        assertEquals("selected_theme", sessionData.key)
        assertEquals("DARK", sessionData.value)
    }
}

