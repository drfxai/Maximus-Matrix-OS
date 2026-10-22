package ai.drfx.maximus.matrixai.studio

/**
 * Chart preview of Pine source on real exchange candles, executed by the local
 * [PineScriptEngine]. It is a preview, NOT TradingView: anything the engine cannot run
 * is reported in the diagnostics instead of being silently drawn.
 */
object PineIndicatorPreview {
    data class Line(
        val label: String,
        val values: List<Double?>,
        val markersOnly: Boolean = false,
        val pane: PineScriptEngine.Pane = PineScriptEngine.Pane.PRICE,
        val style: PineScriptEngine.PlotStyle = PineScriptEngine.PlotStyle.LINE,
        /** ARGB per bar, empty when the script gave no colour. */
        val colors: List<Long?> = emptyList()
    ) {
        val color: Long? get() = colors.lastOrNull { it != null }
    }

    sealed interface Result {
        data class Supported(val lines: List<Line>, val output: PineScriptEngine.Output? = null) : Result {
            val diagnostics: List<PineScriptEngine.Diagnostic> get() = output?.diagnostics.orEmpty()
        }
        data class Unsupported(val reason: String, val diagnostics: List<PineScriptEngine.Diagnostic> = emptyList()) : Result
    }

    const val MAX_PRICE = 1_000_000_000_000.0
    const val MAX_CANDLES = 500
    private const val NUMERIC_LIMIT = "Local preview requires finite positive prices at most 1 trillion; no indicator was plotted."

    fun evaluate(
        source: String,
        candles: List<PineCandle>,
        symbol: String = "BTC-USD",
        timeframeSeconds: Int = 60
    ): Result {
        if (source.length > PineScriptEngine.MAX_SOURCE) return Result.Unsupported("Pine source exceeds 100,000 characters.")
        if (source.isBlank()) return Result.Unsupported("Pine source is empty.")
        if (candles.size > MAX_CANDLES) return Result.Unsupported("Preview is limited to $MAX_CANDLES candles.")
        if (candles.any { candle -> listOf(candle.open, candle.high, candle.low, candle.close).any { !it.isFinite() || it <= 0.0 || it > MAX_PRICE } })
            return Result.Unsupported(NUMERIC_LIMIT)
        if (candles.isEmpty()) {
            val diagnostics = PineScriptEngine.check(source)
            val fatal = diagnostics.firstOrNull { it.severity == PineScriptEngine.Severity.ERROR }
            return if (fatal != null && diagnostics.count { it.severity == PineScriptEngine.Severity.ERROR } >= statementCount(source))
                Result.Unsupported(describe(fatal), diagnostics)
            else Result.Supported(emptyList())
        }
        val output = try {
            PineScriptEngine.run(source, candles, symbol, timeframeSeconds)
        } catch (error: PineScriptEngine.PineException) {
            return Result.Unsupported(error.message ?: "Pine source could not be previewed.")
        } catch (error: RuntimeException) {
            return Result.Unsupported("Pine source could not be previewed: ${error.message ?: error.javaClass.simpleName}")
        }
        if (!output.hasVisualOutput) {
            val reason = output.errors.firstOrNull()?.let(::describe)
                ?: "The script ran but produced no plots, shapes, levels or orders to draw."
            return Result.Unsupported(reason, output.diagnostics)
        }
        val lines = output.plots.map { plot ->
            Line(plot.title, plot.values, plot.isMarker, plot.pane, plot.style, plot.colors)
        }
        return Result.Supported(lines, output)
    }

    fun describe(diagnostic: PineScriptEngine.Diagnostic): String =
        if (diagnostic.line > 0) "Line ${diagnostic.line}: ${diagnostic.message}" else diagnostic.message

    private fun statementCount(source: String): Int =
        source.lineSequence().count { it.isNotBlank() && !it.trimStart().startsWith("//") }.coerceAtLeast(1)
}
