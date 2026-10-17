package ai.drfx.maximus.matrixai.plugins

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Native modules shipped with this signed APK; no downloaded code or implicit tool authority. */
internal object PluginCatalog {
    data class Plugin(val id: String, val version: Int, val permissions: Set<String>) {
        val fingerprint: String get() = MessageDigest.getInstance("SHA-256")
            .digest((id + "\n" + version + "\n" + permissions.sorted().joinToString("\n")).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    val ALL = listOf(
        Plugin("github", 1, setOf("github.read", "github.feature_branch.write", "github.draft_pr.create")),
        Plugin("cloudflare", 1, setOf("cloudflare.workers.read", "cloudflare.workers.deploy_after_review")),
        Plugin("phone-apk", 1, setOf("phone.trusted_source.execute", "phone.apk.export")),
        Plugin("phone-linux", 1, setOf("phone.manual_command.execute", "phone.guest_files.write", "phone.network"))
    )
    fun find(id: String): Plugin = requireNotNull(ALL.singleOrNull { it.id == id }) { "Unknown native plugin." }
}

/** Persist exact reviewed manifest fingerprints. Updates require a new permission review. */
internal class PluginRegistry(private val file: File) {
    companion object { private val locks = ConcurrentHashMap<String, Any>() }
    private val lock = locks.getOrPut(file.absoluteFile.normalize().path) { Any() }
    private fun load(): JSONObject {
        if (!file.exists()) return JSONObject().put("schema", 1).put("installed", JSONObject())
        check(file.isFile && file.length() in 1..16_384) { "Invalid plugin registry." }
        return JSONObject(file.readText()).also { check(it.getInt("schema") == 1) }
    }
    fun installed(id: String): Boolean = synchronized(lock) {
        val plugin = PluginCatalog.find(id)
        // Corrupt state grants no permissions; installs require explicit repair/removal.
        runCatching { load().getJSONObject("installed").optString(id) == plugin.fingerprint }.getOrDefault(false)
    }
    fun install(id: String, reviewedFingerprint: String, approved: Boolean) = synchronized(lock) {
        check(approved) { "Plugin permissions require approval." }
        val plugin = PluginCatalog.find(id)
        check(reviewedFingerprint == plugin.fingerprint) { "Plugin manifest changed. Review permissions again." }
        val state = load()
        state.getJSONObject("installed").put(id, plugin.fingerprint)
        persist(state)
    }
    fun remove(id: String) = synchronized(lock) {
        PluginCatalog.find(id)
        val state = load()
        state.getJSONObject("installed").remove(id)
        persist(state)
    }
    private fun persist(state: JSONObject) {
        file.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
        val temp = File(file.parentFile, file.name + ".pending")
        try {
            FileOutputStream(temp).use { output ->
                output.write(state.toString().toByteArray(Charsets.UTF_8)); output.fd.sync()
            }
            check(temp.renameTo(file)) { "Cannot persist plugin state." }
        } finally { temp.delete() }
    }
}
