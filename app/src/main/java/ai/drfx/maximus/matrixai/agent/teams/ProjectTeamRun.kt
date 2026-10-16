package ai.drfx.maximus.matrixai.agent.teams

import java.security.MessageDigest
import java.util.UUID

/** A bounded text-only project workflow. Model output has no tool authority. */
data class ProjectTeamRun(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val revision: String,
    val prompt: String,
    val createdAt: Long,
    val owner: String,
    val expiresAt: Long,
    val state: State = State.AWAITING_APPROVAL,
    val planner: Evidence? = null,
    val reviewer: Evidence? = null,
    val activeProvider: String = "",
    val activeModel: String = "",
    val approvedDestinations: String = ""
) {
    enum class State { AWAITING_APPROVAL, PLANNER_READY, PLANNER_DISPATCHED,
        REVIEWER_READY, REVIEWER_DISPATCHED, COMPLETE, CANCELLED, UNCERTAIN, BLOCKED }
    data class Evidence(val provider: String, val model: String, val text: String,
        val inputTokens: Int, val outputTokens: Int, val estimated: Boolean) {
        val sha256: String get() = digest(text)
        init {
            require(provider.length in 1..100 && model.length in 1..300)
            require(text.length in 1..MAX_OUTPUT_CHARS)
            require(inputTokens >= 0 && outputTokens >= 0)
        }
    }
    val busy: Boolean get() = state in setOf(State.PLANNER_READY, State.PLANNER_DISPATCHED,
        State.REVIEWER_READY, State.REVIEWER_DISPATCHED)
    val dispatched: Boolean get() = state == State.PLANNER_DISPATCHED || state == State.REVIEWER_DISPATCHED
    init {
        require(UUID.fromString(id).toString() == id && UUID.fromString(projectId).toString() == projectId)
        require(revision.matches(Regex("[a-f0-9]{64}")))
        require(prompt.length in 1..MAX_PROMPT_CHARS && owner.isNotBlank())
        require(approvedDestinations.length <= 8192)
        require(createdAt >= 0 && expiresAt > createdAt)
        require(state !in setOf(State.REVIEWER_READY, State.REVIEWER_DISPATCHED, State.COMPLETE) || planner != null)
        require(state != State.COMPLETE || reviewer != null)
    }
    companion object {
        const val MAX_PROMPT_CHARS = 16_000
        const val MAX_OUTPUT_CHARS = 16_384
        const val MAX_OUTPUT_TOKENS = 1024
        const val LEASE_MS = 5 * 60_000L
        fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
