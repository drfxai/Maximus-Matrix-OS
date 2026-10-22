package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class ModelCompatibilityFilterTest {
    private val models = listOf("a", "b", "c", "d").map { ModelDescriptor(it, verified = true, capabilities = setOf(ModelCapability.CHAT)) }
    private fun ev(outcome: ModelProbeOutcome) = ModelProbeEvidence(outcome, 1L)

    @Test fun untestedCatalogIsShownInFull() {
        assertEquals(listOf("a", "b", "c", "d"), ModelCompatibilityFilter.visible(models, emptyMap(), showAll = false).map { it.id })
    }

    @Test fun afterAScanOnlyAnsweringModelsAndTheSelectionRemain() {
        val evidence = mapOf("b" to ev(ModelProbeOutcome.SUCCEEDED), "c" to ev(ModelProbeOutcome.FAILED), "d" to ev(ModelProbeOutcome.SUCCEEDED))
        assertEquals(listOf("b", "d"), ModelCompatibilityFilter.visible(models, evidence, showAll = false).map { it.id })
        assertEquals(listOf("b", "d", "c"), ModelCompatibilityFilter.visible(models, evidence, showAll = false, selected = "c").map { it.id })
        assertEquals(listOf("b", "d", "a", "c"), ModelCompatibilityFilter.visible(models, evidence, showAll = true).map { it.id })
    }
}
