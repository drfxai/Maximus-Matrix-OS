package ai.drfx.maximus.matrixai.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixPolicyEngineTest {
    private val policy = MatrixPolicyEngine()

    @Test
    fun lowRiskActionIsAllowed() {
        assertEquals(PolicyDecision.ALLOW, policy.evaluate(AgentAction("system_status")))
    }

    @Test
    fun highRiskActionRequiresConfirmation() {
        assertEquals(
            PolicyDecision.REQUIRE_CONFIRMATION,
            policy.evaluate(AgentAction("send_sms", risk = RiskLevel.HIGH))
        )
    }

    @Test
    fun externalAndMutationActionsRequireConfirmationEvenWhenRiskIsLow() {
        listOf("open_url", "web_search", "compose_sms", "share_text", "create_note", "copy_clipboard")
            .forEach { assertEquals(PolicyDecision.REQUIRE_CONFIRMATION, policy.evaluate(AgentAction(it))) }
    }

    @Test
    fun unknownToolCannotBypassAllowlist() {
        assertEquals(PolicyDecision.DENY, policy.evaluate(AgentAction("unregistered_tool")))
    }

    @Test
    fun criticalActionIsDenied() {
        assertEquals(
            PolicyDecision.DENY,
            policy.evaluate(AgentAction("anything", risk = RiskLevel.CRITICAL))
        )
    }
}
