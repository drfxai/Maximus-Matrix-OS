package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.ChatMessage
import ai.drfx.maximus.matrixai.llm.ConnectionStatus

private val ChatBg = Color(0xFF030707)
private val ChatPanel = Color(0xFF071110)
private val ChatBorder = Color(0xFF15342D)
private val ChatAccent = Color(0xFF5CF0BC)
private val ChatMuted = Color(0xFF87A29B)
private val ChatSoft = Color(0xFFB8CBC5)
private val ChatError = Color(0xFFE96E91)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderChatScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.llmState.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()

    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl.ifBlank { "https://api.openai.com" }) }
    var apiKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var modelExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChatBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Text("LLM Chat", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Connect an API. MAXIMUS AI detects the provider, discovers supported models, and routes chat requests natively.",
            color = ChatMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = ChatPanel),
            border = BorderStroke(1.dp, ChatBorder),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("API Connection", color = Color.White, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API base URL") },
                    placeholder = { Text("https://api.openai.com") },
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(if (state.hasSavedKey) "API key (saved key available)" else "API key") },
                    leadingIcon = { Icon(Icons.Default.Key, null) },
                    colors = fieldColors()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { viewModel.detectApi(baseUrl, apiKey) },
                        enabled = state.status != ConnectionStatus.DETECTING,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E3A2E), contentColor = ChatAccent)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.status == ConnectionStatus.DETECTING) "Detecting..." else "Detect API")
                    }
                    if (state.hasSavedKey || state.status == ConnectionStatus.CONNECTED) {
                        OutlinedButton(
                            onClick = {
                                viewModel.disconnectApi()
                                apiKey = ""
                            },
                            border = BorderStroke(1.dp, ChatBorder)
                        ) { Text("Disconnect", color = ChatSoft) }
                    }
                }

                StatusLine(state.status.name, state.statusMessage, when (state.status) {
                    ConnectionStatus.CONNECTED -> ChatAccent
                    ConnectionStatus.ERROR -> ChatError
                    ConnectionStatus.DETECTING -> Color(0xFFE4B34D)
                    else -> ChatMuted
                })

                if (state.models.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = !modelExpanded }
                    ) {
                        OutlinedTextField(
                            value = state.selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            label = { Text("Supported model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                            colors = fieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            state.models.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.displayName)
                                            if (model.displayName != model.id) {
                                                Text(model.id, color = ChatMuted, fontSize = 11.sp)
                                            }
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

                    Text(
                        "Detected provider: " + state.provider.name.replace('_', ' '),
                        color = ChatMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Conversation", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (messages.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearChat() }) {
                    Icon(Icons.Default.Delete, "Clear conversation", tint = ChatMuted)
                }
            }
        }

        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ChatPanel),
            border = BorderStroke(1.dp, ChatBorder),
            shape = RoundedCornerShape(22.dp)
        ) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No messages yet. Connect a provider and start a conversation.",
                        color = ChatMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { message -> ChatBubble(message) }
                    if (state.isGenerating) {
                        item {
                            Text("Model is generating...", color = ChatAccent, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Surface(
            color = ChatPanel,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, ChatBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    placeholder = { Text("Message " + (state.selectedModel.ifBlank { "the model" })) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (!state.isGenerating && prompt.isNotBlank()) {
                            viewModel.sendChat(prompt)
                            prompt = ""
                        }
                    }),
                    colors = fieldColors()
                )
                Button(
                    onClick = {
                        viewModel.sendChat(prompt)
                        prompt = ""
                    },
                    enabled = !state.isGenerating && prompt.isNotBlank() && state.status == ConnectionStatus.CONNECTED,
                    modifier = Modifier.size(54.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E3A2E), contentColor = ChatAccent)
                ) {
                    Icon(Icons.Default.Send, "Send")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val assistant = message.role == "assistant"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (assistant) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            color = when {
                message.isError -> Color(0xFF2B1018)
                assistant -> Color(0xFF0A1714)
                else -> Color(0xFF103126)
            },
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, if (message.isError) ChatError.copy(alpha = .5f) else ChatBorder),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (assistant) "MAXIMUS AI" else "YOU",
                    color = if (message.isError) ChatError else ChatAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(message.content, color = ChatSoft, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text, color = ChatMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = ChatAccent,
    unfocusedBorderColor = ChatBorder,
    focusedLabelColor = ChatAccent,
    unfocusedLabelColor = ChatMuted,
    cursorColor = ChatAccent
)
