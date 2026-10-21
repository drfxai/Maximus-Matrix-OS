package ai.drfx.maximus.matrixai.operations

/** Projections carry observation time separately from the time of the upstream event. */
internal enum class EvidenceState { OBSERVED, DERIVED, STALE, UNKNOWN, UNAVAILABLE, ERROR }
internal data class OperationalEvidence(
    val source: String,
    val value: String,
    val observedAt: Long,
    val eventAt: Long? = null,
    val state: EvidenceState = EvidenceState.OBSERVED
) {
    fun freshness(now: Long): EvidenceState = when {
        state in setOf(EvidenceState.ERROR, EvidenceState.UNKNOWN, EvidenceState.UNAVAILABLE) -> state
        observedAt <= 0L -> EvidenceState.UNKNOWN
        now < observedAt || eventAt?.let { it > observedAt || it < 0L } == true -> EvidenceState.ERROR
        now - observedAt > 30_000L -> EvidenceState.STALE
        else -> state
    }
}
internal data class RoutineProjection(
    val id: String, val name: String, val job: String, val enabled: Boolean,
    val periodMinutes: Long, val evidence: OperationalEvidence,
    val lastResult: OperationalEvidence, val nextEligibleAt: Long? = null
)
internal data class ProjectProjection(val id: String, val name: String, val fileCount: Int)
internal data class OperationsSnapshot(
    val observedAt: Long = 0L,
    val routines: List<RoutineProjection> = emptyList(),
    val projects: List<ProjectProjection> = emptyList(),
    val evidence: List<OperationalEvidence> = emptyList(),
    val personalAgents: Int? = null,
    val commandHistory: List<String> = emptyList(),
    val routinesState: EvidenceState = EvidenceState.UNKNOWN,
    val projectsState: EvidenceState = EvidenceState.UNKNOWN
)
