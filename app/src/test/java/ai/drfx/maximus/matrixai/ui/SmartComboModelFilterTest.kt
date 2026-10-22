package ai.drfx.maximus.matrixai.ui

import ai.drfx.maximus.matrixai.llm.ImageInputSupport
import ai.drfx.maximus.matrixai.llm.ModelCapability
import ai.drfx.maximus.matrixai.llm.ModelDescriptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartComboModelFilterTest {
    @Test fun nameAloneDoesNotClaimAudioCodeOrVisionSupport() {
        val named = ModelDescriptor("audio-code-vision", capabilities = setOf(ModelCapability.CHAT))
        assertFalse(smartComboCategoryMatch(named, "Audio"))
        assertFalse(smartComboCategoryMatch(named, "Code"))
        assertFalse(smartComboCategoryMatch(named, "Vision"))
    }

    @Test fun declaredImageInputAndCapabilitiesDriveCategories() {
        val capable = ModelDescriptor("model", capabilities = setOf(ModelCapability.CHAT, ModelCapability.TOOLS),
            imageInputSupport = ImageInputSupport.SUPPORTED)
        assertTrue(smartComboCategoryMatch(capable, "Code"))
        assertTrue(smartComboCategoryMatch(capable, "Vision"))
        assertFalse(smartComboCategoryMatch(capable, "Audio"))
    }

    @Test fun searchIncludesProviderAndModelNames() {
        val model = ModelDescriptor("m-1", displayName = "General Model")
        assertTrue(smartComboModelSearchMatch(model, "Google Gemini", "gemini"))
        assertTrue(smartComboModelSearchMatch(model, "Google Gemini", "general"))
        assertFalse(smartComboModelSearchMatch(model, "Google Gemini", "anthropic"))
    }
}
