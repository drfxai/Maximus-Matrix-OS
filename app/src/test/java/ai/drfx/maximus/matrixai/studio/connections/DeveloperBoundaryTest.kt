package ai.drfx.maximus.matrixai.studio.connections

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DeveloperBoundaryTest {
    private class Connection(url: URL, val response: ByteArray = "{}".toByteArray(), val code: Int = 200) : HttpURLConnection(url) {
        var dispatches = 0
        val written = ByteArrayOutputStream()
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun connect() {}
        override fun getOutputStream() = written.also { dispatches++ }
        override fun getResponseCode() = code.also { dispatches++ }
        override fun getInputStream() = ByteArrayInputStream(response)
        override fun getContentLengthLong() = response.size.toLong()
    }
    @Test fun accountChangeAtHttpBoundaryBlocksBeforeAnyWrite() = runTest {
        var revision = "old"
        lateinit var connection: Connection
        val transport = DeveloperApiTransport({ DeveloperCredentialSnapshot("old", "test-only") },
            { revision }, { true }, { url -> Connection(url).also { connection = it; revision = "new" } })
        assertTrue(runCatching { transport.call(DevelopmentService.GITHUB, "/repos/a/b/contents/file", "PUT",
            "{}".toByteArray(), "old") }.exceptionOrNull() is DeveloperRequestNotDispatchedException)
        assertEquals(0, connection.dispatches)
        assertEquals(0, connection.written.size())
    }
    @Test fun redirectIsNotFollowedAndUnknownResponseIsNotAccepted() = runTest {
        lateinit var connection: Connection
        val transport = DeveloperApiTransport({ DeveloperCredentialSnapshot("r", "test-only") },
            { "r" }, { true }, { url -> Connection(url, code = 302).also { connection = it } })
        assertTrue(runCatching { transport.call(DevelopmentService.GITHUB, "/repos/a/b") }.isFailure)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("api.github.com", connection.url.host)
        assertEquals(1, connection.dispatches)
    }
    @Test fun requestBoundsAndWrongServiceAreRejectedBeforeOpeningConnection() = runTest {
        var opens = 0
        val transport = DeveloperApiTransport({ DeveloperCredentialSnapshot("r", "test-only") },
            { "r" }, { true }, { url -> opens++; Connection(url) })
        assertTrue(runCatching { transport.call(DevelopmentService.CLOUD_AGENT, "/user") }.isFailure)
        assertTrue(runCatching { transport.call(DevelopmentService.GITHUB, "//attacker.example") }.isFailure)
        assertTrue(runCatching { transport.call(DevelopmentService.CLOUDFLARE, "/accounts", "POST",
            ByteArray(DeveloperApiTransport.MAX_BODY+1)) }.isFailure)
        assertEquals(0, opens)
    }
    @Test fun oversizedResponseFailsAfterSingleReadWithoutRetry() = runTest {
        var opens = 0
        val transport = DeveloperApiTransport({ DeveloperCredentialSnapshot("r", "test-only") },
            { "r" }, { true }, { url -> opens++; Connection(url, ByteArray(DeveloperApiTransport.MAX_RESPONSE+1)) })
        assertTrue(runCatching { transport.call(DevelopmentService.GITHUB, "/repos/a/b") }.isFailure)
        assertEquals(1, opens)
    }
    @Test fun immutableDiffKeepsExactApprovedBytesAndShowsTruncation() {
        val source = GithubSourceFile("a", "b", "file", "feature/test", "a".repeat(40), "old", "r")
        val text = (1..200).joinToString("\n") { "new $it" }
        val review = SourceChangeReview(source, text)
        assertEquals(text, review.proposed)
        assertTrue(review.preview().contains("- old"))
        assertTrue(review.preview().contains("+ new 1"))
        assertTrue(review.preview().contains("PREVIEW TRUNCATED"))
        assertEquals(64, review.sha256.length)
    }
}
