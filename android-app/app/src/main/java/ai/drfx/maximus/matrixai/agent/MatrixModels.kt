package ai.drfx.maximus.matrixai.agent

import java.util.UUID

enum class MatrixEventType {
    MISSION_ACCEPTED,
    MEMORY_RECALLED,
    PLAN_CREATED,
    POLICY_CHECKED,
    CONFIRMATION_REQUIRED,
    TOOL_STARTED,
    TOOL_COMPLETED,
    VALIDATION_STARTED,
    VALIDATION_PASSED,
    VALIDATION_FAILED,
    ARTIFACT_CREATED,
    MISSION_COMPLETED,
    MISSION_FAILED
}

enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
enum class PolicyDecision { ALLOW, REQUIRE_CONFIRMATION, DENY }
enum class MissionState { IDLE, PLANNING, EXECUTING, WAITING_CONFIRMATION, VALIDATING, COMPLETED, FAILED }

data class MatrixEvent(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val type: MatrixEventType,
    val sourceNode: String,
    val targetNode: String? = null,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

data class AgentAction(
    val tool: String,
    val arguments: Map<String, String> = emptyMap(),
    val risk: RiskLevel = RiskLevel.LOW
)

data class MissionStep(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val action: AgentAction
)

data class Mission(
    val id: String = UUID.randomUUID().toString(),
    val objective: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class ToolOutcome(
    val success: Boolean,
    val output: String,
    val evidence: Map<String, String> = emptyMap()
)

data class MissionResult(
    val missionId: String,
    val success: Boolean,
    val summary: String
)
