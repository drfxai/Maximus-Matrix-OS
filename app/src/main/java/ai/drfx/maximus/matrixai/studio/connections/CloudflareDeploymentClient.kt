package ai.drfx.maximus.matrixai.studio.connections

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal data class CloudflareCreationReceipt(
    val accountId: String, val workerName: String, val workerId: String,
    val version: String? = null, val verified: Boolean = false
)

internal data class CloudflareReconciliation(
    val intent: CloudflarePendingMutation,
    val observedId: String?,
    val sessionRevision: String,
    val observedAt: Long
)

/** Transport is pinned to the same encrypted-vault revision for the entire reviewed operation. */
internal class CloudflareDeploymentClient(
    private val revision: () -> String,
    private val request: suspend (path: String, method: String, body: ByteArray?, expectedRevision: String) -> ByteArray,
    private val recordReceipt: (CloudflareDeploymentReceipt) -> Unit = {},
    private val recordCreation: (CloudflareCreationReceipt) -> Unit = {},
    private val journal: CloudflareMutationJournal = CloudflareMutationJournal.memory()
) {
    private suspend fun envelope(path: String, session: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        if (session.isBlank() || revision() != session) throw DeveloperRequestNotDispatchedException()
        val response = JSONObject(request(path, method, body?.toString()?.toByteArray(Charsets.UTF_8), session)
            .toString(Charsets.UTF_8))
        check(response.optBoolean("success")) { "Cloudflare did not confirm the operation." }
        return response
    }
    private suspend fun call(path: String, session: String, method: String = "GET", body: JSONObject? = null): JSONObject =
        envelope(path, session, method, body).getJSONObject("result")
    fun pending(account: String, name: String): CloudflarePendingMutation? = journal.pending(account, name)

    private suspend fun mutate(account: String, name: String, operation: String, session: String,
        path: String, body: JSONObject): Pair<JSONObject, CloudflarePendingMutation> {
        check(session.isNotBlank() && revision() == session) { "Connection changed. Inspect again." }
        val target = if (operation in setOf("ACTIVATE", "ROLLBACK"))
            body.getJSONArray("versions").getJSONObject(0).getString("version_id") else null
        val intent = journal.begin(account, name, operation, target)
        val marked = JSONObject(body.toString())
        val marker = marker(intent)
        if (operation == "CREATE") {
            val tags = marked.optJSONArray("tags") ?: org.json.JSONArray()
            marked.put("tags", tags.put(marker))
        } else {
            val annotations = marked.optJSONObject("annotations") ?: JSONObject()
            annotations.put("workers/message", marker)
            if (operation in setOf("STAGE", "UPLOAD")) annotations.put("workers/tag", marker)
            marked.put("annotations", annotations)
        }
        try { return call(path, session, "POST", marked) to intent }
        catch (notSent: DeveloperRequestNotDispatchedException) {
            // Transport proves that neither outputStream nor responseCode was attempted.
            finish(intent)
            throw notSent
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw CloudflareMutationUncertainException() }
    }
    private fun marker(intent: CloudflarePendingMutation) = "maximus-${intent.id}"
    private fun finish(intent: CloudflarePendingMutation): Boolean =
        try { journal.confirmed(intent); true } catch (_: Exception) { false }

    /** Bounded provider GETs only. Absence is not proof that the write failed. */
    suspend fun reconcile(account: String, name: String): CloudflareReconciliation {
        val intent = journal.pending(account, name) ?: error("No pending write for this Worker.")
        val session = revision()
        val marker = marker(intent)
        val matches = mutableListOf<String>()
        when (intent.operation) {
            "CREATE" -> {
                val resource = call(worker(account, name), session)
                val tags = resource.optJSONArray("tags") ?: org.json.JSONArray()
                check(tags.length() <= 256)
                if (resource.getString("name") == name && (0 until tags.length()).any { tags.getString(it) == marker }) {
                    val id = resource.getString("id")
                    check(id.matches(Regex("[a-fA-F0-9]{32}")))
                    matches += id
                }
            }
            "UPLOAD", "STAGE" -> {
                for (page in 1..3) {
                    val versions = envelope(worker(account, name) + "/versions?per_page=100&page=$page", session)
                        .getJSONArray("result")
                    check(versions.length() <= 100)
                    for (index in 0 until versions.length()) {
                        val version = versions.getJSONObject(index)
                        if (version.optJSONObject("annotations")?.optString("workers/tag") == marker)
                            matches += CloudflareDeploymentPolicy.uuid(version.getString("id"))
                    }
                    if (matches.isNotEmpty() || versions.length() < 100) break
                }
            }
            "ACTIVATE", "ROLLBACK" -> {
                val deployments = call(script(account, name) + "/deployments", session).getJSONArray("deployments")
                check(deployments.length() <= 100)
                for (index in 0 until deployments.length()) {
                    val deployment = deployments.getJSONObject(index)
                    if (deployment.optJSONObject("annotations")?.optString("workers/message") == marker) {
                        val observed = CloudflareDeploymentPolicy.active(JSONObject().put("deployments",
                            org.json.JSONArray().put(deployment)))
                        if (observed.second == intent.targetVersion) matches += observed.first
                    }
                }
            }
        }
        check(matches.size <= 1) { "Multiple matching provider writes require independent review." }
        check(revision() == session && journal.pending(account, name) == intent) { "Connection or pending write changed." }
        return CloudflareReconciliation(intent, matches.singleOrNull(), session, System.currentTimeMillis())
    }

    /** Approval resolves local uncertainty only; it never stages, activates, rolls back, or retries. */
    suspend fun confirmReconciliation(evidence: CloudflareReconciliation, approved: Boolean): CloudflareReconciliation {
        check(approved && evidence.observedId != null && revision() == evidence.sessionRevision &&
            System.currentTimeMillis() - evidence.observedAt in 0L..300_000L) { "Inspect and review current provider evidence first." }
        val current = reconcile(evidence.intent.account, evidence.intent.worker)
        check(current.intent == evidence.intent && current.observedId == evidence.observedId &&
            current.sessionRevision == evidence.sessionRevision && revision() == evidence.sessionRevision) { "Provider evidence changed." }
        journal.confirmed(current.intent, current.observedId)
        return current
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
        val (created, creationIntent) = mutate(account, name, "CREATE", session,
            "/accounts/$account/workers/workers", metadata)
        val id = created.getString("id")
        require(id.matches(Regex("[a-fA-F0-9]{32}")))
        var receipt = CloudflareCreationReceipt(account, name, id)
        // Creating a resource can succeed even if its initial code upload does not.
        if (!recordCreated(receipt) || !finish(creationIntent)) return receipt
        if (created.optString("name") != name) return receipt
        try {
            val body = JSONObject().put("main_module", "agent.js").put("compatibility_date", "2026-10-15")
                .put("bindings", org.json.JSONArray()
                    .put(JSONObject().put("type", "ai").put("name", "AI"))
                    .put(JSONObject().put("type", "secret_text").put("name", "MAXIMUS_AGENT_TOKEN").put("text", accessKey)))
                .put("modules", org.json.JSONArray().put(JSONObject().put("name", "agent.js")
                    .put("content_type", "application/javascript+module")
                    .put("content_base64", java.util.Base64.getEncoder().encodeToString(source.toByteArray(Charsets.UTF_8)))))
            val (uploaded, uploadIntent) = mutate(account, name, "UPLOAD", session,
                "/accounts/$account/workers/workers/$id/versions?deploy=true", body)
            receipt = receipt.copy(version = CloudflareDeploymentPolicy.uuid(uploaded.getString("id")))
            if (!recordCreated(receipt) || !finish(uploadIntent)) return receipt
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
        val (result, intent) = mutate(review.accountId, review.workerName, "STAGE", review.sessionRevision,
            worker(review.accountId, review.workerName) + "/versions?deploy=false", CloudflareDeploymentPolicy.upload(review))
        val staged = CloudflareDeploymentPolicy.uuid(result.getString("id"))
        check(staged != review.activeVersion)
        // Preserve a write receipt even if the account/remote deployment changes after upload.
        val receipt = CloudflareDeploymentReceipt(review, staged)
        val recorded = record(receipt)
        val resolved = recorded && finish(intent)
        return receipt.copy(verified = resolved && verify { val after = current(review)
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
        val (result, intent) = mutate(review.accountId, review.workerName,
            if (rollback) "ROLLBACK" else "ACTIVATE", review.sessionRevision,
            script(review.accountId, review.workerName) + "/deployments", CloudflareDeploymentPolicy.activation(target))
        val id = CloudflareDeploymentPolicy.uuid(result.getString("id"))
        val updated = if (rollback) receipt.copy(rolledBackDeployment = id, verified = false)
            else receipt.copy(activatedDeployment = id, verified = false)
        val recorded = record(updated)
        val resolved = recorded && finish(intent)
        return updated.copy(verified = resolved && verify {
            check(revision() == review.sessionRevision && current(review) == (id to target))
        })
    }
    private fun record(receipt: CloudflareDeploymentReceipt): Boolean = try { recordReceipt(receipt); true }
        catch (_: Exception) { false }
    private suspend fun verify(block: suspend () -> Unit): Boolean = try { block(); true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
}
