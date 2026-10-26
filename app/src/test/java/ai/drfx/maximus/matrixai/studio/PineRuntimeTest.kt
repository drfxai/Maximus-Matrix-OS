package ai.drfx.maximus.matrixai.studio

import org.junit.Assert.*
import org.junit.Test

class PineRuntimeTest {
    private fun bars(count: Int) = (0 until count).map { index ->
        val close = 100.0 + index
        PineCandle(3_600L * (index + 1), close - 0.5, close + 1, close - 1, close, 10.0 + index)
    }
    private fun run(source: String, count: Int = 30) = PineScriptEngine.run(source.trimIndent(), bars(count))
    private fun PineScriptEngine.Output.last(title: String) = plots.first { it.title == title }.values.last()
    private fun PineScriptEngine.Output.errors() = diagnostics.filter { it.severity == PineScriptEngine.Severity.ERROR }

    @Test fun arraysWithMethodSyntaxAndForIn() {
        val out = run("""
            //@version=6
            indicator("Arrays")
            var closes = array.new<float>()
            closes.push(close)
            if closes.size() > 5
                closes.shift()
            total = 0.0
            for value in closes
                total += value
            plot(total / closes.size(), "avg")
            plot(array.avg(closes), "builtin")
            plot(closes.max(), "max")
        """)
        assertTrue(out.errors().toString(), out.errors().isEmpty())
        assertEquals(127.0, out.last("avg")!!, 1e-9)
        assertEquals(127.0, out.last("builtin")!!, 1e-9)
        assertEquals(129.0, out.last("max")!!, 1e-9)
    }

    @Test fun userTypesFieldsAndMethods() {
        val out = run("""
            //@version=6
            indicator("Types", overlay = true)
            type Pivot
                float price = na
                int bar = 0
            method lift(Pivot p, float by) =>
                p.price := p.price + by
                p
            var Pivot last = Pivot.new(close, bar_index)
            if bar_index % 10 == 0
                last := Pivot.new(close, bar_index)
            last.lift(0.5)
            plot(last.price, "pivot")
            plot(last.bar, "bar")
        """)
        assertTrue(out.errors().toString(), out.errors().isEmpty())
        // Reset at bar 20 (close 120), then lifted on bars 20..29 = 10 × 0.5.
        assertEquals(125.0, out.last("pivot")!!, 1e-9)
        assertEquals(20.0, out.last("bar")!!, 1e-9)
    }

    @Test fun mapsAndTuplesInForIn() {
        val out = run("""
            //@version=6
            indicator("Maps")
            m = map.new<string, float>()
            m.put("a", 1.0)
            m.put("b", 2.0)
            m.put("a", 3.0)
            s = 0.0
            for [k, v] in m
                s += v
            arr = array.from(5, 3, 9)
            arr.sort()
            plot(s, "sum")
            plot(m.size(), "size")
            plot(arr.get(0), "first")
            plot(arr.indexof(9), "index")
        """)
        assertTrue(out.errors().toString(), out.errors().isEmpty())
        assertEquals(5.0, out.last("sum")!!, 1e-9)
        assertEquals(2.0, out.last("size")!!, 1e-9)
        assertEquals(3.0, out.last("first")!!, 1e-9)
        assertEquals(2.0, out.last("index")!!, 1e-9)
    }

    @Test fun drawingsTablesAndFillsReachTheOutput() {
        val out = run("""
            //@version=6
            indicator("Draw", overlay = true, max_labels_count = 3)
            label.new(bar_index, high, str.tostring(close, "#.0"))
            var line l = line.new(0, low, 1, high, extend = extend.right, color = color.red)
            line.set_xy2(l, bar_index, close)
            var box b = na
            if barstate.islast
                b := box.new(bar_index - 5, high, bar_index, low, bgcolor = color.new(color.green, 80))
            var table t = table.new(position.top_right, 2, 1)
            if barstate.islast
                table.cell(t, 0, 0, "RSI")
                table.cell(t, 1, 0, str.format("{0,number,#.##}", 55.555))
            p1 = plot(close, "c")
            p2 = plot(open, "o")
            fill(p1, p2, color = color.new(color.blue, 70))
        """)
        assertTrue(out.errors().toString(), out.errors().isEmpty())
        val labels = out.drawings.filterIsInstance<PineScriptEngine.LabelDrawing>()
        assertEquals(3, labels.size)
        assertEquals("129.0", labels.last().text)
        val line = out.drawings.filterIsInstance<PineScriptEngine.LineDrawing>().single()
        assertEquals(29.0, line.x2, 0.0)
        assertEquals("extend.right", line.extend)
        assertEquals(1, out.drawings.filterIsInstance<PineScriptEngine.BoxDrawing>().size)
        val table = out.tables.single()
        assertEquals("55.56", table.cells[1 to 0]?.text)
        val fill = out.fills.single()
        assertEquals(0, fill.upper.plot)
        assertEquals(1, fill.lower.plot)
    }

    @Test fun strategyEquityIsTracked() {
        val out = run("""
            //@version=6
            strategy("S", overlay = true)
            if bar_index == 2
                strategy.entry("L", strategy.long)
            if bar_index == 12
                strategy.close("L")
        """)
        assertEquals(30, out.equity.size)
        assertEquals(100.0, out.equity[1]!!, 1e-9)
        // Entry fills at bar 3's open (102.5), exit at bar 13's open (112.5).
        assertEquals(100.0 * 112.5 / 102.5, out.equity.last()!!, 1e-6)
    }

    @Test fun enumsAndStringHelpers() {
        val out = run("""
            //@version=6
            indicator("Enum")
            enum Mode
                fast = "Fast"
                slow = "Slow"
            mode = Mode.fast
            parts = str.split("a,b,c", ",")
            plot(mode == Mode.fast ? 1 : 0, "isFast")
            plot(parts.size(), "parts")
            plot(str.length(str.substring("abcdef", 1, 3)), "sub")
        """)
        assertTrue(out.errors().toString(), out.errors().isEmpty())
        assertEquals(1.0, out.last("isFast")!!, 0.0)
        assertEquals(3.0, out.last("parts")!!, 0.0)
        assertEquals(2.0, out.last("sub")!!, 0.0)
    }
}
