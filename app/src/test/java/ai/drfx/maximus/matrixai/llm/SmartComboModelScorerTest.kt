package ai.drfx.maximus.matrixai.llm

import ai.drfx.maximus.matrixai.llm.SmartComboModelScorer.Intent
import org.junit.Assert.*
import org.junit.Test

class SmartComboModelScorerTest {
    @Test fun classifiesRequestIntent() {
        assertEquals(Intent.CODE, SmartComboModelScorer.classify("Fix this:\n```kotlin\nval x = 1\n```"))
        assertEquals(Intent.CODE, SmartComboModelScorer.classify("Write a Pine Script indicator for RSI divergence"))
        assertEquals(Intent.REASONING, SmartComboModelScorer.classify("Why does this backtest overfit? Compare the trade-offs."))
        assertEquals(Intent.QUICK, SmartComboModelScorer.classify("hi"))
        assertEquals(Intent.LONG_DOCUMENT, SmartComboModelScorer.classify("summarize", 30_000))
        assertEquals(Intent.GENERAL, SmartComboModelScorer.classify("Tell me a long story about the sea and the ships that sailed on it, " +
            "with characters who remember their homes and families across many years at sea."))
    }

    @Test fun readsParameterCountsFromIds() {
        assertEquals(70.0, SmartComboModelScorer.parameterBillions("meta-llama/llama-3.3-70b-instruct:free")!!, 0.0)
        assertEquals(56.0, SmartComboModelScorer.parameterBillions("mixtral-8x7b-32768")!!, 0.0)
        assertEquals(9.0, SmartComboModelScorer.parameterBillions("gemma-2-9b-it")!!, 0.0)
        assertNull(SmartComboModelScorer.parameterBillions("gpt-4o-mini"))
    }

    @Test fun largerModelsRankHigherAndStayInsidePlannerRange() {
        val big = SmartComboModelScorer.quality("llama-3.3-70b-versatile", 131_072)
        val small = SmartComboModelScorer.quality("llama-3.2-1b-preview", 8_192)
        assertTrue("$big > $small", big > small)
        assertEquals(SmartComboModelScorer.FLOOR, small)
        assertTrue(SmartComboModelScorer.quality("llama-guard-3-8b", 8_192) <= SmartComboModelScorer.FLOOR)
        assertTrue(SmartComboModelScorer.quality("llama-3.1-405b", 128_000) <= 96)
    }

    @Test fun intentReordersOnlyAmongEligibleRoutes() {
        fun route(id: String, quality: Int, caps: Set<ModelCapability> = setOf(ModelCapability.CHAT)) = SmartComboPlanner.Route(
            id = "P::$id", provider = LlmProvider.OPENAI_COMPATIBLE, modelId = id, verified = true, healthy = true,
            capabilities = caps, contextWindowTokens = 32_000, price = SmartComboPlanner.Price(0.0, 0.0, true),
            p95TimeToFirstTokenMs = null, quality = quality)
        val routes = listOf(route("llama-3.3-70b", 82), route("qwen2.5-coder-32b", 78), route("deepseek-r1-distill-70b", 82))
        fun pick(intent: Intent) = SmartComboPlanner.choose(SmartComboPlanner.Task(setOf(ModelCapability.CHAT), 100, 100,
            nowEpochMs = 1L, intent = intent), routes).primary!!.modelId
        assertEquals("qwen2.5-coder-32b", pick(Intent.CODE))
        assertEquals("deepseek-r1-distill-70b", pick(Intent.REASONING))
        assertEquals("llama-3.3-70b", pick(Intent.QUICK))
        // A capability the route lacks is still a hard rejection, whatever the intent.
        val tools = SmartComboPlanner.choose(SmartComboPlanner.Task(setOf(ModelCapability.CHAT, ModelCapability.TOOLS), 100, 100,
            nowEpochMs = 1L, intent = Intent.CODE), routes)
        assertNull(tools.primary)
    }

    @Test fun verifiedLongContextGrantsCapability() {
        assertEquals(setOf(ModelCapability.LONG_CONTEXT), SmartComboModelScorer.contextCapabilities(128_000))
        assertTrue(SmartComboModelScorer.contextCapabilities(32_000).isEmpty())
        assertTrue(SmartComboModelScorer.contextCapabilities(null).isEmpty())
    }
}
