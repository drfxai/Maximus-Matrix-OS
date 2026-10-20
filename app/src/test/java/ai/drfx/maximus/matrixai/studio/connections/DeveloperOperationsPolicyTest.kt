package ai.drfx.maximus.matrixai.studio.connections

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeveloperOperationsPolicyTest {
    private val sha = "a".repeat(40)
    private fun workflow(status: String = "completed", conclusion: Any = "success", head: String = sha): JSONObject =
        JSONObject().put("workflow_runs", JSONArray().put(JSONObject()
            .put("id", 42L).put("head_sha", head).put("status", status)
            .put("conclusion", conclusion).put("updated_at", "2026-10-20T12:00:00Z")))

    @Test fun unknownStaleAndClockAnomaliesNeverBecomeObserved() {
        assertEquals(DeveloperEvidenceFreshness.UNKNOWN, DeveloperOperationsPolicy.freshness(0, 400_000))
        assertEquals(DeveloperEvidenceFreshness.OBSERVED, DeveloperOperationsPolicy.freshness(10, 10))
        assertEquals(DeveloperEvidenceFreshness.STALE, DeveloperOperationsPolicy.freshness(10, 400_000))
        assertEquals(DeveloperEvidenceFreshness.ERROR, DeveloperOperationsPolicy.freshness(20, 10))
        assertEquals(DeveloperEvidenceFreshness.ERROR, DeveloperOperationsPolicy.freshness(10, -1))
    }

    @Test fun noWorkflowIsUnknownInsteadOfSuccess() {
        assertNull(DeveloperOperationsPolicy.workflow(JSONObject().put("workflow_runs", JSONArray()), sha))
    }

    @Test fun workflowFromAnotherCommitCannotProveTheSelectedHead() {
        assertThrows(IllegalArgumentException::class.java) {
            DeveloperOperationsPolicy.workflow(workflow(head = "b".repeat(40)), sha)
        }
    }

    @Test fun runningWorkflowCannotAdvertiseACompletedConclusion() {
        val evidence = DeveloperOperationsPolicy.workflow(workflow("in_progress", "success"), sha)!!
        assertEquals("in_progress", evidence.status)
        assertNull(evidence.conclusion)
    }

    @Test fun failureRemainsFailureAndUpstreamTimeRemainsSeparate() {
        val evidence = DeveloperOperationsPolicy.workflow(workflow(conclusion = "failure"), sha)!!
        assertEquals("failure", evidence.conclusion)
        assertEquals(42L, evidence.id)
        assertEquals(1792497600000L, evidence.updatedAtEpochMs)
    }

    @Test fun unknownStatusAndConclusionDoNotBecomeSuccess() {
        val evidence = DeveloperOperationsPolicy.workflow(workflow("mystery", "success"), sha)!!
        assertEquals("unknown", evidence.status)
        assertNull(evidence.conclusion)
        assertEquals("unknown", DeveloperOperationsPolicy.workflow(workflow(conclusion = "mystery"), sha)!!.conclusion)
    }

    @Test fun unavailableTimestampIsNotInvented() {
        val response = workflow()
        response.getJSONArray("workflow_runs").getJSONObject(0).put("updated_at", "bad date")
        assertNull(DeveloperOperationsPolicy.workflow(response, sha)!!.updatedAtEpochMs)
    }

    @Test fun branchProjectionKeepsMissingProtectionUnknown() {
        val metadata = JSONObject().put("full_name", "owner/repo").put("default_branch", "main")
        val branch = JSONObject().put("name", "feature/inspect")
            .put("commit", JSONObject().put("sha", sha))
        val evidence = DeveloperOperationsPolicy.branch("owner", "repo", "feature/inspect",
            metadata, branch, 123L, null, false)
        assertNull(evidence.protected)
        assertNull(evidence.archived)
        assertFalse(evidence.workflowAvailable)
        assertEquals(123L, evidence.observedAtEpochMs)
        assertEquals(sha, evidence.commitSha)
    }

    @Test fun branchProjectionRejectsWrongRepositoryOrRef() {
        val metadata = JSONObject().put("full_name", "other/repo").put("default_branch", "main")
        val branch = JSONObject().put("name", "feature/inspect")
            .put("commit", JSONObject().put("sha", sha))
        assertThrows(IllegalArgumentException::class.java) {
            DeveloperOperationsPolicy.branch("owner", "repo", "feature/inspect",
                metadata, branch, 123L, null, false)
        }
        metadata.put("full_name", "owner/repo")
        assertThrows(IllegalArgumentException::class.java) {
            DeveloperOperationsPolicy.branch("owner", "repo", "another",
                metadata, branch, 123L, null, false)
        }
    }
}
