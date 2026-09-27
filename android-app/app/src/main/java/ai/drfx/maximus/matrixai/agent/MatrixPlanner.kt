package ai.drfx.maximus.matrixai.agent

class MatrixPlanner {
    fun plan(mission: Mission): List<MissionStep> {
        val objective = mission.objective.trim()
        val normalized = objective.lowercase()
        val steps = mutableListOf<MissionStep>()

        steps += MissionStep(
            description = "Read runtime status",
            action = AgentAction("system_status")
        )

        if (normalized.contains("research") || normalized.contains("strategy") || normalized.contains("indicator")) {
            steps += MissionStep(
                description = "Retrieve relevant knowledge",
                action = AgentAction("knowledge_lookup", mapOf("query" to objective))
            )
        }

        if (normalized.contains("validate") || normalized.contains("strategy")) {
            steps += MissionStep(
                description = "Register strategy validation",
                action = AgentAction("validate_strategy", mapOf("objective" to objective), RiskLevel.MEDIUM)
            )
        }

        if (normalized.startsWith("note ")) {
            steps += MissionStep(
                description = "Store mission note",
                action = AgentAction("create_note", mapOf("text" to objective.removePrefix("note ")))
            )
        }

        return steps.take(6)
    }
}
