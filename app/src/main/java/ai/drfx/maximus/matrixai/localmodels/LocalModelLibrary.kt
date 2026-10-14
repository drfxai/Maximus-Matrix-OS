package ai.drfx.maximus.matrixai.localmodels

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LocalModelEntry(
    val id: String,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val format: LocalModelFormat,
    val headerVerified: Boolean,
    val note: String
)

/** Metadata and SAF URI only. Model data stays in its original user-owned file. */
class LocalModelLibrary(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "maximus_local_model_inventory_v1", Context.MODE_PRIVATE
    )

    fun list(): List<LocalModelEntry> = runCatching {
        val data = JSONArray(prefs.getString("items", "[]"))
        (0 until data.length()).map { index ->
            val item = data.getJSONObject(index)
            val format = LocalModelFormat.valueOf(item.getString("format"))
            LocalModelEntry(
                id = item.getString("id"),
                uri = item.getString("uri"),
                displayName = item.getString("name"),
                sizeBytes = item.getLong("size"),
                format = format,
                headerVerified = item.getBoolean("headerVerified"),
                note = item.getString("note")
            )
        }.filter { valid(it) }
    }.getOrDefault(emptyList())

    fun register(uri: Uri, name: String, sizeBytes: Long, probe: LocalModelProbe): LocalModelEntry {
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank()) {
            "Choose a file from the Android document picker."
        }
        require(probe.format != LocalModelFormat.UNSUPPORTED)
        require(!probe.canRun) { "Import cannot certify model execution." }
        val value = LocalModelEntry(
            UUID.randomUUID().toString(), uri.toString(), name, sizeBytes,
            probe.format, probe.headerVerified, probe.note
        )
        require(valid(value)) { "Unsupported model metadata." }
        val current = list()
        require(current.size < 24 || current.any { it.uri == value.uri }) {
            "Model library limit reached."
        }
        save(current.filterNot { it.uri == value.uri } + value)
        return value
    }

    fun forget(id: String): LocalModelEntry? {
        val current = list()
        val previous = current.firstOrNull { it.id == id } ?: return null
        save(current.filterNot { it.id == id })
        return previous
    }

    private fun save(items: List<LocalModelEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id).put("uri", item.uri)
                .put("name", item.displayName).put("size", item.sizeBytes)
                .put("format", item.format.name)
                .put("headerVerified", item.headerVerified)
                .put("note", item.note))
        }
        check(prefs.edit().putString("items", array.toString()).commit()) {
            "Could not save model inventory."
        }
    }

    private fun valid(item: LocalModelEntry): Boolean {
        val uri = runCatching { Uri.parse(item.uri) }.getOrNull()
        return item.id.matches(Regex("[a-f0-9-]{36}")) &&
            uri != null && uri.scheme == "content" && !uri.authority.isNullOrBlank() &&
            item.displayName.length in 1..128 &&
            item.displayName.none { it == '/' || it == '\\' || it == '\u0000' } &&
            item.sizeBytes in 8L..LocalModelIntakePolicy.MAX_MODEL_BYTES &&
            item.format != LocalModelFormat.UNSUPPORTED &&
            item.note.length <= 300
    }
}
