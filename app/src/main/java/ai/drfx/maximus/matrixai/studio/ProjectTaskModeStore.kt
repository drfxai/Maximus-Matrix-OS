package ai.drfx.maximus.matrixai.studio

import android.content.Context

/** Only the local workspace ID and task-mode identifier are persisted. */
class ProjectTaskModeStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "maximus_project_task_modes_v1", Context.MODE_PRIVATE
    )
    private val projectIdPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun read(projectId: String?): ProjectTaskMode {
        if (projectId == null || !projectIdPattern.matches(projectId)) return ProjectTaskMode.DEVELOP
        return ProjectTaskMode.fromStored(preferences.getString(projectId, null))
    }

    fun save(projectId: String, mode: ProjectTaskMode) {
        require(projectIdPattern.matches(projectId)) { "Select a valid workspace." }
        check(preferences.edit().putString(projectId, mode.name).commit()) {
            "Unable to save project mode."
        }
    }

    fun clear(projectId: String) {
        require(projectIdPattern.matches(projectId))
        check(preferences.edit().remove(projectId).commit())
    }
}
