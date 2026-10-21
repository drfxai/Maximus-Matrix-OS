package ai.drfx.maximus.matrixai.studio.connections

internal data class GithubPullRequestReview(
    val owner: String,
    val repository: String,
    val base: String,
    val head: String,
    val baseSha: String,
    val headSha: String,
    val title: String,
    val body: String,
    val aheadBy: Int,
    val files: List<String>,
    val credentialRevision: String = ""
)

internal data class GithubPullRequestResult(val url: String, val reviewedSnapshotMatches: Boolean)

/** Admission applies both to network-prepared previews and immediately before writes. */
internal object GithubPullRequestPolicy {
    fun requireUnprotectedHead(protected: Boolean?) {
        require(protected == false) { "Protected or unverified heads cannot be submitted." }
    }

    fun review(
        owner: String, repository: String, base: String, head: String,
        baseSha: String, headSha: String, title: String, body: String,
        aheadBy: Int, files: List<String>
    ): GithubPullRequestReview {
        val o = DeveloperConnectionPolicy.repoPart(owner)
        val r = DeveloperConnectionPolicy.repoPart(repository)
        val b = DeveloperConnectionPolicy.branch(base)
        val h = DeveloperConnectionPolicy.writableBranch(head)
        require(h != b) { "The feature branch must differ from the default branch." }
        require(baseSha.matches(Regex("[a-f0-9]{40}")) && headSha.matches(Regex("[a-f0-9]{40}")))
        require(baseSha != headSha && aheadBy > 0 && files.size in 1..100) {
            "A pull request needs changes ahead of the default branch, within the 100-file preview limit."
        }
        val heading = title.trim()
        require(heading.length in 1..120 && heading.none { it == '\n' || it == '\r' || it == '\u0000' })
        require(body.length <= 20_000 && '\u0000' !in body)
        require(files.all { it.isNotBlank() && it.length <= 450 && it.none { c -> c == '\u0000' || c == '\n' || c == '\r' } })
        return GithubPullRequestReview(o, r, b, h, baseSha, headSha, heading, body, aheadBy, files.toList())
    }

    fun requireCurrent(review: GithubPullRequestReview, defaultBranch: String, baseSha: String, headSha: String) {
        require(review.base == defaultBranch && review.baseSha == baseSha && review.headSha == headSha) {
            "Repository branches changed after preview. Prepare and approve a fresh preview."
        }
    }

    fun resultUrl(review: GithubPullRequestReview, number: Int): String {
        require(number > 0)
        return "https://github.com/${DeveloperConnectionPolicy.repoPart(review.owner)}/" +
            "${DeveloperConnectionPolicy.repoPart(review.repository)}/pull/$number"
    }

    fun resultMatches(review: GithubPullRequestReview, base: String, baseSha: String,
        head: String, headSha: String, headRepository: String, isDraft: Boolean): Boolean =
        isDraft && base == review.base && baseSha == review.baseSha && head == review.head &&
            headSha == review.headSha && headRepository.equals("${review.owner}/${review.repository}", ignoreCase = true)
}
