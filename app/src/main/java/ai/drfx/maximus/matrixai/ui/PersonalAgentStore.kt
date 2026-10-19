package ai.drfx.maximus.matrixai.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class PersonalAgent(
    val id: String,
    val name: String,
    val instructions: String,
    val tasks: List<String>
)

internal class PersonalAgentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("personal_agents_v1", Context.MODE_PRIVATE)

    fun list(): List<PersonalAgent> = runCatching {
        val array = JSONArray(prefs.getString("agents", "[]"))
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val tasks = obj.optJSONArray("tasks") ?: JSONArray()
            PersonalAgent(obj.getString("id"), obj.getString("name"), obj.optString("instructions"),
                (0 until tasks.length()).map { tasks.getString(it) })
        }
    }.getOrDefault(emptyList())

    fun create(name: String): PersonalAgent {
        val clean = name.trim().take(60)
        require(clean.isNotBlank()) { "Enter an agent name." }
        val profile = PersonalAgent(UUID.randomUUID().toString(), clean, "", emptyList())
        persist(list() + profile)
        return profile
    }

    fun save(profile: PersonalAgent) {
        require(profile.name.isNotBlank() && profile.name.length <= 60) { "Invalid agent name." }
        require(profile.instructions.length <= 8000 && profile.tasks.size <= 20 &&
            profile.tasks.all { it.length <= 1000 }) { "Agent profile is too large." }
        persist(list().map { if (it.id == profile.id) profile else it })
    }

    fun delete(id: String) { persist(list().filterNot { it.id == id }) }

    private fun persist(profiles: List<PersonalAgent>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(JSONObject().put("id", profile.id).put("name", profile.name)
                .put("instructions", profile.instructions).put("tasks", JSONArray(profile.tasks)))
        }
        check(prefs.edit().putString("agents", array.toString()).commit()) { "Could not save agent." }
    }
}
