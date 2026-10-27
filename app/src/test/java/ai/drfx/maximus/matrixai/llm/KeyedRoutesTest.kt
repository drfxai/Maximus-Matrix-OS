package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class KeyedRoutesTest {
    private fun model(id: String, vararg extra: ModelCapability) =
        ModelDescriptor(id, verified = true, capabilities = setOf(ModelCapability.CHAT) + extra)

    private val nvidia = KeyedRoutes.rank(LlmProvider.NVIDIA, LlmProvider.NVIDIA.defaultBaseUrl, listOf(
        model("meta/llama-3.1-8b-instruct"),
        model("meta/llama-3.3-70b-instruct"),
        model("nvidia/broken-405b"),
        model("minimax/minimax-m3")
    ), preferredModel = "minimax/minimax-m3", probes = mapOf(
        "nvidia/broken-405b" to ModelProbeEvidence(ModelProbeOutcome.FAILED, 1L),
        "meta/llama-3.1-8b-instruct" to ModelProbeEvidence(ModelProbeOutcome.SUCCEEDED, 1L)
    ))

    @Test fun usersChosenModelComesFirstThenTestedModelsAndFailedOnesAreDropped() {
        assertEquals(listOf("minimax/minimax-m3", "meta/llama-3.1-8b-instruct", "meta/llama-3.3-70b-instruct"),
            nvidia.map { it.modelId })
        assertEquals(listOf(0, 1, 2), nvidia.map { it.rank })
        assertTrue(nvidia.all { it.id.startsWith("key:NVIDIA::") })
    }

    @Test fun unverifiedOrNonChatModelsNeverBecomeRoutes() {
        val routes = KeyedRoutes.rank(LlmProvider.GEMINI, LlmProvider.GEMINI.defaultBaseUrl, listOf(
            ModelDescriptor("guess", verified = false, capabilities = setOf(ModelCapability.CHAT)),
            ModelDescriptor("embed-only", verified = true, capabilities = emptySet())
        ), preferredModel = "guess")
        assertTrue(routes.isEmpty())
    }

    @Test fun nextSkipsTriedRoutesAndLeavesAProviderThatFailedAsAWhole() {
        val gemini = KeyedRoutes.rank(LlmProvider.GEMINI, LlmProvider.GEMINI.defaultBaseUrl,
            listOf(model("gemini-2.5-flash")), preferredModel = "gemini-2.5-flash")
        val all = nvidia + gemini
        // Each provider's own first choice is tried before its other models.
        assertEquals(0, KeyedRoutes.next(all, emptySet())?.rank)
        val first = nvidia.first()
        val afterOutage = KeyedRoutes.next(all, setOf(first.id), failedProvider = LlmProvider.NVIDIA, leaveProvider = true)
        assertEquals(LlmProvider.GEMINI, afterOutage?.provider)
        val sameProvider = KeyedRoutes.next(all, setOf(first.id), failedProvider = LlmProvider.NVIDIA, leaveProvider = false)
        assertEquals(LlmProvider.NVIDIA, sameProvider?.provider)
        assertNull(KeyedRoutes.next(all, all.map { it.id }.toSet()))
    }

    @Test fun nextRespectsCapabilitiesContextAndCooldowns() {
        val vision = KeyedRoutes.rank(LlmProvider.OPENAI, LlmProvider.OPENAI.defaultBaseUrl,
            listOf(model("gpt-4o", ModelCapability.VISION)), preferredModel = "gpt-4o")
        val all = nvidia + vision
        assertEquals("gpt-4o", KeyedRoutes.next(all, emptySet(), setOf(ModelCapability.VISION))?.modelId)
        assertNull(KeyedRoutes.next(all, emptySet(), requiredContextTokens = Int.MAX_VALUE))
        val cooled = KeyedRoutes.next(all, emptySet(), coolingDown = { it == nvidia.first().id })
        assertNotEquals(nvidia.first().id, cooled?.id)
    }

    @Test fun scanDiagnosisMentionsOwnKeysWithoutCallingThemFree() {
        val result = SmartComboFreeCatalog.ScanResult(emptyList(), emptyList(), 2, nvidia)
        assertTrue(result.diagnosis.contains("3 models on your own keys"))
        assertTrue(result.diagnosis.contains("charges may apply"))
        assertFalse(result.diagnosis.contains("verified zero-price catalog routes"))
    }
}
