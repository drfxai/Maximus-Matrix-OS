package ai.drfx.maximus.matrixai.agent.teams

/**
 * Evidence-based supervisor of REAL scheduled local jobs. This never launches
 * an LLM, grants privileges or assumes Android WorkManager can run 24/7.
 * The desktop/cloud release supervisor is independent and read-only.
 */
internal object AgentTeamSupervisor {
    enum class Health {
        PAUSED,
        WAITING_FOR_FIRST_RUN,
        HEALTHY,
        STALE,
        UNAVAILABLE,
        CLOCK_ANOMALY
    }

    data class Observation(
        val teamId: String,
        val name: String,
        val health: Health,
        val lastReportEpochMs: Long,
        val evidenceCount: Int,
        val hoursSinceLastReport: Long?,
        val lastStatus: String
    )

    data class Dashboard(
        val items: List<Observation>
    ) {
        val active: Int get() = items.count { it.health != Health.PAUSED }
        val healthy: Int get() = items.count { it.health == Health.HEALTHY }
        val attention: Int get() = items.count {
            it.health == Health.STALE || it.health == Health.UNAVAILABLE ||
                it.health == Health.CLOCK_ANOMALY
        }
    }

    fun observe(
        teams: List<AgentTeamSpec>,
        nowMs: Long,
        findReport: (String) -> AgentTeamReport
    ): Dashboard {
        require(nowMs >= 0L)
        val items = teams.take(8).map { team ->
            val report = findReport(team.id)
            val timestamp = report.atEpochMs
            val elapsed = if (timestamp in 1L..nowMs) nowMs - timestamp else 0L
            val allowedDriftMs = team.periodMinutes.coerceAtMost(1440L) * 3L * 60_000L
            val health = when {
                !team.enabled -> Health.PAUSED
                timestamp == 0L -> Health.WAITING_FOR_FIRST_RUN
                timestamp < 0L || timestamp > nowMs + 5L * 60_000L ->
                    Health.CLOCK_ANOMALY
                report.status == "UNAVAILABLE" -> Health.UNAVAILABLE
                elapsed > allowedDriftMs -> Health.STALE
                // A label without any verified source evidence is NOT healthy.
                report.status == "VERIFIED" && report.evidenceCount > 0 -> Health.HEALTHY
                report.status == "VERIFIED" -> Health.UNAVAILABLE
                else -> Health.WAITING_FOR_FIRST_RUN
            }
            Observation(
                team.id, team.name, health, timestamp,
                report.evidenceCount.coerceAtLeast(0),
                if (timestamp in 1L..nowMs) elapsed / 3_600_000L else null,
                report.status.take(25)
            )
        }
        return Dashboard(items)
    }
}
