package ai.drfx.maximus.matrixai.ui

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
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
import ai.drfx.maximus.matrixai.llm.LlmProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

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
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    var recording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    val recorder = remember(context) { VoiceMessageRecorder(context) }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    fun startRecording() {
        try {
            recorder.start()
            recording = true
            recordingSeconds = 0
            attachmentError = null
        } catch (e: Exception) {
            attachmentError = "Microphone recording could not start."
        }
    }
    fun finishRecording() {
        try {
            attachment = recorder.stop()
            attachmentError = null
        } catch (error: Exception) {
            attachmentError = error.message ?: "Voice recording could not be saved."
        } finally {
            recording = false
        }
    }
    LaunchedEffect(recording) {
        if (recording) {
            repeat(60) {
                delay(1_000)
                if (!recording) return@LaunchedEffect
                recordingSeconds = it + 1
            }
            if (recording) finishRecording()
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else attachmentError = "Microphone permission is required to record a voice message."
    }
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
        if (viewModel.sendChat(text, attachment, fromVoice)) {
            prompt = ""
            attachment = null
            attachmentError = null
        }
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
            if (!transcript.isNullOrBlank()) {
                if (state.status == ConnectionStatus.CONNECTED) send(transcript, fromVoice = true)
                else prompt = transcript
            }
            else attachmentError = "No speech was recognized."
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact unified header: Model info, settings trigger, voice toggle, clear conversation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f).clickable { settingsOpen = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = if (state.status == ConnectionStatus.CONNECTED) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (state.status == ConnectionStatus.CONNECTED) state.selectedModel
                            else "Connect AI Provider",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Text(
                            text = if (state.status == ConnectionStatus.CONNECTED)
                                state.supportedAgents.firstOrNull { it.id == state.selectedAgentId }?.name.orEmpty()
                            else state.statusMessage,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    Icon(Icons.Default.Tune, "Provider settings", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { voiceReplies = !voiceReplies; if (!voiceReplies) tts?.stop() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (voiceReplies) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        if (voiceReplies) "Disable spoken replies" else "Enable spoken replies",
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (messages.isNotEmpty()) {
                    IconButton(
                        onClick = viewModel::clearChat,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, "Clear conversation", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Conversation messages area
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Connect a provider, choose a model and a compatible agent, then start chatting.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        ChatBubble(message, onSpeak = {
                            if (speechReady) tts?.speak(message.content, TextToSpeech.QUEUE_FLUSH,
                                null, "replay-" + message.timestampMs)
                        }, onPlayVoice = {
                            message.attachment?.let { voice ->
                                runCatching { playVoiceMessage(context, voice) }
                                    .onFailure { attachmentError = "Voice playback is unavailable." }
                            }
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
                label = { Text(attachment!!.name +
                    (if (attachment!!.mimeType.startsWith("audio/")) " · " + attachment!!.durationMs / 1_000 + "s" else "") +
                    "  ·  remove", maxLines = 1, fontSize = 11.sp) },
                leadingIcon = { Icon(if (attachment!!.mimeType.startsWith("audio/"))
                    Icons.Default.Mic else Icons.Default.AttachFile, null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.height(28.dp))
        }
        if (attachment?.mimeType?.startsWith("audio/") == true &&
            state.provider != LlmProvider.GEMINI && state.provider != LlmProvider.OPENAI) {
            Text("Recorded audio requires Gemini or OpenAI. Use Dictate with this provider.",
                color = MaterialTheme.colorScheme.tertiary, fontSize = 10.sp)
        }
        if (recording) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recording voice message · " + recordingSeconds + "s / 60s",
                    color = MaterialTheme.colorScheme.error, fontSize = 11.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { recorder.cancel(); recording = false }) { Text("Discard", fontSize = 11.sp) }
            }
        }
        if (attachmentError != null) {
            Text(attachmentError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }

        // Compact message input field; screen-level IME insets keep it above the keyboard.
        Surface(
            modifier = Modifier.semantics { contentDescription = "Chat composer" },
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Chat input" },
                    maxLines = 3,
                    enabled = !recording,
                    placeholder = { Text("Message the selected agent…", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { imagePicker.launch("image/*") }, enabled = !recording, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Image, "Attach image", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { filePicker.launch(arrayOf("text/*", "application/pdf", "application/json")) }, enabled = !recording, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.AttachFile, "Attach document", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
                        try { voiceInput.launch(intent) }
                        catch (e: Exception) { attachmentError = "Speech recognition is unavailable on this device." }
                    }, enabled = !recording, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardVoice, "Dictate text message", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        if (recording) finishRecording()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED) startRecording()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(if (recording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord,
                            if (recording) "Finish voice message" else "Record voice message",
                            tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { send() },
                        enabled = !recording && !state.isGenerating && (prompt.isNotBlank() || attachment != null) &&
                            state.status == ConnectionStatus.CONNECTED && state.selectedAgentId.isNotBlank() &&
                            (attachment?.mimeType?.startsWith("audio/") != true ||
                                state.provider == LlmProvider.GEMINI || state.provider == LlmProvider.OPENAI),
                        modifier = Modifier.size(36.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Send, "Send message", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
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
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("API Endpoint", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.hasSavedKey) {
                        Text(
                            "Key saved securely",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            "Key will be saved",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Button(onClick = { viewModel.detectApi(baseUrl, apiKey) }) {
                        Icon(Icons.Default.Refresh, "Detect API")
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.status == ConnectionStatus.DETECTING) "Detecting..." else "Detect")
                    }
                }
            }
        }
                    }

                    if (state.models.isNotEmpty()) {
                        item {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Model & Agent Selection", fontWeight = FontWeight.SemiBold)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { modelExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(state.selectedModel.ifBlank { "Select model" }, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        state.models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.id) },
                                onClick = {
                                    viewModel.selectModel(model.id)
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { agentExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            state.supportedAgents.firstOrNull { it.id == state.selectedAgentId }?.name
                                ?: "Select agent",
                            maxLines = 1
                        )
                    }
                    DropdownMenu(
                        expanded = agentExpanded,
                        onDismissRequest = { agentExpanded = false }
                    ) {
                        state.supportedAgents.forEach { agent ->
                            DropdownMenuItem(
                                text = { Text(agent.name) },
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
                        OutlinedButton(
                            onClick = {
                                viewModel.disconnectApi()
                                apiKey = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect & clear API key")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onSpeak: () -> Unit, onPlayVoice: () -> Unit) {
    val isUser = message.role == "user"
    val background = if (message.isError) MaterialTheme.colorScheme.errorContainer
    else if (isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isError) MaterialTheme.colorScheme.onErrorContainer
    else if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = background,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                message.attachment?.let { attachment ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (attachment.mimeType.startsWith("audio/")) Icons.Default.Mic else Icons.Default.AttachFile, null,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(attachment.name, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            if (attachment.mimeType.startsWith("audio/")) {
                                IconButton(onClick = onPlayVoice, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.PlayArrow, "Play voice note", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                Text(message.content, color = textColor, fontSize = 13.sp)
                if (!isUser && !message.isError) {
                    IconButton(onClick = onSpeak, modifier = Modifier.size(22.dp).align(Alignment.End)) {
                        Icon(Icons.Default.VolumeUp, "Read aloud", tint = textColor, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

private fun playVoiceMessage(context: Context, attachment: ChatAttachment) {
    val tempFile = File.createTempFile("voice_playback", ".m4a", context.cacheDir)
    tempFile.writeBytes(Base64.decode(attachment.data, Base64.DEFAULT))
    MediaPlayer().apply {
        setDataSource(tempFile.absolutePath)
        prepare()
        start()
        setOnCompletionListener {
            it.release()
            tempFile.delete()
        }
    }
}

private fun loadChatAttachment(context: Context, uri: Uri): ChatAttachment {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    var name = "attachment"
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index)
        }
    }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()
    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    val isText = mimeType.startsWith("text/") || mimeType == "application/json"
    return ChatAttachment(
        name = name,
        mimeType = mimeType,
        data = base64,
        isText = isText
    )
}

