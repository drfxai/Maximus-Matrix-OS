package ai.drfx.maximus.matrixai.studio

import org.junit.Assert.*
import org.junit.Test

class PineSourceImportTest {
    @Test fun sourceRemainsExactAndTitleIsBounded() {
        val source = "//@version=6\nindicator(\"My RSI\", overlay=false)\nplot(close)\n"
        assertEquals(source, PineSourceImport.validate(source))
        assertEquals("My RSI", PineSourceImport.title(source))
    }
    @Test fun unnamedDraftHasSafeFallback() {
        assertEquals("Chat indicator", PineSourceImport.title("plot(close)"))
    }
    @Test fun oversizedAndEmptySourcesAreRejected() {
        listOf("", "  ", "a".repeat(100_001), "plot(close)\u0000").forEach {
            assertTrue(runCatching { PineSourceImport.validate(it) }.isFailure)
        }
    }

    @Test fun detectsPineRegardlessOfFenceTag() {
        assertTrue(PineSourceDetector.looksLikePine("//@version=5\nindicator(\"X\")\nplot(close)"))
        assertTrue(PineSourceDetector.looksLikePine("strategy(\"S\", overlay=true)\nif ta.crossover(a, b)\n    strategy.entry(\"L\", strategy.long)"))
        assertFalse(PineSourceDetector.looksLikePine("function plot(x) { return x }\nconst strategy = 1"))
        assertFalse(PineSourceDetector.looksLikePine("Use an indicator (like RSI) to plot momentum."))
    }
    @Test fun splitsUnfencedScriptFromSurroundingProse() {
        val reply = """
            Here is your indicator:

            //@version=6
            indicator("Trend", overlay=true)
            fast = ta.ema(close, 9)
            plot(fast)

            How it works:
            - The EMA follows price.
        """.trimIndent()
        val parts = PineSourceDetector.splitProse(reply)
        assertEquals(listOf(false, true, false), parts.map { it.second })
        assertEquals("//@version=6\nindicator(\"Trend\", overlay=true)\nfast = ta.ema(close, 9)\nplot(fast)", parts[1].first)
        assertTrue(parts[2].first.startsWith("How it works"))
    }
    @Test fun proseWithoutScriptIsUntouched() {
        val text = "Strategy ideas for BTC:\n1. Trend following"
        assertEquals(listOf(text to false), PineSourceDetector.splitProse(text))
    }
}
