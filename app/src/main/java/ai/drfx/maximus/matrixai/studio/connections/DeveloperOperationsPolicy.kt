package ai.drfx.maximus.matrixai.studio.connections

import org.json.JSONObject
import java.time.Instant

/** Read-only projections. A successful latest workflow is not proof that all required checks passed. */
internal enum class DeveloperEvidenceFreshness { OBSERVED, STALE, UNKNOWN, ERROR }

internal data class GithubWorkflowEvidence(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val updatedAtEpochMs: Long?
)

internal data class GithubRepositoryEvidence(
    val repository: String,
    val branch: String,
    val defaultBranch: String,
    val commitSha: String,
    val protected: Boolean?,
    val archived: Boolean?,
    val observedAtEpochMs: Long,
    val latestWorkflow: GithubWorkflowEvidence?,
    val workflowAvailable: Boolean
)

internal object DeveloperOperationsPolicy {
    const val FRESH_FOR_MS = 5 * 60_000L

    fun freshness(observedAt: Long, now: Long): DeveloperEvidenceFreshness {
        return when {
            now < 0L || observedAt < 0L -> DeveloperEvidenceFreshness.ERROR
            observedAt <= 0L -> DeveloperEvidenceFreshness.UNKNOWN
            observedAt > now -> DeveloperEvidenceFreshness.ERROR
            now - observedAt > FRESH_FOR_MS -> DeveloperEvidenceFreshness.STALE
            else -> DeveloperEvidenceFreshness.OBSERVED
        }
    }

    fun branch(owner: String, repo: String, requestedBranch: String, metadata: JSONObject,
        branch: JSONObject, observedAt: Long, workflow: GithubWorkflowEvidence?,
        workflowAvailable: Boolean): GithubRepositoryEvidence {
        val repository = "${DeveloperConnectionPolicy.repoPart(owner)}/${DeveloperConnectionPolicy.repoPart(repo)}"
        require(metadata.getString("full_name").equals(repository, ignoreCase = true))
        require(branch.getString("name") == DeveloperConnectionPolicy.branch(requestedBranch))
        val sha = branch.getJSONObject("commit").getString("sha")
        require(sha.matches(Regex("[a-f0-9]{40}")) && observedAt > 0L)
        return GithubRepositoryEvidence(repository, requestedBranch,
            DeveloperConnectionPolicy.branch(metadata.getString("default_branch")), sha,
            branch.opt("protected") as? Boolean, metadata.opt("archived") as? Boolean,
            observedAt, workflow, workflowAvailable)
    }

    fun workflow(response: JSONObject, expectedSha: String): GithubWorkflowEvidence? {
        require(expectedSha.matches(Regex("[a-f0-9]{40}")))
        val runs = response.getJSONArray("workflow_runs")
        if (runs.length() == 0) return null
        val run = runs.getJSONObject(0)
        // A repository's latest run on some other commit cannot prove this branch's state.
        require(run.getString("head_sha") == expectedSha)
        val id = run.getLong("id")
        require(id > 0)
        val knownStates = setOf("queued", "in_progress", "completed", "waiting", "requested", "pending")
        val status = run.optString("status").takeIf { it in knownStates } ?: "unknown"
        val knownConclusions = setOf("success", "failure", "cancelled", "skipped", "neutral",
            "timed_out", "action_required", "startup_failure", "stale")
        val conclusion = if (status == "completed")
            run.optString("conclusion").takeIf { it in knownConclusions } ?: "unknown"
        else null
        val updated = run.optString("updated_at").takeIf { it.isNotBlank() }?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        }
        return GithubWorkflowEvidence(id, status, conclusion, updated)
    }
}
