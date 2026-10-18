package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Compact shared visual language; values always come from observed application state. */
@Composable
internal fun CommandDashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.animateContentSize(), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun CommandMetric(label: String, value: String, detail: String,
                           modifier: Modifier = Modifier, attention: Boolean = false,
                           remainingValue: Long? = null) {
    var previous by remember { mutableStateOf(remainingValue) }
    var decreased by remember { mutableStateOf(false) }
    LaunchedEffect(remainingValue) {
        decreased = remainingValue != null && previous != null && remainingValue < previous!!
        previous = remainingValue
        if (decreased) { delay(900); decreased = false }
    }
    val readingColor by animateColorAsState(
        if (attention || decreased) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "remaining-token-direction")
    Surface(modifier, shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, maxLines = 2, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(targetState = value, label = "metric-value") { displayed ->
                Text(displayed, maxLines = 2, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = readingColor)
            }
            Text(detail, maxLines = 2, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CommandStatus(label: String, active: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant, label = "dashboard-status")
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(Modifier.size(7.dp), shape = CircleShape, color = color) {}
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
