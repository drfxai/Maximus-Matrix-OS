package ai.drfx.maximus.matrixai.agent

import android.content.Context
import kotlinx.coroutines.CancellationException

class MaximusMatrixAgent(
    context: Context,
    private val planner: MatrixPlanner = MatrixPlanner(),
    private val policy: MatrixPolicyEngine = MatrixPolicyEngine(),
    private val events: MatrixEventBus = MatrixEventBus()
) {
    private val tools = MatrixToolRegistry(context.applicationContext)
    val eventStream = events.events
    private val confirmations = AgentConfirmationGate()
    val pendingConfirmation = confirmations.pending
    fun approveAction(requestId: String, approved: Boolean): Boolean = confirmations.respond(requestId, approved)

    suspend fun execute(objective: String): MissionResult {
        val mission = Mission(objective = objective.trim().ifBlank { "Inspect Matrix runtime" })
        emit(mission, MatrixEventType.MISSION_ACCEPTED, "agent:maximus", "mission:${mission.id}", mission.objective)
        return try {
            emit(mission, MatrixEventType.MEMORY_RECALLED, "memory:core", "agent:maximus", "Mission context prepared")
            val steps = planner.plan(mission)
            emit(mission, MatrixEventType.PLAN_CREATED, "planner:core", "agent:maximus", "Plan contains ${steps.size} steps")
            val evidence = mutableListOf<String>()
            for (step in steps) {
                val decision = policy.evaluate(step.action)
                emit(mission, MatrixEventType.POLICY_CHECKED, "policy:engine", "tool:${step.action.tool}", "Policy decision: $decision", mapOf("risk" to step.action.risk.name))
                if (decision == PolicyDecision.DENY) return fail(mission, "Tool ${step.action.tool} is unavailable or denied; the requested objective was not completed.")
                var confirmed = false
                if (decision == PolicyDecision.REQUIRE_CONFIRMATION) {
                    emit(mission, MatrixEventType.CONFIRMATION_REQUIRED, "policy:engine", "human:operator", "This action requires an explicit confirmation flow")
                    confirmed = confirmations.awaitApproval(mission.id, step.action)
                    if (!confirmed) return fail(mission, "User rejected ${step.action.tool}; no action was executed.")
                }
                emit(mission, MatrixEventType.TOOL_STARTED, "agent:maximus", "tool:${step.action.tool}", "Executing ${step.action.tool}")
                val outcome = tools.execute(step.action, confirmed = confirmed)
                evidence += "${step.action.tool}: ${outcome.output}"
                emit(mission, MatrixEventType.TOOL_COMPLETED, "tool:${step.action.tool}", "validation:lab", outcome.output, outcome.evidence + ("success" to outcome.success.toString()))
                emit(mission, MatrixEventType.VALIDATION_STARTED, "validation:lab", "tool:${step.action.tool}", "Validating outcome")
                if (!outcome.success) {
                    emit(mission, MatrixEventType.VALIDATION_FAILED, "validation:lab", "mission:${mission.id}", outcome.output)
                    return MissionResult(mission.id, false, outcome.output)
                }
                emit(mission, MatrixEventType.VALIDATION_PASSED, "validation:lab", "artifact:registry", "Outcome validated")
            }
            emit(mission, MatrixEventType.VALIDATION_PASSED, "artifact:registry", "mission:${mission.id}", "Mission event evidence emitted (not persisted)")
            emit(mission, MatrixEventType.MISSION_COMPLETED, "agent:maximus", "mission:${mission.id}", "Mission completed successfully")
            MissionResult(mission.id, true, evidence.joinToString("\n"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            fail(mission, error.message ?: "Unhandled agent failure")
        }
    }

    private suspend fun fail(mission: Mission, message: String): MissionResult {
        emit(mission, MatrixEventType.MISSION_FAILED, "agent:maximus", "mission:${mission.id}", message)
        return MissionResult(mission.id, false, message)
    }

    private suspend fun emit(mission: Mission, type: MatrixEventType, source: String, target: String?, message: String, metadata: Map<String, String> = emptyMap()) {
        events.emit(MatrixEvent(missionId = mission.id, type = type, sourceNode = source, targetNode = target, message = message, metadata = metadata))
    }
}
