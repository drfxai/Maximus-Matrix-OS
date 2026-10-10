package ai.drfx.maximus.matrixai.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AgentConfirmationGateTest {
    @Test fun approvalRequiresExactRequestAndIsConsumedOnce() = runTest {
        val gate = AgentConfirmationGate()
        val result = async { gate.awaitApproval("mission", AgentAction("open_url", mapOf("url" to "https://example.org"))) }
        runCurrent()
        val pending = gate.pending.value!!
        assertFalse(gate.respond("stale", true))
        assertFalse(result.isCompleted)
        assertTrue(gate.respond(pending.id, true))
        assertFalse(gate.respond(pending.id, true))
        assertTrue(result.await())
        assertNull(gate.pending.value)
    }

    @Test fun rejectionAndCancellationClearPendingWithoutApproval() = runTest {
        val gate = AgentConfirmationGate()
        val rejected = async { gate.awaitApproval("mission", AgentAction("share_text")) }
        runCurrent()
        assertTrue(gate.respond(gate.pending.value!!.id, false))
        assertFalse(rejected.await())
        val cancelled = async { gate.awaitApproval("mission", AgentAction("create_note")) }
        runCurrent()
        val id = gate.pending.value!!.id
        cancelled.cancel()
        cancelled.join()
        assertNull(gate.pending.value)
        assertFalse(gate.respond(id, true))
    }
}
