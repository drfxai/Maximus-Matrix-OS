package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.ConnectionStatus

@Composable
fun ApiControlScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.llmState.collectAsState()
    val usage by viewModel.usage.collectAsState()
    var planLabel by remember(state.subscriptionLabel) { mutableStateOf(state.subscriptionLabel) }
    var budget by remember(state.monthlyBudgetUsd) {
        mutableStateOf(if (state.monthlyBudgetUsd > 0) state.monthlyBudgetUsd.toString() else "")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("API Control", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Manage the active provider, local subscription metadata and measured token usage. Provider billing remains authoritative.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        item {
            ControlCard {
                MetricLine(
                    "Connection",
                    state.status.name,
                    if (state.status == ConnectionStatus.CONNECTED) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary
                )
                MetricLine("Provider", state.provider.name.replace('_', ' '), MaterialTheme.colorScheme.onSurface)
                MetricLine("Model", state.selectedModel.ifBlank { "None" }, MaterialTheme.colorScheme.onSurface)
                MetricLine("Compatible agents", state.supportedAgents.size.toString(), MaterialTheme.colorScheme.onSurface)
            }
        }
        item {
            ControlCard {
                Text("Usage", fontWeight = FontWeight.SemiBold)
                MetricLine("Requests", usage.requests.toString(), MaterialTheme.colorScheme.onSurface)
                MetricLine("Input tokens", usage.inputTokens.toString(), MaterialTheme.colorScheme.onSurface)
                MetricLine("Output tokens", usage.outputTokens.toString(), MaterialTheme.colorScheme.onSurface)
                MetricLine("Total tokens", usage.totalTokens.toString(), MaterialTheme.colorScheme.primary)
                MetricLine(
                    "Estimated responses",
                    usage.estimatedResponses.toString(),
                    if (usage.estimatedResponses > 0) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface
                )
                if (state.models.size > 1 && state.selectedAgentId.isNotBlank()) {
                    Text(
                        "Optimization: use the least expensive model that still exposes every capability required by the selected agent.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
                OutlinedButton(onClick = viewModel::clearUsage) {
                    Text("Reset local usage counters")
                }
            }
        }
        item {
            ControlCard {
                Text("Subscription Metadata", fontWeight = FontWeight.SemiBold)
                Text(
                    "Stored locally for planning only.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                OutlinedTextField(
                    value = planLabel,
                    onValueChange = { planLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Plan / subscription label") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = budget,
                    onValueChange = { value -> budget = value.filter { it.isDigit() || it == '.' }.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Monthly budget (USD)") },
                    singleLine = true
                )
                Button(onClick = { viewModel.saveSubscription(planLabel.trim(), budget.toDoubleOrNull() ?: 0.0) }) {
                    Text("Save")
                }
            }
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
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun MetricLine(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
