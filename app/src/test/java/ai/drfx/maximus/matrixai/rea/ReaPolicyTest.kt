package ai.drfx.maximus.matrixai.rea

import org.junit.Assert.*
import org.junit.Test

class ReaPolicyTest {
    @Test fun onlyAlwaysOnSurvivesFreshProcess() {
        assertEquals(ReaMode.OFF, ReaPolicy.restored(false))
        assertEquals(ReaMode.ALWAYS_ON, ReaPolicy.restored(true))
    }

    @Test fun offDoesNotChangeTheProviderSystemPrompt() {
        assertEquals("", ReaPolicy.guidance(ReaMode.OFF))
    }

    @Test fun bothEnabledModesUseProviderIndependentGuardedEvidencePolicy() {
        val on = ReaPolicy.guidance(ReaMode.ON)
        assertTrue(on.isNotBlank())
        assertEquals(on, ReaPolicy.guidance(ReaMode.ALWAYS_ON))
        assertTrue(on.contains("If REA tools are unavailable"))
        assertTrue(on.contains("Do not invent"))
        assertTrue(on.contains("user approval"))
        assertTrue(on.contains("Pine Script"))
        assertFalse(on.contains("api_key", ignoreCase = true))
    }

    @Test fun hostSetupIsExplicitAndContainsNoAutomaticModelDispatch() {
        assertTrue(ReaHostCommands.CHECK.contains("rea --version"))
        assertTrue(ReaHostCommands.INSTALL.contains("rea-agents@latest"))
        assertFalse(ReaHostCommands.INSTALL.contains("curl | bash"))
        assertFalse(ReaHostCommands.INSTALL.contains("API_KEY"))
    }
}
