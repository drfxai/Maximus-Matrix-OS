package ai.drfx.maximus.matrixai.studio.connections

import org.json.JSONObject
import java.util.UUID

internal data class CloudflarePendingMutation(
    val id: String, val account: String, val worker: String, val operation: String, val startedAt: Long,
    val targetVersion: String? = null
)

internal class CloudflareMutationUncertainException : IllegalStateException(
    "Cloudflare write outcome is UNCERTAIN. Inspect the provider before any further write to this Worker."
)

/** Write-ahead barrier, not a claim that a provider operation succeeded or failed. */
internal class CloudflareMutationJournal(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        private val lock = Any()
        fun memory(): CloudflareMutationJournal {
            var data: String? = null
            return CloudflareMutationJournal({ data }, { data = it })
        }
    }
    private fun key(account: String, worker: String): String {
        DeveloperConnectionPolicy.cloudflareAccountId(account)
        DeveloperConnectionPolicy.workerName(worker)
        return CloudflareDeploymentPolicy.digest("$account/$worker")
    }
    private fun decode(value: JSONObject): CloudflarePendingMutation {
        val id = value.getString("id")
        check(UUID.fromString(id).toString() == id)
        val account = DeveloperConnectionPolicy.cloudflareAccountId(value.getString("account"))
        val worker = DeveloperConnectionPolicy.workerName(value.getString("worker"))
        val operation = value.getString("operation")
        check(operation in setOf("CREATE", "UPLOAD", "STAGE", "ACTIVATE", "ROLLBACK"))
        val target = if (value.isNull("target")) null else CloudflareDeploymentPolicy.uuid(value.getString("target"))
        check((operation in setOf("ACTIVATE", "ROLLBACK")) == (target != null))
        return CloudflarePendingMutation(id, account, worker, operation, value.getLong("at"), target)
            .also { check(it.startedAt > 0L) }
    }
    private fun load(): JSONObject {
        val raw = read() ?: return JSONObject().put("schema", 1).put("pending", JSONObject())
        check(raw.length <= 64 * 1024) { "Cloudflare operation journal is invalid." }
        return JSONObject(raw).also {
            check(it.getInt("schema") == 1 && it.getJSONObject("pending").length() <= 64) {
                "Cloudflare operation journal is invalid."
            }
            val entries = it.getJSONObject("pending")
            entries.keys().forEach { entryKey ->
                val entry = decode(entries.getJSONObject(entryKey))
                check(entryKey == key(entry.account, entry.worker)) { "Cloudflare operation scope is invalid." }
            }
        }
    }
    fun pending(account: String, worker: String): CloudflarePendingMutation? = synchronized(lock) {
        val value = load().getJSONObject("pending").optJSONObject(key(account, worker)) ?: return@synchronized null
        decode(value)
    }
    fun begin(account: String, worker: String, operation: String, targetVersion: String? = null): CloudflarePendingMutation = synchronized(lock) {
        require(operation in setOf("CREATE", "UPLOAD", "STAGE", "ACTIVATE", "ROLLBACK"))
        val data = load()
        val pending = data.getJSONObject("pending")
        val key = key(account, worker)
        if (pending.has(key)) throw CloudflareMutationUncertainException()
        check(pending.length() < 64) { "Resolve pending Cloudflare operations before starting another write." }
        val intent = CloudflarePendingMutation(UUID.randomUUID().toString(), account, worker, operation, clock(), targetVersion)
        check((operation in setOf("ACTIVATE", "ROLLBACK")) == (targetVersion != null))
        targetVersion?.let(CloudflareDeploymentPolicy::uuid)
        check(intent.startedAt > 0L)
        pending.put(key, JSONObject().put("id", intent.id).put("account", account).put("worker", worker)
            .put("operation", operation).put("at", intent.startedAt).put("target", targetVersion ?: JSONObject.NULL))
        // Must finish durably before a request body is allowed to leave the app.
        write(data.toString())
        intent
    }
    fun confirmed(intent: CloudflarePendingMutation, evidenceId: String? = null) = synchronized(lock) {
        val data = load()
        val pending = data.getJSONObject("pending")
        val key = key(intent.account, intent.worker)
        check(pending.getJSONObject(key).getString("id") == intent.id) { "Operation ownership changed." }
        pending.remove(key)
        if (evidenceId != null) data.put("lastResolution", JSONObject().put("intent", intent.id)
            .put("account", intent.account).put("worker", intent.worker).put("operation", intent.operation)
            .put("evidence", evidenceId).put("at", clock()))
        write(data.toString())
    }
}
