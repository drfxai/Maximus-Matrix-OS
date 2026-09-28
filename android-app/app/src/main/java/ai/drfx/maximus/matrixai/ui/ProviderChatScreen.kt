package ai.drfx.maximus.matrixai.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.ApiDiscoveryEngine
import ai.drfx.maximus.matrixai.llm.ChatMessage
import ai.drfx.maximus.matrixai.llm.ChatAttachment
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settingsOpen by remember { mutableStateOf(false) }
    var attachment by remember { mutableStateOf<ChatAttachment?>(null) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var voiceReplies by remember { mutableStateOf(false) }
    var speechReady by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { result -> speechReady = result == TextToSpeech.SUCCESS }
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }
    LaunchedEffect(messages.lastOrNull()?.timestampMs, voiceReplies, speechReady) {
        val last = messages.lastOrNull()
        if (voiceReplies && speechReady && last?.role == "assistant" && !last.isError) {
            tts?.speak(last.content, TextToSpeech.QUEUE_FLUSH, null, "reply-" + last.timestampMs)
        }
    }
    fun send(text: String = prompt, fromVoice: Boolean = false) {
        if (state.isGenerating || state.status != ConnectionStatus.CONNECTED ||
            (text.isBlank() && attachment == null)) return
        viewModel.sendChat(text, attachment, fromVoice)
        prompt = ""
        attachment = null
        attachmentError = null
    }
    fun load(uri: Uri) {
        scope.launch {
            runCatching { loadChatAttachment(context, uri) }
                .onSuccess { attachment = it; attachmentError = null }
                .onFailure { attachmentError = it.message ?: "Could not read the file." }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::load)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::load)
    }
    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!transcript.isNullOrBlank()) send(transcript, fromVoice = true)
            else attachmentError = "No speech was recognized."
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("LLM + AGENT", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold)
                Text("Private session · model and agent in sync",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Row {
                IconButton(onClick = { voiceReplies = !voiceReplies; if (!voiceReplies) tts?.stop() }) {
                    Icon(if (voiceReplies) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        if (voiceReplies) "Disable spoken replies" else "Enable spoken replies")
                }
                IconButton(onClick = { settingsOpen = true }) {
                    Icon(Icons.Default.Tune, "Provider, model and agent settings")
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().clickable { settingsOpen = true }) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(9.dp), shape = CircleShape,
                    color = if (state.status == ConnectionStatus.CONNECTED) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary) {}
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (state.status == ConnectionStatus.CONNECTED) state.selectedModel
                         else "Connect an AI provider", fontWeight = FontWeight.SemiBold,
                         fontSize = 13.sp, maxLines = 1)
                    Text(if (state.status == ConnectionStatus.CONNECTED)
                        state.supportedAgents.firstOrNull { it.id == state.selectedAgentId }?.name.orEmpty()
                        else state.statusMessage, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp, maxLines = 1)
                }
                Icon(Icons.Default.Tune, "Provider settings")
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
                    items(messages) { message ->
                        ChatBubble(message, onSpeak = {
                            if (speechReady) tts?.speak(message.content, TextToSpeech.QUEUE_FLUSH,
                                null, "replay-" + message.timestampMs)
                        })
                    }
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

        if (attachment != null) {
            AssistChip(onClick = { attachment = null },
                label = { Text(attachment!!.name + "  ·  remove", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.AttachFile, null) })
        }
        if (attachmentError != null) {
            Text(attachmentError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(8.dp)) {
                OutlinedTextField(value = prompt, onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3,
                    placeholder = { Text("Message the selected agent…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, "Attach image")
                    }
                    IconButton(onClick = { filePicker.launch(arrayOf("text/*", "application/pdf", "application/json")) }) {
                        Icon(Icons.Default.AttachFile, "Attach document")
                    }
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
                        try { voiceInput.launch(intent) }
                        catch (_: Exception) { attachmentError = "Speech recognition is unavailable on this device." }
                    }) { Icon(Icons.Default.Mic, "Speak a message") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { send() },
                        enabled = !state.isGenerating && (prompt.isNotBlank() || attachment != null) &&
                            state.status == ConnectionStatus.CONNECTED && state.selectedAgentId.isNotBlank(),
                        modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(15.dp)) {
                        Icon(Icons.Default.Send, "Send message")
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Provider & agent", style = MaterialTheme.typography.headlineSmall)
                Text("Configure the API, model, and compatible agent.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
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

                    }
                    item {
                        Button(onClick = { settingsOpen = false }, Modifier.fillMaxWidth()) {
                            Text("Back to conversation")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onSpeak: () -> Unit) {
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
                    if (assistant) "MAXIMUS · AGENT" else if (message.fromVoice) "YOU · VOICE" else "YOU",
                    color = if (message.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                if (message.attachment != null) {
                    Text("Attached: " + message.attachment.name,
                        color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
                Text(message.content, fontSize = 13.sp, lineHeight = 19.sp)
                if (assistant && !message.isError) {
                    TextButton(onClick = onSpeak, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.VolumeUp, "Speak response", Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Listen", fontSize = 11.sp)
                    }
                }
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


private suspend fun loadChatAttachment(context: Context, uri: Uri): ChatAttachment = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri)?.lowercase() ?: "application/octet-stream"
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.take(80) ?: "attachment"
    val isText = mime.startsWith("text/") || mime in setOf("application/json", "application/xml") ||
        name.lowercase().endsWith(".md") || name.lowercase().endsWith(".csv")
    if (!isText && !mime.startsWith("image/") && mime != "application/pdf")
        throw IllegalArgumentException("Choose an image, PDF, or text document.")
    if (mime.startsWith("image/") && mime !in setOf("image/jpeg", "image/png", "image/webp", "image/gif"))
        throw IllegalArgumentException("Use a JPEG, PNG, WebP, or GIF image.")
    val limit = if (isText) 128 * 1024 else 3 * 1024 * 1024
    val bytes = resolver.openInputStream(uri)?.use { stream ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw IllegalArgumentException(
                if (isText) "Text files must be under 128 KB." else "Images and PDFs must be under 3 MB.")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
        ?: throw IllegalArgumentException("The selected file could not be opened.")
    if (bytes.isEmpty()) throw IllegalArgumentException("The selected file is empty.")
    ChatAttachment(name, mime, if (isText) bytes.toString(Charsets.UTF_8)
        else Base64.encodeToString(bytes, Base64.NO_WRAP), isText)
}
