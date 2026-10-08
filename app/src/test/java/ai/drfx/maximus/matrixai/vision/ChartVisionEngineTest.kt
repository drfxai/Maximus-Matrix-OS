package ai.drfx.maximus.matrixai.vision

import org.junit.Assert.*
import org.junit.Test

class ChartVisionEngineTest {

    @Test
    fun sampleChartPresetProperties() {
        for (preset in SampleChartPreset.values()) {
            assertNotNull(preset.title)
            assertNotNull(preset.asset)
            assertNotNull(preset.timeframe)
            assertNotNull(preset.patternTheme)
            assertTrue(preset.title.isNotBlank())
        }
        assertEquals("BTC/USDT", SampleChartPreset.BTC_ASCENDING_TRIANGLE.asset)
        assertEquals("4H", SampleChartPreset.BTC_ASCENDING_TRIANGLE.timeframe)
        assertEquals("Ascending Triangle", SampleChartPreset.BTC_ASCENDING_TRIANGLE.patternTheme)
    }

    @Test
    fun trendDirectionAndStrengthProperties() {
        assertEquals("↗", TrendDirection.BULLISH.symbol)
        assertEquals("↘", TrendDirection.BEARISH.symbol)
        assertEquals("↔", TrendDirection.RANGING.symbol)

        val plan = TradePlan(
            bias = "LONG",
            entryZone = "92,500",
            stopLoss = "91,200",
            target1 = "95,000",
            target2 = "98,000",
            riskRewardRatio = "1:2.8",
            invalidationReason = "4H close below 90,800"
        )
        assertEquals("LONG", plan.bias)
        assertEquals("1:2.8", plan.riskRewardRatio)
    }

    @Test
    fun chartPatternItemConfidenceBounds() {
        val pattern = ChartPatternItem(
            name = "Ascending Triangle",
            patternType = "Continuation",
            confidencePercent = 88,
            status = "Breakout Imminent",
            implication = "Bullish expansion"
        )
        assertEquals(88, pattern.confidencePercent)
        assertTrue(pattern.confidencePercent in 0..100)
        assertEquals("Breakout Imminent", pattern.status)
    }

    @Test
    fun chartVisionAnalysisDataIntegrity() {
        val analysis = ChartVisionAnalysis(
            modelUsed = "gemini-3.8-flash",
            assetIdentifier = "BTC/USDT",
            timeframeEstimate = "4H",
            direction = TrendDirection.BULLISH,
            trendStrength = TrendStrength.STRONG,
            trendSummary = "Ascending triangle with compressing higher lows.",
            trendlines = listOf(
                ChartTrendline("Ascending Support", "Connecting swing lows", "32°", "Major"),
                ChartTrendline("Horizontal Ceiling", "Resistance tested 4 times", "0°", "Breakout Zone")
            ),
            patterns = listOf(
                ChartPatternItem("Ascending Triangle", "Continuation", 89, "Confirmed", "Bullish")
            ),
            keyLevels = listOf(
                PriceZone("92,500", "Resistance Ceiling", "Breakout trigger"),
                PriceZone("90,400", "Dynamic Support", "20 EMA")
            ),
            candleSignals = listOf(
                CandleSignal("Hammer", "Dynamic Support", "Buyers active")
            ),
            tradePlan = TradePlan("LONG", "92,500", "91,100", "95,000", "98,000", "1:2.8"),
            inputTokens = 350,
            outputTokens = 420,
            totalTokens = 770
        )

        assertEquals("BTC/USDT", analysis.assetIdentifier)
        assertEquals(2, analysis.trendlines.size)
        assertEquals(1, analysis.patterns.size)
        assertEquals(2, analysis.keyLevels.size)
        assertEquals(1, analysis.candleSignals.size)
        assertEquals(770, analysis.totalTokens)
        assertEquals("gemini-3.8-flash", analysis.modelUsed)
    }

    @Test
    fun shareableReportGeneration() {
        val analysis = ChartVisionAnalysis(
            modelUsed = "gemini-3.8-flash",
            assetIdentifier = "ETH/USDT",
            timeframeEstimate = "1H",
            direction = TrendDirection.BULLISH,
            trendStrength = TrendStrength.STRONG,
            trendSummary = "Expanding volume on retest of key demand.",
            patterns = listOf(
                ChartPatternItem("Double Bottom", "Reversal", 91, "Confirmed", "Bullish bounce")
            ),
            trendlines = listOf(
                ChartTrendline("Ascending Channel", "Support rising", "24°", "Major")
            ),
            tradePlan = TradePlan("LONG", "3,420", "3,350", "3,580", "3,700", "1:3.1")
        )

        val report = analysis.toShareableReport()
        assertNotNull(report)
        assertTrue(report.contains("CHART VISION INTELLIGENCE REPORT"))
        assertTrue(report.contains("ETH/USDT"))
        assertTrue(report.contains("Double Bottom"))
        assertTrue(report.contains("91% Confidence"))
        assertTrue(report.contains("Ascending Channel"))
        assertTrue(report.contains("3,420"))
        assertTrue(report.contains("1:3.1"))
    }
}
