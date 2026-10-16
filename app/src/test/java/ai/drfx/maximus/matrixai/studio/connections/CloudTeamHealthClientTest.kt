package ai.drfx.maximus.matrixai.studio.connections

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class CloudTeamHealthClientTest {
    private val secret = "test-only-worker-access-token-123456789"
    @Test fun onlyPublicHttpsOriginCanReceiveThePrivateWorkerToken() {
        assertEquals("https://worker.example.org",
            CloudTeamHealthClient.origin("https://worker.example.org/"))
        for (invalid in listOf("http://worker.example.org", "https://localhost",
            "https://127.0.0.1", "https://private.internal",
            "https://user:secret@worker.example.org",
            "https://worker.example.org/custom/path", "https://worker.example.org?key=1")) {
            assertThrows(Exception::class.java) { CloudTeamHealthClient.origin(invalid) }
        }
    }

    @Test fun savedBearerCanNeverBeProbedAgainstEditedOrigin() {
        val pinned = "https://worker.example.org"
        assertTrue(CloudTeamHealthClient.matchesSavedOrigin(pinned, pinned))
        assertFalse(CloudTeamHealthClient.matchesSavedOrigin("https://other.example.org", pinned))
        assertFalse(CloudTeamHealthClient.matchesSavedOrigin(pinned + "/", pinned))
        assertFalse(CloudTeamHealthClient.matchesSavedOrigin(pinned, ""))
    }

    @Test fun healthUsesOneAuthenticatedBoundedReadAndNeverFollowsRedirects() = runBlocking {
        var opened = 0
        val client = CloudTeamHealthClient { url ->
            opened++
            assertEquals("https://worker.example.org/v1/health", url.toString())
            Stub(url, 200, """{"status":"ready","scheduler":"cron","externalExecution":false,"configuredTeams":2,"observedAt":1234567}""")
        }
        val result = client.health("https://worker.example.org", secret)
        assertEquals(2, result.configuredTeams)
        assertEquals(1, opened)
    }

    @Test fun unverifiedOrRedirectingWorkerFailsClosed() = runBlocking {
        val client = CloudTeamHealthClient { url -> Stub(url, 302, "") }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { client.health("https://worker.example.org", secret) }
        }
        Unit // JUnit4 requires a void method, not the returned Throwable
    }

    private class Stub(url: URL, private val status: Int, private val body: String) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode(): Int {
            assertFalse(instanceFollowRedirects)
            return status
        }
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
    }
}
