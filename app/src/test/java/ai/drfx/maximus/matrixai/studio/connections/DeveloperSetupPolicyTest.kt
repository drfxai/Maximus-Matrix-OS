package ai.drfx.maximus.matrixai.studio.connections

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class DeveloperSetupPolicyTest {
    @Test fun parsesRepositoryIdentityFromUrlOrShorthand() {
        for (input in listOf("DrFXAi/Maximus-Matrix-OS", " https://github.com/DrFXAi/Maximus-Matrix-OS/ ",
            "https://github.com/DrFXAi/Maximus-Matrix-OS.git")) {
            assertEquals("DrFXAi" to "Maximus-Matrix-OS", DeveloperSetupPolicy.parseGithubRepository(input))
        }
    }

    @Test fun rejectsCredentialsOriginsAndNonRepositoryPaths() {
        for (input in listOf("", "https://github.com.evil.test/a/b", "https://github.com@evil.test/a/b",
            "https://token@github.com/a/b", "http://github.com/a/b", "https://github.com:443/a/b",
            "https://github.com/a/b/tree/main", "https://github.com/a/b?token=secret",
            "https://github.com/a/b#readme", "a/..", "a/%2e%2e", "a/b/c", "a\\b", "../repo",
            "https://github.com/a/%72epo", "https://github.com//a/b", "-org/repo")) {
            assertThrows(input, Exception::class.java) { DeveloperSetupPolicy.parseGithubRepository(input) }
        }
    }

    @Test fun cloudflareTemplateHasOnlyWorkerPermissionAndOptionalSpecificAccount() {
        val account = "abcdef0123456789abcdef0123456789"
        val uri = URI(DeveloperSetupPolicy.cloudflareTokenSetupUrl(account))
        assertEquals("https", uri.scheme)
        assertEquals("dash.cloudflare.com", uri.host)
        assertEquals("/profile/api-tokens", uri.path)
        val params = uri.rawQuery.split('&').associate {
            val parts = it.split('=', limit = 2)
            parts[0] to URLDecoder.decode(parts[1], "UTF-8")
        }
        assertEquals("[{\"key\":\"workers_scripts\",\"type\":\"edit\"}]", params["permissionGroupKeys"])
        assertEquals(account, params["accountId"])
        assertFalse(uri.toString().contains("account_api_tokens"))
        assertTrue(DeveloperSetupPolicy.cloudflareTokenSetupUrl().contains("accountId=*"))
        assertThrows(Exception::class.java) { DeveloperSetupPolicy.cloudflareTokenSetupUrl("bad&account=*") }
    }

    @Test fun suggestedNamesAreValidUniqueAndPurposeSpecific() {
        val names = CloudAgentPurpose.entries.flatMap { purpose ->
            List(10) { DeveloperSetupPolicy.suggestedWorkerName(purpose) }.onEach {
                assertTrue(it.matches(Regex("maximus-[a-z]+-[a-f0-9]{12}")))
                assertTrue(it.length <= 63)
            }
        }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun sourceContainsBindingNamesAndNeverAnEmbeddedSecretOrAutonomousTool() {
        assertEquals("@cf/meta/llama-3.1-8b-instruct-fp8", DeveloperSetupPolicy.WORKER_MODEL)
        val sources = CloudAgentPurpose.entries.map { DeveloperSetupPolicy.workerSource(it) }
        assertEquals(3, sources.toSet().size)
        sources.forEach {
            assertTrue(it.contains(DeveloperSetupPolicy.WORKER_MODEL))
            assertTrue(it.contains("env.MAXIMUS_AGENT_TOKEN"))
            assertTrue(it.contains("env.AI.run"))
            assertFalse(it.contains("__SYSTEM_PROMPT__"))
            assertFalse(it.contains("__MODEL__"))
            assertFalse(it.contains("scheduled("))
            assertFalse(it.contains("fetch(\"https://"))
        }
    }
}
