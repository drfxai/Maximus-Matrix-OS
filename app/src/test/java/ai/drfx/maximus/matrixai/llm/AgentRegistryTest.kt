package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRegistryTest {
    @Test
    fun chatDescriptorsExplicitlyIdentifyPersonas() {
        AgentRegistry.agents.forEach {
            assertTrue(it.executionKind.contains("no executable tools"))
        }
    }

    @Test
    fun chatOnlyModelDoesNotExposeToolAgents() {
        val supported = AgentRegistry.supportedAgents(setOf(ModelCapability.CHAT)).map { it.id }
        assertTrue("general" in supported)
        assertTrue("trading" in supported)
        assertFalse("executive" in supported)
        assertFalse("automation" in supported)
        assertFalse("vision" in supported)
    }

    @Test
    fun capableModelExposesAdvancedAgents() {
        val capabilities = setOf(
            ModelCapability.CHAT,
            ModelCapability.TOOLS,
            ModelCapability.VISION,
            ModelCapability.LONG_CONTEXT,
            ModelCapability.STRUCTURED_OUTPUT,
            ModelCapability.REASONING
        )
        val supported = AgentRegistry.supportedAgents(capabilities).map { it.id }
        assertTrue("executive" in supported)
        assertTrue("research" in supported)
        assertTrue("vision" in supported)
        assertTrue("validation" in supported)
        assertTrue("quant" in supported)
    }
}
