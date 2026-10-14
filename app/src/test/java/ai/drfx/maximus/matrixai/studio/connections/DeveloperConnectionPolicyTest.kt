package ai.drfx.maximus.matrixai.studio.connections

import org.junit.Assert.*
import org.junit.Test

class DeveloperConnectionPolicyTest {
    @Test fun restrictsRepositoryAndFilePaths() {
        assertEquals("org", DeveloperConnectionPolicy.repoPart("org"))
        assertEquals("app/src/Main.kt", DeveloperConnectionPolicy.filePath("app/src/Main.kt"))
        for (path in listOf("../secrets", "/etc/passwd", "a//b", ".github/workflows/release.yml",
            "a\\b", "a?download=1", "a#fragment", "a/../../secret")) {
            assertThrows(path, Exception::class.java) {
                DeveloperConnectionPolicy.filePath(path)
            }
        }
    }

    @Test fun requiresNonProductionBranchForWrites() {
        assertEquals("feature/mobile-edit", DeveloperConnectionPolicy.writableBranch("feature/mobile-edit"))
        for (branch in listOf("main", "master", "production", "release", "../test",
            "feature//bad", "branch.lock", "name?param=1", "/branch")) {
            assertThrows(branch, Exception::class.java) {
                DeveloperConnectionPolicy.writableBranch(branch)
            }
        }
    }

    @Test fun cloudflareInputsAreStrictlyScoped() {
        assertEquals("abcdef0123456789abcdef0123456789",
            DeveloperConnectionPolicy.cloudflareAccountId("abcdef0123456789abcdef0123456789"))
        assertEquals("example-worker", DeveloperConnectionPolicy.workerName("example-worker"))
        for (invalid in listOf("../../script", "a/b", "a?token=x", "a#frag")) {
            assertThrows(Exception::class.java) { DeveloperConnectionPolicy.workerName(invalid) }
        }
    }
}