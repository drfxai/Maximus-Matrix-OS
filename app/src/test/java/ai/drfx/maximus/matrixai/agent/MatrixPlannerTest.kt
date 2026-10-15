package ai.drfx.maximus.matrixai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MatrixPlannerTest {
    @Test fun searchCommandCreatesRealWebSearchTool() {
        val plan = MatrixPlanner().plan(Mission(objective = "search gold price"))
        assertTrue(plan.any { it.action.tool == "web_search" && it.action.arguments["query"] == "gold price" })
    }

    @Test fun smsCommandUsesComposerAndMediumRisk() {
        val plan = MatrixPlanner().plan(Mission(objective = "sms +123 hello"))
        val step = plan.first { it.action.tool == "compose_sms" }
        assertEquals(RiskLevel.MEDIUM, step.action.risk)
    }

    @Test fun cameraCommandCreatesCameraTool() {
        val plan = MatrixPlanner().plan(Mission(objective = "open camera"))
        assertTrue(plan.any { it.action.tool == "open_camera" })
    }

    @Test fun alarmWithoutCustomLabelUsesProductName() {
        val plan = MatrixPlanner().plan(Mission(objective = "alarm 09:00"))
        val step = plan.first { it.action.tool == "set_alarm" }
        assertEquals("MAXIMUS AI", step.action.arguments["label"])
    }
    @Test fun unsupportedObjectiveCannotBeReportedAsDeviceStatusSuccess() {
        val plan = MatrixPlanner().plan(Mission(objective = "execute a market order"))
        assertTrue(plan.any { it.action.tool == "unsupported_objective" })
        assertEquals(PolicyDecision.DENY, MatrixPolicyEngine().evaluate(plan.last().action))
    }

    @Test fun explicitStatusRetainsExecutableReadOnlyPlan() {
        val plan = MatrixPlanner().plan(Mission(objective = "status"))
        assertEquals(listOf("device_info"), plan.map { it.action.tool })
    }

    @Test fun payloadKeywordsCannotSelectAnotherToolOrAppendUnrelatedSteps() {
        val cases = listOf(
            Triple("copy camera strategy notes", "copy_clipboard", "text"),
            Triple("share camera research", "share_text", "text"),
            Triple("note validate camera settings", "create_note", "text"),
            Triple("sms +123 camera indicator", "compose_sms", "message"),
            Triple("calendar camera strategy review", "create_calendar_event", "title"),
            Triple("search camera indicator strategy", "web_search", "query"),
            Triple("app com.example.camera", "open_app", "package")
        )
        for ((objective, tool, argument) in cases) {
            val plan = MatrixPlanner().plan(Mission(objective = objective))
            assertEquals(objective, listOf("device_info", tool), plan.map { it.action.tool })
            val expectedPayload = if (tool == "compose_sms") "camera indicator"
                else objective.substringAfter(" ")
            assertEquals(objective, expectedPayload, plan.last().action.arguments[argument])
        }
    }

    @Test fun commandPrefixesAreCaseInsensitiveAndPreservePayloadCase() {
        val cases = listOf(
            Triple("Search Kotlin API", "web_search", "query"),
            Triple("OPEN URL https://example.com/CaseSensitive", "open_url", "url"),
            Triple("COPY Camera API Key", "copy_clipboard", "text"),
            Triple("App com.example.Camera", "open_app", "package")
        )
        for ((objective, tool, argument) in cases) {
            val plan = MatrixPlanner().plan(Mission(objective = objective))
            assertEquals(listOf("device_info", tool), plan.map { it.action.tool })
            val expectedPayload = if (tool == "open_url") objective.substringAfter("URL ")
                else objective.substringAfter(" ")
            assertEquals(expectedPayload, plan.last().action.arguments[argument])
        }
    }

    @Test fun cameraAndSettingsWordsAreNotStandaloneCommands() {
        for (objective in listOf("explain camera", "do not open camera", "settingsDocumentation")) {
            val plan = MatrixPlanner().plan(Mission(objective = objective))
            assertEquals(objective, listOf("device_info", "unsupported_objective"), plan.map { it.action.tool })
        }
    }

    @Test fun canonicalBrainCommandsRetainTheirTools() {
        val commands = mapOf(
            "camera" to "open_camera",
            "settings" to "open_settings",
            "settings wifi" to "open_settings",
            "app org.telegram.messenger" to "open_app"
        )
        for ((objective, tool) in commands) {
            val plan = MatrixPlanner().plan(Mission(objective = objective))
            assertEquals(listOf("device_info", tool), plan.map { it.action.tool })
        }
    }
    @Test fun commandMatchingDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val plan = MatrixPlanner().plan(Mission(objective = "DIAL +123"))
            assertEquals(listOf("device_info", "open_dialer"), plan.map { it.action.tool })
            assertEquals("+123", plan.last().action.arguments["number"])
        } finally {
            Locale.setDefault(previous)
        }
    }
}
