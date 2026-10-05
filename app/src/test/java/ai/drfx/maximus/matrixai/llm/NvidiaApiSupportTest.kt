package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NvidiaApiSupportTest {
    @Test
    fun nvidiaKeySelectsNvidiaCatalogWhenDefaultOpenAiUrlIsPresent() {
        val base = ApiDiscoveryEngine.resolveBaseUrl("https://api.openai.com", "nvapi-example-key")
        assertEquals(ApiDiscoveryEngine.NVIDIA_BASE_URL, base)
    }

    @Test
    fun nvidiaReasoningModelProfilesChatAndReasoning() {
        val capabilities = ModelCapabilityResolver.resolve(LlmProvider.NVIDIA, "openai/gpt-oss-120b")
        assertTrue(ModelCapability.CHAT in capabilities)
        assertTrue(ModelCapability.REASONING in capabilities)
        assertTrue(ModelCapability.LONG_CONTEXT in capabilities)
    }
}
