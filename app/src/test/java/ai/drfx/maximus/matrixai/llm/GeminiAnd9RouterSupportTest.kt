package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class GeminiAnd9RouterSupportTest {
    @Test fun suggestionsAreNeverVerifiedModels() {
        assertTrue(ApiDiscoveryEngine.defaultCatalog(LlmProvider.GEMINI).all { !it.verified })
        assertFalse(ApiDiscoveryEngine.defaultCatalog(LlmProvider.GEMINI).any { it.id == "gemini-3.8-flash" })
        assertTrue(ApiDiscoveryEngine.defaultCatalog(LlmProvider.ROUTER_9_SMART).isEmpty())
        assertTrue(ApiDiscoveryEngine.defaultCatalog(LlmProvider.ROUTER_9_COMBO).isEmpty())
    }
    @Test fun routerRequiresUserDeploymentAndModel() {
        assertEquals("", LlmProvider.ROUTER_9_SMART.defaultBaseUrl)
        assertEquals("", LlmProvider.ROUTER_9_SMART.defaultModel)
        assertFalse(ModelCapabilityResolver.resolve(LlmProvider.ROUTER_9_SMART, "custom-combo").contains(ModelCapability.VISION))
    }
    @Test fun unknownGeminiDoesNotAcquireVisionFromProviderAlone() {
        assertFalse(ModelCapabilityResolver.resolve(LlmProvider.GEMINI, "unknown-model").contains(ModelCapability.VISION))
    }
}
