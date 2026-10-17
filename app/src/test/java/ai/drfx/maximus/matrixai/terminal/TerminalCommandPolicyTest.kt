package ai.drfx.maximus.matrixai.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalCommandPolicyTest {
    @Test fun paletteRoutesLocally() {
        assertEquals(TerminalIntent.Help, TerminalCommandPolicy.parse("/help"))
        assertEquals(TerminalIntent.Team, TerminalCommandPolicy.parse("/team"))
        assertEquals(TerminalIntent.SmartCombo, TerminalCommandPolicy.parse("/combo"))
        assertEquals(TerminalIntent.Clear, TerminalCommandPolicy.parse("/clear"))
        assertEquals(TerminalIntent.Mission("Inspect project structure"),
            TerminalCommandPolicy.parse("/mission Inspect project structure"))
    }

    @Test fun ordinaryObjectivesUseGovernedMissionPath() {
        assertEquals(TerminalIntent.Mission("Review the current build"),
            TerminalCommandPolicy.parse("Review the current build"))
    }

    @Test fun rawShellIsNeverInterpretedAsAnAgentMission() {
        for (input in listOf("ls -la", "sudo rm -rf temp",
            "git status", "npm test", "curl https://example.org", "$ ls -la", "./gradlew test")) {
            assertEquals(input, TerminalIntent.ShellBlocked, TerminalCommandPolicy.parse(input))
        }
    }

    @Test fun emptyOrOversizedInputsDoNotExecute() {
        assertTrue(TerminalCommandPolicy.parse("  ") is TerminalIntent.Unsupported)
        assertTrue(TerminalCommandPolicy.parse("x".repeat(4001)) is TerminalIntent.Unsupported)
        assertTrue(TerminalCommandPolicy.parse("/run sh") is TerminalIntent.Unsupported)
    }

    @Test fun secretsAreRedactedBeforeDisplayAndCopy() {
        val secret = "Bearer abcdefghijklmnop"
        val apiKey = "api_key=super-secret-value"
        val text = TerminalCommandPolicy.redact(secret + " " + apiKey)
        assertFalse(text.contains("abcdefghijklmnop"))
        assertFalse(text.contains("super-secret-value"))
        assertTrue(text.contains("[REDACTED]"))
    }
}
