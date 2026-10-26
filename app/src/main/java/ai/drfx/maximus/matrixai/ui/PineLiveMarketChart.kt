package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.studio.PineAnalysis
import ai.drfx.maximus.matrixai.studio.PineCandle
import ai.drfx.maximus.matrixai.studio.PineIndicatorPreview
import ai.drfx.maximus.matrixai.studio.PineMarketDataSource
import ai.drfx.maximus.matrixai.studio.PineMarketSeries
import ai.drfx.maximus.matrixai.studio.PineScriptEngine
import ai.drfx.maximus.matrixai.studio.PineTimeframe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

private val chartGreen = Color(0xFF19C7A0)
private val chartRed = Color(0xFFF0585B)
private val chartBackground = Color(0xFF041422)
private val chartGrid = Color(0xFF12304A)

/**
 * Real exchange candles with the Pine source executed locally by [PineScriptEngine].
 * This is a local preview, NOT a TradingView embed or a TradingView compile.
 */
@Composable
internal fun PineLiveMarketChart(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    previewOnly: Boolean = false,
    indicatorSource: String? = null,
    onAnalyze: ((String) -> Unit)? = null
) {
    val source = remember { PineMarketDataSource() }
    var product by rememberSaveable { mutableStateOf(PineMarketDataSource.PRODUCTS.first()) }
    var interval by rememberSaveable { mutableStateOf(PineTimeframe.ONE_HOUR) }
    var series by remember { mutableStateOf<PineMarketSeries?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var candleWidth by remember { mutableFloatStateOf(if (compact) 6f else 7f) }
    var selected by remember { mutableIntStateOf(-1) }
    var showIssues by remember { mutableStateOf(false) }

    LaunchedEffect(product, interval, refresh) {
        loading = true
        error = null
        selected = -1
        series = null
        try {
            series = source.load(product, interval)
        } catch (cause: Exception) {
            error = cause.message?.take(220) ?: "Market feed unavailable."
        } finally { loading = false }
    }

    // Large indicators take real time to parse and run; never block the main thread on them.
    val sourceCheckState by produceState<PineIndicatorPreview.Result?>(null, indicatorSource) {
        value = indicatorSource?.let { source -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            PineIndicatorPreview.evaluate(source, emptyList()) } }
    }
    val sourceCheck = sourceCheckState
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
        if (!previewOnly) Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { menu = true }, shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                    modifier = Modifier.testTag("pine_market_picker")) {
                    Text(product, fontSize = 11.sp, color = MaximusReference.gold)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    PineMarketDataSource.PRODUCTS.forEach { name ->
                        DropdownMenuItem(text = { Text("$name · Coinbase Exchange") }, onClick = {
                            product = name; menu = false
                        })
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Text("OHLCV · Coinbase", fontSize = 10.sp, color = MaximusReference.muted,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { refresh++ }, enabled = !loading,
                modifier = Modifier.size(33.dp)) {
                Icon(Icons.Default.Refresh, "Refresh live market candles",
                    modifier = Modifier.size(18.dp), tint = MaximusReference.gold)
            }
        }
        if (!previewOnly) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PineTimeframe.entries.forEach { tf ->
                val chosen = tf == interval
                Surface(
                    onClick = { interval = tf },
                    modifier = Modifier.weight(1f),
                    color = if (chosen) Color(0xFF28210E) else MaximusReference.navy,
                    border = BorderStroke(1.dp,
                        if (chosen) MaximusReference.gold else MaximusReference.stroke),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(Modifier.height(if (compact) 27.dp else 32.dp), contentAlignment = Alignment.Center) {
                        Text(tf.label, color = if (chosen) MaximusReference.gold else MaximusReference.muted,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp)
                    }
                }
            }
        }
        if (sourceCheck is PineIndicatorPreview.Result.Unsupported) Text(
            "Source not previewed: ${sourceCheck.reason}", color = MaterialTheme.colorScheme.error,
            fontSize = 11.sp, modifier = Modifier.testTag("pine_indicator_unsupported"))
        when {
            loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                color = MaximusReference.gold)
            error != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp)
                Text("No synthetic fallback is displayed. Check network access and retry.",
                    color = MaximusReference.muted, fontSize = 10.sp)
                OutlinedButton(onClick = { refresh++ }) { Text("Retry exchange feed") }
            }
            series != null -> {
                val market = series!!
                val indicatorState by produceState<PineIndicatorPreview.Result?>(null, indicatorSource, market) {
                    value = indicatorSource?.let { source -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        PineIndicatorPreview.evaluate(source, market.candles, market.product, market.timeframe.seconds) } }
                }
                val indicator = indicatorState
                val supported = indicator as? PineIndicatorPreview.Result.Supported
                val output = supported?.output
                val lines = supported?.lines.orEmpty()
                val priceLines = lines.filter { it.pane == PineScriptEngine.Pane.PRICE }
                val paneLines = lines.filter { it.pane == PineScriptEngine.Pane.INDICATOR }
                val paneLevels = output?.hlines.orEmpty().filter { it.pane == PineScriptEngine.Pane.INDICATOR }
                val priceLevels = output?.hlines.orEmpty().filter { it.pane == PineScriptEngine.Pane.PRICE }
                when (indicator) {
                    is PineIndicatorPreview.Result.Unsupported -> if (sourceCheck !is PineIndicatorPreview.Result.Unsupported) Text(
                        "Source not previewed: ${indicator.reason}", color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp, modifier = Modifier.testTag("pine_indicator_unsupported"))
                    is PineIndicatorPreview.Result.Supported -> Text(
                        "Local Pine preview · ${output?.title ?: "Script"} · ${lines.size} plot${if (lines.size == 1) "" else "s"}" +
                            (output?.let { if (it.skippedStatements > 0) " · ${it.skippedStatements} statement(s) skipped" else "" } ?: "") +
                            " · run bar-by-bar on real candles, not on TradingView.",
                        color = MaximusReference.gold, fontSize = 11.sp, lineHeight = 14.sp,
                        modifier = Modifier.testTag("pine_indicator_preview"))
                    null -> Unit
                }
                Text("${market.candles.size} historical candles · $product · ${market.timeframe.label} · Source: ${market.source}",
                    color = MaximusReference.muted, fontSize = 10.sp,
                    modifier = Modifier.testTag("pine_live_candle_count"))
                if (previewOnly) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("▼ High sweep", fontSize = 9.sp, color = chartRed)
                    Text("▲ Low sweep", fontSize = 9.sp, color = chartGreen)
                    Text("﹘ Sweep level", fontSize = 9.sp, color = MaximusReference.gold)
                }
                val visibleCandles = if (previewOnly) market.candles.takeLast(45) else market.candles
                val trim = market.candles.size - visibleCandles.size
                fun <T> List<T>.visible() = if (trim > 0) drop(trim) else this
                val horizontal = rememberScrollState()
                LaunchedEffect(market, candleWidth) { horizontal.scrollTo(horizontal.maxValue) }
                val priceHeight = if (previewOnly) 170.dp else if (compact) 170.dp else 250.dp
                val paneHeight = if (compact) 82.dp else 110.dp
                val hasPane = paneLines.isNotEmpty() || paneLevels.isNotEmpty()
                val chartWidth: Modifier = if (previewOnly) Modifier.fillMaxWidth()
                    else Modifier.width(max(320f, market.candles.size * candleWidth).dp)
                val visiblePriceLines = priceLines.map { it.copy(values = it.values.visible(), colors = it.colors.visible()) }
                val priceRange = remember(visibleCandles, visiblePriceLines, priceLevels) {
                    priceRange(visibleCandles, visiblePriceLines, priceLevels)
                }
                val paneRange = remember(paneLines, paneLevels) { paneRange(paneLines, paneLevels) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    // Two fingers zoom the candles; one finger keeps scrolling through history.
                    val pinch = Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1f) candleWidth = (candleWidth * zoom).coerceIn(2f, 24f)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    Box(Modifier.weight(1f).then(if (previewOnly) Modifier else pinch.horizontalScroll(horizontal))) {
                        Column(chartWidth, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            PineOhlcvCanvas(
                                candles = visibleCandles,
                                selectedIndex = if (previewOnly) -1 else selected,
                                onSelect = { if (!previewOnly) selected = it },
                                markSweeps = previewOnly && indicatorSource == null,
                                overlays = visiblePriceLines,
                                levels = priceLevels,
                                backgrounds = output?.backgrounds.orEmpty().visible(),
                                barColors = output?.barColors.orEmpty().visible(),
                                signals = output?.signals.orEmpty(),
                                range = priceRange,
                                drawings = output?.drawings.orEmpty(),
                                fills = output?.fills.orEmpty(),
                                allLines = lines,
                                firstBar = trim,
                                modifier = Modifier.fillMaxWidth().height(priceHeight)
                                    .testTag("pine_real_ohlcv_canvas")
                                    .semantics { contentDescription = "OHLCV price candles" }
                            )
                            if (hasPane && !previewOnly) PineIndicatorPaneCanvas(
                                size = market.candles.size,
                                lines = paneLines,
                                levels = paneLevels,
                                fills = output?.fills.orEmpty(),
                                allLines = lines,
                                selectedIndex = selected,
                                onSelect = { selected = it },
                                range = paneRange,
                                modifier = Modifier.fillMaxWidth().height(paneHeight)
                                    .testTag("pine_indicator_pane")
                                    .semantics { contentDescription = "Indicator pane" }
                            )
                            val equity = output?.equity.orEmpty()
                            if (equity.any { it != null } && !previewOnly) PineEquityCanvas(equity, trim,
                                step = with(androidx.compose.ui.platform.LocalDensity.current) { candleWidth.dp.toPx() },
                                modifier = Modifier.fillMaxWidth().height(if (compact) 46.dp else 64.dp))
                        }
                    }
                    Column(Modifier.width(58.dp).padding(start = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        AxisLabels(priceRange, priceHeight * .80f, 6)
                        Spacer(Modifier.height(priceHeight * .20f))
                        if (hasPane && !previewOnly) AxisLabels(paneRange, paneHeight, 3)
                        if (output?.equity.orEmpty().any { it != null } && !previewOnly) {
                            Spacer(Modifier.height(3.dp))
                            Text("Equity", color = MaximusReference.muted, fontSize = 9.sp)
                            Text("%+.1f%%".format(Locale.US, (output!!.equity.lastOrNull { it != null } ?: 100.0) - 100.0),
                                color = if ((output.equity.lastOrNull { it != null } ?: 100.0) >= 100.0) chartGreen else chartRed, fontSize = 9.sp)
                        }
                    }
                }
                val activeIndex = selected.takeIf { it in market.candles.indices } ?: market.candles.lastIndex
                val active = market.candles[activeIndex]
                if (lines.isNotEmpty()) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("pine_chart_legend"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    lines.forEachIndexed { index, line ->
                        val value = line.values.getOrNull(activeIndex)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(lineColor(line, index), CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text(line.label + ": " + when {
                                line.markersOnly -> if (value != null) "signal" else "–"
                                value != null -> PineAnalysis.format(value)
                                else -> "Warm-up"
                            }, color = MaximusReference.white, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                Text("UTC ${marketUtc(active.epochSeconds * 1000)}  O ${prettyPrice(active.open)}  " +
                    "H ${prettyPrice(active.high)}  L ${prettyPrice(active.low)}  C ${prettyPrice(active.close)}",
                    color = MaximusReference.muted, fontSize = 10.sp,
                    maxLines = 2, lineHeight = 14.sp)
                if (!previewOnly) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zoom", color = MaximusReference.muted, fontSize = 10.sp)
                    Spacer(Modifier.width(5.dp))
                    OutlinedButton(onClick = { candleWidth = max(2f, candleWidth - 2f) },
                        enabled = candleWidth > 2f,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)) { Text("−", fontSize = 15.sp) }
                    Spacer(Modifier.width(5.dp))
                    OutlinedButton(onClick = { candleWidth = min(24f, candleWidth + 2f) },
                        enabled = candleWidth < 24f,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)) { Text("+", fontSize = 15.sp) }
                    Spacer(Modifier.weight(1f))
                    Text("Fetched ${marketUtc(market.fetchedAtMillis)} UTC",
                        fontSize = 9.sp, color = MaximusReference.muted)
                }
                if (output != null && !previewOnly) {
                    PineTablesView(output.tables)
                    PineAnalysisCard(output, market.candles, compact)
                    val issues = output.diagnostics
                    if (issues.isNotEmpty()) {
                        TextButton(onClick = { showIssues = !showIssues }, contentPadding = PaddingValues(horizontal = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("pine_preview_issues_toggle")) {
                            val errors = issues.count { it.severity == PineScriptEngine.Severity.ERROR }
                            Text((if (showIssues) "Hide" else "Show") + " preview notes (${issues.size}" +
                                (if (errors > 0) ", $errors skipped" else "") + ")",
                                color = if (errors > 0) MaximusReference.orange else MaximusReference.muted, fontSize = 11.sp)
                        }
                        if (showIssues) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            issues.take(12).forEach { issue ->
                                Text((if (issue.line > 0) "Line ${issue.line} · " else "") + issue.message,
                                    fontSize = 10.sp, lineHeight = 13.sp,
                                    color = when (issue.severity) {
                                        PineScriptEngine.Severity.ERROR -> MaximusReference.orange
                                        PineScriptEngine.Severity.WARNING -> MaximusReference.gold
                                        PineScriptEngine.Severity.INFO -> MaximusReference.muted
                                    })
                            }
                            if (issues.size > 12) Text("+${issues.size - 12} more", fontSize = 10.sp, color = MaximusReference.muted)
                        }
                    }
                }
                if (onAnalyze != null && indicatorSource != null && !previewOnly) {
                    OutlinedButton(
                        onClick = {
                            val summary = output?.let { PineAnalysis.summary(it, market.candles, market.product, market.timeframe.label) }
                            onAnalyze(PineAnalysis.reviewPrompt(indicatorSource, summary))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("pine_analyze_with_ai"),
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, MaximusReference.gold)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = MaximusReference.gold)
                        Spacer(Modifier.width(6.dp))
                        Text("Analyze with AI", color = MaximusReference.gold, fontSize = 12.sp)
                    }
                }
            }
        }
        if (!compact) Text("Exchange-provided market data · Not a TradingView feed · Local previews are not TradingView execution.",
            fontSize = 10.sp, color = MaximusReference.muted)
    }
}

@Composable
private fun PineAnalysisCard(output: PineScriptEngine.Output, candles: List<PineCandle>, compact: Boolean) {
    val facts = remember(output, candles) { PineAnalysis.facts(output, candles) }
    if (facts.isEmpty()) return
    Surface(shape = RoundedCornerShape(10.dp), color = MaximusReference.navy,
        border = BorderStroke(1.dp, MaximusReference.stroke), modifier = Modifier.fillMaxWidth().testTag("pine_analysis_card")) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (output.isStrategy) "Strategy preview" else "Indicator readout", color = MaximusReference.white,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            facts.take(if (compact) 6 else 12).forEach { fact ->
                Row(Modifier.fillMaxWidth()) {
                    Text(fact.label, color = MaximusReference.muted, fontSize = 11.sp, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fact.value, color = MaximusReference.white, fontSize = 11.sp, maxLines = 1)
                }
            }
            if (output.isStrategy) Text("Simplified local simulation: one position, next-bar-open fills, no fees or slippage. Not a TradingView backtest.",
                color = MaximusReference.muted, fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun AxisLabels(range: ClosedFloatingPointRange<Double>, height: Dp, count: Int) {
    Column(Modifier.height(height), verticalArrangement = Arrangement.SpaceBetween) {
        (0 until count).forEach { i ->
            val value = range.endInclusive - (range.endInclusive - range.start) * i / (count - 1).toDouble()
            Text(PineAnalysis.format(value), color = MaximusReference.muted, fontSize = 9.sp, maxLines = 1)
        }
    }
}

private val palette = listOf(MaximusReference.gold, Color(0xFF60B8EF), Color(0xFFCE8AFF), Color(0xFF19C7A0),
    Color(0xFFFF8A65), Color(0xFFFFEB3B))

private fun lineColor(line: PineIndicatorPreview.Line, index: Int): Color =
    line.color?.let { Color(it).copy(alpha = 1f) } ?: palette[index % palette.size]

private fun barColor(line: PineIndicatorPreview.Line, bar: Int, index: Int): Color =
    line.colors.getOrNull(bar)?.let { Color(it) } ?: lineColor(line, index)

private fun priceRange(
    candles: List<PineCandle>,
    overlays: List<PineIndicatorPreview.Line>,
    levels: List<PineScriptEngine.HLine>
): ClosedFloatingPointRange<Double> {
    if (candles.isEmpty()) return 0.0..1.0
    var hi = candles.maxOf { it.high }
    var lo = candles.minOf { it.low }
    // Keep the candles readable: ignore overlay values far outside the visible price action.
    val span = hi - lo
    overlays.filterNot { it.markersOnly }.flatMap { it.values }.filterNotNull().forEach { v ->
        if (v <= hi + span && v >= lo - span) { hi = max(hi, v); lo = min(lo, v) }
    }
    levels.forEach { if (it.price <= hi + span && it.price >= lo - span) { hi = max(hi, it.price); lo = min(lo, it.price) } }
    val margin = max((hi - lo) * .08, hi * .0001)
    return (lo - margin)..(hi + margin)
}

private fun paneRange(lines: List<PineIndicatorPreview.Line>, levels: List<PineScriptEngine.HLine>): ClosedFloatingPointRange<Double> {
    val values = lines.flatMap { it.values }.filterNotNull() + levels.map { it.price }
    if (values.isEmpty()) return 0.0..1.0
    var hi = values.max()
    var lo = values.min()
    if (lines.any { it.style == PineScriptEngine.PlotStyle.HISTOGRAM || it.style == PineScriptEngine.PlotStyle.COLUMNS }) {
        hi = max(hi, 0.0); lo = min(lo, 0.0)
    }
    if (hi == lo) { hi += 1; lo -= 1 }
    val margin = (hi - lo) * .08
    return (lo - margin)..(hi + margin)
}

private fun prettyPrice(value: Double) =
    String.format(Locale.US, if (value >= 1000) "%,.1f" else "%.4f", value)

private fun marketUtc(millis: Long): String =
    SimpleDateFormat("MMM dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))

private fun DrawScope.drawSeries(
    line: PineIndicatorPreview.Line,
    index: Int,
    step: Float,
    y: (Double) -> Float,
    zeroY: Float
) {
    when (line.style) {
        PineScriptEngine.PlotStyle.HISTOGRAM, PineScriptEngine.PlotStyle.COLUMNS -> line.values.forEachIndexed { bar, value ->
            if (value != null) {
                val x = (bar + .5f) * step
                val top = min(y(value), zeroY)
                val height = max(1f, kotlin.math.abs(y(value) - zeroY))
                val w = if (line.style == PineScriptEngine.PlotStyle.COLUMNS) step * .7f else max(1.5f, step * .35f)
                drawRect(barColor(line, bar, index), Offset(x - w / 2, top), Size(w, height))
            }
        }
        PineScriptEngine.PlotStyle.CIRCLES, PineScriptEngine.PlotStyle.CROSS, PineScriptEngine.PlotStyle.MARKER,
        PineScriptEngine.PlotStyle.MARKER_UP, PineScriptEngine.PlotStyle.MARKER_DOWN -> line.values.forEachIndexed { bar, value ->
            if (value != null) drawCircle(barColor(line, bar, index), 2.4f, Offset((bar + .5f) * step, y(value)))
        }
        else -> {
            var previous: Offset? = null
            line.values.forEachIndexed { bar, value ->
                if (value == null || !value.isFinite()) previous = null
                else {
                    val point = Offset((bar + .5f) * step, y(value))
                    previous?.let { from ->
                        if (line.style == PineScriptEngine.PlotStyle.STEP) {
                            drawLine(barColor(line, bar, index), from, Offset(point.x, from.y), 1.8f)
                            drawLine(barColor(line, bar, index), Offset(point.x, from.y), point, 1.8f)
                        } else drawLine(barColor(line, bar, index), from, point, 1.8f)
                        if (line.style == PineScriptEngine.PlotStyle.AREA) drawLine(
                            barColor(line, bar, index).copy(alpha = .18f), point, Offset(point.x, zeroY), step)
                    }
                    previous = point
                }
            }
        }
    }
}

@Composable
private fun PineOhlcvCanvas(
    candles: List<PineCandle>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    markSweeps: Boolean = false,
    overlays: List<PineIndicatorPreview.Line> = emptyList(),
    levels: List<PineScriptEngine.HLine> = emptyList(),
    backgrounds: List<Long?> = emptyList(),
    barColors: List<Long?> = emptyList(),
    signals: List<PineScriptEngine.Signal> = emptyList(),
    range: ClosedFloatingPointRange<Double> = priceRange(candles, overlays, levels),
    drawings: List<PineScriptEngine.Drawing> = emptyList(),
    fills: List<PineScriptEngine.Fill> = emptyList(),
    allLines: List<PineIndicatorPreview.Line> = emptyList(),
    firstBar: Int = 0
) {
    val measurer = rememberTextMeasurer()
    var crosshair by remember { mutableStateOf<Offset?>(null) }
    Canvas(modifier.background(chartBackground).pointerInput(candles) {
        detectTapGestures { position ->
            onSelect(((position.x / size.width) * candles.size)
                .toInt().coerceIn(0, candles.lastIndex))
        }
    }.pointerInput(candles) {
        // Long-press and drag shows a crosshair with the exact price, like a trading terminal.
        detectDragGesturesAfterLongPress(
            onDragStart = { crosshair = it },
            onDragEnd = { crosshair = null },
            onDragCancel = { crosshair = null }
        ) { change, _ ->
            change.consume()
            crosshair = change.position
            onSelect(((change.position.x / size.width) * candles.size).toInt().coerceIn(0, candles.lastIndex))
        }
    }) {
        if (candles.isEmpty()) return@Canvas
        val maxPrice = range.endInclusive
        val minPrice = range.start
        val candleArea = size.height * .80f
        val volY = size.height * .84f
        val volHeight = size.height * .13f
        val step = size.width / candles.size
        val volumeMax = max(1.0, candles.maxOf { it.volume })
        fun priceY(price: Double) = ((maxPrice - price) / (maxPrice - minPrice) * candleArea).toFloat()
        backgrounds.forEachIndexed { i, argb ->
            if (argb != null && i < candles.size) drawRect(Color(argb), Offset(i * step, 0f), Size(step + .5f, size.height))
        }
        for (i in 0..5) {
            val y = candleArea * i / 5
            drawLine(chartGrid, Offset(0f, y), Offset(size.width, y), 1f)
        }
        for (i in 0..8) {
            val x = size.width * i / 8
            drawLine(chartGrid.copy(alpha = .6f), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        drawPineFills(fills, allLines, PineScriptEngine.Pane.PRICE, firstBar, candles.size, step, ::priceY)
        candles.forEachIndexed { i, candle ->
            val x = (i + .5f) * step
            val rising = candle.close >= candle.open
            val color = barColors.getOrNull(i)?.let { Color(it) } ?: if (rising) chartGreen else chartRed
            val top = priceY(max(candle.open, candle.close))
            val bottom = priceY(min(candle.open, candle.close))
            drawLine(color, Offset(x, priceY(candle.high)), Offset(x, priceY(candle.low)), 1.2f)
            drawRect(color, Offset(x - step * .32f, top), Size(step * .64f, max(1.6f, bottom - top)))
            val vol = (candle.volume / volumeMax * volHeight).toFloat()
            drawRect(color.copy(alpha = .45f), Offset(x - step * .32f, volY + volHeight - vol),
                Size(step * .64f, max(1f, vol)))
            if (i == selectedIndex) {
                drawLine(MaximusReference.gold.copy(alpha = .7f), Offset(x, 0f), Offset(x, size.height), 1.3f)
            }
        }
        levels.forEach { level ->
            val y = priceY(level.price)
            if (y in 0f..candleArea) drawLine((level.color?.let { Color(it) } ?: MaximusReference.muted).copy(alpha = .8f),
                Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        overlays.forEachIndexed { lineIndex, overlay ->
            if (overlay.markersOnly) {
                overlay.values.forEachIndexed { index, value ->
                    if (value != null && value.isFinite()) {
                        val x = (index + .5f) * step
                        val color = barColor(overlay, index, lineIndex)
                        val half = max(3.5f, min(6f, step * .45f))
                        when (overlay.style) {
                            PineScriptEngine.PlotStyle.MARKER_UP -> {
                                val y = (priceY(value) + 6f + half).coerceAtMost(candleArea - 2f)
                                drawPath(Path().apply {
                                    moveTo(x, y - half); lineTo(x + half, y + half); lineTo(x - half, y + half); close()
                                }, color)
                            }
                            PineScriptEngine.PlotStyle.MARKER_DOWN -> {
                                val y = (priceY(value) - 6f - half).coerceAtLeast(half + 2f)
                                drawPath(Path().apply {
                                    moveTo(x - half, y - half); lineTo(x + half, y - half); lineTo(x, y + half); close()
                                }, color)
                            }
                            else -> drawCircle(color, half * .8f, Offset(x, priceY(value)))
                        }
                    }
                }
            } else drawSeries(overlay, lineIndex, step, ::priceY, candleArea)
        }
        signals.forEach { signal ->
            if (signal.bar !in candles.indices) return@forEach
            val x = (signal.bar + .5f) * step
            val candle = candles[signal.bar]
            when (signal.kind) {
                PineScriptEngine.SignalKind.LONG_ENTRY -> {
                    val y = (priceY(candle.low) + 14f).coerceAtMost(candleArea)
                    drawPath(Path().apply { moveTo(x, y - 6f); lineTo(x + 5f, y + 3f); lineTo(x - 5f, y + 3f); close() }, chartGreen)
                }
                PineScriptEngine.SignalKind.SHORT_ENTRY -> {
                    val y = (priceY(candle.high) - 14f).coerceAtLeast(6f)
                    drawPath(Path().apply { moveTo(x - 5f, y - 3f); lineTo(x + 5f, y - 3f); lineTo(x, y + 6f); close() }, chartRed)
                }
                PineScriptEngine.SignalKind.EXIT -> drawCircle(MaximusReference.muted, 3f,
                    Offset(x, (priceY(candle.high) - 10f).coerceAtLeast(4f)), style = Stroke(1.4f))
            }
        }
        if (markSweeps && candles.size > 20) {
            // Derived from ACTUAL exchange bars, never a fabricated Pine script/backtest signal.
            for (i in 20 until candles.size) {
                val bar = candles[i]
                val prior = candles.subList(i - 20, i)
                val priorHigh = prior.maxOf { it.high }
                val priorLow = prior.minOf { it.low }
                val highSweep = bar.high > priorHigh && bar.close < priorHigh
                val lowSweep = bar.low < priorLow && bar.close > priorLow
                if (highSweep || lowSweep) {
                    val x = (i + .5f) * step
                    val y = if (highSweep) priceY(bar.high) - 10f else priceY(bar.low) + 10f
                    val mark = Path().apply {
                        moveTo(x - 5f, if (highSweep) y - 4f else y + 4f)
                        lineTo(x + 5f, if (highSweep) y - 4f else y + 4f)
                        lineTo(x, if (highSweep) y + 4f else y - 4f)
                        close()
                    }
                    drawPath(mark, if (highSweep) chartRed else chartGreen)
                    val yLevel = priceY(if (highSweep) priorHigh else priorLow)
                    drawLine(MaximusReference.gold.copy(alpha = .8f),
                        Offset(x, yLevel), Offset(size.width, yLevel), 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
                }
            }
        }
        drawPineDrawings(drawings, ChartX(firstBar, step), ::priceY, measurer, candleArea)
        val last = candles.last()
        val lastColor = if (last.close >= last.open) chartGreen else chartRed
        drawLine(lastColor.copy(alpha = .6f),
            Offset(0f, priceY(last.close)), Offset(size.width, priceY(last.close)),
            1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
        drawPriceTag(measurer, priceY(last.close), PineAnalysis.format(last.close), lastColor, Color.Black)
        crosshair?.let { point ->
            val y = point.y.coerceIn(0f, candleArea)
            val dashed = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            drawLine(MaximusReference.white.copy(alpha = .55f), Offset(point.x, 0f), Offset(point.x, size.height), 1f, pathEffect = dashed)
            drawLine(MaximusReference.white.copy(alpha = .55f), Offset(0f, y), Offset(size.width, y), 1f, pathEffect = dashed)
            val price = maxPrice - (y / candleArea) * (maxPrice - minPrice)
            drawPriceTag(measurer, y, crosshairLabel(price), MaximusReference.white, Color.Black)
        }
        drawRect(MaximusReference.stroke, style = Stroke(1f))
    }
}

@Composable
private fun PineIndicatorPaneCanvas(
    size: Int,
    lines: List<PineIndicatorPreview.Line>,
    levels: List<PineScriptEngine.HLine>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    range: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
    fills: List<PineScriptEngine.Fill> = emptyList(),
    allLines: List<PineIndicatorPreview.Line> = emptyList()
) {
    Canvas(modifier.background(chartBackground).pointerInput(size) {
        detectTapGestures { position ->
            if (size > 0) onSelect(((position.x / this.size.width) * size).toInt().coerceIn(0, size - 1))
        }
    }) {
        if (size == 0) return@Canvas
        val step = this.size.width / size
        val height = this.size.height
        fun y(value: Double) = ((range.endInclusive - value) / (range.endInclusive - range.start) * height).toFloat()
        val zeroY = y(0.0).coerceIn(0f, height)
        for (i in 0..2) drawLine(chartGrid, Offset(0f, height * i / 2), Offset(this.size.width, height * i / 2), 1f)
        drawPineFills(fills, allLines, PineScriptEngine.Pane.INDICATOR, 0, size, step, ::y)
        levels.forEach { level ->
            drawLine((level.color?.let { Color(it) } ?: MaximusReference.muted).copy(alpha = .75f),
                Offset(0f, y(level.price)), Offset(this.size.width, y(level.price)), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        lines.forEachIndexed { index, line -> drawSeries(line, index, step, ::y, zeroY) }
        if (selectedIndex in 0 until size) {
            val x = (selectedIndex + .5f) * step
            drawLine(MaximusReference.gold.copy(alpha = .7f), Offset(x, 0f), Offset(x, height), 1.3f)
        }
        drawRect(MaximusReference.stroke, style = Stroke(1f))
    }
}
