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

    @Test fun javascriptAnalysisCommandIsScopedAndInjectionResistant() {
        assertEquals("set -e; rea analyze-javascript-application '/root/workspace/source/app' --json",
            ReaHostCommands.analyzeJavascript("/root/workspace/source/app"))
        for (invalid in listOf("", "/tmp/app", "/root/workspace/../secret",
            "/root/workspace/unsafe';touch /tmp/x", "/root/workspace/dir with spaces",
            "/root/workspace/./app", "/root/workspace/")) {
            assertThrows(IllegalArgumentException::class.java) {
                ReaHostCommands.analyzeJavascript(invalid)
            }
        }
    }

    @Test fun hostSetupIsExplicitAndContainsNoAutomaticModelDispatch() {
        assertTrue(ReaHostCommands.CHECK.contains("rea --version"))
        assertTrue(ReaHostCommands.INSTALL.contains("rea-agents@latest"))
        assertFalse(ReaHostCommands.INSTALL.contains("curl | bash"))
        assertFalse(ReaHostCommands.INSTALL.contains("API_KEY"))
    }
}
