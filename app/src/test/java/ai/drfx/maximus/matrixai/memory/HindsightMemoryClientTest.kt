package ai.drfx.maximus.matrixai.memory

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

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

    @Test fun queuedAccessRevocationStopsRequestBeforeAnyTransportIo() = runBlocking {
        val operations = listOf("recall", "retain")
        for (operation in operations) {
            val ioCount = AtomicInteger()
            val client = HindsightMemoryClient { url -> NoNetworkConnection(url, ioCount) }
            val original = mapOf<String, Any>(
                "endpoint" to "https://memory.example.org", "token" to "encrypted-test-a",
                "active_project" to project, "enabled" to true, "auto_share" to true,
                HindsightAccessPolicy.REVISION_KEY to "a"
            )
            val captured = HindsightAccessPolicy.capture(original) { "test-token-a-not-a-real-key" }
            val changed = original + (HindsightAccessPolicy.REVISION_KEY to "changed-while-queued")
            val guard = {
                check(HindsightAccessPolicy.isCurrent(captured, changed, automatic = true)) {
                    "Memory access changed before dispatch."
                }
            }
            val failure = runCatching {
                if (operation == "recall") client.recall(captured.scope.endpoint, captured.token,
                    project, "unfinished project tasks", beforeDispatch = guard)
                else client.retain(captured.scope.endpoint, captured.token, project,
                    "Project parser review is unfinished.", "model-progress", beforeDispatch = guard)
            }.exceptionOrNull()
            assertTrue(operation, failure is IllegalStateException)
            assertEquals("Rejected queued request must not open an output or response stream", 0, ioCount.get())
        }
    }

    @Test fun unchangedCapturedAccessStillAllowsTheActualRequestBoundary() = runBlocking {
        val ioCount = AtomicInteger()
        val client = HindsightMemoryClient { url -> NoNetworkConnection(url, ioCount) }
        var guarded = false
        assertTrue(client.recall("https://memory.example.org", "test-token-a-not-a-real-key",
            project, "unfinished project tasks", beforeDispatch = { guarded = true }).isEmpty())
        assertTrue(guarded)
        assertTrue(ioCount.get() > 0)
    }

    private class NoNetworkConnection(url: URL, private val ioCount: AtomicInteger) : HttpURLConnection(url) {
        override fun connect() { ioCount.incrementAndGet() }
        override fun disconnect() { }
        override fun usingProxy() = false
        override fun getOutputStream(): ByteArrayOutputStream {
            ioCount.incrementAndGet()
            return ByteArrayOutputStream()
        }
        override fun getResponseCode(): Int { ioCount.incrementAndGet(); return 200 }
        override fun getInputStream(): ByteArrayInputStream {
            ioCount.incrementAndGet()
            return ByteArrayInputStream("{\"results\":[]}".toByteArray())
        }
    }
}
