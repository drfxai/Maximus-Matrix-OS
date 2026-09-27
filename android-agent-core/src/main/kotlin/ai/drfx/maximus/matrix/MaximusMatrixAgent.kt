package ai.drfx.maximus.matrix
import java.util.UUID
data class Mission(val id:String=UUID.randomUUID().toString(),val objective:String)
data class MissionStep(val id:String,val description:String,val action:AgentAction)
data class ToolOutcome(val success:Boolean,val output:String)
data class MissionResult(val missionId:String,val success:Boolean,val summary:String)
fun interface MissionPlanner { suspend fun plan(mission:Mission):List<MissionStep> }
fun interface ToolExecutor { suspend fun execute(action:AgentAction):ToolOutcome }
fun interface ConfirmationGate { suspend fun approve(mission:Mission,step:MissionStep):Boolean }
fun interface ResultValidator { suspend fun validate(step:MissionStep,outcome:ToolOutcome):Boolean }
fun interface MemoryGateway { suspend fun contextFor(objective:String):String }
class MaximusMatrixAgent(private val planner:MissionPlanner,private val tools:ToolExecutor,private val policy:MatrixPolicyEngine,private val confirmation:ConfirmationGate,private val validator:ResultValidator,private val memory:MemoryGateway,private val events:MatrixEventSink) {
 suspend fun execute(objective:String):MissionResult {
  val mission=Mission(objective=objective)
  emit(mission,MatrixEventType.MISSION_ACCEPTED,"agent:maximus","mission:"+mission.id,objective)
  return try {
   val memoryContext=memory.contextFor(objective)
   emit(mission,MatrixEventType.MEMORY_RECALLED,"memory:core","agent:maximus","Relevant memory loaded",mapOf("contextAvailable" to memoryContext.isNotBlank().toString()))
   val steps=planner.plan(mission)
   emit(mission,MatrixEventType.PLAN_CREATED,"agent:maximus","planner:core","Plan created",mapOf("steps" to steps.size.toString()))
   for(step in steps) {
    val decision=policy.evaluate(step.action)
    emit(mission,MatrixEventType.POLICY_CHECKED,"policy:engine","tool:"+step.action.tool,"Policy decision: "+decision,mapOf("risk" to step.action.risk.name))
    if(decision==PolicyDecision.DENY) { emit(mission,MatrixEventType.MISSION_FAILED,"policy:engine","mission:"+mission.id,"Action denied by policy: "+step.action.tool); return MissionResult(mission.id,false,"Mission stopped by policy.") }
    if(decision==PolicyDecision.REQUIRE_CONFIRMATION) {
     emit(mission,MatrixEventType.CONFIRMATION_REQUIRED,"policy:engine","human:operator","Human confirmation required for "+step.action.tool)
     if(!confirmation.approve(mission,step)) return MissionResult(mission.id,false,"Mission cancelled at confirmation gate.")
    }
    emit(mission,MatrixEventType.TOOL_STARTED,"agent:maximus","tool:"+step.action.tool,"Executing "+step.action.tool)
    val outcome=tools.execute(step.action)
    emit(mission,MatrixEventType.TOOL_COMPLETED,"tool:"+step.action.tool,"agent:maximus",outcome.output,mapOf("success" to outcome.success.toString()))
    if(!outcome.success) { emit(mission,MatrixEventType.MISSION_FAILED,"tool:"+step.action.tool,"mission:"+mission.id,"Tool execution failed"); return MissionResult(mission.id,false,"Mission failed during tool execution.") }
    emit(mission,MatrixEventType.VALIDATION_STARTED,"validation:lab","tool:"+step.action.tool,"Validating tool outcome")
    val valid=validator.validate(step,outcome)
    emit(mission,if(valid) MatrixEventType.VALIDATION_PASSED else MatrixEventType.VALIDATION_FAILED,"validation:lab","mission:"+mission.id,if(valid) "Validation passed" else "Validation failed")
    if(!valid) return MissionResult(mission.id,false,"Mission stopped by validation.")
   }
   emit(mission,MatrixEventType.MISSION_COMPLETED,"agent:maximus","mission:"+mission.id,"Mission completed successfully")
   MissionResult(mission.id,true,"Mission completed successfully.")
  } catch(error:Throwable) {
   emit(mission,MatrixEventType.MISSION_FAILED,"agent:maximus","mission:"+mission.id,error.message?:"Unhandled agent failure")
   MissionResult(mission.id,false,"Mission failed safely.")
  }
 }
 private suspend fun emit(mission:Mission,type:MatrixEventType,source:String,target:String?,message:String,metadata:Map<String,String> = emptyMap())=events.emit(MatrixEvent(missionId=mission.id,type=type,sourceNode=source,targetNode=target,message=message,metadata=metadata))
}
