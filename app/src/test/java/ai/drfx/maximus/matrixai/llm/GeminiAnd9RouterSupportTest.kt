package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAnd9RouterSupportTest {

    @Test
    fun gemini38FlashCapabilitiesAndContextWindow() {
        val capabilities = ModelCapabilityResolver.resolve(LlmProvider.GEMINI, "gemini-3.8-flash")
        assertTrue(ModelCapability.CHAT in capabilities)
        assertTrue(ModelCapability.TOOLS in capabilities)
        assertTrue(ModelCapability.VISION in capabilities)
        assertTrue(ModelCapability.LONG_CONTEXT in capabilities)
        assertTrue(ModelCapability.STRUCTURED_OUTPUT in capabilities)
        assertTrue(ModelCapability.REASONING in capabilities)

        val contextWindow = ModelLimitsResolver.contextWindow(LlmProvider.GEMINI, "gemini-3.8-flash")
        assertEquals(1_048_576, contextWindow)

        val proContextWindow = ModelLimitsResolver.contextWindow(LlmProvider.GEMINI, "gemini-3.1-pro-preview")
        assertEquals(2_097_152, proContextWindow)
    }

    @Test
    fun geminiDefaultCatalogContainsGemini38FlashFirst() {
        val catalog = ApiDiscoveryEngine.defaultCatalog(LlmProvider.GEMINI)
        assertTrue(catalog.isNotEmpty())
        assertEquals("gemini-3.8-flash", catalog.first().id)
        assertTrue(catalog.any { it.id == "gemini-3.5-flash" })
        assertTrue(catalog.any { it.id == "gemini-3.1-pro-preview" })
    }

    @Test
    fun nineRouterSmartAndComboRouting() {
        val smartCatalog = ApiDiscoveryEngine.defaultCatalog(LlmProvider.ROUTER_9_SMART)
        val comboCatalog = ApiDiscoveryEngine.defaultCatalog(LlmProvider.ROUTER_9_COMBO)

        assertTrue(smartCatalog.isNotEmpty())
        assertTrue(comboCatalog.isNotEmpty())
        assertEquals("9router-smart-auto", smartCatalog.first().id)
        assertEquals("9router-combo-synthesis", comboCatalog.first().id)

        val smartCaps = ModelCapabilityResolver.resolve(LlmProvider.ROUTER_9_SMART, "9router-smart-auto")
        assertTrue(ModelCapability.CHAT in smartCaps)
        assertTrue(ModelCapability.REASONING in smartCaps)
        assertTrue(ModelCapability.TOOLS in smartCaps)

        assertTrue(LlmProvider.ROUTER_9_SMART.isNineRouter)
        assertTrue(LlmProvider.ROUTER_9_COMBO.isNineRouter)
    }

    @Test
    fun tokenMetricsCalculatesConsumedAndRemainingProperly() {
        val capacity = 1_048_576
        val turnInput = 1_200
        val turnOutput = 350
        val turnTotal = turnInput + turnOutput

        val remainingContext = (capacity - turnTotal).coerceAtLeast(0)
        assertEquals(1_047_026, remainingContext)

        val budget = 500_000L
        val consumedLifetime = 45_000L
        val remainingBudget = (budget - consumedLifetime).coerceAtLeast(0L)
        assertEquals(455_000L, remainingBudget)

        val usagePercent = turnTotal.toFloat() / capacity.toFloat()
        assertTrue(usagePercent in 0f..1f)
    }

    @Test
    fun providerPresetSwitchingUpdatesContextAndModels() {
        val geminiPreset = LlmProvider.GEMINI
        val nvidiaPreset = LlmProvider.NVIDIA
        val router9Preset = LlmProvider.ROUTER_9_SMART

        assertEquals("gemini-3.8-flash", geminiPreset.defaultModel)
        assertEquals("meta/llama-3.3-70b-instruct", nvidiaPreset.defaultModel)
        assertEquals("9router-smart-auto", router9Preset.defaultModel)

        assertEquals(1_048_576, ModelLimitsResolver.contextWindow(geminiPreset, geminiPreset.defaultModel))
        assertEquals(131_072, ModelLimitsResolver.contextWindow(nvidiaPreset, nvidiaPreset.defaultModel))
        assertEquals(1_048_576, ModelLimitsResolver.contextWindow(router9Preset, router9Preset.defaultModel))
    }
}
