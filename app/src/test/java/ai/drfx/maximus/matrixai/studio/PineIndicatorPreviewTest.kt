package ai.drfx.maximus.matrixai.studio

import org.junit.Assert.*
import org.junit.Test

class PineIndicatorPreviewTest {
    private fun candles(vararg closes: Double) = closes.mapIndexed { index, close ->
        PineCandle(index + 1L, close, close, close, close, 1.0)
    }
    @Test fun literalSmaUsesActualClosesWithWarmup() {
        val result = PineIndicatorPreview.evaluate("""
            //@version=6
            indicator("Average", overlay=true)
            plot(ta.sma(close, 3), color=color.orange)
        """.trimIndent(), candles(1.0, 2.0, 3.0, 6.0)) as PineIndicatorPreview.Result.Supported
        assertEquals(listOf(null, null, 2.0, 11.0 / 3), result.lines.single().values)
    }
    @Test fun emaInputAndAssignmentHaveExplicitFirstLoadedCloseSeed() {
        val result = PineIndicatorPreview.evaluate("""
            indicator("EMA", overlay=true)
            length = input.int(3, "Length")
            average = ta.ema(close, length)
            plot(average, title="EMA", linewidth=2)
        """.trimIndent(), candles(2.0, 4.0, 8.0)) as PineIndicatorPreview.Result.Supported
        assertEquals(listOf(2.0, 3.0, 5.5), result.lines.single().values)
    }
    @Test fun rejectsUnsupportedCodeRatherThanRenderingPartialMisleadingResult() {
        listOf(
            "plot(ta.rsi(close, 14))",
            "plot(ta.sma(open, 3))",
            "plot(ta.sma(close, 0))",
            "plot(ta.sma(close, 501))",
            "plot(ta.sma(close, 3))\nstrategy.entry(\"buy\", strategy.long)",
            "plot(ta.sma(close, 3))\nif close > open\n    plot(close)",
            "plot(ta.sma(close, 3), offset=4)",
            "x = close * 2\nplot(ta.sma(close, 3))"
        ).forEach { code ->
            assertTrue(code, PineIndicatorPreview.evaluate("indicator(\"X\", overlay=true)\n$code", candles(1.0)) is PineIndicatorPreview.Result.Unsupported)
        }
    }
    @Test fun limitsPlotCountAndDataSize() {
        val header = "indicator(\"X\", overlay=true)\n"
        assertTrue(PineIndicatorPreview.evaluate(header + List(5) { "plot(ta.ema(close, 2))" }.joinToString("\n"), candles(1.0)) is PineIndicatorPreview.Result.Unsupported)
        assertTrue(PineIndicatorPreview.evaluate(header + "plot(ta.sma(close, 3))", List(201) { candles(1.0).single() }) is PineIndicatorPreview.Result.Unsupported)
        assertTrue(PineIndicatorPreview.evaluate(" ".repeat(100_001), emptyList()) is PineIndicatorPreview.Result.Unsupported)
    }
    @Test fun multipleLinesPreserveSameCandleIdentity() {
        val result = PineIndicatorPreview.evaluate("""
            indicator("Both", overlay=true)
            plot(ta.sma(close, 2))
            plot(ta.ema(close, 2))
        """.trimIndent(), candles(3.0, 6.0)) as PineIndicatorPreview.Result.Supported
        assertEquals(2, result.lines.size)
        assertEquals(listOf(null, 4.5), result.lines[0].values)
        assertEquals(listOf(3.0, 5.0), result.lines[1].values)
    }
    @Test fun rejectsExtremeNonfiniteAndOutOfRangePricesWithoutChangingEmptySourceValidation() {
        for (kind in listOf("sma", "ema")) {
            val source = "indicator(\"X\", overlay=true)\nplot(ta.$kind(close, 2))"
            assertTrue(PineIndicatorPreview.evaluate(source, emptyList()) is PineIndicatorPreview.Result.Supported)
            for (price in listOf(Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN, -1.0, 0.0, PineIndicatorPreview.MAX_PRICE + 1)) {
                assertTrue("$kind $price", PineIndicatorPreview.evaluate(source, candles(price, price)) is PineIndicatorPreview.Result.Unsupported)
            }
            val valid = PineIndicatorPreview.evaluate(source, candles(PineIndicatorPreview.MAX_PRICE, PineIndicatorPreview.MAX_PRICE)) as PineIndicatorPreview.Result.Supported
            assertTrue(valid.lines.single().values.filterNotNull().all { it.isFinite() })
        }
    }
    @Test fun rejectsReservedInputAndAverageNames() {
        for (name in listOf("close", "open", "high", "low", "volume", "time", "bar_index", "hl2", "ta", "input", "color", "plot", "indicator", "na")) {
            for (assignment in listOf("$name = input.int(3)", "$name = ta.sma(close, 3)")) {
                val source = "indicator(\"X\", overlay=true)\n$assignment\nplot(ta.sma(close, 2))"
                assertTrue(assignment, PineIndicatorPreview.evaluate(source, candles(1.0, 2.0)) is PineIndicatorPreview.Result.Unsupported)
            }
        }
    }

    @Test fun builtInLiquiditySweepPreviewsOnlyConfirmedPriorHighMarkers() {
        val source = """
            //@version=6
            indicator("Liquidity sweep", overlay=true)
            length = input.int(2, "Lookback", minval=2)
            level = ta.highest(high[1], length)
            sweep = high > level and close < level
            plotshape(sweep, title="Sweep", style=shape.triangledown, location=location.abovebar, color=color.orange)
            alertcondition(sweep, title="Liquidity sweep", message="Potential liquidity sweep detected")
        """.trimIndent()
        val bars = listOf(
            PineCandle(1, 10.0, 12.0, 9.0, 11.0, 100.0),
            PineCandle(2, 11.0, 13.0, 10.0, 12.0, 100.0),
            PineCandle(3, 12.0, 16.0, 11.0, 12.0, 100.0),
            PineCandle(4, 13.0, 14.0, 12.0, 13.0, 100.0)
        )
        val preview = PineIndicatorPreview.evaluate(source, bars) as PineIndicatorPreview.Result.Supported
        assertTrue(preview.lines.single().markersOnly)
        assertEquals(listOf(null, null, 16.0, null), preview.lines.single().values)
        assertTrue(PineIndicatorPreview.evaluate(source + "\nstrategy.entry(\"buy\", strategy.long)", bars)
            is PineIndicatorPreview.Result.Unsupported)
        assertTrue(PineIndicatorPreview.evaluate(source.replace("high[1]", "high"), bars)
            is PineIndicatorPreview.Result.Unsupported)
    }

}
