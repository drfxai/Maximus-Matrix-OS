package ai.drfx.maximus.matrixai.agent.teams

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * User-owned best-effort mobile automations. No AI/LLM execution is implied:
 * only allowlisted on-device publisher scans and device-health probes can run.
 */
enum class AgentTeamJob { VERIFIED_NEWS, DEVICE_HEALTH }

internal data class AgentTeamSpec(
    val id: String,
    val name: String,
    val job: AgentTeamJob,
    val periodMinutes: Long,
    val enabled: Boolean = true
) {
    init {
        require(name.trim().length in 2..60)
        require(periodMinutes in setOf(15L, 180L, 1440L))
        require(UUID.fromString(id).toString() == id)
    }
    val roles: List<String> = listOf("Collector", "Validator", "Reporter")
}

internal data class AgentTeamReport(
    val atEpochMs: Long = 0L,
    val status: String = "NOT_RUN",
    val summary: String = "",
    val evidenceCount: Int = 0
)

internal class AgentTeamStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("maximus_agent_teams_v1", Context.MODE_PRIVATE)

    @Synchronized fun list(): List<AgentTeamSpec> = runCatching {
        val array = JSONArray(preferences.getString("teams", "[]"))
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                AgentTeamSpec(
                    json.getString("id"),
                    json.getString("name"),
                    AgentTeamJob.valueOf(json.getString("job")),
                    json.getLong("period"),
                    json.optBoolean("enabled", false)
                )
            }.getOrNull()
        }.take(8)
    }.getOrDefault(emptyList())

    @Synchronized fun create(name: String, job: AgentTeamJob, periodMinutes: Long): AgentTeamSpec {
        val existing = list()
        require(existing.size < 8) { "Maximum eight local monitoring teams." }
        val added = AgentTeamSpec(UUID.randomUUID().toString(), name.trim(), job, periodMinutes)
        save(existing + added)
        return added
    }

    @Synchronized fun find(id: String): AgentTeamSpec? = list().firstOrNull { it.id == id }

    @Synchronized fun setEnabled(id: String, enabled: Boolean): AgentTeamSpec {
        val teams = list()
        require(teams.any { it.id == id })
        val selected = teams.first { it.id == id }.copy(enabled = enabled)
        save(teams.map { if (it.id == id) selected else it })
        return selected
    }

    @Synchronized fun remove(id: String) {
        save(list().filterNot { it.id == id })
        preferences.edit().remove("report_" + id).apply()
    }

    @Synchronized fun report(id: String): AgentTeamReport = runCatching {
        val json = JSONObject(preferences.getString("report_" + id, "{}"))
        AgentTeamReport(
            json.optLong("time", 0L),
            json.optString("status", "NOT_RUN"),
            json.optString("summary", ""),
            json.optInt("evidence", 0)
        )
    }.getOrDefault(AgentTeamReport())

    @Synchronized fun record(id: String, report: AgentTeamReport) {
        if (find(id) == null) return
        val objectValue = JSONObject()
            .put("time", report.atEpochMs)
            .put("status", report.status.take(25))
            .put("summary", report.summary.take(1250))
            .put("evidence", report.evidenceCount.coerceAtLeast(0))
        check(preferences.edit().putString("report_" + id, objectValue.toString()).commit()) {
            "Cannot persist verified team run."
        }
    }

    private fun save(teams: List<AgentTeamSpec>) {
        val array = JSONArray()
        teams.forEach { t ->
            array.put(JSONObject().put("id", t.id).put("name", t.name)
                .put("job", t.job.name).put("period", t.periodMinutes)
                .put("enabled", t.enabled))
        }
        check(preferences.edit().putString("teams", array.toString()).commit()) {
            "Could not save team configuration."
        }
    }
}
