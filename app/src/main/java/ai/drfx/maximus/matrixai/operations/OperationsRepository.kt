package ai.drfx.maximus.matrixai.operations

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ai.drfx.maximus.matrixai.agent.teams.AgentTeamScheduler
import ai.drfx.maximus.matrixai.agent.teams.AgentTeamStore
import ai.drfx.maximus.matrixai.memory.HindsightMemorySettings
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHostSession
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneRuntimeCoordinator
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** Local-only adapters. No HTTP, credential resolution, recovery writes or scheduling on read. */
internal class OperationsRepository(context: Context) {
    private val app = context.applicationContext
    private val teams = AgentTeamStore(app)
    private val audit = app.getSharedPreferences("maximus_operations_audit_v1", Context.MODE_PRIVATE)

    fun snapshot(): OperationsSnapshot {
        val at = System.currentTimeMillis()
        val observations = mutableListOf<OperationalEvidence>()
        fun read(source: String, block: () -> String) {
            observations += try { OperationalEvidence(source, block(), at) }
            catch (_: Exception) { OperationalEvidence(source, "Local observation failed", at, state = EvidenceState.ERROR) }
        }
        var routinesState = EvidenceState.OBSERVED
        var projectsState = EvidenceState.OBSERVED
        val routines = try {
            // Store.list intentionally skips malformed entries for legacy UI. Do not turn that
            // behavior into a false zero/success in an operational dashboard.
            val raw = JSONArray(app.getSharedPreferences("maximus_agent_teams_v1", 0).getString("teams", "[]"))
            val specs = teams.list()
            check(raw.length() == specs.size)
            val manager = WorkManager.getInstance(app)
            specs.map { team ->
                var next: Long? = null
                val work = try {
                    val jobs = listOf("periodic", "manual").flatMap { type ->
                        manager.getWorkInfosForUniqueWork("maximus.team.$type.${team.id}").get(3, TimeUnit.SECONDS)
                    }
                    next = jobs.filter { it.state == WorkInfo.State.ENQUEUED }
                        .map { it.nextScheduleTimeMillis }.filter { it > 0 && it != Long.MAX_VALUE }.minOrNull()
                    val running = jobs.count { it.state == WorkInfo.State.RUNNING }
                    val queued = jobs.count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                    OperationalEvidence("Android WorkManager", when {
                        running > 0 -> "RUNNING · $running job(s)"
                        queued > 0 -> "QUEUED · $queued job(s)"
                        jobs.isNotEmpty() -> "No active jobs · ${jobs.size} recorded terminal jobs"
                        else -> "No recorded job"
                    }, at)
                } catch (_: Exception) {
                    OperationalEvidence("Android WorkManager", "Job state unavailable", at, state = EvidenceState.UNAVAILABLE)
                }
                val reportKey = "report_${team.id}"
                val report = try {
                    val prefs = app.getSharedPreferences("maximus_agent_teams_v1", 0)
                    if (!prefs.contains(reportKey)) OperationalEvidence("AgentTeamStore", "No verified run result", at,
                        state = EvidenceState.UNKNOWN)
                    else {
                        val obj = JSONObject(prefs.getString(reportKey, ""))
                        val time = obj.getLong("time")
                        check(time > 0L)
                        OperationalEvidence("AgentTeamStore", "${obj.getString("status")} · ${obj.optInt("evidence")} evidence item(s)", at, time)
                    }
                } catch (_: Exception) { OperationalEvidence("AgentTeamStore", "Invalid run report", at, state = EvidenceState.ERROR) }
                RoutineProjection(team.id, team.name, team.job.name, team.enabled, team.periodMinutes, work, report, next)
            }
        } catch (_: Exception) {
            routinesState = EvidenceState.ERROR
            observations += OperationalEvidence("Pulse configuration", "Routine configuration unavailable or invalid", at, state = EvidenceState.ERROR)
            emptyList()
        }
        val projects = try {
            val root = java.io.File(app.filesDir, "studio/projects")
            check(!root.exists() || root.isDirectory) { "Workspace index is not a directory." }
            val result = if (root.isDirectory) ProjectWorkspaceStore(app).projects() else emptyList()
            val directories = root.listFiles().orEmpty().count { it.isDirectory }
            check(result.size == directories)
            result.take(100).map { ProjectProjection(it.id, it.name, it.fileCount) }
        } catch (_: Exception) {
            projectsState = EvidenceState.ERROR
            observations += OperationalEvidence("ProjectWorkspaceStore", "Project index unavailable or invalid", at, state = EvidenceState.ERROR)
            emptyList()
        }
        val agentCount = try {
            val raw = JSONArray(app.getSharedPreferences("personal_agents_v1", 0).getString("agents", "[]"))
            repeat(raw.length()) { index ->
                val item = raw.getJSONObject(index)
                item.getString("id")
                item.getString("name")
                val tasks = item.optJSONArray("tasks") ?: JSONArray()
                repeat(tasks.length()) { tasks.getString(it) }
            }
            raw.length()
        } catch (_: Exception) { null }
        read("Hindsight settings") {
            val settings = HindsightMemorySettings(app)
            "${if (settings.enabled()) "Sharing enabled" else "Sharing disabled"} · project ${settings.activeProject().take(8).ifBlank { "none" }} · credential ${if (settings.hasKey()) "saved" else "absent"}"
        }
        observations += OperationalEvidence("Hindsight remote health", "Not probed by dashboard", at, state = EvidenceState.UNKNOWN)
        read("Phone runtime lease") { PhoneRuntimeCoordinator.current()?.name ?: "No process-local operation owns the runtime" }
        read("Phone host process") {
            if (PhoneHostSession.active != null) "Loopback host object present · 127.0.0.1 only; reachability not probed"
            else "No active host object"
        }
        read("Android process heap") {
            val runtime = Runtime.getRuntime()
            "${(runtime.totalMemory() - runtime.freeMemory()) / 1_048_576} MiB used · ${runtime.maxMemory() / 1_048_576} MiB ceiling"
        }
        observations += OperationalEvidence("Cloud worker health", "No authenticated health observation in this projection", at, state = EvidenceState.UNKNOWN)
        val history = try {
            val values = JSONArray(audit.getString("commands", "[]"))
            (0 until values.length()).map { values.getString(it) }.takeLast(30).reversed()
        } catch (_: Exception) { listOf("ERROR · command audit cannot be read") }
        return OperationsSnapshot(at, routines, projects, observations, agentCount, history, routinesState, projectsState)
    }

    /** Revalidate exact visible configuration before invoking the existing local command layer. */
    fun command(captured: RoutineProjection, action: String) = synchronized(commandLock) {
        require(action in setOf("RUN", "PAUSE", "RESUME", "CANCEL"))
        val current = teams.find(captured.id) ?: error("Routine no longer exists. Refresh.")
        check(current.name == captured.name && current.job.name == captured.job &&
            current.enabled == captured.enabled && current.periodMinutes == captured.periodMinutes) {
            "Routine changed. Refresh before issuing a command."
        }
        check(action != "RUN" || current.enabled) { "Resume routine first." }
        record(action, current.id, "REQUESTED") // durable intent before any scheduling
        try {
            when (action) {
                "RUN" -> AgentTeamScheduler.runNow(app, current)
                "PAUSE" -> AgentTeamScheduler.schedule(app, teams.setEnabled(current.id, false))
                "RESUME" -> AgentTeamScheduler.schedule(app, teams.setEnabled(current.id, true))
                "CANCEL" -> AgentTeamScheduler.schedule(app, teams.setEnabled(current.id, false))
            }
            // Scheduler return is an enqueue request, never proof of running/completion.
            record(action, current.id, "SUBMITTED; verify WorkManager")
        } catch (error: Exception) {
            record(action, current.id, "UNCERTAIN; refresh state before retry")
            throw error
        }
    }
    private fun record(action: String, id: String, phase: String) {
        val values = JSONArray(audit.getString("commands", "[]"))
        val kept = JSONArray()
        for (i in (values.length() - 29).coerceAtLeast(0) until values.length()) kept.put(values.getString(i))
        kept.put("${System.currentTimeMillis()} · $action · ${id.take(8)} · $phase")
        check(audit.edit().putString("commands", kept.toString()).commit()) { "Cannot persist command audit." }
    }
    companion object { private val commandLock = Any() }
}
