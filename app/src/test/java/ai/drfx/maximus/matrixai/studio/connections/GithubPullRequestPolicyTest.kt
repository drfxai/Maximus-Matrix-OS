package ai.drfx.maximus.matrixai.studio.connections

import org.junit.Assert.*
import org.junit.Test

class GithubPullRequestPolicyTest {
    private val baseSha = "a".repeat(40)
    private val headSha = "b".repeat(40)
    private fun review(base: String = "develop", head: String = "feature/fix", title: String = "Fix crash",
        body: String = "Reviewed change", ahead: Int = 2, files: List<String> = listOf("app/Main.kt")) =
        GithubPullRequestPolicy.review("owner", "repo", base, head, baseSha, headSha, title, body, ahead, files)

    @Test fun admitsSameRepositoryFeaturePreviewAgainstActualDefault() {
        val admitted = review()
        assertEquals("develop", admitted.base)
        assertEquals("feature/fix", admitted.head)
        assertEquals("https://github.com/owner/repo/pull/42", GithubPullRequestPolicy.resultUrl(admitted, 42))
    }

    @Test fun rejectsDefaultProductionOrInjectedHeads() {
        for (head in listOf("develop", "main", "master", "production", "release", "attacker:feature", "feature?x=1"))
            assertThrows(head, Exception::class.java) { review(head = head) }
    }

    @Test fun rejectsMissingChangesOrOversizedPreview() {
        assertThrows(Exception::class.java) { review(ahead = 0) }
        assertThrows(Exception::class.java) { review(files = emptyList()) }
        assertThrows(Exception::class.java) { review(files = List(101) { "file$it.kt" }) }
    }

    @Test fun rejectsInvalidUserTextBeforeWrite() {
        for (title in listOf("", "   ", "bad\nheading", "bad\rheading", "a".repeat(121), "bad\u0000title"))
            assertThrows(Exception::class.java) { review(title = title) }
        assertThrows(Exception::class.java) { review(body = "a".repeat(20_001)) }
        assertThrows(Exception::class.java) { review(body = "bad\u0000body") }
    }

    @Test fun rejectsChangedDefaultBaseOrHeadAfterApproval() {
        val approved = review()
        GithubPullRequestPolicy.requireCurrent(approved, "develop", baseSha, headSha)
        assertThrows(Exception::class.java) { GithubPullRequestPolicy.requireCurrent(approved, "main", baseSha, headSha) }
        assertThrows(Exception::class.java) { GithubPullRequestPolicy.requireCurrent(approved, "develop", "c".repeat(40), headSha) }
        assertThrows(Exception::class.java) { GithubPullRequestPolicy.requireCurrent(approved, "develop", baseSha, "d".repeat(40)) }
    }

    @Test fun protectedAndUnknownProtectionAreRejected() {
        GithubPullRequestPolicy.requireUnprotectedHead(false)
        assertThrows(Exception::class.java) { GithubPullRequestPolicy.requireUnprotectedHead(true) }
        assertThrows(Exception::class.java) { GithubPullRequestPolicy.requireUnprotectedHead(null) }
    }

    @Test fun concurrentPushOrWrongRepositoryKeepsUrlButMarksSnapshotChanged() {
        val approved = review()
        assertTrue(GithubPullRequestPolicy.resultMatches(approved, "develop", baseSha, "feature/fix", headSha, "owner/repo", true))
        assertFalse(GithubPullRequestPolicy.resultMatches(approved, "develop", baseSha, "feature/fix", "c".repeat(40), "owner/repo", true))
        assertFalse(GithubPullRequestPolicy.resultMatches(approved, "main", baseSha, "feature/fix", headSha, "owner/repo", true))
        assertFalse(GithubPullRequestPolicy.resultMatches(approved, "develop", baseSha, "feature/fix", headSha, "fork/repo", true))
        assertFalse(GithubPullRequestPolicy.resultMatches(approved, "develop", baseSha, "feature/fix", headSha, "owner/repo", false))
        assertEquals("https://github.com/owner/repo/pull/42", GithubPullRequestPolicy.resultUrl(approved, 42))
        assertThrows(Exception::class.java) { GithubPullRequestPolicy.resultUrl(approved, 0) }
    }
}
