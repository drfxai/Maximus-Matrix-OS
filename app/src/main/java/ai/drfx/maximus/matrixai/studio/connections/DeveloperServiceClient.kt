package ai.drfx.maximus.matrixai.studio.connections

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
    val branch: String, val sha: String, val text: String
)

internal data class CloudflareAccount(val id: String, val name: String)

/** Explicit user-owned API operations. No autonomous deployment or privileged remote shell. */
internal class DeveloperServiceClient(private val vault: DeveloperCredentialVault) {
    private companion object {
        const val GH = "https://api.github.com"
        const val CF = "https://api.cloudflare.com/client/v4"
        const val MAX_RESPONSE = 1024 * 1024
    }

    private suspend fun call(
        service: DevelopmentService,
        path: String,
        method: String = "GET",
        body: ByteArray? = null,
        expectedCloudflareRevision: String? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.startsWith("//") && '#' !in path)
        require(method in setOf("GET", "POST", "PUT"))
        require(body == null || body.size <= 512 * 1024)
        val credentials = vault.snapshot(service)
        if (expectedCloudflareRevision != null) check(service == DevelopmentService.CLOUDFLARE &&
            credentials.revision == expectedCloudflareRevision) { "Connection changed." }
        val token = credentials.token
        check(token.isNotBlank()) { "Connect this service with a restricted API token first." }
        val base = if (service == DevelopmentService.GITHUB) GH else CF
        val url = URL(base + path)
        check(url.protocol == "https" && url.host ==
            if (service == DevelopmentService.GITHUB) "api.github.com" else "api.cloudflare.com")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 22_000
            connection.requestMethod = method
            connection.setRequestProperty("Accept",
                if (service == DevelopmentService.GITHUB)
                    "application/vnd.github+json" else "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("User-Agent", "Maximus-Matrix-Mobile/1.0")
            if (service == DevelopmentService.GITHUB)
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            // Last check before either outputStream or responseCode can dispatch HTTP.
            if (expectedCloudflareRevision != null)
                check(vault.revision(service) == expectedCloudflareRevision) { "Connection changed." }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            check(code in 200..299) { "Service request unavailable (HTTP $code). No change was confirmed." }
            val content = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    check(content.size() + n <= MAX_RESPONSE) {
                        "API response is larger than the supported editor limit."
                    }
                    content.write(buffer, 0, n)
                }
            }
            content.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun verifyGithub(): String {
        val user = JSONObject(call(DevelopmentService.GITHUB, "/user")
            .toString(StandardCharsets.UTF_8))
        return user.optString("login").take(80).ifBlank { "Authenticated account" }
    }

    suspend fun verifyCloudflare(): String {
        val status = JSONObject(call(DevelopmentService.CLOUDFLARE, "/user/tokens/verify")
            .toString(StandardCharsets.UTF_8))
        check(status.optBoolean("success")) { "Cloudflare token validation failed." }
        check(status.optJSONObject("result")?.optString("status") == "active") {
            "Cloudflare token is not active."
        }
        return "active"
    }

    suspend fun readGithubFile(
        owner: String, repository: String, branch: String, file: String
    ): GithubSourceFile {
        val o = DeveloperConnectionPolicy.repoPart(owner)
        val r = DeveloperConnectionPolicy.repoPart(repository)
        val ref = DeveloperConnectionPolicy.branch(branch)
        val path = DeveloperConnectionPolicy.filePath(file)
        val json = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$o/$r/contents/$path?ref=" +
                URLEncoder.encode(ref, "UTF-8"))
            .toString(StandardCharsets.UTF_8))
        require(json.optString("type") == "file" && json.optString("encoding") == "base64") {
            "Only single, text-based GitHub files are supported."
        }
        val blob = Base64.decode(json.getString("content"), Base64.DEFAULT)
        require(blob.size <= 256 * 1024 && blob.none { it == 0.toByte() }) {
            "Binary or oversized files cannot be edited here."
        }
        val sha = json.getString("sha")
        require(sha.matches(Regex("[a-f0-9]{40}")))
        return GithubSourceFile(o, r, path, ref, sha, blob.toString(Charsets.UTF_8))
    }

    suspend fun createGithubFeatureBranch(
        owner: String, repository: String, from: String, newBranch: String,
        approved: Boolean
    ): String {
        check(approved) { "Explicit user approval is required." }
        val o = DeveloperConnectionPolicy.repoPart(owner)
        val r = DeveloperConnectionPolicy.repoPart(repository)
        val source = DeveloperConnectionPolicy.branch(from)
        val target = DeveloperConnectionPolicy.writableBranch(newBranch)
        val ref = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$o/$r/git/ref/heads/" + source).toString(StandardCharsets.UTF_8))
        val sha = ref.getJSONObject("object").getString("sha")
        require(sha.matches(Regex("[a-f0-9]{40}")))
        val body = JSONObject().put("ref", "refs/heads/$target")
            .put("sha", sha).toString().toByteArray(Charsets.UTF_8)
        val created = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$o/$r/git/refs", "POST", body).toString(StandardCharsets.UTF_8))
        return created.optString("ref")
    }

    suspend fun commitGithubFile(
        file: GithubSourceFile, editedText: String, confirmed: Boolean
    ): String {
        check(confirmed) { "User confirmation required before every repository write." }
        val o = DeveloperConnectionPolicy.repoPart(file.owner)
        val r = DeveloperConnectionPolicy.repoPart(file.repo)
        val path = DeveloperConnectionPolicy.filePath(file.path)
        val branch = DeveloperConnectionPolicy.writableBranch(file.branch)
        require(file.sha.matches(Regex("[a-f0-9]{40}")))
        requireGithubWritableTarget(o, r, branch)
        val bytes = editedText.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..256 * 1024) { "Source file too large or empty." }
        val objectValue = JSONObject()
            .put("message", "Update $path from Maximus Matrix mobile editor")
            .put("branch", branch)
            .put("sha", file.sha)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
        val result = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$o/$r/contents/$path",
            "PUT", objectValue.toString().toByteArray(Charsets.UTF_8))
            .toString(StandardCharsets.UTF_8))
        return result.getJSONObject("commit").getString("sha")
    }

    /** Fail closed when the authenticated API cannot establish a safe write target. */
    private suspend fun requireGithubWritableTarget(owner: String, repo: String, branch: String) {
        val repository = JSONObject(call(DevelopmentService.GITHUB, "/repos/$owner/$repo")
            .toString(StandardCharsets.UTF_8))
        val defaultBranch = repository.optString("default_branch")
        check(defaultBranch.isNotBlank() && branch != defaultBranch) {
            "Choose a feature branch instead of the repository default branch."
        }
        val target = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$owner/$repo/branches/" + URLEncoder.encode(branch, "UTF-8"))
            .toString(StandardCharsets.UTF_8))
        check(target.optString("name") == branch && target.has("protected") &&
            !target.getBoolean("protected")) {
            "Protected or unverified branches cannot be edited here."
        }
    }

    private suspend fun githubBranch(owner: String, repo: String, branch: String): JSONObject =
        JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$owner/$repo/branches/" + URLEncoder.encode(branch, "UTF-8"))
            .toString(StandardCharsets.UTF_8))

    /** Reads repository evidence only. Creating the draft requires a second, explicit action. */
    suspend fun prepareGithubPullRequest(
        owner: String, repository: String, head: String, title: String, body: String
    ): GithubPullRequestReview {
        val o = DeveloperConnectionPolicy.repoPart(owner)
        val r = DeveloperConnectionPolicy.repoPart(repository)
        val h = DeveloperConnectionPolicy.writableBranch(head)
        val metadata = JSONObject(call(DevelopmentService.GITHUB, "/repos/$o/$r")
            .toString(StandardCharsets.UTF_8))
        val base = DeveloperConnectionPolicy.branch(metadata.getString("default_branch"))
        require(h != base) { "Choose a feature branch instead of the default branch." }
        val baseInfo = githubBranch(o, r, base)
        val headInfo = githubBranch(o, r, h)
        check(headInfo.optString("name") == h) { "The feature branch could not be verified." }
        GithubPullRequestPolicy.requireUnprotectedHead(headInfo.opt("protected") as? Boolean)
        val baseSha = baseInfo.getJSONObject("commit").getString("sha")
        val headSha = headInfo.getJSONObject("commit").getString("sha")
        require(baseSha.matches(Regex("[a-f0-9]{40}")) && headSha.matches(Regex("[a-f0-9]{40}")))
        val comparison = JSONObject(call(DevelopmentService.GITHUB,
            "/repos/$o/$r/compare/$baseSha...$headSha").toString(StandardCharsets.UTF_8))
        check(comparison.optString("status") in setOf("ahead", "diverged")) {
            "The feature branch has no changes ahead of the default branch."
        }
        val changed = comparison.getJSONArray("files")
        require(changed.length() in 1..100) { "The review supports between 1 and 100 changed files." }
        val names = (0 until changed.length()).map { changed.getJSONObject(it).getString("filename") }
        return GithubPullRequestPolicy.review(o, r, base, h, baseSha, headSha,
            title, body, comparison.getInt("ahead_by"), names)
    }

    /** Does not merge, approve, deploy or retry. A changed preview must be reviewed again. */
    suspend fun createGithubPullRequest(review: GithubPullRequestReview, approved: Boolean): GithubPullRequestResult {
        check(approved) { "Explicit approval is required to create a draft pull request." }
        val admitted = GithubPullRequestPolicy.review(review.owner, review.repository,
            review.base, review.head, review.baseSha, review.headSha, review.title,
            review.body, review.aheadBy, review.files)
        val o = admitted.owner
        val r = admitted.repository
        val metadata = JSONObject(call(DevelopmentService.GITHUB, "/repos/$o/$r")
            .toString(StandardCharsets.UTF_8))
        val currentBase = metadata.getString("default_branch")
        val baseInfo = githubBranch(o, r, admitted.base)
        val headInfo = githubBranch(o, r, admitted.head)
        check(headInfo.optString("name") == admitted.head) { "The feature branch could not be verified." }
        GithubPullRequestPolicy.requireUnprotectedHead(headInfo.opt("protected") as? Boolean)
        GithubPullRequestPolicy.requireCurrent(admitted, currentBase,
            baseInfo.getJSONObject("commit").getString("sha"),
            headInfo.getJSONObject("commit").getString("sha"))
        val body = JSONObject().put("title", admitted.title).put("body", admitted.body)
            .put("head", admitted.head).put("base", admitted.base).put("draft", true)
            .put("maintainer_can_modify", false).toString().toByteArray(Charsets.UTF_8)
        val created = JSONObject(call(DevelopmentService.GITHUB, "/repos/$o/$r/pulls",
            "POST", body).toString(StandardCharsets.UTF_8))
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
        revision = { vault.revision(DevelopmentService.CLOUDFLARE) },
        request = { path, method, body, revision -> call(DevelopmentService.CLOUDFLARE,
            path, method, body, expectedCloudflareRevision = revision) },
        recordReceipt = vault::recordCloudflareReceipt,
        recordCreation = vault::recordCloudflareCreation)

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
