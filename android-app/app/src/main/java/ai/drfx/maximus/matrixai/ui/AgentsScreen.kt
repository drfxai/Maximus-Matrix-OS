package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.AgentRegistry

@Composable
fun AgentsScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.llmState.collectAsState()
    val supportedIds = state.supportedAgents.map { it.id }.toSet()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Agent Factory", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Agents are enabled only when the selected API model exposes the capabilities they require.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        items(AgentRegistry.agents) { agent ->
            val supported = agent.id in supportedIds
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (supported) MaterialTheme.colorScheme.primary.copy(alpha = .55f)
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = if (supported) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    agent.name.take(1),
                                    color = if (supported) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(agent.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                agent.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Text(
                            if (supported) "SUPPORTED" else "UNAVAILABLE",
                            color = if (supported) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Requires: " + agent.requiredCapabilities.joinToString(" · ") { it.name.replace('_', ' ') },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    if (supported) {
                        Spacer(Modifier.height(9.dp))
                        OutlinedButton(onClick = { viewModel.selectAgent(agent.id) }) {
                            Text(if (state.selectedAgentId == agent.id) "Selected" else "Use Agent")
                        }
                    }
                }
            }
        }
    }
}
