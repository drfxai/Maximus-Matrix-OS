package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.ConnectionStatus

@Composable
fun ApiControlScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.llmState.collectAsState()
    val usage by viewModel.usage.collectAsState()
    var planLabel by remember(state.subscriptionLabel) { mutableStateOf(state.subscriptionLabel) }
    var budget by remember(state.monthlyBudgetUsd) { mutableStateOf(if (state.monthlyBudgetUsd > 0) state.monthlyBudgetUsd.toString() else "") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("API Control", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Manage the active provider, local subscription metadata and measured token usage. MAXIMUS AI does not invent provider billing data.",
                color = Color(0xFF87A29B), fontSize = 12.sp, lineHeight = 17.sp
            )
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF071110), border = BorderStroke(1.dp, Color(0xFF15342D))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricLine("Connection", state.status.name, if (state.status == ConnectionStatus.CONNECTED) Color(0xFF5CF0BC) else Color(0xFFE4B34D))
                    MetricLine("Provider", state.provider.name.replace('_', ' '), Color.White)
                    MetricLine("Model", state.selectedModel.ifBlank { "None" }, Color.White)
                    MetricLine("Compatible agents", state.supportedAgents.size.toString(), Color.White)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF071110), border = BorderStroke(1.dp, Color(0xFF15342D))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usage", color = Color.White, fontWeight = FontWeight.SemiBold)
                    MetricLine("Requests", usage.requests.toString(), Color.White)
                    MetricLine("Input tokens", usage.inputTokens.toString(), Color.White)
                    MetricLine("Output tokens", usage.outputTokens.toString(), Color.White)
                    MetricLine("Total tokens", usage.totalTokens.toString(), Color(0xFF5CF0BC))
                    MetricLine("Estimated responses", usage.estimatedResponses.toString(), if (usage.estimatedResponses > 0) Color(0xFFE4B34D) else Color.White)
                    if (state.models.size > 1 && state.selectedAgentId.isNotBlank()) {
                        Text(
                            "Optimization: use the least expensive model in this API that still exposes every capability required by the selected agent.",
                            color = Color(0xFF87A29B), fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                    OutlinedButton(onClick = viewModel::clearUsage, border = BorderStroke(1.dp, Color(0xFF15342D))) {
                        Text("Reset local usage counters", color = Color(0xFFB8CBC5))
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF071110), border = BorderStroke(1.dp, Color(0xFF15342D))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Subscription Metadata", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Stored locally for planning only. Provider billing remains authoritative.", color = Color(0xFF87A29B), fontSize = 10.sp)
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
                    Button(
                        onClick = { viewModel.saveSubscription(planLabel.trim(), budget.toDoubleOrNull() ?: 0.0) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E3A2E), contentColor = Color(0xFF5CF0BC))
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF87A29B), fontSize = 11.sp)
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
