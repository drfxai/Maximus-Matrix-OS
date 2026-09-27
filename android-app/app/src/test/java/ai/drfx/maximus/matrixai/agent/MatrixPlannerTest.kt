package ai.drfx.maximus.matrixai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
