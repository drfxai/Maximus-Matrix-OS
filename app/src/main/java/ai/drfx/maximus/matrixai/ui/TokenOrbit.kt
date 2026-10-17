package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Animates only between observed context readings; never simulates provider credit. */
@Composable
internal fun TokenOrbit(usedFraction: Float, value: String, label: String, modifier: Modifier = Modifier) {
    val target = usedFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val fraction by animateFloatAsState(target, tween(650), label = "context-orbit")
    val accent by animateColorAsState(
        if (target >= .85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        tween(300), label = "context-warning")
    val track = MaterialTheme.colorScheme.outlineVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(136.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
        }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 10.dp.toPx()
                val diameter = size.minDimension - inset * 2
                val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                val bounds = Size(diameter, diameter)
                drawArc(track, 135f, 270f, false, origin, bounds,
                    style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                if (fraction > 0f) drawArc(accent, 135f, 270f * fraction, false, origin, bounds,
                    style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                repeat(25) { index ->
                    val angle = Math.toRadians((135 + index * 270.0 / 24))
                    val radius = diameter / 2 - 14.dp.toPx()
                    val center = Offset(size.width / 2, size.height / 2)
                    val start = center + Offset(kotlin.math.cos(angle).toFloat() * radius,
                        kotlin.math.sin(angle).toFloat() * radius)
                    val end = center + Offset(kotlin.math.cos(angle).toFloat() * (radius - 4.dp.toPx()),
                        kotlin.math.sin(angle).toFloat() * (radius - 4.dp.toPx()))
                    drawLine(track, start, end, 1.dp.toPx())
                }
            }
            AnimatedContent(value, label = "context-reading") { displayed ->
                Text(displayed, style = MaterialTheme.typography.titleLarge,
                    color = accent, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
