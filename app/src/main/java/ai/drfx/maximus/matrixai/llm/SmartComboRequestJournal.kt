package ai.drfx.maximus.matrixai.llm

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Reference only: neither a model instruction, a permission grant nor executable state. */
internal data class SmartComboCheckpointReference(
    val projectId: String, val checkpointId: String, val revision: String
) {
    init {
        require(projectId.matches(Regex("[a-f0-9-]{36}")))
        require(checkpointId.matches(Regex("[a-f0-9-]{36}")))
        require(revision.matches(Regex("[a-f0-9]{64}")))
    }
}

/** Device-local metadata journal. A corrupt/stale write never resets existing evidence. */
internal class SmartComboRequestJournal(private val file: File,
    private val beforeCommit: (() -> Unit)? = null) {
    companion object {
        const val MAX_ENTRIES = 1024
        const val MAX_ARCHIVES = 64
        private const val MAX_BYTES = 1_048_576L
        private val locks = ConcurrentHashMap<String, Any>()
        fun identity(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    data class Archive(val name: String, val sha256: String)
    data class ArchivedMetadata(val requestHashes: Set<String>, val count: Int,
        val checkpointReferences: Int, val confirmedTokensByProvider: Map<LlmProvider, Long>)
    data class Snapshot(
        val revision: Long = 0,
        val entries: List<SmartComboQuotaLedger.Entry> = emptyList(),
        val quotas: Map<String, List<SmartComboPlanner.QuotaWindow>> = emptyMap(),
        val archives: List<Archive> = emptyList()
    )
    private val monitor = locks.getOrPut(file.absolutePath) { Any() }
    private fun <T> locked(action: () -> T): T = synchronized(monitor) {
        val parent = requireNotNull(file.parentFile)
        check(parent.isDirectory || parent.mkdirs())
        RandomAccessFile(File(parent, file.name + ".lock"), "rw").use { lockFile ->
            lockFile.channel.lock().use { action() }
        }
    }
    fun load(): Snapshot = locked { read() }
    fun save(expectedRevision: Long, snapshot: Snapshot): Long = locked {
        val current = read()
        check(current.revision == expectedRevision) { "Stale request journal writer." }
        check(expectedRevision < Long.MAX_VALUE)
        val revision = expectedRevision + 1
        val body = encode(snapshot.copy(revision = revision)).toString()
        val bytes = JSONObject().put("schema", 1).put("body", body)
            .put("sha256", identity(body)).toString().toByteArray(Charsets.UTF_8)
        check(bytes.size.toLong() <= MAX_BYTES)
        val pending = File(file.parentFile, file.name + ".pending")
        try {
            FileOutputStream(pending).use { it.write(bytes); it.fd.sync() }
            beforeCommit?.invoke()
            check(pending.renameTo(file)) { "Could not commit request journal." }
        } finally { pending.delete() }
        revision
    }
    /** Explicit owner-reviewed archival only. Writes immutable evidence before journal references it. */
    fun archive(entries: List<SmartComboQuotaLedger.Entry>): Archive = locked {
        require(entries.isNotEmpty())
        val body = encode(Snapshot(revision = 1, entries = entries)).toString()
        val text = JSONObject().put("schema", 1).put("body", body)
            .put("sha256", identity(body)).toString()
        check(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val folder = File(file.parentFile, "archives").apply { check(isDirectory || mkdirs()) }
        val target = File(folder, UUID.randomUUID().toString() + ".json")
        val pending = File(folder, target.name + ".pending")
        try {
            FileOutputStream(pending).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            beforeCommit?.invoke()
            check(pending.renameTo(target))
        } finally { pending.delete() }
        Archive(target.name, identity(text))
    }
    fun archivedMetadata(archives: List<Archive>): ArchivedMetadata = locked {
        require(archives.size <= MAX_ARCHIVES && archives.map { it.name }.distinct().size == archives.size)
        val hashes = mutableSetOf<String>()
        val totals = mutableMapOf<LlmProvider, Long>()
        var count = 0
        var checkpoints = 0
        for (ref in archives) {
            require(ref.name.matches(Regex("[a-f0-9-]{36}\\.json")) && ref.sha256.matches(Regex("[a-f0-9]{64}")))
            val archive = File(File(file.parentFile, "archives"), ref.name)
            check(archive.isFile && archive.length() in 1L..MAX_BYTES)
            val text = archive.readText(Charsets.UTF_8)
            check(identity(text) == ref.sha256)
            val snapshot = decode(text)
            check(snapshot.archives.isEmpty() && snapshot.quotas.isEmpty())
            for (entry in snapshot.entries) {
                check(entry.state in setOf(SmartComboQuotaLedger.State.RELEASED,
                    SmartComboQuotaLedger.State.RECONCILED, SmartComboQuotaLedger.State.FINISHED_UNVERIFIED))
                check(hashes.add(identity(entry.plan.requestId)))
                count++
                if (entry.plan.checkpoint != null) checkpoints++
                entry.confirmed?.let { usage ->
                    val previous = totals[entry.plan.provider] ?: 0L
                    totals[entry.plan.provider] = if (previous > Long.MAX_VALUE - usage.total)
                        Long.MAX_VALUE else previous + usage.total
                }
            }
        }
        ArchivedMetadata(hashes, count, checkpoints, totals)
    }
    private fun encode(snapshot: Snapshot): JSONObject {
        require(snapshot.entries.size <= MAX_ENTRIES && snapshot.quotas.size <= 128)
        val entries = JSONArray()
        snapshot.entries.forEach { e ->
            val p = e.plan
            require(p.scope.matches(Regex("[a-f0-9]{64}")) && p.routeId.matches(Regex("[a-f0-9]{64}")))
            require(p.requestId.length <= 128 && e.createdAt >= 0)
            val item = JSONObject().put("id", p.requestId).put("scope", p.scope).put("route", p.routeId)
                .put("provider", p.provider.name).put("input", p.input).put("reservedOutput", p.reservedOutput)
                .put("reservedMicros", p.reservedMicros).put("created", e.createdAt).put("state", e.state.name)
                .put("settled", e.settledAt ?: JSONObject.NULL)
            e.confirmed?.let { u -> item.put("usage", JSONObject().put("input", u.input)
                .put("output", u.output).put("total", u.total).put("micros", u.billedMicros ?: JSONObject.NULL)) }
            p.checkpoint?.let { cp -> item.put("checkpoint", JSONObject().put("project", cp.projectId)
                .put("id", cp.checkpointId).put("revision", cp.revision)) }
            entries.put(item)
        }
        val quotas = JSONArray()
        snapshot.quotas.forEach { (scope, windows) ->
            require(scope.matches(Regex("[a-f0-9]{64}")) && windows.size <= SmartComboPlanner.LimitKind.entries.size)
            windows.forEach { w -> quotas.put(JSONObject().put("scope", scope).put("kind", w.kind.name)
                .put("remaining", w.remaining ?: JSONObject.NULL).put("limit", w.limit ?: JSONObject.NULL)
                .put("reset", w.resetAtEpochMs ?: JSONObject.NULL).put("observed", w.observedAtEpochMs ?: JSONObject.NULL)
                .put("confidence", w.confidence.name)) }
        }
        require(snapshot.archives.size <= MAX_ARCHIVES)
        val archives = JSONArray()
        snapshot.archives.forEach { archives.put(JSONObject().put("name", it.name).put("sha256", it.sha256)) }
        return JSONObject().put("revision", snapshot.revision).put("entries", entries).put("quotas", quotas)
            .put("archives", archives)
    }
    private fun read(): Snapshot {
        if (!file.exists()) return Snapshot()
        check(file.isFile && file.length() in 1L..MAX_BYTES)
        return decode(file.readText(Charsets.UTF_8))
    }
    private fun decode(text: String): Snapshot {
        val envelope = JSONObject(text)
        check(envelope.getInt("schema") == 1)
        val body = envelope.getString("body")
        check(identity(body) == envelope.getString("sha256")) { "Request journal integrity failure." }
        val data = JSONObject(body)
        val revision = data.getLong("revision").also { check(it > 0) }
        val rows = data.getJSONArray("entries")
        check(rows.length() <= MAX_ENTRIES)
        val entries = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val cp = row.optJSONObject("checkpoint")?.let {
                SmartComboCheckpointReference(it.getString("project"), it.getString("id"), it.getString("revision"))
            }
            val plan = SmartComboQuotaLedger.Plan(row.getString("id"), row.getString("scope"),
                row.getString("route"), row.getLong("input"), row.getLong("reservedOutput"),
                row.getLong("reservedMicros"), LlmProvider.valueOf(row.getString("provider")), cp)
            check(plan.scope.matches(Regex("[a-f0-9]{64}")) && plan.routeId.matches(Regex("[a-f0-9]{64}")))
            check(plan.requestId.length <= 128)
            val state = SmartComboQuotaLedger.State.valueOf(row.getString("state"))
            val usage = row.optJSONObject("usage")?.let { u -> SmartComboQuotaLedger.Usage(
                u.getLong("input"), u.getLong("output"), u.getLong("total"),
                if (u.isNull("micros")) null else u.getLong("micros")) }
            check((state == SmartComboQuotaLedger.State.RECONCILED) == (usage != null))
            val created = row.getLong("created").also { check(it >= 0) }
            val settled = if (row.isNull("settled")) null else row.getLong("settled").also { check(it >= 0) }
            SmartComboQuotaLedger.Entry(plan, created, state, usage, settled)
        }
        check(entries.map { it.plan.requestId }.distinct().size == entries.size)
        val quotaRows = data.getJSONArray("quotas")
        check(quotaRows.length() <= 128 * SmartComboPlanner.LimitKind.entries.size)
        val quotas = linkedMapOf<String, MutableList<SmartComboPlanner.QuotaWindow>>()
        for (i in 0 until quotaRows.length()) {
            val row = quotaRows.getJSONObject(i)
            val scope = row.getString("scope").also { check(it.matches(Regex("[a-f0-9]{64}"))) }
            fun number(key: String): Long? = if (row.isNull(key)) null else row.getLong(key)
            val window = SmartComboPlanner.QuotaWindow(SmartComboPlanner.LimitKind.valueOf(row.getString("kind")),
                number("remaining"), number("limit"), number("reset"), number("observed"),
                SmartComboPlanner.Confidence.valueOf(row.getString("confidence")))
            check(window.confidence == SmartComboPlanner.Confidence.PROVIDER_VERIFIED)
            val list = quotas.getOrPut(scope) { mutableListOf() }
            check(list.none { it.kind == window.kind })
            list.add(window)
        }
        check(quotas.size <= 128)
        val archiveRows = data.optJSONArray("archives") ?: JSONArray()
        check(archiveRows.length() <= MAX_ARCHIVES)
        val archives = (0 until archiveRows.length()).map { i -> archiveRows.getJSONObject(i).let {
            Archive(it.getString("name"), it.getString("sha256"))
        } }
        return Snapshot(revision, entries, quotas, archives)
    }
}
