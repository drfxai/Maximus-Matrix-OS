package ai.drfx.maximus.matrixai.agent

import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Each approval is bound to exactly one immutable action and one mission. */
data class PendingAgentConfirmation(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val action: AgentAction
)

class AgentConfirmationGate {
    private val mutex = Mutex()
    private val mutablePending = MutableStateFlow<PendingAgentConfirmation?>(null)
    val pending = mutablePending.asStateFlow()
    private val responseLock = Any()
    private var response: CompletableDeferred<Boolean>? = null

    suspend fun awaitApproval(missionId: String, action: AgentAction): Boolean = mutex.withLock {
        val request = PendingAgentConfirmation(missionId = missionId, action = action.copy(arguments = action.arguments.toMap()))
        val answer = CompletableDeferred<Boolean>()
        synchronized(responseLock) {
            response = answer
            mutablePending.value = request
        }
        try { answer.await() } finally {
            synchronized(responseLock) {
                mutablePending.value = null
                response = null
            }
        }
    }

    fun respond(requestId: String, approved: Boolean): Boolean = synchronized(responseLock) {
        if (mutablePending.value?.id != requestId) false
        else response?.complete(approved) ?: false
    }
}
