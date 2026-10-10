package ai.drfx.maximus.matrixai.vision

import ai.drfx.maximus.matrixai.llm.ChatUsage
import org.junit.Assert.*
import org.junit.Test

class ChartVisionOutputParserTest {
    private val uncertain = """{"asset":"Unknown","timeframe":"Unknown","direction":"UNKNOWN","strength":"UNKNOWN","trendSummary":"Unreadable price scale","priceScaleReadable":false,"keyLevels":[],"trendlines":[],"patterns":[],"candleSignals":[],"tradePlan":null,"report":"Insufficient evidence"}"""
    private fun parse(text: String) = ChartVisionOutputParser.parse(text, "Selected provider / actual-model", ChatUsage(1, 2, 3, true), 12)

    @Test fun insufficientEvidenceDoesNotBecomeBullishSignal() {
        val result = parse(uncertain)
        assertEquals(TrendDirection.UNKNOWN, result.direction)
        assertEquals(TrendStrength.UNKNOWN, result.trendStrength)
        assertEquals("WAIT", result.tradePlan.bias)
        assertTrue(result.keyLevels.isEmpty())
        assertTrue(result.usageEstimated)
        assertNull(result.imageBase64)
        assertEquals("Selected provider / actual-model", result.modelUsed)
    }
    @Test(expected = IllegalArgumentException::class) fun malformedResponseFailsInsteadOfFabricating() { parse("Analysis: bullish") }
    @Test(expected = IllegalArgumentException::class) fun missingStructureFails() { parse("{}") }
    @Test(expected = IllegalArgumentException::class) fun invalidDirectionFails() { parse(uncertain.replace("\"direction\":\"UNKNOWN\"", "\"direction\":\"UP\"")) }
    @Test(expected = IllegalArgumentException::class) fun unreadablePriceScaleRejectsPrices() {
        parse(uncertain.replace("\"keyLevels\":[]", "\"keyLevels\":[{\"level\":\"95000\",\"type\":\"Resistance\"}]"))
    }
    @Test fun uncalibratedConfidenceAndAnglesAreDiscarded() {
        val result = parse(uncertain.replace("\"patterns\":[]", "\"patterns\":[{\"name\":\"Possible triangle\",\"patternType\":\"Uncertain\",\"status\":\"Hypothesis\",\"implication\":\"Requires confirmation\",\"confidencePercent\":99}]"))
        assertEquals(0, result.patterns.single().confidencePercent)
        assertFalse(result.toShareableReport().contains("99%"))
    }
    @Test(expected = IllegalArgumentException::class) fun wrongTradePlanTypeFails() {
        parse(uncertain.replace("\"tradePlan\":null", "\"tradePlan\":\"buy now\""))
    }
    @Test(expected = IllegalArgumentException::class) fun wrongLevelTypeFails() {
        parse(uncertain.replace("\"priceScaleReadable\":false", "\"priceScaleReadable\":true")
            .replace("\"keyLevels\":[]", "\"keyLevels\":[{\"level\":95000,\"type\":\"Resistance\"}]"))
    }
    @Test fun defaultDataModelCannotInventAnalysis() {
        val empty = ChartVisionAnalysis()
        assertEquals(TrendDirection.UNKNOWN, empty.direction)
        assertEquals("WAIT", empty.tradePlan.bias)
        assertEquals("Unavailable", empty.tradePlan.riskRewardRatio)
    }
}
