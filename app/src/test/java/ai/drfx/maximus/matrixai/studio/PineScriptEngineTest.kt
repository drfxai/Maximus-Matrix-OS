package ai.drfx.maximus.matrixai.studio

import org.junit.Assert.*
import org.junit.Test

class PineScriptEngineTest {
    private fun bars(vararg closes: Double) = closes.mapIndexed { index, close ->
        PineCandle(86_400L * (index + 1), close, close + 1, close - 1, close, 10.0)
    }
    private fun run(source: String, vararg closes: Double) = PineScriptEngine.run(source.trimIndent(), bars(*closes))
    private fun PineScriptEngine.Output.plot(title: String) = plots.first { it.title == title }

    @Test fun varPersistsAcrossBarsWhilePlainDeclarationsReset() {
        val out = run("""
            indicator("Counter")
            var count = 0
            count := count + 1
            local = 0
            local := local + 1
            plot(count, "count")
            plot(local, "local")
        """, 1.0, 2.0, 3.0)
        assertEquals(listOf(1.0, 2.0, 3.0), out.plot("count").values)
        assertEquals(listOf(1.0, 1.0, 1.0), out.plot("local").values)
    }

    @Test fun historyReferencesIfElseAndTernary() {
        val out = run("""
            indicator("Flow", overlay=false)
            diff = close - close[1]
            state = 0.0
            if diff > 0
                state := 1
            else if diff < 0
                state := -1
            else
                state := 0
            plot(state, "state")
            plot(na(close[1]) ? -9 : close[1], "prev")
        """, 1.0, 3.0, 2.0, 2.0)
        assertEquals(listOf(0.0, 1.0, -1.0, 0.0), out.plot("state").values)
        assertEquals(listOf(-9.0, 1.0, 3.0, 2.0), out.plot("prev").values)
    }

    @Test fun userFunctionsKeepSeparateSeriesStatePerCallSite() {
        val out = run("""
            indicator("Fn")
            avg(src, len) => ta.sma(src, len)
            plot(avg(close, 2), "a")
            plot(avg(close * 2, 2), "b")
        """, 2.0, 4.0, 6.0)
        assertEquals(listOf(null, 3.0, 5.0), out.plot("a").values)
        assertEquals(listOf(null, 6.0, 10.0), out.plot("b").values)
    }

    @Test fun multiLineFunctionTupleAndForLoop() {
        val out = run("""
            indicator("Tuple")
            stats(len) =>
                total = 0.0
                for i = 0 to len - 1
                    total += nz(close[i])
                [total, total / len]
            [s, m] = stats(2)
            plot(s, "sum")
            plot(m, "mean")
        """, 1.0, 3.0, 5.0)
        assertEquals(listOf(1.0, 4.0, 8.0), out.plot("sum").values)
        assertEquals(listOf(0.5, 2.0, 4.0), out.plot("mean").values)
    }

    @Test fun rsiMatchesWilderDefinition() {
        // gains 1,1 then loss 1 with length 2: rma seeds with sma of the first two changes.
        val out = run("""
            indicator("RSI")
            plot(ta.rsi(close, 2), "rsi")
        """, 1.0, 2.0, 3.0, 2.0)
        val values = out.plot("rsi").values
        assertNull(values[0]); assertNull(values[1])
        assertEquals(100.0, values[2]!!, 1e-9)
        // up rma = (0 + 1*1)/2 = 0.5, down rma = (1 + 1*0)/2 = 0.5 -> 50
        assertEquals(50.0, values[3]!!, 1e-9)
        assertEquals(PineScriptEngine.Pane.INDICATOR, out.plot("rsi").pane)
    }

    @Test fun crossoverMacdAndBollingerTuples() {
        val out = run("""
            indicator("Combo", overlay=true)
            fast = ta.sma(close, 1)
            slow = ta.sma(close, 3)
            plotshape(ta.crossover(fast, slow), "up", shape.triangleup, location.belowbar)
            [mid, upper, lower] = ta.bb(close, 2, 2)
            plot(upper, "upper")
            [m, s, h] = ta.macd(close, 2, 3, 2)
            plot(h, "hist")
        """, 5.0, 4.0, 3.0, 6.0, 7.0)
        assertEquals(listOf(null, null, null, 5.0, null), out.plot("up").values)
        assertEquals(5.5, out.plot("upper").values[1]!!, 1e-9) // mean 4.5 + 2 * 0.5
        assertTrue(out.plot("hist").values.last() != null)
    }

    @Test fun strategyOrdersFillNextBarOpenAndProduceBacktest() {
        val out = run("""
            strategy("Flip", overlay=true)
            if close > close[1]
                strategy.entry("L", strategy.long)
            if close < close[1]
                strategy.close("L")
        """, 10.0, 11.0, 12.0, 9.0, 8.0)
        val bt = out.backtest!!
        assertTrue(out.isStrategy)
        assertEquals(1, bt.closedTrades)
        val trade = bt.trades.first()
        assertEquals(2, trade.entryBar)
        assertEquals(12.0, trade.entryPrice, 0.0)
        assertEquals(4, trade.exitBar)
        assertEquals(8.0, trade.exitPrice!!, 0.0)
        assertEquals(-33.333, trade.returnPercent!!, 0.01)
        assertTrue(out.signals.any { it.kind == PineScriptEngine.SignalKind.LONG_ENTRY })
    }

    @Test fun unsupportedStatementsAreSkippedAndReportedWithoutBlockingOtherPlots() {
        val out = run("""
            //@version=5
            indicator("Partial", overlay=true)
            eps = request.financial("AAPL", "EPS", "FQ")
            polyline.new(array.new<chart.point>())
            plot(close, "close")
        """, 1.0, 2.0)
        assertEquals(listOf(1.0, 2.0), out.plot("close").values)
        assertTrue(out.diagnostics.any { it.line == 3 && it.severity == PineScriptEngine.Severity.ERROR })
        assertTrue(out.diagnostics.any { it.line == 4 })
        assertEquals(1, out.skippedStatements)
    }

    @Test fun legacyV4NamesAndColorsResolve() {
        val out = run("""
            //@version=4
            study("Legacy", overlay=true)
            len = input(2, title="Length", type=input.integer)
            c = close > open ? color.green : color.new(color.red, 50)
            plot(sma(close, len), "ma", color=c)
            bgcolor(close > 2 ? color.blue : na)
        """, 1.0, 2.0, 3.0)
        assertEquals(listOf(null, 1.5, 2.5), out.plot("ma").values)
        assertTrue(out.overlay)
        assertNotNull(out.backgrounds[2])
        assertNull(out.backgrounds[0])
        assertEquals(0x80L, (out.plot("ma").colors[1]!! ushr 24) and 0xFF)
    }

    @Test fun switchExpressionAndWhileLoop() {
        val out = run("""
            indicator("Switch")
            mode = input.string("double", "Mode")
            value = switch mode
                "double" => close * 2
                "triple" => close * 3
                => close
            n = 0
            while n < 3
                n += 1
            plot(value + n, "v")
        """, 1.0, 2.0)
        assertEquals(listOf(5.0, 7.0), out.plot("v").values)
    }

    @Test fun lineContinuationAndNamedArguments() {
        val out = run("""
            indicator(title = "Wrapped",
                 overlay = true)
            total = close +
                 open
            plot(series = total, title = "t")
        """, 2.0)
        assertEquals("Wrapped", out.title)
        assertEquals(listOf(4.0), out.plot("t").values)
    }

    @Test fun runawayLoopsAreBounded() {
        val out = run("""
            indicator("Loop")
            x = 0
            while true
                x += 1
            plot(x, "x")
        """, 1.0)
        assertTrue(out.diagnostics.any { it.message.contains("Loop exceeded") })
    }

    @Test fun syntaxErrorsAreReportedWithLineNumbers() {
        val diagnostics = PineScriptEngine.check("indicator(\"X\")\nplot(close,\n\nx = = 2")
        assertTrue(diagnostics.isNotEmpty())
        assertTrue(diagnostics.all { it.line > 0 })
    }

    @Test fun volumeBuiltinsMatchPineReferenceDefinitions() {
        val out = run("""
            indicator("Volume built-ins", overlay=false)
            plot(ta.pvt, "pvt")
            plot(ta.wad, "wad")
            plot(ta.nvi, "nvi")
            plot(ta.pvi, "pvi")
            plot(ta.iii, "iii")
            plot(ta.wvad, "wvad")
        """, 10.0, 12.0, 11.0, 11.0)
        val pvt = out.plot("pvt").values
        assertEquals(listOf(0.0, 2.0), pvt.take(2))
        assertEquals(2.0 - 10.0 / 12.0, pvt[2]!!, 1e-9)
        assertEquals(pvt[2]!!, pvt[3]!!, 1e-9)
        assertEquals(listOf(0.0, 2.0, 1.0, 1.0), out.plot("wad").values)
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0), out.plot("nvi").values)
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0), out.plot("pvi").values)
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), out.plot("iii").values)
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), out.plot("wvad").values)
    }
}
