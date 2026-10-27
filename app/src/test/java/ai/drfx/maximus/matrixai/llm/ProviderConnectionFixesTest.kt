package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderConnectionFixesTest {
    @Test fun pastedModelsUrlIsReducedToTheBaseEndpoint() {
        val base = ApiDiscoveryEngine.normalizeBaseUrl("https://api.commandcode.ai/provider/v1/models")
        assertEquals("https://api.commandcode.ai/provider/v1", base)
        assertEquals("https://api.commandcode.ai/provider/v1", ApiDiscoveryEngine.apiRoot(base))
        assertEquals("https://api.example.test/v1",
            ApiDiscoveryEngine.normalizeBaseUrl("api.example.test/v1/chat/completions/"))
        assertEquals("https://api.anthropic.com/v1", ApiDiscoveryEngine.normalizeBaseUrl("https://api.anthropic.com/v1/messages"))
        assertEquals("https://host.test", ApiDiscoveryEngine.normalizeBaseUrl("https://host.test/models"))
    }

    @Test fun apiRootKeepsAnyVersionSegmentAndOnlyAddsV1WhenMissing() {
        assertEquals("https://open.bigmodel.cn/api/paas/v4", ApiDiscoveryEngine.apiRoot("https://open.bigmodel.cn/api/paas/v4"))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai",
            ApiDiscoveryEngine.apiRoot("https://generativelanguage.googleapis.com/v1beta/openai"))
        assertEquals("https://api.groq.com/openai/v1", ApiDiscoveryEngine.apiRoot("https://api.groq.com/openai/v1/"))
        assertEquals("https://api.deepseek.com/v1", ApiDiscoveryEngine.apiRoot("https://api.deepseek.com"))
        assertEquals("https://integrate.api.nvidia.com/v1", ApiDiscoveryEngine.apiRoot(LlmProvider.NVIDIA.defaultBaseUrl))
    }

    @Test fun providerErrorTextIsShownButSecretsAreRedacted() {
        val error = ProviderRequestException.fromHttp(403,
            """{"error":{"message":"Model gemini-3.8-flash is not included in your plan (key sk-abcdefghijklmnop)"}}""")
        assertEquals(ProviderFailure.FORBIDDEN, error.failure)
        assertTrue(error.message!!.contains("not included in your plan"))
        assertFalse(error.message!!.contains("sk-abcdefghijklmnop"))
        assertEquals("Upstream says no", ProviderErrorDetail.extract("""{"detail":"Upstream says no"}"""))
        assertNull(ProviderErrorDetail.extract("<html>blocked</html>"))
        assertNull(ProviderRequestException.fromHttp(404).providerDetail)
        assertEquals(ProviderFailure.RATE_LIMIT_OR_QUOTA, ProviderRequestException.fromHttp(402).failure)
    }

    @Test fun nullContentIsNeverTreatedAsTheWordNull() {
        val message = JSONObject().put("content", JSONObject.NULL).put("reasoning_content", "thinking")
        assertEquals("", ReplyText.content(message))
        assertEquals("thinking", ReplyText.reasoning(message))
        assertTrue(ReplyText.isLengthStop("length"))
        assertTrue(ReplyText.isLengthStop("MAX_TOKENS"))
        assertFalse(ReplyText.isLengthStop("stop"))
    }

    @Test fun reasoningOnlyStreamEventsKeepTheRouteAliveWithoutVisibleText() {
        val deltas = mutableListOf<String>()
        val parser = OpenAiStreamParser { deltas += it }
        parser.line("data: {\"choices\":[{\"delta\":{\"content\":null,\"reasoning_content\":\"hmm\"}}]}")
        parser.line("data: {\"choices\":[{\"delta\":{\"content\":null},\"finish_reason\":\"length\"}]}")
        assertEquals(listOf(""), deltas)
        assertEquals("", parser.text())
        assertTrue(parser.completed)
        assertTrue(parser.stoppedAtLimit)
    }

    @Test fun catalogStaysValidForALongConversation() {
        var now = 1_000L
        val model = ModelDescriptor("chat", verified = true, capabilities = setOf(ModelCapability.CHAT))
        val session = ApiCatalogSession { now }
        val revision = session.invalidate()
        session.bind(revision, LlmProvider.NVIDIA, LlmProvider.NVIDIA.defaultBaseUrl, "key", listOf(model))
        now += 45 * 60_000L
        assertTrue(session.permits(revision, LlmProvider.NVIDIA, LlmProvider.NVIDIA.defaultBaseUrl, "key", "chat"))
        assertFalse(session.permits(revision, LlmProvider.NVIDIA, LlmProvider.NVIDIA.defaultBaseUrl, "rotated", "chat"))
    }

    @Test fun deniedKeysAndOutagesMoveToAnotherProvider() {
        val denied = ProviderRequestException.fromHttp(403)
        assertEquals(SmartComboFailover.Reason.DENIED, SmartComboFailover.classify(denied, false))
        assertFalse(SmartComboFailover.affectsWholeProvider(SmartComboFailover.Reason.DENIED, denied))
        val badKey = ProviderRequestException.fromHttp(401)
        assertTrue(SmartComboFailover.affectsWholeProvider(SmartComboFailover.classify(badKey, false), badKey))
        assertTrue(SmartComboFailover.affectsWholeProvider(SmartComboFailover.Reason.OUTAGE, null))
        assertEquals("Switched to llama-3.3-70b (NVIDIA NIM): gemini-3.8-flash refused access with this key.",
            SmartComboFailover.notice(LlmProvider.OPENAI_COMPATIBLE, "gemini-3.8-flash", LlmProvider.NVIDIA,
                "meta/llama-3.3-70b", SmartComboFailover.Reason.DENIED))
    }
}
