package ai.drfx.maximus.matrixai.agent

class MatrixPlanner {
    fun plan(mission: Mission): List<MissionStep> {
        val objective = mission.objective.trim()
        val normalized = objective.lowercase()
        val steps = mutableListOf<MissionStep>()
        steps += MissionStep(description = "Read device runtime status", action = AgentAction("device_info"))

        when {
            normalized.startsWith("search ") -> steps += MissionStep(description = "Open web search", action = AgentAction("web_search", mapOf("query" to objective.substringAfter("search ").trim())))
            normalized.startsWith("open url ") -> steps += MissionStep(description = "Open URL", action = AgentAction("open_url", mapOf("url" to objective.substringAfter("open url ").trim())))
            normalized.startsWith("settings") -> steps += MissionStep(description = "Open Android settings", action = AgentAction("open_settings", mapOf("section" to normalized.substringAfter("settings", "").trim())))
            normalized.contains("camera") -> steps += MissionStep(description = "Open camera", action = AgentAction("open_camera"))
            normalized.startsWith("dial ") -> steps += MissionStep(description = "Open dialer", action = AgentAction("open_dialer", mapOf("number" to objective.substringAfter("dial ").trim()), RiskLevel.MEDIUM))
            normalized.startsWith("sms ") -> {
                val payload = objective.substringAfter("sms ").trim()
                val number = payload.substringBefore(" ").trim()
                val message = payload.substringAfter(" ", "").trim()
                steps += MissionStep(description = "Open SMS composer", action = AgentAction("compose_sms", mapOf("number" to number, "message" to message), RiskLevel.MEDIUM))
            }
            normalized.startsWith("copy ") -> steps += MissionStep(description = "Copy text", action = AgentAction("copy_clipboard", mapOf("text" to objective.substringAfter("copy "))))
            normalized.startsWith("share ") -> steps += MissionStep(description = "Share text", action = AgentAction("share_text", mapOf("text" to objective.substringAfter("share ")), RiskLevel.MEDIUM))
            normalized.startsWith("note ") -> steps += MissionStep(description = "Store mission note", action = AgentAction("create_note", mapOf("text" to objective.substringAfter("note "))))
            normalized.startsWith("alarm ") -> {
                val alarmPayload = objective.substringAfter("alarm ").trim()
                val hhmm = alarmPayload.substringBefore(" ").split(":")
                val label = alarmPayload.substringAfter(" ", "").ifBlank { "MAXIMUS AI" }
                steps += MissionStep(description = "Open alarm editor", action = AgentAction("set_alarm", mapOf("hour" to (hhmm.getOrNull(0) ?: "9"), "minute" to (hhmm.getOrNull(1) ?: "0"), "label" to label), RiskLevel.MEDIUM))
            }
            normalized.startsWith("calendar ") -> steps += MissionStep(description = "Open calendar event editor", action = AgentAction("create_calendar_event", mapOf("title" to objective.substringAfter("calendar ")), RiskLevel.MEDIUM))
            normalized.startsWith("app ") -> steps += MissionStep(description = "Open Android application", action = AgentAction("open_app", mapOf("package" to objective.substringAfter("app ").trim())))
        }

        if (normalized.contains("research") || normalized.contains("strategy") || normalized.contains("indicator")) {
            steps += MissionStep(description = "Retrieve relevant knowledge", action = AgentAction("knowledge_lookup", mapOf("query" to objective)))
        }
        if (normalized.contains("validate") || normalized.contains("strategy")) {
            steps += MissionStep(description = "Register strategy validation", action = AgentAction("validate_strategy", mapOf("objective" to objective), RiskLevel.MEDIUM))
        }
        return steps.distinctBy { it.action.tool + it.action.arguments.toString() }.take(8)
    }
}
