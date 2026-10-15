package ai.drfx.maximus.matrixai.agent.teams

import org.junit.Assert.*
import org.junit.Test

class AgentTeamSupervisorTest {
    private val a = AgentTeamSpec(
        "00112233-4455-6677-8899-aabbccddeeff", "Voice QA",
        AgentTeamJob.DEVICE_HEALTH, 15L
    )
    private val b = AgentTeamSpec(
        "11112233-4455-6677-8899-aabbccddeeff", "News QA",
        AgentTeamJob.VERIFIED_NEWS, 180L
    )
    private val now = 1_800_000_000_000L

    @Test fun healthyReportIsObservedWithoutPretendingAllDayUptime() {
        val result = AgentTeamSupervisor.observe(listOf(a), now) {
            AgentTeamReport(now - 20_000L, "VERIFIED", "Device health checked", 1)
        }
        assertEquals(AgentTeamSupervisor.Health.HEALTHY, result.items.single().health)
        assertEquals(1, result.healthy)
        assertEquals(0, result.attention)
    }

    @Test fun lateOrFailedTeamsNeedAttention() {
        val result = AgentTeamSupervisor.observe(listOf(a, b), now) { id ->
            if (id == a.id) AgentTeamReport(now - 4L * 60L * 60_000L, "VERIFIED", "", 1)
            else AgentTeamReport(now - 2_000L, "UNAVAILABLE", "No verified news", 0)
        }
        assertEquals(2, result.attention)
        assertEquals(AgentTeamSupervisor.Health.STALE, result.items[0].health)
        assertEquals(AgentTeamSupervisor.Health.UNAVAILABLE, result.items[1].health)
    }

    @Test fun pausedTeamDoesNotAppearBroken() {
        val paused = a.copy(enabled = false)
        val result = AgentTeamSupervisor.observe(listOf(paused), now) {
            AgentTeamReport(0, "NOT_RUN", "", 0)
        }
        assertEquals(0, result.active)
        assertEquals(0, result.attention)
        assertEquals(AgentTeamSupervisor.Health.PAUSED, result.items.single().health)
    }

    @Test fun unrunAndClockAnomaliesStayDistinct() {
        val missing = AgentTeamSupervisor.observe(listOf(a), now) {
            AgentTeamReport()
        }
        assertEquals(AgentTeamSupervisor.Health.WAITING_FOR_FIRST_RUN,
            missing.items.single().health)
        val future = AgentTeamSupervisor.observe(listOf(a), now) {
            AgentTeamReport(now + 900_000, "VERIFIED", "Future timestamp", 1)
        }
        assertEquals(AgentTeamSupervisor.Health.CLOCK_ANOMALY,
            future.items.single().health)
    }

    @Test fun reportsAreNeverCreditedWithFabricatedEvidence() {
        val observed = AgentTeamSupervisor.observe(listOf(a), now) {
            AgentTeamReport(now - 1L, "VERIFIED", "Incomplete response", -12)
        }.items.single()
        assertEquals(0, observed.evidenceCount)
        assertEquals("VERIFIED", observed.lastStatus)
        assertEquals(AgentTeamSupervisor.Health.UNAVAILABLE, observed.health)
    }
}
