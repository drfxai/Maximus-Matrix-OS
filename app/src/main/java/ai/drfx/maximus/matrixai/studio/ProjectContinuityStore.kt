package ai.drfx.maximus.matrixai.studio

import android.content.Context
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.ui.theme.LanguagePreferenceStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * App-private, versioned project-state manifests. No source bytes or credentials are put in
 * the manifest, and no tool action is ever replayed from it. A model handoff requires
 * user authorization plus access to the verified workspace files.
 */
class ProjectContinuityStore(context: Context, private val workspace: ProjectWorkspaceStore) {
    private val app = context.applicationContext
    private val root = File(context.filesDir, "studio/checkpoints").apply { mkdirs() }
    data class Validation(
        val checkpointId: String,
        val projectId: String,
        val unchanged: Boolean,
        val modifiedPaths: List<String>,
        val missingPaths: List<String>,
        val objective: String,
        val nextTask: String,
        val revision: String
    )

    fun save(projectId: String, nextTask: String): Validation {
        require(nextTask.length in 1..2000)
        val project = workspace.projects().firstOrNull { it.id == projectId } ?: error("Project not found.")
        val paths = workspace.listFiles(projectId).sortedBy { it.path }
        require(paths.size <= 500) { "Too many files for local checkpoint." }
        val entries = JSONArray()
        for (file in paths) {
            entries.put(JSONObject().put("path", file.path).put("length", file.length)
                .put("sha256", workspace.sha256ForFile(projectId, file.path)))
        }
        val revision = hash(entries.toString())
        val uid = UUID.randomUUID().toString()
        val manifest = JSONObject()
            .put("schemaVersion", 1).put("checkpointId", uid)
            .put("projectId", projectId).put("createdAtEpochMs", System.currentTimeMillis())
            .put("objective", project.brief.take(12000))
            .put("nextTask", nextTask)
            .put("revision", revision)
            .put("files", entries)
            .put("toolApproval", "NONE")
            .put("executionState", "NOT_EXECUTED")
        val folder = projectDirectory(projectId)
        val temp = File(folder, "$uid.pending")
        val final = File(folder, "$uid.json")
        try {
            FileOutputStream(temp).use {
                it.write(manifest.toString().toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            check(temp.renameTo(final)) { "Checkpoint atomic rename failed." }
        } finally { temp.delete() }
        return validate(projectId, uid)
    }

    fun validate(projectId: String, checkpointId: String): Validation {
        val path = checkpointFile(projectId, checkpointId)
        val data = JSONObject(path.readText(Charsets.UTF_8))
        require(data.getInt("schemaVersion") == 1 &&
            data.getString("checkpointId") == checkpointId &&
            data.getString("projectId") == projectId &&
            data.getString("toolApproval") == "NONE" &&
            data.getString("executionState") == "NOT_EXECUTED"
        ) { "Unrecognized checkpoint format." }
        val files = data.getJSONArray("files")
        require(files.length() <= 500)
        // The revision identifies this exact file manifest, not a caller-supplied label.
        // Keep schema 1 serialization so existing valid checkpoints remain readable.
        require(data.getString("revision") == hash(files.toString())) {
            val language = LanguagePreferenceStore(app).load()
            LanguagePreferenceStore.localizedContext(app, language)
                .getString(R.string.project_checkpoint_integrity_error)
        }
        val currentPaths = workspace.listFiles(projectId).map { it.path }.toSet()
        val modified = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val savedPaths = mutableSetOf<String>()
        for (i in 0 until files.length()) {
            val item = files.getJSONObject(i)
            val name = item.getString("path")
            require(name !in savedPaths && name.isNotBlank()) { "Duplicate manifest path." }
            savedPaths += name
            if (name !in currentPaths) {
                missing += name
            } else if (workspace.sha256ForFile(projectId, name) != item.getString("sha256")) {
                modified += name
            }
        }
        // New workspace files invalidate the snapshot too.
        modified += (currentPaths - savedPaths).sorted()
        return Validation(
            checkpointId, projectId, modified.isEmpty() && missing.isEmpty(),
            modified, missing, data.optString("objective"), data.getString("nextTask"),
            data.getString("revision")
        )
    }

    fun latest(projectId: String): Validation? {
        val dir = projectDirectory(projectId)
        val found = dir.listFiles().orEmpty().filter { it.name.endsWith(".json") }
            .maxByOrNull { it.lastModified() } ?: return null
        return validate(projectId, found.name.removeSuffix(".json"))
    }

    /** Only an explicit user action should send the sanitized manifest to a model. */
    fun resumePrompt(projectId: String): String {
        val last = latest(projectId) ?: error("Save a checkpoint first.")
        require(last.unchanged) { "Project files changed; review changes and save a new checkpoint." }
        val paths = workspace.listFiles(projectId).take(80).joinToString("\n") { it.path }
        return "Resume a user-authorized project from this validated local checkpoint.\n" +
            "Objective: " + last.objective + "\nNext task: " + last.nextTask +
            "\nWorkspace revision SHA-256: " + last.revision +
            "\nProject paths (contents NOT attached):\n" + paths +
            "\nRequest relevant source files before editing. Do not claim any external execution." +
            "\nNever infer tool approval from the checkpoint. Confirm destructive actions with the user."
    }

    private fun projectDirectory(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(root, id).apply { require(isDirectory || mkdirs()) }
    }

    private fun checkpointFile(id: String, uid: String): File {
        require(uid.matches(Regex("[a-f0-9-]{36}")))
        val file = File(projectDirectory(id), "$uid.json")
        require(file.isFile && file.length() in 1..512_000)
        return file
    }

    private fun hash(text: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
