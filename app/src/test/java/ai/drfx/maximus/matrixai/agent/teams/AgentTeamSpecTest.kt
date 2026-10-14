package ai.drfx.maximus.matrixai.agent.teams

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AgentTeamSpecTest {
    private fun team(minutes: Long = 180L) = AgentTeamSpec(
        UUID.randomUUID().toString(), "My team", AgentTeamJob.VERIFIED_NEWS,
        minutes
    )

    @Test fun onlySchedulableBestEffortIntervalsAreAllowed() {
        assertEquals(3, team().roles.size)
        assertEquals(180L, team().periodMinutes)
        for (minutes in listOf(0L, 1L, 10L, 60L, 720L, 10080L)) {
            assertThrows(IllegalArgumentException::class.java) { team(minutes) }
        }
        for (minutes in listOf(15L, 180L, 1440L)) {
            assertTrue(team(minutes).enabled)
        }
    }

    @Test fun teamRequiresValidIdAndHumanReadableName() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentTeamSpec("bad-id", "Team", AgentTeamJob.DEVICE_HEALTH, 15)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AgentTeamSpec(UUID.randomUUID().toString(), "", AgentTeamJob.DEVICE_HEALTH, 15)
        }
    }
}