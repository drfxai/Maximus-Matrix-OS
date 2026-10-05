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
    fun criticalActionIsDenied() {
        assertEquals(
            PolicyDecision.DENY,
            policy.evaluate(AgentAction("anything", risk = RiskLevel.CRITICAL))
        )
    }
}
