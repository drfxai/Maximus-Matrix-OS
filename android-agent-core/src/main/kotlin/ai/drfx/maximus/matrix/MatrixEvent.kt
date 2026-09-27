package ai.drfx.maximus.matrix
import java.time.Instant
import java.util.UUID
enum class MatrixEventType { MISSION_ACCEPTED, PLAN_CREATED, RETRIEVAL_STARTED, MEMORY_RECALLED, POLICY_CHECKED, CONFIRMATION_REQUIRED, TOOL_STARTED, TOOL_COMPLETED, VALIDATION_STARTED, VALIDATION_PASSED, VALIDATION_FAILED, ARTIFACT_CREATED, MISSION_COMPLETED, MISSION_FAILED }
data class MatrixEvent(val id:String=UUID.randomUUID().toString(),val missionId:String,val type:MatrixEventType,val sourceNode:String,val targetNode:String?=null,val message:String,val timestamp:Instant=Instant.now(),val metadata:Map<String,String> = emptyMap())
fun interface MatrixEventSink { suspend fun emit(event: MatrixEvent) }
