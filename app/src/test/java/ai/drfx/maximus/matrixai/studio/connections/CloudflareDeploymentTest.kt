package ai.drfx.maximus.matrixai.studio.connections

import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudflareDeploymentTest {
    private val account = "a".repeat(32)
    private val old = "00000000-0000-0000-0000-000000000001"
    private val staged = "00000000-0000-0000-0000-000000000002"
    private val initial = "10000000-0000-0000-0000-000000000001"
    private val activated = "10000000-0000-0000-0000-000000000002"
    private val rollback = "10000000-0000-0000-0000-000000000003"
    private fun version() = JSONObject().put("id", old).put("main_module", "index.js")
        .put("compatibility_date", "2026-09-01").put("compatibility_flags", JSONArray().put("nodejs_compat"))
        .put("limits", JSONObject().put("cpu_ms", 20)).put("bindings", JSONArray()
            .put(JSONObject().put("name", "AI").put("type", "ai"))
            .put(JSONObject().put("name", "MAXIMUS_AGENT_TOKEN").put("type", "secret_text"))
            .put(JSONObject().put("name", "KEEP_ME").put("type", "plain_text").put("text", "private-value")))
    private fun review() = CloudflareDeploymentPolicy.review(account, "agent", "session", initial, old,
        version(), "export default {fetch(){return new Response('ok')}}")
    private fun deployments(id: String = initial, v: String = old) = JSONObject().put("deployments", JSONArray()
        .put(JSONObject().put("id", id).put("strategy", "percentage")
            .put("versions", JSONArray().put(JSONObject().put("version_id", v).put("percentage", 100)))))
    private fun envelope(result: JSONObject) = JSONObject().put("success", true).put("result", result)
        .toString().toByteArray()
    private fun rejected(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun uploadPinsAllBindingsToActiveVersionWithoutCopyingValues() {
        val body = CloudflareDeploymentPolicy.upload(review())
        assertEquals(3, body.getJSONArray("bindings").length())
        for (i in 0..2) {
            val binding = body.getJSONArray("bindings").getJSONObject(i)
            assertEquals("inherit", binding.getString("type"))
            assertEquals(old, binding.getString("version_id"))
            assertFalse(binding.has("text"))
        }
        assertFalse(body.toString().contains("private-value"))
        assertEquals("nodejs_compat", body.getJSONArray("compatibility_flags").getString(0))
        assertEquals(20, body.getJSONObject("limits").getInt("cpu_ms"))
        assertEquals("index.js", body.getJSONArray("modules").getJSONObject(0).getString("name"))
    }
    @Test fun defaultExportCacheIsPreservedWithoutLifecycleState() {
        val v = version().put("exports", JSONObject().put("default", JSONObject().put("type", "worker")
            .put("state", "created").put("cache", JSONObject().put("enabled", true))))
        val r = CloudflareDeploymentPolicy.review(account, "agent", "session", initial, old, v, "code")
        val export = CloudflareDeploymentPolicy.upload(r).getJSONObject("exports").getJSONObject("default")
        assertTrue(export.getJSONObject("cache").getBoolean("enabled"))
        assertFalse(export.has("state"))
    }
    @Test fun unsupportedLifecycleAndStatefulSettingsFailClosed() {
        listOf("assets", "containers", "migrations", "migration_tag", "future_option").forEach { key ->
            rejected { CloudflareDeploymentPolicy.review(account, "agent", "session", initial, old,
                version().put(key, JSONObject()), "code") }
        }
        rejected { CloudflareDeploymentPolicy.review(account, "agent", "session", initial, old,
            version().put("exports", JSONObject().put("Counter", JSONObject().put("type", "durable-object"))), "code") }
    }
    @Test fun unknownOrDurableBindingsFailClosed() {
        for (type in listOf("durable_object_namespace", "queue", "future_binding")) {
            val v = version(); v.getJSONArray("bindings").put(JSONObject().put("name", "OTHER").put("type", type))
            rejected { CloudflareDeploymentPolicy.review(account, "agent", "session", initial, old, v, "code") }
        }
    }
    @Test fun requiredBindingTypesCannotBeSpoofed() {
        val v = version(); v.getJSONArray("bindings").getJSONObject(1).put("type", "plain_text")
        rejected { CloudflareDeploymentPolicy.review(account, "agent", "session", initial, old, v, "code") }
    }
    @Test fun trafficSplitAndEmptyHistoryCannotBeReplaced() {
        val result = deployments(); result.getJSONArray("deployments").getJSONObject(0)
            .getJSONArray("versions").getJSONObject(0).put("percentage", 50)
        rejected { CloudflareDeploymentPolicy.active(result) }
        rejected { CloudflareDeploymentPolicy.active(JSONObject().put("deployments", JSONArray())) }
    }
    @Test fun reviewedSourceCannotBeChangedAfterApproval() {
        rejected { CloudflareDeploymentPolicy.upload(review().copy(source = "different")) }
    }
    @Test fun accountRevisionAndRemoteDeploymentBothInvalidateReview() {
        rejected { CloudflareDeploymentPolicy.requireCurrent(review(), "new-session", initial, old) }
        rejected { CloudflareDeploymentPolicy.requireCurrent(review(), "session", activated, old) }
        rejected { CloudflareDeploymentPolicy.requireCurrent(review(), "session", initial, staged) }
    }
    @Test fun stageUploadsWithoutTrafficAndSeparatelyApprovedActivationCanRollback() = runTest {
        var currentDeployment = initial; var currentVersion = old
        val posts = mutableListOf<Pair<String, JSONObject>>()
        val receipts = mutableListOf<CloudflareDeploymentReceipt>()
        val client = CloudflareDeploymentClient({ "session" }, { path, method, bytes, _ ->
            if (method == "POST") {
                val body = JSONObject(checkNotNull(bytes).toString(Charsets.UTF_8)); posts += path to body
                if (path.endsWith("/versions?deploy=false")) envelope(JSONObject().put("id", staged))
                else {
                    assertFalse(path.contains("force"))
                    currentVersion = body.getJSONArray("versions").getJSONObject(0).getString("version_id")
                    currentDeployment = if (currentVersion == staged) activated else rollback
                    envelope(JSONObject().put("id", currentDeployment))
                }
            } else if (path.endsWith("/deployments")) envelope(deployments(currentDeployment, currentVersion))
            else envelope(version())
        }, { receipts += it })
        val preview = client.inspect(account, "agent", "code")
        assertTrue(posts.isEmpty())
        val uploaded = client.stage(preview, true)
        assertTrue(uploaded.verified); assertNull(uploaded.activatedDeployment)
        assertEquals(old, currentVersion); assertEquals(1, posts.size)
        val live = client.activate(uploaded, true)
        assertTrue(live.verified); assertEquals(staged, currentVersion)
        val restored = client.activate(live, true, rollback = true)
        assertTrue(restored.verified); assertEquals(old, currentVersion)
        assertEquals(rollback, restored.rolledBackDeployment); assertEquals(3, receipts.size)
    }
    @Test fun missingApprovalAndStaleAccountNeverDispatch() = runTest {
        var calls = 0
        val client = CloudflareDeploymentClient({ "different-session" }, { _, _, _, _ -> calls++; error("dispatch") })
        assertTrue(runCatching { client.stage(review(), false) }.isFailure)
        assertTrue(runCatching { client.stage(review(), true) }.isFailure)
        assertEquals(0, calls)
    }
    @Test fun deploymentDriftPreventsWrite() = runTest {
        var writes = 0
        val client = CloudflareDeploymentClient({ "session" }, { _, method, _, _ ->
            if (method == "POST") writes++
            envelope(deployments(activated, staged))
        })
        assertTrue(runCatching { client.stage(review(), true) }.isFailure)
        assertEquals(0, writes)
    }
    @Test fun postUploadAccountDriftKeepsReceiptAndDisablesActivation() = runTest {
        var session = "session"; var recorded: CloudflareDeploymentReceipt? = null
        val client = CloudflareDeploymentClient({ session }, { path, _, _, _ ->
            if (path.endsWith("deploy=false")) { session = "changed"; envelope(JSONObject().put("id", staged)) }
            else envelope(deployments())
        }, { recorded = it })
        val result = client.stage(review(), true)
        assertEquals(staged, recorded?.stagedVersion)
        assertFalse(result.verified)
        assertTrue(runCatching { client.activate(result, true) }.isFailure)
    }
    @Test fun changedDeploymentCannotBeRolledBackOverAnotherUsersChanges() = runTest {
        var writes = 0
        val client = CloudflareDeploymentClient({ "session" }, { _, method, _, _ ->
            if (method == "POST") writes++
            envelope(deployments(rollback, staged))
        })
        val receipt = CloudflareDeploymentReceipt(review(), staged, activated, verified = true)
        assertTrue(runCatching { client.activate(receipt, true, rollback = true) }.isFailure)
        assertEquals(0, writes)
    }
    @Test fun newWorkerCreationUsesPostAndUploadsOnlyToReturnedImmutableId() = runTest {
        val id = "b".repeat(32)
        val calls = mutableListOf<Pair<String, String>>()
        val records = mutableListOf<CloudflareCreationReceipt>()
        val client = CloudflareDeploymentClient({ "session" }, { path, method, bytes, _ ->
            calls += method to path
            when {
                path.endsWith("/workers/workers") -> {
                    val body = JSONObject(checkNotNull(bytes).toString(Charsets.UTF_8))
                    assertEquals("agent", body.getString("name"))
                    assertFalse(body.getJSONObject("subdomain").getBoolean("enabled"))
                    assertFalse(body.getJSONObject("subdomain").getBoolean("previews_enabled"))
                    envelope(JSONObject().put("id", id).put("name", "agent"))
                }
                path.endsWith("/versions?deploy=true") -> {
                    assertTrue(path.contains("/workers/workers/$id/versions"))
                    envelope(JSONObject().put("id", staged))
                }
                else -> envelope(deployments(activated, staged))
            }
        }, recordCreation = { records += it })
        val result = client.create(account, "agent", CloudAgentPurpose.ASSISTANT, "x".repeat(64), true)
        assertTrue(result.verified)
        assertEquals(id, result.workerId)
        assertEquals(staged, result.version)
        assertEquals(id, records.first().workerId)
        assertNull(records.first().version)
        assertFalse(calls.any { it.first == "PUT" })
    }
    @Test fun creationConflictNeverFallsBackToOverwrite() = runTest {
        val calls = mutableListOf<String>()
        val client = CloudflareDeploymentClient({ "session" }, { path, _, _, _ ->
            calls += path; error("HTTP 409")
        })
        assertTrue(runCatching { client.create(account, "agent", CloudAgentPurpose.ASSISTANT,
            "x".repeat(64), true) }.isFailure)
        assertEquals(listOf("/accounts/$account/workers/workers"), calls)
    }
    @Test fun partialCreationPersistsResourceIdAndDoesNotRetryUpload() = runTest {
        var calls = 0; val records = mutableListOf<CloudflareCreationReceipt>()
        val client = CloudflareDeploymentClient({ "session" }, { _, _, _, _ ->
            calls++
            if (calls == 1) envelope(JSONObject().put("id", "b".repeat(32)).put("name", "agent"))
            else error("upload unavailable")
        }, recordCreation = { records += it })
        val result = client.create(account, "agent", CloudAgentPurpose.PROJECT, "x".repeat(64), true)
        assertFalse(result.verified); assertEquals("b".repeat(32), result.workerId)
        assertEquals(2, calls); assertEquals(1, records.size)
    }
    @Test fun accountChangeAfterCreatePreventsInitialCodeUpload() = runTest {
        var session = "session"; var calls = 0
        val client = CloudflareDeploymentClient({ session }, { _, _, _, _ ->
            calls++; session = "changed"
            envelope(JSONObject().put("id", "b".repeat(32)).put("name", "agent"))
        })
        val result = client.create(account, "agent", CloudAgentPurpose.RESEARCH, "x".repeat(64), true)
        assertFalse(result.verified); assertEquals(1, calls)
    }

}
