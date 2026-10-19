package ai.drfx.maximus.matrixai.studio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PineRevision(val code: String, val savedAt: Long)
data class PineDraft(
    val id: String,
    val name: String,
    val brief: String,
    val code: String,
    val revisions: List<PineRevision>,
    val updatedAt: Long
)

/** Private on-device Pine drafts. Revisions are actual saved snapshots, never invented results. */
class PineStudioStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("pine_studio_v1", Context.MODE_PRIVATE)

    fun list(): List<PineDraft> = runCatching {
        val array = JSONArray(preferences.getString("drafts", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val revisions = item.optJSONArray("revisions") ?: JSONArray()
            PineDraft(
                id = item.getString("id"),
                name = item.getString("name"),
                brief = item.optString("brief"),
                code = item.optString("code"),
                revisions = (0 until revisions.length()).map { i ->
                    val revision = revisions.getJSONObject(i)
                    PineRevision(revision.getString("code"), revision.getLong("savedAt"))
                },
                updatedAt = item.optLong("updatedAt")
            )
        }
    }.getOrDefault(emptyList())

    fun create(name: String): PineDraft {
        val clean = name.trim().take(60)
        require(clean.isNotBlank()) { "Enter a draft name." }
        val draft = PineDraft(
            id = UUID.randomUUID().toString(), name = clean,
            brief = if (clean.equals("Liquidity Sweep", ignoreCase = true))
                "Mark liquidity sweeps and add configurable alerts." else "",
            code = starterCode(clean), revisions = emptyList(), updatedAt = System.currentTimeMillis()
        )
        persist(list() + draft)
        return draft
    }

    fun update(id: String, name: String? = null, brief: String? = null, code: String? = null): PineDraft {
        val current = list()
        val old = current.firstOrNull { it.id == id } ?: error("Draft unavailable.")
        val cleanName = name?.trim()?.take(60) ?: old.name
        require(cleanName.isNotBlank()) { "Enter a draft name." }
        require((brief ?: old.brief).length <= 12_000 && (code ?: old.code).length <= 100_000) { "Draft is too large." }
        val newRevisions = if (code != null && code != old.code)
            (listOf(PineRevision(old.code, old.updatedAt)) + old.revisions).take(30) else old.revisions
        val changed = old.copy(
            name = cleanName, brief = brief ?: old.brief, code = code ?: old.code,
            revisions = newRevisions, updatedAt = System.currentTimeMillis()
        )
        persist(current.map { if (it.id == id) changed else it })
        return changed
    }

    fun delete(id: String) { persist(list().filterNot { it.id == id }) }

    private fun persist(drafts: List<PineDraft>) {
        val array = JSONArray()
        drafts.forEach { draft ->
            val revisions = JSONArray()
            draft.revisions.forEach { revisions.put(JSONObject().put("code", it.code).put("savedAt", it.savedAt)) }
            array.put(JSONObject()
                .put("id", draft.id).put("name", draft.name).put("brief", draft.brief)
                .put("code", draft.code).put("revisions", revisions).put("updatedAt", draft.updatedAt))
        }
        check(preferences.edit().putString("drafts", array.toString()).commit()) { "Could not save Pine draft." }
    }

    companion object {
        fun starterCode(name: String): String {
            val title = name.replace("\\", "").replace("\"", "'").replace("\n", " ").take(60)
            return """//@version=6
indicator("$title", overlay=true)
length = input.int(20, "Lookback", minval=2)
level = ta.highest(high[1], length)
sweep = high > level and close < level
plotshape(sweep, title="Sweep", style=shape.triangledown, location=location.abovebar, color=color.orange)
alertcondition(sweep, title="Liquidity sweep", message="Potential liquidity sweep detected")"""
        }
    }
}
