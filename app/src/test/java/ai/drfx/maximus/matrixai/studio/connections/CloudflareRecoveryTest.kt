package ai.drfx.maximus.matrixai.studio.connections

import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudflareRecoveryTest {
    private val account = "a".repeat(32)
    private val workerId = "b".repeat(32)
    private fun response(result: JSONObject) = JSONObject().put("success", true).put("result", result).toString().toByteArray()
    @Test fun uncertainCreateSurvivesRestartAndBlocksDuplicatePost() = runTest {
        var saved: String? = null
        val journal = CloudflareMutationJournal({ saved }, { saved = it })
        var posts = 0
        val client = CloudflareDeploymentClient({ "r" }, { _, method, _, _ ->
            if (method == "POST") posts++
            throw SocketTimeoutException()
        }, journal = journal)
        assertTrue(runCatching { client.create(account, "agent", CloudAgentPurpose.ASSISTANT, "x".repeat(64), true) }.isFailure)
        assertTrue(runCatching { client.create(account, "agent", CloudAgentPurpose.ASSISTANT, "x".repeat(64), true) }.isFailure)
        assertEquals(1, posts)
        assertNotNull(CloudflareMutationJournal({ saved }, { saved = it }).pending(account, "agent"))
        assertFalse(saved!!.contains("x".repeat(64)))
    }
    @Test fun positiveMarkerRequiresExplicitApprovalAndSecondReadBeforeResolution() = runTest {
        val journal = CloudflareMutationJournal.memory()
        val intent = journal.begin(account, "agent", "CREATE")
        var gets = 0
        val client = CloudflareDeploymentClient({ "r" }, { _, method, _, _ ->
            assertEquals("GET", method); gets++
            response(JSONObject().put("id", workerId).put("name", "agent")
                .put("tags", JSONArray().put("maximus-${intent.id}")))
        }, journal = journal)
        val evidence = client.reconcile(account, "agent")
        assertEquals(workerId, evidence.observedId)
        assertTrue(runCatching { client.confirmReconciliation(evidence, false) }.isFailure)
        assertNotNull(journal.pending(account, "agent"))
        client.confirmReconciliation(evidence, true)
        assertEquals(2, gets)
        assertNull(journal.pending(account, "agent"))
    }
    @Test fun absenceOrChangedAccountNeverClearsUncertainty() = runTest {
        val journal = CloudflareMutationJournal.memory()
        journal.begin(account, "agent", "CREATE")
        val client = CloudflareDeploymentClient({ "r" }, { _, method, _, _ ->
            assertEquals("GET", method)
            response(JSONObject().put("id", workerId).put("name", "agent").put("tags", JSONArray()))
        }, journal = journal)
        val unknown = client.reconcile(account, "agent")
        assertNull(unknown.observedId)
        assertTrue(runCatching { client.confirmReconciliation(unknown, true) }.isFailure)
        assertNotNull(journal.pending(account, "agent"))
    }
    @Test fun invalidJournalCannotMasqueradeAsNoPendingWrite() {
        val journal = CloudflareMutationJournal({ "{\"schema\":1,\"pending\":{\"bad\":null}}" }, {})
        assertTrue(runCatching { journal.pending(account, "agent") }.isFailure)
        assertTrue(runCatching { journal.begin(account, "agent", "CREATE") }.isFailure)
    }
    @Test fun provenUndispatchedRequestReleasesBarrierWithoutRetry() = runTest {
        val journal = CloudflareMutationJournal.memory()
        val client = CloudflareDeploymentClient({ "r" }, { _, _, _, _ ->
            throw DeveloperRequestNotDispatchedException()
        }, journal = journal)
        assertTrue(runCatching { client.create(account, "agent", CloudAgentPurpose.ASSISTANT, "x".repeat(64), true) }.isFailure)
        assertNull(journal.pending(account, "agent"))
    }
    @Test fun accountSwitchBetweenApprovalGuardAndSecondReadKeepsBarrier() = runTest {
        val journal = CloudflareMutationJournal.memory()
        val intent = journal.begin(account, "Agent_test", "CREATE")
        var switching = false
        var reads = 0
        val client = CloudflareDeploymentClient({
            if (switching && ++reads >= 2) "new" else "old"
        }, { _, method, _, _ ->
            assertEquals("GET", method)
            response(JSONObject().put("id", workerId).put("name", "Agent_test")
                .put("tags", JSONArray().put("maximus-${intent.id}")))
        }, journal = journal)
        val evidence = client.reconcile(account, "Agent_test")
        switching = true
        assertTrue(runCatching { client.confirmReconciliation(evidence, true) }.isFailure)
        assertNotNull(journal.pending(account, "Agent_test"))
    }
}
