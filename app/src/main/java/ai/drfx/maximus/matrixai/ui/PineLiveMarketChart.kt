package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.studio.PineCandle
import ai.drfx.maximus.matrixai.studio.PineMarketDataSource
import ai.drfx.maximus.matrixai.studio.PineMarketSeries
import ai.drfx.maximus.matrixai.studio.PineTimeframe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

/** Real history chart, explicitly NOT a Pine compiler/backtester or a TradingView embed. */
@Composable
internal fun PineLiveMarketChart(modifier: Modifier = Modifier, compact: Boolean = false, previewOnly: Boolean = false) {
    val source = remember { PineMarketDataSource() }
    var product by remember { mutableStateOf(PineMarketDataSource.PRODUCTS.first()) }
    var interval by remember { mutableStateOf(PineTimeframe.ONE_MINUTE) }
    var series by remember { mutableStateOf<PineMarketSeries?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var candleWidth by remember { mutableIntStateOf(7) }
    var selected by remember { mutableIntStateOf(-1) }

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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 7.dp)) {
        if (!previewOnly) Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { menu = true }, shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp)) {
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
                Text("${market.candles.size} / 200 historical candles · ${market.timeframe.label} · Source: ${market.source}",
                    color = MaximusReference.muted, fontSize = 10.sp,
                    modifier = Modifier.testTag("pine_live_candle_count"))
                if (previewOnly) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("▼ High sweep", fontSize = 9.sp, color = Color(0xFFF0585B))
                    Text("▲ Low sweep", fontSize = 9.sp, color = Color(0xFF19C7A0))
                    Text("﹘ Sweep level", fontSize = 9.sp, color = MaximusReference.gold)
                }
                val visibleCandles = if (previewOnly) market.candles.takeLast(45) else market.candles
                val horizontal = rememberScrollState()
                LaunchedEffect(market, candleWidth) {
                    horizontal.scrollTo(horizontal.maxValue)
                }
                val chartHeight = if (previewOnly) 170.dp else if (compact) 155.dp else 230.dp
                Row(Modifier.fillMaxWidth().height(chartHeight), verticalAlignment = Alignment.Top) {
                    Row(Modifier.weight(1f).horizontalScroll(horizontal)) {
                        PineOhlcvCanvas(
                            candles = visibleCandles,
                            selectedIndex = if (previewOnly) -1 else selected,
                            onSelect = { selected = it },
                            markSweeps = previewOnly,
                            modifier = Modifier.width(if (previewOnly) 350.dp else (max(320, market.candles.size * candleWidth)).dp)
                                .height(chartHeight).testTag("pine_real_ohlcv_canvas")
                                .semantics { contentDescription = "OHLCV price candles" }
                        )
                    }
                    val hi = visibleCandles.maxOf { it.high }
                    val lo = visibleCandles.minOf { it.low }
                    val digits = if (hi > 1000) 0 else if (hi > 100) 2 else 4
                    Column(
                        Modifier.width(61.dp).height(if (previewOnly) 160.dp else if (compact) 140.dp else 190.dp).padding(start = 3.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        (0..5).forEach { i ->
                            Text(String.format(Locale.US, "%.${digits}f",
                                hi - (hi - lo) * i / 5.0),
                                color = MaximusReference.muted, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
                val active = market.candles.getOrNull(selected)
                    ?: market.candles.last()
                Text("UTC ${marketUtc(active.epochSeconds * 1000)}  O ${prettyPrice(active.open)}  " +
                    "H ${prettyPrice(active.high)}  L ${prettyPrice(active.low)}  C ${prettyPrice(active.close)}",
                    color = MaximusReference.muted, fontSize = 10.sp,
                    maxLines = 2, lineHeight = 14.sp)
                if (!compact && !previewOnly) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zoom", color = MaximusReference.muted, fontSize = 10.sp)
                    Spacer(Modifier.width(5.dp))
                    OutlinedButton(onClick = { candleWidth = max(4, candleWidth - 2) },
                        enabled = candleWidth > 4,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)) { Text("−", fontSize = 15.sp) }
                    Spacer(Modifier.width(5.dp))
                    OutlinedButton(onClick = { candleWidth = min(16, candleWidth + 2) },
                        enabled = candleWidth < 16,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)) { Text("+", fontSize = 15.sp) }
                    Spacer(Modifier.weight(1f))
                    Text("Last fetch ${marketUtc(market.fetchedAtMillis)} UTC",
                        fontSize = 9.sp, color = MaximusReference.muted)
                }
            }
        }
        if (!compact) Text("Exchange-provided market data · Not a TradingView feed · Pine code is not executed on this chart.",
            fontSize = 10.sp, color = MaximusReference.muted)
    }
}

private fun prettyPrice(value: Double) =
    String.format(Locale.US, if (value >= 1000) "%,.1f" else "%.4f", value)

private fun marketUtc(millis: Long): String =
    SimpleDateFormat("MMM dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))

@Composable
private fun PineOhlcvCanvas(
    candles: List<PineCandle>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    markSweeps: Boolean = false
) {
    val green = Color(0xFF19C7A0)
    val red = Color(0xFFF0585B)
    Canvas(modifier.background(Color(0xFF041422)).pointerInput(candles) {
        detectTapGestures { position ->
            onSelect(((position.x / size.width) * candles.size)
                .toInt().coerceIn(0, candles.lastIndex))
        }
    }) {
        if (candles.isEmpty()) return@Canvas
        val hi = candles.maxOf { it.high }
        val lo = candles.minOf { it.low }
        val margin = max((hi - lo) * .08, hi * .0001)
        val maxPrice = hi + margin
        val minPrice = lo - margin
        val candleArea = size.height * .80f
        val volY = size.height * .84f
        val volHeight = size.height * .13f
        val step = size.width / candles.size
        val volumeMax = max(1.0, candles.maxOf { it.volume })
        fun priceY(price: Double) = ((maxPrice - price) / (maxPrice - minPrice) * candleArea).toFloat()
        for (i in 0..5) {
            val y = candleArea * i / 5
            drawLine(Color(0xFF1A3447), Offset(0f, y), Offset(size.width, y), 1f)
        }
        for (i in 0..8) {
            val x = size.width * i / 8
            drawLine(Color(0xFF102E42), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        candles.forEachIndexed { i, candle ->
            val x = (i + .5f) * step
            val rising = candle.close >= candle.open
            val color = if (rising) green else red
            val top = priceY(max(candle.open, candle.close))
            val bottom = priceY(min(candle.open, candle.close))
            drawLine(color, Offset(x, priceY(candle.high)),
                Offset(x, priceY(candle.low)), 1.4f)
            drawRect(color, Offset(x - step * .32f, top),
                Size(step * .64f, max(1.6f, bottom - top)))
            val vol = (candle.volume / volumeMax * volHeight).toFloat()
            drawRect(color.copy(alpha = .53f), Offset(x - step * .32f, volY + volHeight - vol),
                Size(step * .64f, max(1f, vol)))
            if (i == selectedIndex) {
                drawLine(MaximusReference.gold.copy(alpha = .7f), Offset(x, 0f),
                    Offset(x, size.height), 1.3f)
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
                    drawPath(mark, if (highSweep) red else green)
                    val yLevel = priceY(if (highSweep) priorHigh else priorLow)
                    drawLine(MaximusReference.gold.copy(alpha = .8f),
                        Offset(x, yLevel), Offset(size.width, yLevel), 1f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(5f, 5f)))
                }
            }
        }
        val last = candles.last()
        drawLine(MaximusReference.gold.copy(alpha = .5f),
            Offset(0f, priceY(last.close)), Offset(size.width, priceY(last.close)),
            1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
        drawRect(MaximusReference.stroke, style = Stroke(1f))
    }
}
