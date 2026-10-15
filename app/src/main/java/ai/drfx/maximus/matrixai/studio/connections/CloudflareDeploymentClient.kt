package ai.drfx.maximus.matrixai.studio.connections

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal data class CloudflareCreationReceipt(
    val accountId: String, val workerName: String, val workerId: String,
    val version: String? = null, val verified: Boolean = false
)

/** Transport is pinned to the same encrypted-vault revision for the entire reviewed operation. */
internal class CloudflareDeploymentClient(
    private val revision: () -> String,
    private val request: suspend (path: String, method: String, body: ByteArray?, expectedRevision: String) -> ByteArray,
    private val recordReceipt: (CloudflareDeploymentReceipt) -> Unit = {},
    private val recordCreation: (CloudflareCreationReceipt) -> Unit = {}
) {
    private suspend fun call(path: String, session: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        check(session.isNotBlank() && revision() == session) { "Connection changed. Inspect again." }
        val response = JSONObject(request(path, method, body?.toString()?.toByteArray(Charsets.UTF_8), session)
            .toString(Charsets.UTF_8))
        check(response.optBoolean("success")) { "Cloudflare did not confirm the operation." }
        return response.getJSONObject("result")
    }
    private fun script(account: String, name: String) = "/accounts/${DeveloperConnectionPolicy.cloudflareAccountId(account)}/workers/scripts/${DeveloperConnectionPolicy.workerName(name)}"
    private fun worker(account: String, name: String) = "/accounts/${DeveloperConnectionPolicy.cloudflareAccountId(account)}/workers/workers/${DeveloperConnectionPolicy.workerName(name)}"
    /** Create is POST-only; subsequent code upload targets the returned immutable Worker ID. */
    suspend fun create(account: String, name: String, purpose: CloudAgentPurpose,
        accessKey: String, approved: Boolean): CloudflareCreationReceipt {
        check(approved)
        DeveloperConnectionPolicy.cloudflareAccountId(account); DeveloperConnectionPolicy.workerName(name)
        require(accessKey.matches(Regex("[A-Za-z0-9_-]{32,128}")))
        val session = revision()
        val source = DeveloperSetupPolicy.workerSource(purpose)
        val metadata = JSONObject().put("name", name).put("subdomain", JSONObject()
            .put("enabled", false).put("previews_enabled", false))
        val created = call("/accounts/$account/workers/workers", session, "POST", metadata)
        val id = created.getString("id")
        require(id.matches(Regex("[a-fA-F0-9]{32}")))
        var receipt = CloudflareCreationReceipt(account, name, id)
        // Creating a resource can succeed even if its initial code upload does not.
        if (!recordCreated(receipt)) return receipt
        if (created.optString("name") != name) return receipt
        try {
            val body = JSONObject().put("main_module", "agent.js").put("compatibility_date", "2026-10-15")
                .put("bindings", org.json.JSONArray()
                    .put(JSONObject().put("type", "ai").put("name", "AI"))
                    .put(JSONObject().put("type", "secret_text").put("name", "MAXIMUS_AGENT_TOKEN").put("text", accessKey)))
                .put("modules", org.json.JSONArray().put(JSONObject().put("name", "agent.js")
                    .put("content_type", "application/javascript+module")
                    .put("content_base64", java.util.Base64.getEncoder().encodeToString(source.toByteArray(Charsets.UTF_8)))))
            val uploaded = call("/accounts/$account/workers/workers/$id/versions?deploy=true", session, "POST", body)
            receipt = receipt.copy(version = CloudflareDeploymentPolicy.uuid(uploaded.getString("id")))
            if (!recordCreated(receipt)) return receipt
            // This read confirms activation, not an invocation or a health check.
            val active = CloudflareDeploymentPolicy.active(call(script(account, name) + "/deployments", session))
            receipt = receipt.copy(verified = revision() == session && active.second == receipt.version)
            if (!recordCreated(receipt)) receipt = receipt.copy(verified = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep the known resource/version receipt; no retry or delete. */ }
        return receipt
    }
    private fun recordCreated(receipt: CloudflareCreationReceipt): Boolean = try { recordCreation(receipt); true }
        catch (_: Exception) { false }

    private suspend fun current(review: CloudflareDeploymentReview) = CloudflareDeploymentPolicy.active(
        call(script(review.accountId, review.workerName) + "/deployments", review.sessionRevision))

    suspend fun inspect(account: String, name: String, source: String): CloudflareDeploymentReview {
        val session = revision()
        val active = CloudflareDeploymentPolicy.active(call(script(account, name) + "/deployments", session))
        val version = call(worker(account, name) + "/versions/${active.second}", session)
        check(revision() == session)
        val review = CloudflareDeploymentPolicy.review(account, name, session, active.first, active.second, version, source)
        val after = current(review)
        CloudflareDeploymentPolicy.requireCurrent(review, revision(), after.first, after.second)
        return review
    }
    suspend fun stage(review: CloudflareDeploymentReview, approved: Boolean): CloudflareDeploymentReceipt {
        check(approved) { "Review and explicitly approve the upload first." }
        val active = current(review)
        CloudflareDeploymentPolicy.requireCurrent(review, revision(), active.first, active.second)
        val result = call(worker(review.accountId, review.workerName) + "/versions?deploy=false",
            review.sessionRevision, "POST", CloudflareDeploymentPolicy.upload(review))
        val staged = CloudflareDeploymentPolicy.uuid(result.getString("id"))
        check(staged != review.activeVersion)
        // Preserve a write receipt even if the account/remote deployment changes after upload.
        val receipt = CloudflareDeploymentReceipt(review, staged)
        val recorded = record(receipt)
        return receipt.copy(verified = recorded && verify { val after = current(review)
            CloudflareDeploymentPolicy.requireCurrent(review, revision(), after.first, after.second) })
    }
    suspend fun activate(receipt: CloudflareDeploymentReceipt, approved: Boolean, rollback: Boolean = false): CloudflareDeploymentReceipt {
        check(approved && receipt.verified && receipt.rolledBackDeployment == null)
        val review = receipt.review
        val active = current(review)
        val expectedDeployment = if (rollback) checkNotNull(receipt.activatedDeployment) else review.deploymentId
        val expectedVersion = if (rollback) receipt.stagedVersion else review.activeVersion
        check(rollback || receipt.activatedDeployment == null)
        check(revision() == review.sessionRevision && active == (expectedDeployment to expectedVersion)) {
            "Deployment changed. No traffic change was sent."
        }
        val target = if (rollback) review.activeVersion else receipt.stagedVersion
        val result = call(script(review.accountId, review.workerName) + "/deployments", review.sessionRevision,
            "POST", CloudflareDeploymentPolicy.activation(target))
        val id = CloudflareDeploymentPolicy.uuid(result.getString("id"))
        val updated = if (rollback) receipt.copy(rolledBackDeployment = id, verified = false)
            else receipt.copy(activatedDeployment = id, verified = false)
        val recorded = record(updated)
        return updated.copy(verified = recorded && verify {
            check(revision() == review.sessionRevision && current(review) == (id to target))
        })
    }
    private fun record(receipt: CloudflareDeploymentReceipt): Boolean = try { recordReceipt(receipt); true }
        catch (_: Exception) { false }
    private suspend fun verify(block: suspend () -> Unit): Boolean = try { block(); true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
}
