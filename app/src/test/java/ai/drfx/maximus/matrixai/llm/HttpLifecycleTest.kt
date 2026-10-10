package ai.drfx.maximus.matrixai.llm

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HttpLifecycleTest {
    private class Connection : HttpURLConnection(URL("https://example.com")) {
        val disconnected = CountDownLatch(1)
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected.countDown() }
    }
    @Test fun cancellationDisconnectsBlockedTransport() = runBlocking {
        val connection = Connection()
        val registered = CountDownLatch(1)
        val job = launch {
            CancellableHttp.execute {
                CancellableHttp.register(connection)
                registered.countDown()
                connection.disconnected.await(10, TimeUnit.SECONDS)
            }
        }
        kotlinx.coroutines.yield()
        assertTrue(registered.await(2, TimeUnit.SECONDS))
        job.cancel()
        job.join()
        assertTrue(connection.disconnected.await(2, TimeUnit.SECONDS))
        assertTrue(job.isCancelled)
    }
    @Test fun lateConnectionRegistrationAfterCancellationIsRejected() {
        val session = CancellableHttp.Session()
        session.cancel()
        val connection = Connection()
        try { session.register(connection); fail("Must reject late connection") } catch (_: java.util.concurrent.CancellationException) {}
        assertEquals(0L, connection.disconnected.count)
    }
    @Test fun catalogsExpireAndNeverCrossCredentialProviderOrEndpoint() {
        var time = 100L
        val cache = VerifiedCatalogCache { time }
        val models = listOf(ModelDescriptor("actual-model", verified = true))
        cache.put(LlmProvider.OPENAI, "https://api.openai.com", "key-one", models)
        assertEquals(models, cache.get(LlmProvider.OPENAI, "https://api.openai.com", "key-one"))
        assertNull(cache.get(LlmProvider.OPENAI, "https://api.openai.com", "key-two"))
        assertNull(cache.get(LlmProvider.GEMINI, "https://api.openai.com", "key-one"))
        assertNull(cache.get(LlmProvider.OPENAI, "https://other.example", "key-one"))
        time += VerifiedCatalogCache.TTL_MS
        assertNull(cache.get(LlmProvider.OPENAI, "https://api.openai.com", "key-one"))
    }
}
