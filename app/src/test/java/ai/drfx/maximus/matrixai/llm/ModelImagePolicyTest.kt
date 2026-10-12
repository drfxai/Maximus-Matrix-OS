package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelImagePolicyTest {
    private fun routerModel(
        support: ImageInputSupport = ImageInputSupport.UNKNOWN,
        verified: Boolean = true,
        capabilities: Set<ModelCapability> = setOf(ModelCapability.CHAT)
    ) = ModelDescriptor("cx/gpt-6-sol", verified = verified, capabilities = capabilities, imageInputSupport = support)

    @Test fun authenticatedUnknownRouterAllowsExplicitImageRequestWithoutClaimingVision() {
        for (provider in listOf(LlmProvider.ROUTER_9_SMART, LlmProvider.ROUTER_9_COMBO)) {
            val model = routerModel()
            assertTrue(ModelImagePolicy.canAttemptImage(provider, model))
            assertTrue(ModelImagePolicy.requiresUnverifiedRouterAttempt(provider, model))
            assertFalse(model.capabilities.contains(ModelCapability.VISION))
        }
    }

    @Test fun textOnlyAndUnverifiedRoutesAreRejected() {
        assertFalse(ModelImagePolicy.canAttemptImage(LlmProvider.ROUTER_9_COMBO, routerModel(ImageInputSupport.UNSUPPORTED)))
        assertFalse(ModelImagePolicy.canAttemptImage(LlmProvider.ROUTER_9_COMBO, routerModel(verified = false)))
        assertFalse(ModelImagePolicy.canAttemptImage(LlmProvider.OPENAI_COMPATIBLE, routerModel()))
        assertFalse(ModelImagePolicy.requiresUnverifiedRouterAttempt(LlmProvider.ROUTER_9_COMBO, routerModel(ImageInputSupport.SUPPORTED)))
    }

    @Test fun confirmedImageInputIsAllowedAndTextOnlyOverridesModelNameHeuristics() {
        assertTrue(ModelImagePolicy.canAttemptImage(LlmProvider.ROUTER_9_COMBO, routerModel(ImageInputSupport.SUPPORTED)))
        assertFalse(ModelImagePolicy.canAttemptImage(LlmProvider.ROUTER_9_COMBO,
            routerModel(ImageInputSupport.UNSUPPORTED, capabilities = setOf(ModelCapability.CHAT, ModelCapability.VISION))))
    }

    @Test fun catalogMetadataDistinguishesImagesFromTextAndUnknown() {
        assertEquals(ImageInputSupport.SUPPORTED, ModelImagePolicy.fromCatalog(JSONObject(
            """{"architecture":{"input_modalities":["text","image"]}}""")))
        assertEquals(ImageInputSupport.UNSUPPORTED, ModelImagePolicy.fromCatalog(JSONObject(
            """{"input_modalities":["text"]}""")))
        assertEquals(ImageInputSupport.SUPPORTED, ModelImagePolicy.fromCatalog(JSONObject(
            """{"capabilities":{"vision":true}}""")))
        assertEquals(ImageInputSupport.UNSUPPORTED, ModelImagePolicy.fromCatalog(JSONObject(
            """{"capabilities":{"vision":false}}""")))
        assertEquals(ImageInputSupport.UNKNOWN, ModelImagePolicy.fromCatalog(JSONObject("""{"id":"cx/gpt-6-sol"}""")))
    }
}
