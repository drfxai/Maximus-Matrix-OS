package ai.drfx.maximus.matrixai.memory

import org.junit.Assert.*
import org.junit.Test

class HindsightMemoryClientTest {
    private val project = "123e4567-e89b-12d3-a456-426614174000"

    @Test fun projectBanksAreDeterministicAndIsolated() {
        assertEquals("maximus-project-" + project, HindsightMemoryClient.bankFor(project))
        assertNotEquals(HindsightMemoryClient.bankFor(project),
            HindsightMemoryClient.bankFor("123e4567-e89b-12d3-a456-426614174001"))
        assertThrows(IllegalArgumentException::class.java) {
            HindsightMemoryClient.bankFor("../../admin")
        }
    }

    @Test fun onlyHttpsOriginsWithoutInjectedHeadersOrPathsAllowed() {
        assertEquals("https://api.hindsight.vectorize.io",
            HindsightMemoryClient.normalizedEndpoint("https://api.hindsight.vectorize.io/"))
        assertEquals("https://memory.example.org:8443",
            HindsightMemoryClient.normalizedEndpoint("https://memory.example.org:8443"))
        listOf(
            "http://memory.example.org", "https://username:password@memory.example.org",
            "https://memory.example.org/v1/default", "https://memory.example.org?token=hello",
            "https://localhost", "https://127.0.0.1", "https://metadata.internal",
            "https://memory.example.org#fragment"
        ).forEach { invalid ->
            assertThrows("Unexpected accepted URL: $invalid", Exception::class.java) {
                HindsightMemoryClient.normalizedEndpoint(invalid)
            }
        }
    }

    @Test fun commonSecretsAndOversizedMemoriesAreRejected() {
        assertTrue(HindsightMemoryClient.shareable("Project build 12 passed two unit tests."))
        assertFalse(HindsightMemoryClient.shareable("password = some-password"))
        assertFalse(HindsightMemoryClient.shareable("Authorization: Bearer abcdefghijklmnopqrstuvwxyz"))
        assertFalse(HindsightMemoryClient.shareable("-----BEGIN PRIVATE KEY-----"))
        // Synthetic format fixtures, not actual account credentials.
        assertFalse(HindsightMemoryClient.shareable("Discovered " + "AIza" + "a".repeat(35)))
        assertFalse(HindsightMemoryClient.shareable("Discovered " + "AKIA" + "A".repeat(16)))
        assertFalse(HindsightMemoryClient.shareable("x".repeat(2600)))
        assertFalse(HindsightMemoryClient.shareable(""))
    }
}
