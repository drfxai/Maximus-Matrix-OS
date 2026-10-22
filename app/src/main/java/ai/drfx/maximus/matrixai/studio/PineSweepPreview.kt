package ai.drfx.maximus.matrixai.studio

/**
 * Deliberately narrow, audited arithmetic visualisation for the built-in
 * prior-high sweep template. This is NOT a Pine runtime or backtest.
 */
internal object PineSweepPreview {
    private val declaration = Regex("""indicator\(\s*"[^"\r\n]{1,80}"\s*,\s*overlay\s*=\s*true\s*\)""")
    private val input = Regex("""length\s*=\s*input\.int\(\s*(\d{1,3})\s*,\s*"[^"\r\n]{1,80}"\s*,\s*minval\s*=\s*2\s*\)""")
    private val level = Regex("""level\s*=\s*ta\.highest\(\s*high\[1]\s*,\s*length\s*\)""")
    private val sweep = Regex("""sweep\s*=\s*high\s*>\s*level\s+and\s+close\s*<\s*level""")
    private val marker = Regex("""plotshape\(\s*sweep\s*,\s*title\s*=\s*"Sweep"\s*,\s*style\s*=\s*shape\.triangledown\s*,\s*location\s*=\s*location\.abovebar\s*,\s*color\s*=\s*color\.orange\s*\)""")
    private val alert = Regex("""alertcondition\(\s*sweep\s*,\s*title\s*=\s*"Liquidity sweep"\s*,\s*message\s*=\s*"Potential liquidity sweep detected"\s*\)""")

    fun evaluate(source: String, candles: List<PineCandle>): PineIndicatorPreview.Result? {
        val lines = source.lineSequence().map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("//") }.toList()
        if (lines.none { it.startsWith("plotshape(") }) return null
        val unsupported = PineIndicatorPreview.Result.Unsupported(
            "This plotshape expression is outside the verified prior-high sweep preview. " +
                "Full compilation and backtesting require TradingView.")
        if (lines.size !in 5..6 || !declaration.matches(lines[0])) return unsupported
        val lookback = input.matchEntire(lines[1])?.groupValues?.get(1)?.toIntOrNull()
            ?: return unsupported
        if (lookback !in 2..500 || !level.matches(lines[2]) ||
            !sweep.matches(lines[3]) || !marker.matches(lines[4]) ||
            (lines.size == 6 && !alert.matches(lines[5]))) return unsupported
        val prices = candles.mapIndexed { index, candle ->
            if (index < lookback) null else {
                // Pine high[1] excludes the current candle: never read future bars.
                val priorHigh = candles.subList(index - lookback, index).maxOf { it.high }
                if (candle.high > priorHigh && candle.close < priorHigh) candle.high else null
            }
        }
        return PineIndicatorPreview.Result.Supported(
            listOf(PineIndicatorPreview.Line("Prior-high sweep $lookback", prices, markersOnly = true)))
    }
}
