package ai.drfx.maximus.matrixai.agent

class MatrixPlanner {
    fun plan(mission: Mission): List<MissionStep> {
        val objective = mission.objective.trim()
        val normalized = objective.lowercase()
        val steps = mutableListOf<MissionStep>()
        steps += MissionStep(description = "Read device runtime status", action = AgentAction("device_info"))

        when {
            normalized.startsWith("search ") -> steps += MissionStep("Open web search", AgentAction("web_search", mapOf("query" to objective.substringAfter("search ").trim())))
            normalized.startsWith("open url ") -> steps += MissionStep("Open URL", AgentAction("open_url", mapOf("url" to objective.substringAfter("open url ").trim())))
            normalized.startsWith("settings") -> steps += MissionStep("Open Android settings", AgentAction("open_settings", mapOf("section" to normalized.substringAfter("settings", "").trim())))
            normalized.contains("camera") -> steps += MissionStep("Open camera", AgentAction("open_camera"))
            normalized.startsWith("dial ") -> steps += MissionStep("Open dialer", AgentAction("open_dialer", mapOf("number" to objective.substringAfter("dial ").trim()), RiskLevel.MEDIUM))
            normalized.startsWith("sms ") -> {
                val payload = objective.substringAfter("sms ").trim()
                val number = payload.substringBefore(" ").trim()
                val message = payload.substringAfter(" ", "").trim()
                steps += MissionStep("Open SMS composer", AgentAction("compose_sms", mapOf("number" to number, "message" to message), RiskLevel.MEDIUM))
            }
            normalized.startsWith("copy ") -> steps += MissionStep("Copy text", AgentAction("copy_clipboard", mapOf("text" to objective.substringAfter("copy "))))
            normalized.startsWith("share ") -> steps += MissionStep("Share text", AgentAction("share_text", mapOf("text" to objective.substringAfter("share ")), RiskLevel.MEDIUM))
            normalized.startsWith("note ") -> steps += MissionStep("Store mission note", AgentAction("create_note", mapOf("text" to objective.substringAfter("note "))))
            normalized.startsWith("alarm ") -> {
                val hhmm = normalized.substringAfter("alarm ").substringBefore(" ").split(":")
                steps += MissionStep("Open alarm editor", AgentAction("set_alarm", mapOf("hour" to (hhmm.getOrNull(0) ?: "9"), "minute" to (hhmm.getOrNull(1) ?: "0"), "label" to objective.substringAfter(" ", "MAXIMUS MATRIX AI")), RiskLevel.MEDIUM))
            }
            normalized.startsWith("calendar ") -> steps += MissionStep("Open calendar event editor", AgentAction("create_calendar_event", mapOf("title" to objective.substringAfter("calendar ")), RiskLevel.MEDIUM))
            normalized.startsWith("app ") -> steps += MissionStep("Open Android application", AgentAction("open_app", mapOf("package" to objective.substringAfter("app ").trim())))
        }

        if (normalized.contains("research") || normalized.contains("strategy") || normalized.contains("indicator")) {
            steps += MissionStep("Retrieve relevant knowledge", AgentAction("knowledge_lookup", mapOf("query" to objective)))
        }
        if (normalized.contains("validate") || normalized.contains("strategy")) {
            steps += MissionStep("Register strategy validation", AgentAction("validate_strategy", mapOf("objective" to objective), RiskLevel.MEDIUM))
        }
        return steps.distinctBy { it.action.tool + it.action.arguments.toString() }.take(8)
    }
}
