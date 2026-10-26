package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.studio.PineAnalysis
import ai.drfx.maximus.matrixai.studio.PineIndicatorPreview
import ai.drfx.maximus.matrixai.studio.PineScriptEngine
import kotlin.math.max
import kotlin.math.min

/** Maps a script bar index to a canvas x position for the visible window. */
internal class ChartX(private val firstBar: Int, private val step: Float) {
    fun of(bar: Double): Float = ((bar - firstBar + .5) * step).toFloat()
}

/** fill() areas between two plots or two levels, drawn under the series of the same pane. */
internal fun DrawScope.drawPineFills(
    fills: List<PineScriptEngine.Fill>,
    lines: List<PineIndicatorPreview.Line>,
    pane: PineScriptEngine.Pane,
    firstBar: Int,
    bars: Int,
    step: Float,
    y: (Double) -> Float
) {
    fun valueAt(edge: PineScriptEngine.FillEdge, bar: Int): Double? =
        edge.price ?: edge.plot?.let { lines.getOrNull(it)?.values?.getOrNull(bar) }
    for (fill in fills) {
        if (fill.pane != pane) continue
        for (i in 0 until bars) {
            val bar = i + firstBar
            val color = fill.colors.getOrNull(bar) ?: continue
            val a0 = valueAt(fill.upper, bar) ?: continue
            val b0 = valueAt(fill.lower, bar) ?: continue
            val a1 = valueAt(fill.upper, bar + 1) ?: a0
            val b1 = valueAt(fill.lower, bar + 1) ?: b0
            val x0 = (i + .5f) * step
            val x1 = if (i == bars - 1) x0 + step / 2 else x0 + step
            drawPath(Path().apply {
                moveTo(x0, y(a0)); lineTo(x1, y(a1)); lineTo(x1, y(b1)); lineTo(x0, y(b0)); close()
            }, Color(color))
        }
    }
}

private fun dash(style: String): PathEffect? = when {
    style.contains("dotted") -> PathEffect.dashPathEffect(floatArrayOf(2f, 4f))
    style.contains("dashed") -> PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
    else -> null
}

private fun labelSize(size: String) = when {
    size.contains("tiny") -> 8.sp
    size.contains("small") -> 9.sp
    size.contains("large") -> 13.sp
    size.contains("huge") -> 16.sp
    else -> 10.sp
}

/** Script labels, lines, boxes and line fills on the price pane. */
internal fun DrawScope.drawPineDrawings(
    drawings: List<PineScriptEngine.Drawing>,
    xOf: ChartX,
    priceY: (Double) -> Float,
    measurer: TextMeasurer,
    plotHeight: Float
) = clipRect(0f, 0f, size.width, plotHeight) {
    fun lineEnds(line: PineScriptEngine.LineDrawing): Pair<Offset, Offset>? {
        if (listOf(line.x1, line.y1, line.x2, line.y2).any { it.isNaN() }) return null
        var a = Offset(xOf.of(line.x1), priceY(line.y1))
        var b = Offset(xOf.of(line.x2), priceY(line.y2))
        val dx = b.x - a.x
        val slope = if (dx == 0f) 0f else (b.y - a.y) / dx
        if (line.extend.contains("right") || line.extend.contains("both")) {
            val right = if (b.x >= a.x) b else a
            val far = size.width + 10f
            val extended = Offset(far, right.y + slope * (far - right.x))
            if (right == b) b = extended else a = extended
        }
        if (line.extend.contains("left") || line.extend.contains("both")) {
            val left = if (a.x <= b.x) a else b
            val extended = Offset(-10f, left.y + slope * (-10f - left.x))
            if (left == a) a = extended else b = extended
        }
        return a to b
    }
    for (drawing in drawings) when (drawing) {
        is PineScriptEngine.LinefillDrawing -> {
            val first = lineEnds(drawing.first) ?: continue
            val second = lineEnds(drawing.second) ?: continue
            val color = drawing.color ?: continue
            drawPath(Path().apply {
                moveTo(first.first.x, first.first.y); lineTo(first.second.x, first.second.y)
                lineTo(second.second.x, second.second.y); lineTo(second.first.x, second.first.y); close()
            }, Color(color))
        }
        is PineScriptEngine.BoxDrawing -> {
            if (listOf(drawing.left, drawing.top, drawing.right, drawing.bottom).any { it.isNaN() }) continue
            var left = min(xOf.of(drawing.left), xOf.of(drawing.right))
            var right = max(xOf.of(drawing.left), xOf.of(drawing.right))
            if (drawing.extend.contains("right") || drawing.extend.contains("both")) right = size.width
            if (drawing.extend.contains("left") || drawing.extend.contains("both")) left = 0f
            val top = min(priceY(drawing.top), priceY(drawing.bottom))
            val bottom = max(priceY(drawing.top), priceY(drawing.bottom))
            drawing.background?.let { drawRect(Color(it), Offset(left, top), Size(right - left, bottom - top)) }
            drawing.border?.let { drawRect(Color(it), Offset(left, top), Size(right - left, bottom - top), style = Stroke(1f)) }
            if (drawing.text.isNotBlank() && right - left > 16f && bottom - top > 10f) {
                val layout = measurer.measure(drawing.text, TextStyle(color = drawing.textColor?.let { Color(it) } ?: Color.White,
                    fontSize = 9.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = (right - left - 4f).toInt().coerceAtLeast(1)))
                drawText(layout, topLeft = Offset(left + (right - left - layout.size.width) / 2, top + (bottom - top - layout.size.height) / 2))
            }
        }
        is PineScriptEngine.LineDrawing -> {
            val (a, b) = lineEnds(drawing) ?: continue
            drawLine(drawing.color?.let { Color(it) } ?: MaximusReference.gold, a, b,
                strokeWidth = drawing.width.toFloat().coerceIn(1f, 6f) * 1.2f, pathEffect = dash(drawing.style))
        }
        is PineScriptEngine.LabelDrawing -> {
            if (drawing.x.isNaN() || drawing.y.isNaN()) continue
            val x = xOf.of(drawing.x)
            val y = priceY(drawing.y)
            if (x < -40f || x > size.width + 40f) continue
            val color = drawing.color?.let { Color(it) } ?: Color(0xFF2962FF)
            val style = drawing.style
            val shape = listOf("circle", "cross", "xcross", "triangleup", "triangledown", "arrowup", "arrowdown",
                "square", "diamond", "flag").firstOrNull { style.endsWith(it) }
            if (drawing.text.isBlank() || shape != null && !style.contains("label_")) {
                val r = 4.5f
                when (shape) {
                    "triangleup", "arrowup" -> drawPath(Path().apply { moveTo(x, y - r); lineTo(x + r, y + r); lineTo(x - r, y + r); close() }, color)
                    "triangledown", "arrowdown" -> drawPath(Path().apply { moveTo(x - r, y - r); lineTo(x + r, y - r); lineTo(x, y + r); close() }, color)
                    "square" -> drawRect(color, Offset(x - r, y - r), Size(2 * r, 2 * r))
                    "diamond" -> drawPath(Path().apply { moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close() }, color)
                    "cross", "xcross" -> {
                        drawLine(color, Offset(x - r, y - r), Offset(x + r, y + r), 1.6f)
                        drawLine(color, Offset(x - r, y + r), Offset(x + r, y - r), 1.6f)
                    }
                    else -> drawCircle(color, r, Offset(x, y))
                }
                if (drawing.text.isBlank()) continue
            }
            val layout = measurer.measure(drawing.text.take(80), TextStyle(
                color = drawing.textColor?.let { Color(it) } ?: Color.White, fontSize = labelSize(drawing.size),
                fontWeight = FontWeight.Medium), maxLines = 3, overflow = TextOverflow.Ellipsis)
            val w = layout.size.width + 10f
            val h = layout.size.height + 6f
            val noBubble = style.contains("style_none") || style.contains("text_outline")
            val (left, top) = when {
                style.contains("label_up") -> (x - w / 2) to (y + 6f)
                style.contains("label_left") -> (x + 6f) to (y - h / 2)
                style.contains("label_right") -> (x - w - 6f) to (y - h / 2)
                style.contains("label_center") || noBubble -> (x - w / 2) to (y - h / 2)
                else -> (x - w / 2) to (y - h - 6f)
            }
            if (!noBubble && color.alpha > 0f) {
                drawRoundRect(color, Offset(left, top), Size(w, h), CornerRadius(4f))
                val pointer = Path()
                when {
                    style.contains("label_up") -> pointer.apply { moveTo(x - 4f, top); lineTo(x + 4f, top); lineTo(x, y); close() }
                    style.contains("label_left") -> pointer.apply { moveTo(left, y - 4f); lineTo(left, y + 4f); lineTo(x, y); close() }
                    style.contains("label_right") -> pointer.apply { moveTo(left + w, y - 4f); lineTo(left + w, y + 4f); lineTo(x, y); close() }
                    style.contains("label_center") -> Unit
                    else -> pointer.apply { moveTo(x - 4f, top + h); lineTo(x + 4f, top + h); lineTo(x, y); close() }
                }
                drawPath(pointer, color)
            }
            drawText(layout, topLeft = Offset(left + 5f, top + 3f))
        }
    }
}

/** A price tag on the right edge, e.g. the last close or the crosshair price. */
internal fun DrawScope.drawPriceTag(measurer: TextMeasurer, y: Float, text: String, background: Color, foreground: Color) {
    val layout = measurer.measure(text, TextStyle(color = foreground, fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
    val w = layout.size.width + 8f
    val h = layout.size.height + 4f
    val top = (y - h / 2).coerceIn(0f, size.height - h)
    drawRoundRect(background, Offset(size.width - w, top), Size(w, h), CornerRadius(3f))
    drawText(layout, topLeft = Offset(size.width - w + 4f, top + 2f))
}

/** Strategy equity curve under the chart, relative to the starting capital. */
@Composable
internal fun PineEquityCanvas(equity: List<Double?>, firstBar: Int, step: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFF041422)).testTag("pine_equity_pane")) {
        val values = equity.drop(firstBar)
        val known = values.filterNotNull()
        if (known.isEmpty()) return@Canvas
        val hi = max(known.max(), 100.0)
        val lo = min(known.min(), 100.0)
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = ((hi - v) / span * (size.height - 8f) + 4f).toFloat()
        drawLine(MaximusReference.muted.copy(alpha = .5f), Offset(0f, y(100.0)), Offset(size.width, y(100.0)), 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        val above = Path(); val line = Path()
        var started = false
        values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val x = (i + .5f) * step
            if (!started) { line.moveTo(x, y(v)); above.moveTo(x, y(100.0)); above.lineTo(x, y(v)); started = true }
            else { line.lineTo(x, y(v)); above.lineTo(x, y(v)) }
        }
        val lastX = (values.indexOfLast { it != null } + .5f) * step
        above.lineTo(lastX, y(100.0)); above.close()
        val up = (known.last() >= 100.0)
        val tone = if (up) Color(0xFF19C7A0) else Color(0xFFF0585B)
        drawPath(above, tone.copy(alpha = .14f))
        drawPath(line, tone, style = Stroke(1.6f))
        drawRect(MaximusReference.stroke, style = Stroke(1f))
    }
}

/** table.* output as compact grids, in the script's order, labelled by their chart position. */
@Composable
internal fun PineTablesView(tables: List<PineScriptEngine.Table>) {
    if (tables.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("pine_tables"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tables.forEach { table ->
            val usedRows = (0 until table.rows).filter { r -> (0 until table.columns).any { c -> table.cells[c to r]?.text?.isNotBlank() == true } }
            val usedColumns = (0 until table.columns).filter { c -> usedRows.any { r -> table.cells[c to r]?.text?.isNotBlank() == true } }
            if (usedRows.isEmpty()) return@forEach
            Surface(shape = RoundedCornerShape(8.dp), color = table.background?.let { Color(it) } ?: MaximusReference.navy,
                border = BorderStroke(1.dp, table.frame?.let { Color(it) } ?: MaximusReference.stroke)) {
                Column(Modifier.padding(4.dp)) {
                    Text("Table · " + table.position.removePrefix("position.").replace('_', ' '), color = MaximusReference.muted,
                        fontSize = 9.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
                    usedRows.forEach { r ->
                        Row(Modifier.fillMaxWidth()) {
                            usedColumns.forEach { c ->
                                val cell = table.cells[c to r]
                                Box(Modifier.weight(1f).padding(1.dp)
                                    .background(cell?.background?.let { Color(it) } ?: Color.Transparent, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.CenterStart) {
                                    Text(cell?.text.orEmpty(), color = cell?.textColor?.let { Color(it) } ?: MaximusReference.white,
                                        fontSize = when {
                                            cell?.textSize?.contains("tiny") == true -> 8.sp
                                            cell?.textSize?.contains("small") == true -> 9.sp
                                            cell?.textSize?.contains("large") == true -> 12.sp
                                            else -> 10.sp
                                        }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun crosshairLabel(price: Double): String = PineAnalysis.format(price)
