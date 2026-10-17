package ai.drfx.maximus.matrixai.ui

import androidx.compose.ui.res.stringResource

import ai.drfx.maximus.matrixai.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.ConnectionStatus
import ai.drfx.maximus.matrixai.llm.LlmProvider
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiControlScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.llmState.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val metrics = state.tokenMetrics
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    var planLabel by remember(state.subscriptionLabel) { mutableStateOf(state.subscriptionLabel) }
    var budgetUsd by remember(state.monthlyBudgetUsd) {
        mutableStateOf(if (state.monthlyBudgetUsd > 0) state.monthlyBudgetUsd.toString() else "")
    }
    var tokenBudgetInput by remember(state.monthlyTokenBudget) {
        mutableStateOf(if (state.monthlyTokenBudget > 0) state.monthlyTokenBudget.toString() else "")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(stringResource(R.string.api_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.api_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        item { SmartComboCommandDashboard(state) }
        item {
            CommandDashboardCard {
                Text(stringResource(R.string.api_presets), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.api_provider_presets_info),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val providers = listOf(LlmProvider.GEMINI, LlmProvider.NVIDIA,
                    LlmProvider.ROUTER_9_SMART, LlmProvider.ROUTER_9_COMBO,
                    LlmProvider.OPENAI, LlmProvider.ANTHROPIC)
                providers.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { provider ->
                            val selected = state.provider == provider
                            val tone by androidx.compose.animation.animateColorAsState(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow, label = "provider-card")
                            Surface(onClick = { viewModel.applyProviderPreset(provider) },
                                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                                color = tone, border = BorderStroke(1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.Memory,
                                        null, tint = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(provider.displayName, style = MaterialTheme.typography.labelLarge)
                                    if (selected) Text(stringResource(R.string.motion_selected),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            ControlCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.api_combo_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(stringResource(R.string.api_combo_desc),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MotionSwitch(checked = state.smartComboEnabled, onCheckedChange = viewModel::setSmartComboEnabled,
                        enabled = !state.isGenerating)
                }
                Text(smartComboStatusLabel(state), fontSize = 12.sp,
                    color = if (state.smartComboEnabled && state.smartComboOffers > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.api_verified_offers, state.smartComboOffers), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = viewModel::refreshSmartCombo,
                        enabled = state.smartComboEnabled && !state.smartComboRefreshing && !state.isGenerating
                    ) { Text(stringResource(R.string.api_refresh), fontSize = 12.sp) }
                }
                if (state.smartComboRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.smartComboEnabled && state.smartComboCatalogChecks.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.api_provider_results), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.smartComboCatalogChecks.forEach { check ->
                        Text("• " + smartComboProviderLabel(check), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.api_pricing_warning),
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.smartComboRouteLabels.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.api_free_routes), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.smartComboRouteLabels.forEach { label ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp))
                            Text(label, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    if (state.smartComboOffers > state.smartComboRouteLabels.size) {
                        Text(stringResource(R.string.api_visible_offers,
                            state.smartComboRouteLabels.size, state.smartComboOffers),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.api_free_evidence_note),
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.api_free_only_caution),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Token Telemetry & Context Window Card
        item {
            ControlCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DataUsage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.api_usage), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = stringResource(R.string.api_estimated_limit,
                                numberFormat.format(metrics.contextCapacity)),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Visual context progress meter
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.api_context_used),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${(metrics.contextUsagePercent * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (metrics.contextUsagePercent > 0.85f) MaterialTheme.colorScheme.error
                            else if (metrics.contextUsagePercent > 0.6f) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { metrics.contextUsagePercent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (metrics.contextUsagePercent > 0.85f) MaterialTheme.colorScheme.error
                        else if (metrics.contextUsagePercent > 0.6f) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(stringResource(R.string.api_usage_estimate_info), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (metrics.streamingEstimateActive) {
                    MetricLine(
                        stringResource(R.string.api_live_output),
                        stringResource(R.string.api_estimated_output,
                            numberFormat.format(metrics.streamingOutputTokensEstimate)),
                        MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        stringResource(R.string.api_streaming_estimate_warning),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (metrics.rateEvidenceAtEpochMs != null) {
                    MetricLine(
                        stringResource(R.string.api_rate_requests,
                            metrics.verifiedRequestRateKind),
                        metrics.verifiedRateRequestsRemaining?.let(numberFormat::format) ?: stringResource(R.string.api_unknown),
                        MaterialTheme.colorScheme.primary
                    )
                    MetricLine(
                        stringResource(R.string.api_rate_tokens),
                        metrics.verifiedRateTokensRemaining?.let(numberFormat::format) ?: stringResource(R.string.api_unknown),
                        MaterialTheme.colorScheme.primary
                    )
                    Text(stringResource(R.string.api_provider_snapshot),
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    MetricLine(
                        stringResource(R.string.api_rate_account),
                        stringResource(R.string.api_rate_no_headers),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Key Metric Grid: Consumed vs Remaining
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        title = stringResource(R.string.api_stat_turn),
                        value = numberFormat.format(metrics.consumedTurnTotalTokens),
                        subtext = stringResource(R.string.api_stat_in_out,
                            numberFormat.format(metrics.consumedTurnInputTokens),
                            numberFormat.format(metrics.consumedTurnOutputTokens)),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = stringResource(R.string.api_stat_context_left),
                        value = numberFormat.format(metrics.remainingContextTokens),
                        subtext = stringResource(R.string.api_stat_max, numberFormat.format(metrics.contextCapacity)),
                        valueColor = Color(0xFF00E676),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        title = stringResource(R.string.api_stat_session),
                        value = numberFormat.format(metrics.consumedSessionTokens),
                        subtext = stringResource(R.string.api_stat_active_turns),
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = stringResource(R.string.api_stat_lifetime),
                        value = numberFormat.format(usage.totalTokens),
                        subtext = stringResource(R.string.api_stat_completed_requests, numberFormat.format(usage.requests)),
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (metrics.remainingBudgetTokens != null) {
                    MetricLine(
                        stringResource(R.string.api_local_budget_remaining),
                        "${numberFormat.format(metrics.remainingBudgetTokens)} / ${numberFormat.format(metrics.monthlyTokenBudget)}",
                        if (metrics.remainingBudgetTokens < (metrics.monthlyTokenBudget * 0.15)) MaterialTheme.colorScheme.error
                        else Color(0xFF00E676)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = viewModel::clearUsage,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.api_reset), fontSize = 11.sp)
                    }
                }
            }
        }

        // Active Connection & Model Detail Card
        item {
            ControlCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.api_status), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                MetricLine(
                    stringResource(R.string.api_status_label),
                    state.status.name,
                    if (state.status == ConnectionStatus.CONNECTED) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary
                )
                MetricLine(stringResource(R.string.api_provider_label), state.provider.displayName, MaterialTheme.colorScheme.onSurface)
                MetricLine(stringResource(R.string.api_model_label), state.selectedModel.ifBlank { stringResource(R.string.api_none) }, MaterialTheme.colorScheme.onSurface)
                MetricLine(stringResource(R.string.api_model_limit), stringResource(R.string.api_token_count, numberFormat.format(metrics.contextCapacity)), MaterialTheme.colorScheme.primary)
                MetricLine(stringResource(R.string.api_personas_label), state.supportedAgents.size.toString(), MaterialTheme.colorScheme.onSurface)
                MetricLine(stringResource(R.string.api_endpoint_label), state.baseUrl.ifBlank { stringResource(R.string.api_default_endpoint) }, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Budget & Quota Metadata
        item {
            ControlCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.api_budget), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Text(
                    stringResource(R.string.api_budget_info),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = tokenBudgetInput,
                    onValueChange = { value -> tokenBudgetInput = value.filter { it.isDigit() }.take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.api_local_budget_input)) },
                    placeholder = { Text(stringResource(R.string.api_unlimited)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = planLabel,
                    onValueChange = { planLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.api_subscription_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = budgetUsd,
                    onValueChange = { value -> budgetUsd = value.filter { it.isDigit() || it == '.' }.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.api_usd_planning)) },
                    singleLine = true
                )
                Button(
                    onClick = {
                        val usd = budgetUsd.toDoubleOrNull() ?: 0.0
                        val tokens = tokenBudgetInput.toLongOrNull() ?: 0L
                        viewModel.saveSubscription(planLabel.trim(), usd)
                        viewModel.saveTokenBudget(tokens)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.api_save_quotas))
                }
            }
        }
    }
}

@Composable
private fun ProviderPresetChip(
    name: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = accentColor,
                modifier = Modifier.size(8.dp)
            ) {}
            Spacer(Modifier.width(6.dp))
            Text(
                name,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatBox(
    title: String,
    value: String,
    subtext: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
            Text(subtext, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ControlCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun MetricLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(value, modifier = Modifier.weight(1f), color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
