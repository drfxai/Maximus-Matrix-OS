package ai.drfx.maximus.matrixai.llm

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Capture the actual serialized adapter requests. No server or real key is used. */
class OutputTokenLimitTest {
    private val agent = AgentDescriptor("code", "Code", "Test", "Be concise", setOf(ModelCapability.CHAT))
    private suspend fun request(provider: LlmProvider, cap: Int?): JSONObject {
        var captured: JSONObject? = null
        val client = LlmChatClient { _, body, _ ->
            captured = JSONObject(body)
            when (provider) {
                LlmProvider.ANTHROPIC -> """{"content":[{"type":"text","text":"ok"}],"usage":{"input_tokens":2,"output_tokens":1}}"""
                LlmProvider.GEMINI -> """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}],"usageMetadata":{"promptTokenCount":2,"candidatesTokenCount":1,"totalTokenCount":3}}"""
                else -> """{"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}"""
            }
        }
        client.send(ApiConnectionConfig(provider.defaultBaseUrl.ifBlank { "https://fixture.example/v1" },
            "fixture-not-a-credential", provider, "test-model", "code"), listOf(ChatMessage("user", "hello")),
            agent, maxOutputTokens = cap)
        return requireNotNull(captured)
    }
    @Test fun directOpenAiUsesCompletionCapForReasoningAndOrdinaryModels() = runTest {
        val body = request(LlmProvider.OPENAI, 1024)
        assertEquals(1024, body.getInt("max_completion_tokens"))
        assertFalse(body.has("max_tokens"))
        assertFalse(body.has("tools"))
    }
    @Test fun compatibleProtocolsReceiveExactCapWithoutFallback() = runTest {
        for (provider in listOf(LlmProvider.NVIDIA, LlmProvider.OPENAI_COMPATIBLE,
            LlmProvider.ROUTER_9_SMART, LlmProvider.ROUTER_9_COMBO)) {
            val body = request(provider, 1024)
            assertEquals(provider.name, 1024, body.getInt("max_tokens"))
            assertFalse(body.has("max_completion_tokens"))
        }
    }
    @Test fun anthropicHasExplicitMessagesLimit() = runTest {
        assertEquals(1024, request(LlmProvider.ANTHROPIC, 1024).getInt("max_tokens"))
    }
    @Test fun geminiPutsCapInsideGenerationConfig() = runTest {
        val body = request(LlmProvider.GEMINI, 1024)
        assertEquals(1024, body.getJSONObject("generationConfig").getInt("maxOutputTokens"))
        assertFalse(body.has("max_tokens"))
    }
    @Test fun chatAutomaticReservationCanUseExact2048WireLimit() = runTest {
        assertEquals(2048, request(LlmProvider.OPENAI_COMPATIBLE, 2048).getInt("max_tokens"))
    }
    @Test fun omittedCapPreservesManualCompatibleBehavior() = runTest {
        assertFalse(request(LlmProvider.OPENAI_COMPATIBLE, null).has("max_tokens"))
    }
    @Test fun approvalRevokedWhileHttpWorkerQueuedPreventsTransport() = runTest {
        val occupied = java.util.concurrent.CountDownLatch(4)
        val release = java.util.concurrent.CountDownLatch(1)
        val revoked = java.util.concurrent.atomic.AtomicBoolean(false)
        val sent = java.util.concurrent.atomic.AtomicInteger(0)
        val response = """{"choices":[{"message":{"content":"ok"}}]}"""
        val blocker = LlmChatClient { _, _, _ ->
            occupied.countDown()
            check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            response
        }
        val target = LlmChatClient { _, _, _ -> sent.incrementAndGet(); response }
        val config = ApiConnectionConfig("https://fixture.example/v1", "fixture-not-a-credential",
            LlmProvider.OPENAI_COMPATIBLE, "test-model", "code")
        val blockers = (1..4).map {
            async {
                blocker.send(config, listOf(ChatMessage("user", "hello")), agent, maxOutputTokens = 1024)
            }
        }
        testScheduler.runCurrent()
        try {
            assertTrue(occupied.await(10, java.util.concurrent.TimeUnit.SECONDS))
            val result = async {
                runCatching { target.send(config, listOf(ChatMessage("user", "hello")), agent,
                    maxOutputTokens = 1024, beforeDispatch = { check(!revoked.get()) { "Approval revoked" } }) }
            }
            testScheduler.runCurrent()
            revoked.set(true)
            release.countDown()
            blockers.forEach { it.await() }
            assertTrue(result.await().isFailure)
            assertEquals(0, sent.get())
        } finally { release.countDown() }
    }
}
