package ai.drfx.maximus.matrixai.studio

import ai.drfx.maximus.matrixai.llm.LlmProvider
import ai.drfx.maximus.matrixai.llm.OutputTokenLimit
import ai.drfx.maximus.matrixai.llm.SmartComboModelScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeSourceTest {
    private val bigPine: String = buildString {
        append("//@version=6\nindicator(\"Big\", overlay=true)\n")
        for (i in 1..12_000) append("v$i = ta.sma(close, ${i % 50 + 2})\n")
        append("plot(v12000)")
    }

    @Test fun twelveThousandLineIndicatorFitsEveryPineLimit() {
        assertTrue(bigPine.length < LargeSource.MAX_CHARS)
        assertEquals(bigPine, PineSourceImport.validate(bigPine))
        assertTrue(PineScriptEngine.check(bigPine).none { it.message.contains("exceeds") })
    }

    @Test fun lineIndexMapsOffsetsBothWays() {
        val index = LargeSource.LineIndex("a\nbb\n\nccc")
        assertEquals(4, index.lineCount)
        assertEquals(1, index.lineOf(0))
        assertEquals(2, index.lineOf(2))
        assertEquals(4, index.lineOf(9))
        assertEquals(5, index.startOf(3))
        assertEquals("ccc", index.line(4))
        assertEquals("", index.line(3))
    }

    @Test fun findWrapsInBothDirections() {
        val text = "alpha beta alpha gamma"
        assertEquals(11, LargeSource.find(text, "ALPHA", 1))
        assertEquals(0, LargeSource.find(text, "alpha", 12))
        assertEquals(0, LargeSource.findPrevious(text, "alpha", 11))
        assertEquals(11, LargeSource.findPrevious(text, "alpha", 0))
        assertEquals(-1, LargeSource.find(text, "delta", 0))
        assertEquals(2, LargeSource.countMatches(text, "alpha"))
    }

    @Test fun smallSourcesGoToTheAiWhole() {
        val context = LargeSource.promptContext("plot(close)", "change colour")
        assertTrue(context.complete)
        assertEquals("plot(close)", context.text)
    }

    @Test fun largeSourcesSendOutlineAndRelevantWindows() {
        val context = LargeSource.promptContext(bigPine, "Change v9000 to use ema", budgetChars = 20_000)
        assertFalse(context.complete)
        assertTrue(context.text.contains("2: indicator(\"Big\""))
        assertTrue(context.text.contains("9002|v9000 = ta.sma"))
        assertTrue(context.text.contains("omitted"))
        assertTrue(context.text.length < 40_000)
    }

    @Test fun revisionBudgetKeepsNewestSnapshots() {
        assertEquals(30, LargeSource.revisionsWithinBudget(List(40) { 10 }))
        assertEquals(2, LargeSource.revisionsWithinBudget(listOf(900_000, 900_000, 900_000)))
        assertEquals(1, LargeSource.revisionsWithinBudget(listOf(5_000_000)))
    }

    @Test fun fragmentCannotReplaceCompleteDraft() {
        val fragment = "v9000 = ta.ema(close, 20)"
        assertTrue(runCatching { PineSourceImport.validateReplacement(bigPine, fragment) }.isFailure)
        assertEquals(bigPine, PineSourceImport.validateReplacement(bigPine, bigPine))
        assertEquals(fragment, PineSourceImport.validateReplacement("plot(close)", fragment))
    }

    @Test fun codeRequestsReserveRoomForWholeFiles() {
        assertEquals(listOf(16_384, 8_192, 2_048), SmartComboModelScorer.outputBudgets(SmartComboModelScorer.Intent.CODE))
        assertEquals(listOf(2_048), SmartComboModelScorer.outputBudgets(SmartComboModelScorer.Intent.QUICK))
        assertTrue(SmartComboModelScorer.outputBudgets(SmartComboModelScorer.Intent.CODE).all { it <= SmartComboModelScorer.MAX_OUTPUT_TOKENS })
    }

    @Test fun classifierSeesCodeAtTheEndOfLongPastes() {
        val prompt = "x".repeat(20_000) + "\nPlease refactor this kotlin function"
        assertEquals(SmartComboModelScorer.Intent.CODE, SmartComboModelScorer.classify(prompt))
    }

    @Test fun anthropicCodeCapSkipsLegacyModels() {
        val code = SmartComboModelScorer.Intent.CODE
        assertEquals(8_192, OutputTokenLimit.directCap(LlmProvider.ANTHROPIC, "claude-sonnet-4-5", code))
        assertNull(OutputTokenLimit.directCap(LlmProvider.ANTHROPIC, "claude-3-haiku-20240307", code))
        assertNull(OutputTokenLimit.directCap(LlmProvider.OPENAI, "gpt-4.1", code))
        assertNull(OutputTokenLimit.directCap(LlmProvider.ANTHROPIC, "claude-sonnet-4-5", SmartComboModelScorer.Intent.QUICK))
    }
}
