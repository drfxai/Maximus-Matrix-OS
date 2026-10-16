package ai.drfx.maximus.matrixai.agent.teams

import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/** One atomic, app-private run. A persisted dispatch is never replayed after process death. */
class ProjectTeamRunStore(private val file: File, private val owner: String = processOwner,
    private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        private val processOwner = UUID.randomUUID().toString()
        private val locks = ConcurrentHashMap<String, Any>()
    }
    private val lock = locks.getOrPut(file.absolutePath) { Any() }
    fun read(): ProjectTeamRun? = synchronized(lock) {
        val run = load() ?: return@synchronized null
        if ((run.busy || run.state == ProjectTeamRun.State.AWAITING_APPROVAL) &&
            (run.owner != owner || clock() !in run.createdAt..run.expiresAt)) {
            val recovered = run.copy(state = if (run.dispatched) ProjectTeamRun.State.UNCERTAIN
                else ProjectTeamRun.State.BLOCKED, activeProvider = "", activeModel = "")
            persist(recovered)
            recovered
        } else run
    }
    fun prepare(projectId: String, revision: String, prompt: String, destinations: String = ""): ProjectTeamRun = synchronized(lock) {
        check(read()?.busy != true) { "A project team already owns the execution lease." }
        ProjectTeamRun(projectId = projectId, revision = revision, prompt = prompt,
            createdAt = clock(), owner = owner, expiresAt = clock() + ProjectTeamRun.LEASE_MS,
            approvedDestinations = destinations).also(::persist)
    }
    fun change(id: String, expected: Set<ProjectTeamRun.State>,
        action: (ProjectTeamRun) -> ProjectTeamRun): ProjectTeamRun = synchronized(lock) {
        val run = read() ?: error("Project team run missing.")
        check(run.id == id && run.owner == owner && run.state in expected) { "Stale project team lease." }
        val next = action(run)
        check(next.id == run.id && next.owner == run.owner && next.projectId == run.projectId &&
            next.prompt == run.prompt && next.revision == run.revision && next.approvedDestinations == run.approvedDestinations) { "Captured inputs cannot change." }
        persist(next)
        next
    }
    /** Explicit user recovery only. The invalid journal is retained once, and never replayed. */
    fun quarantineInvalid() = synchronized(lock) {
        check(file.exists() && runCatching { load() }.isFailure) { "Checkpoint is not invalid." }
        check(file.renameTo(File(file.parentFile, file.name + ".invalid"))) { "Could not quarantine checkpoint." }
    }
    private fun persist(run: ProjectTeamRun) {
        file.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
        fun evidence(item: ProjectTeamRun.Evidence?): Any = item?.let {
            JSONObject().put("provider", it.provider).put("model", it.model).put("text", it.text)
                .put("input", it.inputTokens).put("output", it.outputTokens).put("estimated", it.estimated)
        } ?: JSONObject.NULL
        val data = JSONObject().put("schema", 1).put("id", run.id).put("project", run.projectId)
            .put("revision", run.revision).put("prompt", run.prompt).put("created", run.createdAt)
            .put("owner", run.owner).put("expires", run.expiresAt).put("state", run.state.name)
            .put("planner", evidence(run.planner)).put("reviewer", evidence(run.reviewer))
            .put("provider", run.activeProvider).put("model", run.activeModel)
            .put("destinations", run.approvedDestinations).toString()
        val temp = File(file.parentFile, file.name + ".pending")
        try {
            FileOutputStream(temp).use { it.write(data.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            check(temp.renameTo(file)) { "Could not persist project team checkpoint." }
        } finally { temp.delete() }
    }
    private fun load(): ProjectTeamRun? {
        if (!file.exists()) return null
        check(file.isFile && file.length() in 1..512_000) { "Invalid project team checkpoint." }
        val data = JSONObject(file.readText())
        check(data.getInt("schema") == 1)
        fun evidence(key: String): ProjectTeamRun.Evidence? = data.optJSONObject(key)?.let {
            ProjectTeamRun.Evidence(it.getString("provider"), it.getString("model"), it.getString("text"),
                it.getInt("input"), it.getInt("output"), it.getBoolean("estimated"))
        }
        return ProjectTeamRun(data.getString("id"), data.getString("project"), data.getString("revision"),
            data.getString("prompt"), data.getLong("created"), data.getString("owner"), data.getLong("expires"),
            ProjectTeamRun.State.valueOf(data.getString("state")), evidence("planner"), evidence("reviewer"),
            data.getString("provider"), data.getString("model"), data.optString("destinations"))
    }
}
