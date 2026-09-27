package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.ApiDiscoveryEngine
import ai.drfx.maximus.matrixai.llm.ChatMessage
import ai.drfx.maximus.matrixai.llm.ConnectionStatus
import ai.drfx.maximus.matrixai.llm.ModelCapability

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderChatScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.llmState.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()

    var baseUrl by remember(state.baseUrl) {
        mutableStateOf(state.baseUrl.ifBlank { "https://api.openai.com" })
    }
    var apiKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var modelExpanded by remember { mutableStateOf(false) }
    var agentExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("LLM Chat", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Auto-detect NVIDIA NIM, OpenAI-compatible, Anthropic or Gemini APIs. Only compatible MAXIMUS agents are shown.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { baseUrl = ApiDiscoveryEngine.NVIDIA_BASE_URL },
                        label = { Text("NVIDIA NIM") }
                    )
                    AssistChip(
                        onClick = { baseUrl = "https://api.openai.com" },
                        label = { Text("OpenAI") }
                    )
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API base URL") },
                    placeholder = { Text(ApiDiscoveryEngine.NVIDIA_BASE_URL) }
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = {
                        Text(
                            if (state.hasSavedKey) "API key (saved key available)"
                            else "API key (optional for local APIs)"
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Key, null) }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { viewModel.detectApi(baseUrl, apiKey) },
                        enabled = state.status != ConnectionStatus.DETECTING
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.status == ConnectionStatus.DETECTING) "Detecting..." else "Detect API")
                    }
                    if (state.status == ConnectionStatus.CONNECTED) {
                        OutlinedButton(onClick = { viewModel.disconnectApi(); apiKey = "" }) {
                            Text("Disconnect")
                        }
                    }
                }

                StatusLine(
                    state.status.name,
                    state.statusMessage,
                    when (state.status) {
                        ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                        ConnectionStatus.DETECTING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (state.models.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = !modelExpanded }
                    ) {
                        OutlinedTextField(
                            value = state.selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            label = { Text("Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            state.models.filter { ModelCapability.CHAT in it.capabilities }.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.displayName)
                                            Text(
                                                model.capabilities.joinToString(" · ") { it.name.replace('_', ' ') },
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectModel(model.id)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (state.supportedAgents.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = agentExpanded,
                        onExpandedChange = { agentExpanded = !agentExpanded }
                    ) {
                        val selected = state.supportedAgents.firstOrNull { it.id == state.selectedAgentId }
                        OutlinedTextField(
                            value = selected?.name.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            label = { Text("Compatible agent") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(agentExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = agentExpanded,
                            onDismissRequest = { agentExpanded = false }
                        ) {
                            state.supportedAgents.forEach { agent ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(agent.name)
                                            Text(
                                                agent.description,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectAgent(agent.id)
                                        agentExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Conversation", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (messages.isNotEmpty()) {
                IconButton(onClick = viewModel::clearChat) {
                    Icon(Icons.Default.Delete, "Clear conversation")
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(20.dp)
        ) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Connect a provider, choose a model and a compatible agent, then start chatting.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(messages) { ChatBubble(it) }
                    if (state.isGenerating) {
                        item {
                            Text(
                                "Agent is generating...",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(9.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    placeholder = { Text("Message the selected agent") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (!state.isGenerating && prompt.isNotBlank()) {
                            viewModel.sendChat(prompt)
                            prompt = ""
                        }
                    })
                )
                Button(
                    onClick = { viewModel.sendChat(prompt); prompt = "" },
                    enabled = !state.isGenerating &&
                        prompt.isNotBlank() &&
                        state.status == ConnectionStatus.CONNECTED &&
                        state.selectedAgentId.isNotBlank(),
                    modifier = Modifier.size(54.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Icon(Icons.Default.Send, "Send")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val assistant = message.role == "assistant"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (assistant) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            color = when {
                message.isError -> MaterialTheme.colorScheme.errorContainer
                assistant -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            shape = RoundedCornerShape(17.dp),
            border = BorderStroke(
                1.dp,
                if (message.isError) MaterialTheme.colorScheme.error.copy(alpha = .5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = .7f)
            ),
            modifier = Modifier.fillMaxWidth(.92f)
        ) {
            Column(Modifier.padding(11.dp)) {
                Text(
                    if (assistant) "MAXIMUS AI" else "YOU",
                    color = if (message.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(message.content, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(Modifier.padding(top = 4.dp).size(8.dp), shape = CircleShape, color = color) {}
        Spacer(Modifier.width(7.dp))
        Column {
            Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }
    }
}
