package ai.drfx.maximus.matrixai.ui

import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.llm.LlmProvider
import ai.drfx.maximus.matrixai.llm.LlmUiState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

/** Three stable columns keep provider, agent and usage visible without a toolbar stack. */
@Composable
internal fun ConversationTelemetryRibbon(
    state: LlmUiState,
    onProviderSettings: () -> Unit,
    onTokenDetails: () -> Unit
) {
    val dispatched = state.activeRequest?.takeIf { state.isGenerating }
    val provider = dispatched?.provider ?: state.provider
    val model = dispatched?.modelId ?: state.selectedModel
    val agent = dispatched?.agentName ?: state.supportedAgents
        .firstOrNull { it.id == state.selectedAgentId }?.name.orEmpty()
    val metrics = state.tokenMetrics
    val format = NumberFormat.getNumberInstance(Locale.US)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            RibbonTile(
                if (provider == LlmProvider.UNKNOWN) stringResource(R.string.focus_unknown) else provider.displayName,
                model.ifBlank { "—" }, agent.ifBlank { "—" },
                Modifier.weight(1.65f), onProviderSettings
            )
            RibbonTile(
                stringResource(when {
                    metrics.streamingEstimateActive -> R.string.focus_tokens_live
                    metrics.turnUsageEstimated -> R.string.focus_tokens_estimated
                    else -> R.string.focus_tokens_reported
                }),
                if (metrics.streamingEstimateActive)
                    stringResource(R.string.focus_live_output, format.format(metrics.streamingOutputTokensEstimate))
                else stringResource(R.string.focus_used, format.format(metrics.consumedTurnTotalTokens)),
                if (model.isBlank()) stringResource(R.string.focus_unknown)
                else stringResource(R.string.focus_context_left, format.format(metrics.remainingContextTokens)),
                Modifier.weight(1f), onTokenDetails
            )
        }
    }
}

@Composable
private fun RibbonTile(label: String, value: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
