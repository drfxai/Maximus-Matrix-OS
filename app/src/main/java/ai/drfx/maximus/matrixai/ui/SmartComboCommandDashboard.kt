package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.llm.LlmUiState
import ai.drfx.maximus.matrixai.llm.LlmProvider
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/** Context, local budgets and provider rate snapshots are deliberately separate quantities. */
@Composable
internal fun SmartComboCommandDashboard(state: LlmUiState, modifier: Modifier = Modifier) {
    val metrics = state.tokenMetrics
    val active = state.activeRequest?.takeIf { state.isGenerating }
    val format = NumberFormat.getIntegerInstance()
    val unknown = stringResource(R.string.api_unknown)
    val progress by animateFloatAsState(metrics.contextUsagePercent.coerceIn(0f, 1f),
        label = "combo-context-pressure")
    CommandDashboardCard(modifier.fillMaxWidth().testTag("smart_combo_command_dashboard")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(stringResource(R.string.dash_combo_title), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold)
                CommandStatus(stringResource(when {
                    active != null -> R.string.dash_request_in_use
                    state.isGenerating -> R.string.dash_request_preparing
                    else -> R.string.dash_request_idle
                }), active != null)
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(stringResource(if (state.smartComboEnabled) R.string.dash_free_first else R.string.dash_manual_route), Modifier.padding(9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (active != null) {
            CommandStatus(stringResource(R.string.dash_active_route), true)
            Text(active.provider.displayName + " · " + active.modelId,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.dash_executing_agent, active.agentName),
                style = MaterialTheme.typography.bodySmall)
        }
        Text(smartComboStatusLabel(state), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CommandMetric(stringResource(R.string.dash_session_tokens),
                format.format(metrics.consumedSessionTokens), stringResource(R.string.dash_reported_or_estimated),
                Modifier.weight(1f))
            CommandMetric(stringResource(R.string.dash_context_left),
                (if (state.selectedModel.isBlank()) unknown else format.format(metrics.remainingContextTokens)), stringResource(R.string.dash_context_estimate),
                Modifier.weight(1f))
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp),
            color = if (progress > .85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CommandMetric(stringResource(R.string.dash_provider_tokens),
                metrics.verifiedRateTokensRemaining?.let(format::format) ?: unknown,
                stringResource(R.string.dash_rate_snapshot), Modifier.weight(1f))
            CommandMetric(stringResource(R.string.dash_local_budget),
                metrics.remainingBudgetTokens?.let(format::format) ?: stringResource(R.string.dash_not_set),
                stringResource(R.string.dash_user_budget), Modifier.weight(1f))
        }
        if (metrics.rateEvidenceAtEpochMs != null) {
            Text((metrics.rateProvider?.displayName ?: unknown) + " · " +
                metrics.rateModelId.ifBlank { unknown },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.dash_snapshot_at,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(metrics.rateEvidenceAtEpochMs))),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.dash_configured_route), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text((if (state.provider == LlmProvider.UNKNOWN) unknown else state.provider.displayName) +
            " · " + state.selectedModel.ifBlank { unknown },
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        val persona = state.supportedAgents.firstOrNull { it.id == state.selectedAgentId }
        Text(stringResource(R.string.dash_selected_agent, persona?.name ?: state.selectedAgentId),
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CommandMetric(stringResource(R.string.dash_verified_routes), format.format(state.smartComboOffers),
                stringResource(R.string.dash_catalog_not_quota), Modifier.weight(1f))
            CommandMetric(stringResource(R.string.dash_provider_accounts), format.format(state.smartComboConfiguredProviders),
                stringResource(R.string.dash_configured_count), Modifier.weight(1f))
        }
        Text(stringResource(R.string.dash_last_turn_usage,
            format.format(metrics.consumedTurnTotalTokens),
            stringResource(if (metrics.turnUsageEstimated) R.string.dash_usage_estimated
                else R.string.dash_usage_reported)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (metrics.streamingEstimateActive) {
            CommandStatus(stringResource(R.string.api_estimated_output,
                format.format(metrics.streamingOutputTokensEstimate)), true)
        }
        Text(stringResource(R.string.dash_usage_scope_note), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
