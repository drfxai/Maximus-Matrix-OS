package ai.drfx.maximus.matrixai.terminal

import android.content.Context
import android.util.AtomicFile
import ai.drfx.maximus.matrixai.database.ChatContentCipher
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local, encrypted and intentionally bounded console transcript; not a shell history. */
internal data class TerminalHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val command: String,
    val output: String
)

/** Pure serialization code can be JVM-tested without an Android KeyStore. */
internal object TerminalHistoryCodec {
    private const val VERSION = 1
    const val MAX_ENTRIES = 30
    const val MAX_JSON_CHARS = 90_000

    fun encode(entries: List<TerminalHistoryEntry>): String {
        val lines = JSONArray()
        entries.takeLast(MAX_ENTRIES).forEach { entry ->
            lines.put(JSONObject().apply {
                put("id", entry.id)
                put("timestamp", entry.timestampMs)
                put("command", TerminalCommandPolicy.redact(entry.command).take(500))
                put("output", TerminalCommandPolicy.redact(entry.output).take(1200))
            })
        }
        return JSONObject().put("version", VERSION).put("entries", lines).toString().also {
            require(it.length <= MAX_JSON_CHARS) { "Console history exceeds size limit." }
        }
    }

    fun decode(text: String): List<TerminalHistoryEntry> {
        require(text.length <= MAX_JSON_CHARS) { "Console history exceeds size limit." }
        val root = JSONObject(text)
        require(root.getInt("version") == VERSION) { "Unsupported console history schema." }
        val lines = root.getJSONArray("entries")
        require(lines.length() <= MAX_ENTRIES) { "Console history entry limit exceeded." }
        return buildList {
            for (i in 0 until lines.length()) {
                val record = lines.getJSONObject(i)
                val id = record.getString("id")
                val time = record.getLong("timestamp")
                val cmd = record.getString("command")
                val output = record.getString("output")
                require(UUID.fromString(id).toString() == id && time >= 0)
                require(cmd.length <= 500 && output.length <= 1200)
                add(TerminalHistoryEntry(id, time,
                    TerminalCommandPolicy.redact(cmd), TerminalCommandPolicy.redact(output)))
            }
        }
    }
}

/** No plaintext fallback and no silent overwrite if the KeyStore cannot decrypt. */
internal class TerminalHistoryStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "terminal/console-history.aes"))
    private val cipher = ChatContentCipher()

    fun load(): List<TerminalHistoryEntry> {
        if (!file.baseFile.exists()) return emptyList()
        require(file.baseFile.length() in 1..200_000L) { "Invalid console history file size." }
        val payload = file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        require(cipher.isEncrypted(payload)) { "Unencrypted console history rejected." }
        return TerminalHistoryCodec.decode(cipher.decrypt(payload))
    }

    fun save(entries: List<TerminalHistoryEntry>) {
        val text = cipher.encrypt(TerminalHistoryCodec.encode(entries))
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}
