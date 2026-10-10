package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("AI Engine & Token Telemetry", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Provider-specific connections with authenticated model discovery and explicitly estimated context metrics.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        // Quick Provider Presets Carousel
        item {
            ControlCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Provider Quick Presets", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Text(
                    "Select an isolated provider configuration; model availability requires authenticated discovery.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProviderPresetChip(
                        name = "Google Gemini",
                        selected = state.provider == LlmProvider.GEMINI,
                        accentColor = Color(0xFF4285F4),
                        onClick = { viewModel.applyProviderPreset(LlmProvider.GEMINI) }
                    )
                    ProviderPresetChip(
                        name = "9Router",
                        selected = state.provider == LlmProvider.ROUTER_9_SMART,
                        accentColor = Color(0xFF00E5FF),
                        onClick = { viewModel.applyProviderPreset(LlmProvider.ROUTER_9_SMART) }
                    )
                    ProviderPresetChip(
                        name = "9Router (server combo)",
                        selected = state.provider == LlmProvider.ROUTER_9_COMBO,
                        accentColor = Color(0xFF7C4DFF),
                        onClick = { viewModel.applyProviderPreset(LlmProvider.ROUTER_9_COMBO) }
                    )
                    ProviderPresetChip(
                        name = "NVIDIA NIM",
                        selected = state.provider == LlmProvider.NVIDIA,
                        accentColor = Color(0xFF76B900),
                        onClick = { viewModel.applyProviderPreset(LlmProvider.NVIDIA) }
                    )
                    ProviderPresetChip(
                        name = "OpenAI",
                        selected = state.provider == LlmProvider.OPENAI,
                        accentColor = Color(0xFF10A37F),
                        onClick = { viewModel.applyProviderPreset(LlmProvider.OPENAI) }
                    )
                    ProviderPresetChip(
                        name = "Claude",
                        selected = state.provider == LlmProvider.ANTHROPIC,
                        accentColor = Color(0xFFD97706),
                        onClick = { viewModel.applyProviderPreset(LlmProvider.ANTHROPIC) }
                    )
                }
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
                        Text("Token Usage & Estimated Capacity", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "${numberFormat.format(metrics.contextCapacity)} estimated limit",
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
                            "Estimated Active Context Used",
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
                        progress = { metrics.contextUsagePercent.coerceIn(0.01f, 1f) },
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

                Text("Turn usage may be estimated when the provider omits usage. Context is a local estimate; provider quota is unavailable.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Key Metric Grid: Consumed vs Remaining
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        title = "CONSUMED (TURN)",
                        value = numberFormat.format(metrics.consumedTurnTotalTokens),
                        subtext = "In: ${numberFormat.format(metrics.consumedTurnInputTokens)} · Out: ${numberFormat.format(metrics.consumedTurnOutputTokens)}",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "EST. CONTEXT LEFT",
                        value = numberFormat.format(metrics.remainingContextTokens),
                        subtext = "Max: ${numberFormat.format(metrics.contextCapacity)}",
                        valueColor = Color(0xFF00E676),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        title = "SESSION CONSUMED",
                        value = numberFormat.format(metrics.consumedSessionTokens),
                        subtext = "Active chat turns",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "LIFETIME CONSUMED",
                        value = numberFormat.format(usage.totalTokens),
                        subtext = "${numberFormat.format(usage.requests)} completed API requests",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (metrics.remainingBudgetTokens != null) {
                    MetricLine(
                        "Local Lifetime Budget Remaining (not provider quota)",
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
                        Text("Reset token statistics", fontSize = 11.sp)
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
                    Text("Connection & Engine Status", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                MetricLine(
                    "Status",
                    state.status.name,
                    if (state.status == ConnectionStatus.CONNECTED) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary
                )
                MetricLine("Engine Provider", state.provider.displayName, MaterialTheme.colorScheme.onSurface)
                MetricLine("Active Model", state.selectedModel.ifBlank { "None" }, MaterialTheme.colorScheme.onSurface)
                MetricLine("Estimated Model Context Limit", "${numberFormat.format(metrics.contextCapacity)} tokens", MaterialTheme.colorScheme.primary)
                MetricLine("Conversational Personas", state.supportedAgents.size.toString(), MaterialTheme.colorScheme.onSurface)
                MetricLine("Base Endpoint", state.baseUrl.ifBlank { "Default Provider URL" }, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Budget & Quota Metadata
        item {
            ControlCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Quotas & Budgets", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Text(
                    "Configure local usage tracking. Provider account quotas are not enforced here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = tokenBudgetInput,
                    onValueChange = { value -> tokenBudgetInput = value.filter { it.isDigit() }.take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Monthly Token Quota (e.g., 1000000)") },
                    placeholder = { Text("Leave blank for unlimited") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = planLabel,
                    onValueChange = { planLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Plan / subscription label") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = budgetUsd,
                    onValueChange = { value -> budgetUsd = value.filter { it.isDigit() || it == '.' }.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Monthly USD budget limit") },
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
                    Text("Save Quotas")
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
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
