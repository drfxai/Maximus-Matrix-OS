package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Surface(modifier, shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f)),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.background(Brush.linearGradient(listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = .09f),
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.tertiary.copy(alpha = .05f)
        ))).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun CommandMetric(label: String, value: String, detail: String,
                           modifier: Modifier = Modifier, attention: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(detail, style = MaterialTheme.typography.labelSmall,
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
