package ai.drfx.maximus.matrixai.studio.connections

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * The only supported origins are first-party GitHub and Cloudflare HTTPS APIs.
 * Tokens never reach LLM prompts, URLs, logs, redirects or unrelated services.
 */
internal object DeveloperConnectionPolicy {
    private val segment = Regex("[a-zA-Z0-9_.-]{1,110}")
    private val branch = Regex("[a-zA-Z0-9_./-]{1,180}")
    private val account = Regex("[a-fA-F0-9]{32}")
    private val worker = Regex("[A-Za-z0-9_-]{1,63}")

    fun repoPart(value: String): String {
        require(segment.matches(value) && value !in setOf(".", ".."))
        return value
    }

    fun filePath(value: String): String {
        val split = value.split('/')
        require(split.size in 1..24 && value.length <= 450)
        split.forEach { repoPart(it) }
        require(!value.startsWith(".github/workflows/")) {
            "Workflow modifications require separate repository-specific authorization."
        }
        return split.joinToString("/")
    }

    fun branch(value: String): String {
        require(branch.matches(value) && !value.contains("..") &&
            !value.contains("//") && !value.startsWith("/") && !value.endsWith("/") &&
            !value.contains("@{") && !value.endsWith(".lock"))
        return value
    }

    fun writableBranch(value: String): String {
        val name = branch(value)
        require(name !in setOf("main", "master", "production", "release")) {
            "Create/select a separate feature branch to write code."
        }
        return name
    }

    fun cloudflareAccountId(value: String): String {
        require(account.matches(value)) { "Cloudflare account ID must be 32 hexadecimal digits." }
        return value
    }

    fun workerName(value: String): String {
        require(worker.matches(value)) { "Invalid Cloudflare Worker name." }
        return value
    }
}

internal data class GithubSourceFile(
    val owner: String, val repo: String, val path: String,
    val branch: String, val sha: String, val text: String, val credentialRevision: String = ""
)

internal data class CloudflareAccount(val id: String, val name: String)

/** Explicit user-owned API operations. No autonomous deployment or privileged remote shell. */
internal class DeveloperServiceClient(
    private val transport: DeveloperApiTransport,
    private val credentialRevision: (DevelopmentService) -> String,
    private val recordReceipt: (CloudflareDeploymentReceipt) -> Unit = {},
    private val recordCreation: (CloudflareCreationReceipt) -> Unit = {},
    private val cloudflareJournal: CloudflareMutationJournal = CloudflareMutationJournal.memory()
) {
    constructor(vault: DeveloperCredentialVault) : this(
        DeveloperApiTransport(vault::snapshot, vault::revision, { service ->
            vault.plugins.installed(if (service == DevelopmentService.GITHUB) "github" else "cloudflare")
        }), vault::revision, vault::recordCloudflareReceipt, vault::recordCloudflareCreation,
        vault.cloudflareMutationJournal())

    private suspend fun call(service: DevelopmentService, path: String, method: String = "GET",
        body: ByteArray? = null, expectedCloudflareRevision: String? = null,
        expectedGithubRevision: String? = null): ByteArray {
        require(expectedCloudflareRevision == null || service == DevelopmentService.CLOUDFLARE)
        require(expectedGithubRevision == null || service == DevelopmentService.GITHUB)
        return transport.call(service, path, method, body,
            expectedCloudflareRevision ?: expectedGithubRevision)
    }

    /** Explicit read-only inspection; no token, approval, or write authority enters the projection. */
    suspend fun inspectGithubRepository(owner: String, repository: String,
        branch: String): GithubRepositoryEvidence {
        val o = DeveloperConnectionPolicy.repoPart(owner)
        val r = DeveloperConnectionPolicy.repoPart(repository)
        val b = DeveloperConnectionPolicy.branch(branch)
        val session = credentialRevision(DevelopmentService.GITHUB)
        suspend fun read(path: String): JSONObject = JSONObject(call(DevelopmentService.GITHUB,
            path, expectedGithubRevision = session).toString(StandardCharsets.UTF_8))
        val metadata = read("/repos/$o/$r")
        val branchPath = "/repos/$o/$r/branches/" + URLEncoder.encode(b, "UTF-8")
        val info = read(branchPath)
        val sha = info.getJSONObject("commit").getString("sha")
        require(sha.matches(Regex("[a-f0-9]{40}")))
        var workflow: GithubWorkflowEvidence? = null
        var workflowAvailable = true
        try {
            workflow = DeveloperOperationsPolicy.workflow(
                read("/repos/$o/$r/actions/runs?head_sha=$sha&per_page=1"), sha)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { workflowAvailable = false }
        // Do not combine a workflow with a branch head that moved during this inspection.
        check(read(branchPath).getJSONObject("commit").getString("sha") == sha &&
            credentialRevision(DevelopmentService.GITHUB) == session) {
            "Branch or connection changed during inspection. Refresh."
        }
        return DeveloperOperationsPolicy.branch(o, r, b, metadata, info,
            System.currentTimeMillis(), workflow, workflowAvailable)
    }

    suspend fun verifyGithub(): String {
        val session = credentialRevision(DevelopmentService.GITHUB)
        val user = JSONObject(call(DevelopmentService.GITHUB, "/user", expectedGithubRevision = session)
            .toString(StandardCharsets.UTF_8))
        check(credentialRevision(DevelopmentService.GITHUB) == session) { "Connection changed." }
        return user.optString("login").take(80).ifBlank { "Authenticated account" }
    }

    suspend fun verifyCloudflare(): String {
        val session = credentialRevision(DevelopmentService.CLOUDFLARE)
        val status = JSONObject(call(DevelopmentService.CLOUDFLARE, "/user/tokens/verify", expectedCloudflareRevision = session)
            .toString(StandardCharsets.UTF_8))
        check(status.optBoolean("success")) { "Cloudflare token validation failed." }
        check(status.optJSONObject("result")?.optString("status") == "active") {
            "Cloudflare token is not active."
        }
        check(credentialRevision(DevelopmentService.CLOUDFLARE) == session) { "Connection changed." }
        return "active"
    }

    suspend fun readGithubFile(
        owner: String, repository: String, branch: String, file: String
    ): GithubSourceFile {
        val session = credentialRevision(DevelopmentService.GITHUB)
        val o = DeveloperConnectionPolicy.repoPart(owner)
        val r = DeveloperConnectionPolicy.repoPart(repository)
        val ref = DeveloperConnectionPolicy.branch(branch)
        val path = DeveloperConnectionPolicy.filePath(file)
        val json = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$o/$r/contents/$path?ref=" +
                URLEncoder.encode(ref, "UTF-8"), expectedGithubRevision = session)
            .toString(StandardCharsets.UTF_8))
        require(json.optString("type") == "file" && json.optString("encoding") == "base64") {
            "Only single, text-based GitHub files are supported."
        }
        val blob = java.util.Base64.getDecoder().decode(json.getString("content")
            .replace("\n", "").replace("\r", ""))
        require(blob.size <= 256 * 1024 && blob.none { it == 0.toByte() }) {
            "Binary or oversized files cannot be edited here."
        }
        val sha = json.getString("sha")
        require(sha.matches(Regex("[a-f0-9]{40}")))
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(blob)).toString()
        check(credentialRevision(DevelopmentService.GITHUB) == session) { "Connection changed." }
        return GithubSourceFile(o, r, path, ref, sha, text, session)
    }

    suspend fun createGithubFeatureBranch(
        owner: String, repository: String, from: String, newBranch: String,
        approved: Boolean
    ): String {
        check(approved) { "Explicit user approval is required." }
        val session = credentialRevision(DevelopmentService.GITHUB)
        val o = DeveloperConnectionPolicy.repoPart(owner)
        val r = DeveloperConnectionPolicy.repoPart(repository)
        val source = DeveloperConnectionPolicy.branch(from)
        val target = DeveloperConnectionPolicy.writableBranch(newBranch)
        val ref = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$o/$r/git/ref/heads/" + URLEncoder.encode(source, "UTF-8"), expectedGithubRevision = session).toString(StandardCharsets.UTF_8))
        val sha = ref.getJSONObject("object").getString("sha")
        require(sha.matches(Regex("[a-f0-9]{40}")))
        val body = JSONObject().put("ref", "refs/heads/$target")
            .put("sha", sha).toString().toByteArray(Charsets.UTF_8)
        val created = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$o/$r/git/refs", "POST", body, expectedGithubRevision = session).toString(StandardCharsets.UTF_8))
        check(created.getString("ref") == "refs/heads/$target" &&
            created.getJSONObject("object").getString("sha") == sha) { "Created branch could not be confirmed." }
        return created.getString("ref")
    }

    suspend fun commitGithubFile(
        file: GithubSourceFile, editedText: String, confirmed: Boolean
    ): String {
        check(confirmed) { "User confirmation required before every repository write." }
        val session = file.credentialRevision
        check(session.isNotBlank() && credentialRevision(DevelopmentService.GITHUB) == session) {
            "Connection changed since the file was loaded. Load and review the file again."
        }
        val o = DeveloperConnectionPolicy.repoPart(file.owner)
        val r = DeveloperConnectionPolicy.repoPart(file.repo)
        val path = DeveloperConnectionPolicy.filePath(file.path)
        val branch = DeveloperConnectionPolicy.writableBranch(file.branch)
        require(file.sha.matches(Regex("[a-f0-9]{40}")))
        requireGithubWritableTarget(o, r, branch, session)
        val bytes = editedText.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..256 * 1024) { "Source file too large or empty." }
        val objectValue = JSONObject()
            .put("message", "Update $path from Maximus Matrix mobile editor")
            .put("branch", branch)
            .put("sha", file.sha)
            .put("content", java.util.Base64.getEncoder().encodeToString(bytes))
        val result = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$o/$r/contents/$path",
            "PUT", objectValue.toString().toByteArray(Charsets.UTF_8), expectedGithubRevision = session)
            .toString(StandardCharsets.UTF_8))
        val commit = result.getJSONObject("commit").getString("sha")
        check(commit.matches(Regex("[a-f0-9]{40}"))) { "Commit identity could not be confirmed." }
        return commit
    }

    /** Fail closed when the authenticated API cannot establish a safe write target. */
    private suspend fun requireGithubWritableTarget(owner: String, repo: String, branch: String, session: String) {
        val repository = JSONObject(call(DevelopmentService.GITHUB, "/repos/$owner/$repo", expectedGithubRevision = session)
            .toString(StandardCharsets.UTF_8))
        check(repository.has("archived") && !repository.getBoolean("archived")) {
            "Archived or unverified repositories cannot be edited here."
        }
        val defaultBranch = repository.optString("default_branch")
        check(defaultBranch.isNotBlank() && branch != defaultBranch) {
            "Choose a feature branch instead of the repository default branch."
        }
        val target = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$owner/$repo/branches/" + URLEncoder.encode(branch, "UTF-8"), expectedGithubRevision = session)
            .toString(StandardCharsets.UTF_8))
        check(target.optString("name") == branch && target.has("protected") &&
            !target.getBoolean("protected")) {
            "Protected or unverified branches cannot be edited here."
        }
    }

    private suspend fun githubBranch(owner: String, repo: String, branch: String, session: String): JSONObject =
        JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$owner/$repo/branches/" + URLEncoder.encode(branch, "UTF-8"), expectedGithubRevision = session)
            .toString(StandardCharsets.UTF_8))

    /** Reads repository evidence only. Creating the draft requires a second, explicit action. */
    suspend fun prepareGithubPullRequest(
        owner: String, repository: String, head: String, title: String, body: String
    ): GithubPullRequestReview {
        val session = credentialRevision(DevelopmentService.GITHUB)
        val o = DeveloperConnectionPolicy.repoPart(owner)
        val r = DeveloperConnectionPolicy.repoPart(repository)
        val h = DeveloperConnectionPolicy.writableBranch(head)
        val metadata = JSONObject(call(DevelopmentService.GITHUB, "/repos/$o/$r", expectedGithubRevision = session)
            .toString(StandardCharsets.UTF_8))
        val base = DeveloperConnectionPolicy.branch(metadata.getString("default_branch"))
        require(h != base) { "Choose a feature branch instead of the default branch." }
        val baseInfo = githubBranch(o, r, base, session)
        val headInfo = githubBranch(o, r, h, session)
        check(headInfo.optString("name") == h) { "The feature branch could not be verified." }
        GithubPullRequestPolicy.requireUnprotectedHead(headInfo.opt("protected") as? Boolean)
        val baseSha = baseInfo.getJSONObject("commit").getString("sha")
        val headSha = headInfo.getJSONObject("commit").getString("sha")
        require(baseSha.matches(Regex("[a-f0-9]{40}")) && headSha.matches(Regex("[a-f0-9]{40}")))
        val comparison = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$o/$r/compare/$baseSha...$headSha", expectedGithubRevision = session).toString(StandardCharsets.UTF_8))
        check(comparison.optString("status") in setOf("ahead", "diverged")) {
            "The feature branch has no changes ahead of the default branch."
        }
        val changed = comparison.getJSONArray("files")
        require(changed.length() in 1..100) { "The review supports between 1 and 100 changed files." }
        val names = (0 until changed.length()).map { changed.getJSONObject(it).getString("filename") }
        check(credentialRevision(DevelopmentService.GITHUB) == session) { "Connection changed." }
        return GithubPullRequestPolicy.review(o, r, base, h, baseSha, headSha,
            title, body, comparison.getInt("ahead_by"), names).copy(credentialRevision = session)
    }

    /** Does not merge, approve, deploy or retry. A changed preview must be reviewed again. */
    suspend fun createGithubPullRequest(review: GithubPullRequestReview, approved: Boolean): GithubPullRequestResult {
        check(approved) { "Explicit approval is required to create a draft pull request." }
        val session = review.credentialRevision
        check(session.isNotBlank() && credentialRevision(DevelopmentService.GITHUB) == session) {
            "Connection changed since preview. Prepare and approve a fresh preview."
        }
        val admitted = GithubPullRequestPolicy.review(review.owner, review.repository,
            review.base, review.head, review.baseSha, review.headSha, review.title,
            review.body, review.aheadBy, review.files).copy(credentialRevision = session)
        val o = admitted.owner
        val r = admitted.repository
        val metadata = JSONObject(call(DevelopmentService.GITHUB, "/repos/$o/$r", expectedGithubRevision = session)
            .toString(StandardCharsets.UTF_8))
        val currentBase = metadata.getString("default_branch")
        val baseInfo = githubBranch(o, r, admitted.base, session)
        val headInfo = githubBranch(o, r, admitted.head, session)
        check(headInfo.optString("name") == admitted.head) { "The feature branch could not be verified." }
        GithubPullRequestPolicy.requireUnprotectedHead(headInfo.opt("protected") as? Boolean)
        GithubPullRequestPolicy.requireCurrent(admitted, currentBase,
            baseInfo.getJSONObject("commit").getString("sha"),
            headInfo.getJSONObject("commit").getString("sha"))
        val body = JSONObject().put("title", admitted.title).put("body", admitted.body)
            .put("head", admitted.head).put("base", admitted.base).put("draft", true)
            .put("maintainer_can_modify", false).toString().toByteArray(Charsets.UTF_8)
        val created = JSONObject(call(DevelopmentService.GITHUB, "/repos/$o/$r/pulls",
            "POST", body, expectedGithubRevision = session).toString(StandardCharsets.UTF_8))
        // Construct the known first-party URL instead of opening an arbitrary API html_url.
        val url = GithubPullRequestPolicy.resultUrl(admitted, created.getInt("number"))
        val actualBase = created.optJSONObject("base")
        val actualHead = created.optJSONObject("head")
        // GitHub cannot atomically freeze refs for this API. Preserve the created URL
        // even if a concurrent push changed the snapshot after our last check.
        val matches = GithubPullRequestPolicy.resultMatches(admitted,
            actualBase?.optString("ref").orEmpty(), actualBase?.optString("sha").orEmpty(),
            actualHead?.optString("ref").orEmpty(), actualHead?.optString("sha").orEmpty(),
            actualHead?.optJSONObject("repo")?.optString("full_name").orEmpty(),
            created.optBoolean("draft"))
        return GithubPullRequestResult(url, matches)
    }

    /** Discovery is restricted to accounts exposed by this user's scoped API token. */
    suspend fun listCloudflareAccounts(): List<CloudflareAccount> {
        val response = JSONObject(call(DevelopmentService.CLOUDFLARE,
            "/accounts?per_page=50").toString(StandardCharsets.UTF_8))
        check(response.optBoolean("success")) { "Cloudflare account discovery failed." }
        val accounts = response.optJSONArray("result") ?: JSONArray()
        return (0 until minOf(50, accounts.length())).mapNotNull { index ->
            val item = accounts.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (runCatching { DeveloperConnectionPolicy.cloudflareAccountId(id) }.isFailure)
                return@mapNotNull null
            CloudflareAccount(id, item.optString("name").take(120).ifBlank { id })
        }
    }

    fun cloudflareDeployments(): CloudflareDeploymentClient = CloudflareDeploymentClient(
        revision = { credentialRevision(DevelopmentService.CLOUDFLARE) },
        request = { path, method, body, revision -> call(DevelopmentService.CLOUDFLARE,
            path, method, body, expectedCloudflareRevision = revision) },
        recordReceipt = recordReceipt,
        recordCreation = recordCreation, journal = cloudflareJournal)

    /** The create endpoint cannot replace an existing Worker; code upload uses its immutable ID. */
    suspend fun deployCloudflareAgent(
        accountId: String, workerName: String, purpose: CloudAgentPurpose,
        accessToken: String, approved: Boolean
    ): CloudflareCreationReceipt = cloudflareDeployments().create(accountId, workerName, purpose,
        accessToken, approved)

    suspend fun listCloudflareWorkers(accountId: String): List<String> {
        val account = DeveloperConnectionPolicy.cloudflareAccountId(accountId)
        val response = JSONObject(call(DevelopmentService.CLOUDFLARE,
            "/accounts/$account/workers/scripts").toString(StandardCharsets.UTF_8))
        check(response.optBoolean("success")) { "Cloudflare Worker list not available." }
        val list = response.optJSONArray("result") ?: JSONArray()
        return (0 until minOf(50, list.length())).mapNotNull { index ->
            val value = list.optJSONObject(index)?.optString("id") ?: return@mapNotNull null
            value.takeIf { runCatching {
                DeveloperConnectionPolicy.workerName(it)
            }.isSuccess }
        }
    }
}
