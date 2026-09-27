package ai.drfx.maximus.matrixai.agent

import kotlinx.coroutines.delay

class MaximusMatrixAgent(
    private val planner: MatrixPlanner = MatrixPlanner(),
    private val policy: MatrixPolicyEngine = MatrixPolicyEngine(),
    private val tools: MatrixToolRegistry = MatrixToolRegistry(),
    private val events: MatrixEventBus = MatrixEventBus()
) {
    val eventStream = events.events

    suspend fun execute(objective: String): MissionResult {
        val mission = Mission(objective = objective.trim().ifBlank { "Inspect Matrix runtime" })
        emit(mission, MatrixEventType.MISSION_ACCEPTED, "agent:maximus", "mission:${mission.id}", mission.objective)

        return try {
            emit(mission, MatrixEventType.MEMORY_RECALLED, "memory:core", "agent:maximus", "Mission memory context prepared")
            delay(110)
            val steps = planner.plan(mission)
            emit(mission, MatrixEventType.PLAN_CREATED, "planner:core", "agent:maximus", "Plan contains ${steps.size} steps")

            for (step in steps) {
                val decision = policy.evaluate(step.action)
                emit(
                    mission,
                    MatrixEventType.POLICY_CHECKED,
                    "policy:engine",
                    "tool:${step.action.tool}",
                    "Policy decision: $decision",
                    mapOf("risk" to step.action.risk.name)
                )

                when (decision) {
                    PolicyDecision.DENY -> {
                        emit(mission, MatrixEventType.MISSION_FAILED, "policy:engine", "mission:${mission.id}", "Action denied by policy")
                        return MissionResult(mission.id, false, "Mission stopped by policy.")
                    }
                    PolicyDecision.REQUIRE_CONFIRMATION -> {
                        emit(mission, MatrixEventType.CONFIRMATION_REQUIRED, "policy:engine", "human:operator", "Confirmation is required before this action")
                        return MissionResult(mission.id, false, "Mission paused for human confirmation.")
                    }
                    PolicyDecision.ALLOW -> Unit
                }

                emit(mission, MatrixEventType.TOOL_STARTED, "agent:maximus", "tool:${step.action.tool}", "Executing ${step.action.tool}")
                delay(140)
                val outcome = tools.execute(step.action)
                emit(
                    mission,
                    MatrixEventType.TOOL_COMPLETED,
                    "tool:${step.action.tool}",
                    "validation:lab",
                    outcome.output,
                    outcome.evidence + ("success" to outcome.success.toString())
                )

                emit(mission, MatrixEventType.VALIDATION_STARTED, "validation:lab", "tool:${step.action.tool}", "Validating tool outcome")
                delay(90)
                if (!outcome.success) {
                    emit(mission, MatrixEventType.VALIDATION_FAILED, "validation:lab", "mission:${mission.id}", "Validation failed")
                    return MissionResult(mission.id, false, "Mission stopped by validation.")
                }
                emit(mission, MatrixEventType.VALIDATION_PASSED, "validation:lab", "artifact:registry", "Validation passed")
            }

            emit(mission, MatrixEventType.ARTIFACT_CREATED, "artifact:registry", "mission:${mission.id}", "Mission evidence bundle committed")
            emit(mission, MatrixEventType.MISSION_COMPLETED, "agent:maximus", "mission:${mission.id}", "Mission completed successfully")
            MissionResult(mission.id, true, "Mission completed successfully.")
        } catch (error: Throwable) {
            emit(mission, MatrixEventType.MISSION_FAILED, "agent:maximus", "mission:${mission.id}", error.message ?: "Unhandled agent failure")
            MissionResult(mission.id, false, "Mission failed safely.")
        }
    }

    private suspend fun emit(
        mission: Mission,
        type: MatrixEventType,
        source: String,
        target: String?,
        message: String,
        metadata: Map<String, String> = emptyMap()
    ) {
        events.emit(
            MatrixEvent(
                missionId = mission.id,
                type = type,
                sourceNode = source,
                targetNode = target,
                message = message,
                metadata = metadata
            )
        )
    }
}
