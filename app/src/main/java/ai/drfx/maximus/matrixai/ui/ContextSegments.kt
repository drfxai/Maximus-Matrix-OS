package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Discrete context occupancy. Never presented as remaining provider credit. */
@Composable
internal fun ContextSegments(value: Float) {
    val target = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(target, tween(500), label = "context-segments")
    val gold = MaterialTheme.colorScheme.primary
    val orange = MaterialTheme.colorScheme.tertiary
    val red = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(18.dp).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
    }) {
        val gap = 4.dp.toPx()
        val width = ((size.width - gap * 24) / 25).coerceAtLeast(1f)
        repeat(25) { index ->
            val color = if (index + .5f > progress * 25) track
                else if (target >= .85f) red else if (index >= 15) orange else gold
            drawRoundRect(color, Offset(index * (width + gap), 0f), Size(width, size.height), CornerRadius(width / 2))
        }
    }
}
