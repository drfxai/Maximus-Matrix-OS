package ai.drfx.maximus.matrixai.agent

import java.util.Locale

class MatrixPlanner {
    fun plan(mission: Mission): List<MissionStep> {
        val objective = mission.objective.trim()
        val normalized = objective.lowercase(Locale.ROOT)
        // Match command syntax separately from user payloads. Payload words must
        // never select another tool or add unrelated actions to the plan.
        fun payload(prefix: String) = objective.drop(prefix.length).trimStart()
        val steps = mutableListOf<MissionStep>()
        steps += MissionStep(description = "Read device runtime status", action = AgentAction("device_info"))

        when {
            normalized.startsWith("search ") -> steps += MissionStep(description = "Open web search", action = AgentAction("web_search", mapOf("query" to payload("search ").trim())))
            normalized.startsWith("open url ") -> steps += MissionStep(description = "Open URL", action = AgentAction("open_url", mapOf("url" to payload("open url ").trim())))
            (normalized == "settings" || normalized.startsWith("settings ")) -> steps += MissionStep(description = "Open Android settings", action = AgentAction("open_settings", mapOf("section" to normalized.substringAfter("settings", "").trim())))
            normalized in setOf("camera", "open camera", "launch camera", "start camera", "take photo", "take a photo") -> steps += MissionStep(description = "Open camera", action = AgentAction("open_camera"))
            normalized.startsWith("dial ") -> steps += MissionStep(description = "Open dialer", action = AgentAction("open_dialer", mapOf("number" to payload("dial ").trim()), RiskLevel.MEDIUM))
            normalized.startsWith("sms ") -> {
                val smsPayload = payload("sms ").trim()
                val number = smsPayload.substringBefore(" ").trim()
                val message = smsPayload.substringAfter(" ", "").trim()
                steps += MissionStep(description = "Open SMS composer", action = AgentAction("compose_sms", mapOf("number" to number, "message" to message), RiskLevel.MEDIUM))
            }
            normalized.startsWith("copy ") -> steps += MissionStep(description = "Copy text", action = AgentAction("copy_clipboard", mapOf("text" to payload("copy "))))
            normalized.startsWith("share ") -> steps += MissionStep(description = "Share text", action = AgentAction("share_text", mapOf("text" to payload("share ")), RiskLevel.MEDIUM))
            normalized.startsWith("note ") -> steps += MissionStep(description = "Store mission note", action = AgentAction("create_note", mapOf("text" to payload("note "))))
            normalized.startsWith("alarm ") -> {
                val alarmPayload = payload("alarm ").trim()
                val hhmm = alarmPayload.substringBefore(" ").split(":")
                val label = alarmPayload.substringAfter(" ", "").ifBlank { "MAXIMUS AI" }
                steps += MissionStep(description = "Open alarm editor", action = AgentAction("set_alarm", mapOf("hour" to (hhmm.getOrNull(0) ?: "9"), "minute" to (hhmm.getOrNull(1) ?: "0"), "label" to label), RiskLevel.MEDIUM))
            }
            normalized.startsWith("calendar ") -> steps += MissionStep(description = "Open calendar event editor", action = AgentAction("create_calendar_event", mapOf("title" to payload("calendar ")), RiskLevel.MEDIUM))
            normalized.startsWith("app ") -> steps += MissionStep(description = "Open Android application", action = AgentAction("open_app", mapOf("package" to payload("app ").trim())))
        }

        val hasExplicitAction = steps.size > 1
        if (!hasExplicitAction && (normalized.contains("research") || normalized.contains("strategy") || normalized.contains("indicator"))) {
            steps += MissionStep(description = "Retrieve relevant knowledge", action = AgentAction("knowledge_lookup", mapOf("query" to objective)))
        }
        if (!hasExplicitAction && (normalized.contains("validate") || normalized.contains("strategy"))) {
            steps += MissionStep(description = "Register strategy validation", action = AgentAction("validate_strategy", mapOf("objective" to objective), RiskLevel.MEDIUM))
        }
        if (steps.size == 1 && normalized !in setOf("status", "device info", "inspect matrix runtime", "system status")) {
            steps += MissionStep(description = "Unsupported objective", action = AgentAction("unsupported_objective"))
        }
        return steps.distinctBy { it.action.tool + it.action.arguments.toString() }.take(8)
    }
}
