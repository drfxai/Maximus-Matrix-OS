package ai.drfx.maximus.matrixai.studio

/** Bounded local arithmetic preview, NOT a Pine interpreter, compiler or backtester. */
object PineIndicatorPreview {
    data class Line(val label: String, val values: List<Double?>, val markersOnly: Boolean = false)
    sealed interface Result {
        data class Supported(val lines: List<Line>) : Result
        data class Unsupported(val reason: String) : Result
    }
    private data class Average(val kind: String, val period: Int)
    private val reservedNames = setOf("close", "open", "high", "low", "volume", "time", "time_close", "bar_index", "hl2", "hlc3", "ohlc4", "hlcc4", "ta", "input", "color", "indicator", "plot", "true", "false", "na")
    const val MAX_PRICE = 1_000_000_000_000.0
    private const val NUMERIC_LIMIT = "Local preview requires finite positive prices at most 1 trillion; no indicator was plotted."
    private const val LIMIT = "Preview supports only overlay SMA/EMA of close, literal or input.int periods (1–500), and up to four plots. Full Pine execution requires TradingView."

    fun evaluate(source: String, candles: List<PineCandle>): Result {
        if (source.length > 100_000 || candles.size > 200) return Result.Unsupported(LIMIT)
        if (candles.any { candle -> listOf(candle.open, candle.high, candle.low, candle.close).any { !it.isFinite() || it <= 0.0 || it > MAX_PRICE } }) return Result.Unsupported(NUMERIC_LIMIT)
        PineSweepPreview.evaluate(source, candles)?.let { return it }
        val periods = mutableMapOf<String, Int>()
        val averages = mutableMapOf<String, Average>()
        val plots = mutableListOf<Average>()
        var declaration = false
        // Deliberately reject expressions/statements outside the entire supported grammar.
        val identifier = "[A-Za-z_][A-Za-z_0-9]*"
        val quoted = "\"[^\"\\\\\\r\\n]*\""
        val declarationPattern = Regex("indicator\\(\\s*$quoted\\s*,\\s*overlay\\s*=\\s*true\\s*\\)")
        val inputPattern = Regex("($identifier)\\s*=\\s*(?:input\\.int\\(\\s*(\\d{1,3})(?:\\s*,\\s*(?:title\\s*=\\s*)?$quoted)?\\s*\\)|(\\d{1,3}))")
        fun average(expression: String): Average? {
            val match = Regex("ta\\.(sma|ema)\\(\\s*close\\s*,\\s*($identifier|\\d{1,3})\\s*\\)").matchEntire(expression.trim()) ?: return null
            val period = match.groupValues[2].toIntOrNull() ?: periods[match.groupValues[2]] ?: return null
            return if (period in 1..500) Average(match.groupValues[1], period) else null
        }
        val statements = source.lineSequence().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("//") }.toList()
        if (statements.size > 200) return Result.Unsupported(LIMIT)
        for (line in statements) {
            when {
                declarationPattern.matches(line) && !declaration && periods.isEmpty() && averages.isEmpty() && plots.isEmpty() -> declaration = true
                !declaration -> return Result.Unsupported(LIMIT)
                inputPattern.matches(line) -> {
                    val match = inputPattern.matchEntire(line)!!
                    val period = (match.groupValues[2].ifBlank { match.groupValues[3] }).toIntOrNull()
                    val name = match.groupValues[1]
                    if (period == null || period !in 1..500 || name in reservedNames || name in periods || name in averages) return Result.Unsupported(LIMIT)
                    periods[name] = period
                }
                line.startsWith("plot(") && line.endsWith(")") -> {
                    // Only presentation arguments may follow the actual plotted average.
                    val body = line.substring(5, line.length - 1)
                    var depth = 0
                    val split = body.indexOfFirst { ch ->
                        if (ch == '(') depth++
                        if (ch == ')') depth--
                        ch == ',' && depth == 0
                    }
                    val expression = if (split < 0) body else body.substring(0, split)
                    val presentation = if (split < 0) "" else body.substring(split)
                    if (!Regex("(?:\\s*,\\s*(?:title\\s*=\\s*$quoted|color\\s*=\\s*color\\.(?:orange|blue|red|green|yellow|white|purple|aqua|gray)|linewidth\\s*=\\s*[1-4]|$quoted))*\\s*").matches(presentation)) return Result.Unsupported(LIMIT)
                    val value = average(expression) ?: averages[expression.trim()] ?: return Result.Unsupported(LIMIT)
                    plots += value
                    if (plots.size > 4) return Result.Unsupported(LIMIT)
                }
                else -> {
                    val assignment = Regex("($identifier)\\s*=\\s*(.+)").matchEntire(line) ?: return Result.Unsupported(LIMIT)
                    val name = assignment.groupValues[1]
                    if (name in reservedNames || name in periods || name in averages) return Result.Unsupported(LIMIT)
                    averages[name] = average(assignment.groupValues[2]) ?: return Result.Unsupported(LIMIT)
                }
            }
        }
        if (!declaration || plots.isEmpty() || candles.any { !it.close.isFinite() }) return Result.Unsupported(LIMIT)
        val lines = plots.map { specification ->
            var sum = 0.0
            var ema: Double? = null
            val alpha = 2.0 / (specification.period + 1)
            val values = candles.mapIndexed { index, candle ->
                if (specification.kind == "sma") {
                    sum += candle.close
                    if (index >= specification.period) sum -= candles[index - specification.period].close
                    if (index + 1 >= specification.period) sum / specification.period else null
                } else {
                    ema = ema?.let { alpha * candle.close + (1 - alpha) * it } ?: candle.close
                    ema
                }
            }
            if (values.any { it != null && (!it.isFinite() || it <= 0.0 || it > MAX_PRICE) }) return Result.Unsupported(NUMERIC_LIMIT)
            Line("${specification.kind.uppercase()} ${specification.period} · close", values)
        }
        return Result.Supported(lines)
    }
}
