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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderChatScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.llmState.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val sessions by viewModel.chatSessions.collectAsState()
    val activeSessionId by viewModel.activeChatSessionId.collectAsState()
    var sessionsOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var sessionTitle by remember { mutableStateOf("") }
    val sendable = state.status in setOf(ConnectionStatus.CONNECTED, ConnectionStatus.CONFIGURED, ConnectionStatus.DEGRADED)
    val metrics = state.tokenMetrics
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    val clipboardManager = LocalClipboardManager.current

    var baseUrl by remember(state.baseUrl) {
        mutableStateOf(state.baseUrl.ifBlank { state.provider.defaultBaseUrl })
    }
    var apiKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var modelExpanded by remember { mutableStateOf(false) }
    var agentExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settingsOpen by remember { mutableStateOf(false) }
    var tokenDetailsOpen by remember { mutableStateOf(false) }
    var quickKeyDialogOpen by remember { mutableStateOf(false) }
    var quickKeyTargetProvider by remember { mutableStateOf(state.provider) }
    var quickKeyInput by remember { mutableStateOf("") }

    var attachment by remember { mutableStateOf<ChatAttachment?>(null) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.provider) { apiKey = ""; quickKeyInput = ""; attachment = null }
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
    LaunchedEffect(messages.lastOrNull()?.timestampMs, voiceReplies, speechReady, state.isGenerating) {
        val last = messages.lastOrNull()
        if (!state.isGenerating && voiceReplies && speechReady && last?.role == "assistant" && !last.isError) {
            val language = if (last.content.any { it in '\u0600'..'\u06ff' }) Locale("fa", "IR") else Locale.ENGLISH
            val engine = tts
            if (engine == null || engine.isLanguageAvailable(language) < TextToSpeech.LANG_AVAILABLE) {
                attachmentError = "The installed speech engine has no voice for this response language."
                return@LaunchedEffect
            }
            engine.language = language
            engine.speak(last.content, TextToSpeech.QUEUE_FLUSH, null, "reply-" + last.timestampMs)
        }
    }
    fun send(text: String = prompt, fromVoice: Boolean = false) {
        if (state.isGenerating || !sendable ||
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
                if (sendable) send(transcript, fromVoice = true)
                else prompt = transcript
            }
            else attachmentError = "No speech was recognized."
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { sessionsOpen = true }) {
                    Text(sessions.firstOrNull { it.id == activeSessionId }?.title ?: "Conversation")
                }
                DropdownMenu(expanded = sessionsOpen, onDismissRequest = { sessionsOpen = false }) {
                    sessions.forEach { session ->
                        DropdownMenuItem(text = { Text(session.title + " · " + session.provider) },
                            onClick = { viewModel.switchChatSession(session.id); sessionsOpen = false })
                    }
                }
            }
            TextButton(onClick = { viewModel.createChatSession() }, enabled = !state.isGenerating) { Text("New") }
            TextButton(onClick = {
                sessionTitle = sessions.firstOrNull { it.id == activeSessionId }?.title.orEmpty()
                renameOpen = true
            }, enabled = sessions.any { it.id == activeSessionId }) { Text("Rename") }
            TextButton(onClick = { deleteOpen = true }, enabled = sessions.any { it.id == activeSessionId }) { Text("Delete") }
        }
        if (messages.lastOrNull()?.isError == true && !state.isGenerating && sendable) {
            TextButton(onClick = {
                messages.lastOrNull { it.role == "user" }?.let { viewModel.sendChat(it.content, it.attachment, false) }
            }) { Text("Retry last request") }
        }
        // Compact unified header: Model info, Token telemetry badge, settings trigger, voice toggle
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
                        color = if (sendable) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (sendable) state.selectedModel
                                else "Connect ${state.provider.displayName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Text(
                            text = if (sendable)
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

            // Token Telemetry Chip in Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.clickable { tokenDetailsOpen = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DataUsage,
                        null,
                        tint = if (metrics.contextUsagePercent > 0.85f) MaterialTheme.colorScheme.error
                        else if (metrics.contextUsagePercent > 0.6f) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Used: ${numberFormat.format(metrics.consumedTurnTotalTokens)}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Est. left: ${numberFormat.format(metrics.remainingContextTokens)}",
                            fontSize = 8.sp,
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { voiceReplies = !voiceReplies; if (!voiceReplies) tts?.stop() },
                    modifier = Modifier.size(34.dp)
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
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Delete, "Clear conversation", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Dedicated In-Chat LLM Provider Switcher Bar
        ChatProviderSwitcherBar(
            currentProvider = state.provider,
            currentModel = state.selectedModel,
            models = state.models,
            hasSavedKey = state.hasSavedKey,
            onSelectProvider = { provider ->
                viewModel.applyProviderPreset(provider)
                baseUrl = viewModel.llmState.value.baseUrl
            },
            onSelectModel = { modelId ->
                viewModel.selectModel(modelId)
            },
            onConfigureKey = { provider ->
                if (state.provider != provider) viewModel.applyProviderPreset(provider)
                baseUrl = viewModel.llmState.value.baseUrl
                quickKeyTargetProvider = provider
                quickKeyInput = ""
                quickKeyDialogOpen = true
            }
        )

        // Conversation messages area
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(
                            "MAXIMUS AI Engine Ready",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Active: ${state.provider.displayName} · Model: ${state.selectedModel.ifBlank { "Not configured" }}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Select a provider and verify its models. Conversations remain isolated; /mission invokes approved runtime tools.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            lineHeight = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (!state.hasSavedKey) {
                            Button(
                                onClick = {
                                    quickKeyTargetProvider = state.provider
                                    quickKeyInput = ""
                                    quickKeyDialogOpen = true
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Key, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Enter ${state.provider.displayName} Key", fontSize = 11.sp)
                            }
                        }
                    }
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Text(
                                    "Persona is generating response with ${state.provider.displayName}…",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.isGenerating) {
            TextButton(onClick = { viewModel.cancelChat() }) { Text("Stop generation") }
        }
        if (attachment != null) {
            AssistChip(
                onClick = { attachment = null },
                label = {
                    Text(
                        attachment!!.name +
                            (if (attachment!!.mimeType.startsWith("audio/")) " · " + attachment!!.durationMs / 1_000 + "s" else "") +
                            "  ·  remove",
                        maxLines = 1,
                        fontSize = 11.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        if (attachment!!.mimeType.startsWith("audio/")) Icons.Default.Mic else Icons.Default.AttachFile,
                        null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                modifier = Modifier.height(28.dp)
            )
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
                    placeholder = { Text("Message the selected persona…", fontSize = 13.sp) },
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

                    // Live quick consumed / remaining pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { tokenDetailsOpen = true }
                    ) {
                        Text(
                            text = "${numberFormat.format(metrics.consumedTurnTotalTokens)} / ${numberFormat.format(metrics.remainingContextTokens)}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Button(
                        onClick = { send() },
                        enabled = !recording && !state.isGenerating && (prompt.isNotBlank() || attachment != null) &&
                            sendable && state.selectedAgentId.isNotBlank() &&
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

    if (renameOpen) {
        AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text("Rename conversation") },
            text = { OutlinedTextField(value = sessionTitle, onValueChange = { sessionTitle = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.renameChatSession(activeSessionId, sessionTitle); renameOpen = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } })
    }
    if (deleteOpen) {
        AlertDialog(onDismissRequest = { deleteOpen = false }, title = { Text("Delete conversation?") },
            text = { Text("This removes its locally saved messages.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteChatSession(activeSessionId); deleteOpen = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } })
    }
    // Quick In-Chat API Key Entry Dialog
    if (quickKeyDialogOpen) {
        AlertDialog(
            onDismissRequest = { quickKeyDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect ${quickKeyTargetProvider.displayName}", fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when (quickKeyTargetProvider) {
                            LlmProvider.GEMINI -> "Enter your Google AI Studio API key, then discover available models and test inference."
                            LlmProvider.NVIDIA -> "Enter your NVIDIA NIM API key (starts with nvapi-). Direct access to Llama 3.3 70B and DeepSeek R1."
                            LlmProvider.ROUTER_9_SMART -> "Enter your deployment endpoint and key. Select a model returned by its catalog."
                            LlmProvider.ROUTER_9_COMBO -> "Combo names must exist in your server catalog. Routing behavior is managed by that server."
                            else -> "Enter the API key for ${quickKeyTargetProvider.displayName}."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    if (quickKeyTargetProvider.isNineRouter || quickKeyTargetProvider == LlmProvider.OPENAI_COMPATIBLE) {
                        OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Trusted HTTPS deployment endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(
                        value = quickKeyInput,
                        onValueChange = { quickKeyInput = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text.orEmpty()
                                if (clip.isNotBlank()) quickKeyInput = clip.trim()
                            }
                        ) {
                            Text("Paste from clipboard", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val key = quickKeyInput.trim()
                        if (key.isNotBlank()) {
                            viewModel.detectApi(baseUrl.ifBlank { quickKeyTargetProvider.defaultBaseUrl }, key)
                            quickKeyDialogOpen = false
                        }
                    },
                    enabled = quickKeyInput.isNotBlank()
                ) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { quickKeyDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Token Details & Context Inspector BottomSheet
    if (tokenDetailsOpen) {
        ModalBottomSheet(onDismissRequest = { tokenDetailsOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DataUsage, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Token & Context Inspector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "${(metrics.contextUsagePercent * 100).toInt()}% Used",
                        color = if (metrics.contextUsagePercent > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { metrics.contextUsagePercent.coerceIn(0.01f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (metrics.contextUsagePercent > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Active Engine", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.provider.displayName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Model", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.selectedModel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Max Context Window", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${numberFormat.format(metrics.contextCapacity)} tokens", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Consumed (Last Turn)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${numberFormat.format(metrics.consumedTurnTotalTokens)} tokens (Prompt: ${numberFormat.format(metrics.consumedTurnInputTokens)} · Response: ${numberFormat.format(metrics.consumedTurnOutputTokens)})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Remaining in Context", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${numberFormat.format(metrics.remainingContextTokens)} tokens", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Session Consumed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${numberFormat.format(metrics.consumedSessionTokens)} tokens", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Lifetime Consumed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${numberFormat.format(metrics.consumedLifetimeTokens)} tokens", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (metrics.remainingBudgetTokens != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Local Lifetime Budget Remaining (not provider quota)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${numberFormat.format(metrics.remainingBudgetTokens)} / ${numberFormat.format(metrics.monthlyTokenBudget)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                            }
                        }
                    }
                }

                Button(
                    onClick = { tokenDetailsOpen = false },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text("Close Inspector")
                }
            }
        }
    }

    // Provider & Agent Selection Sheet
    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(.88f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("AI Engine & Provider Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Select engine preset or enter custom OpenAI-compatible endpoint.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                // Quick Presets inside BottomSheet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.provider == LlmProvider.GEMINI,
                        onClick = {
                            viewModel.applyProviderPreset(LlmProvider.GEMINI)
                            baseUrl = LlmProvider.GEMINI.defaultBaseUrl
                        },
                        label = { Text("Google Gemini", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = state.provider == LlmProvider.ROUTER_9_SMART,
                        onClick = {
                            viewModel.applyProviderPreset(LlmProvider.ROUTER_9_SMART)
                            baseUrl = viewModel.llmState.value.baseUrl
                        },
                        label = { Text("9Router", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = state.provider == LlmProvider.ROUTER_9_COMBO,
                        onClick = {
                            viewModel.applyProviderPreset(LlmProvider.ROUTER_9_COMBO)
                            baseUrl = viewModel.llmState.value.baseUrl
                        },
                        label = { Text("9Router (server combo)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = state.provider == LlmProvider.NVIDIA,
                        onClick = {
                            viewModel.applyProviderPreset(LlmProvider.NVIDIA)
                            baseUrl = LlmProvider.NVIDIA.defaultBaseUrl
                        },
                        label = { Text("NVIDIA NIM", fontSize = 11.sp) }
                    )
                    listOf(LlmProvider.ANTHROPIC, LlmProvider.OPENAI_COMPATIBLE).forEach { provider ->
                        FilterChip(selected = state.provider == provider,
                            onClick = { viewModel.applyProviderPreset(provider); baseUrl = viewModel.llmState.value.baseUrl },
                            label = { Text(provider.displayName, fontSize = 11.sp) })
                    }
                    FilterChip(
                        selected = state.provider == LlmProvider.OPENAI,
                        onClick = {
                            viewModel.applyProviderPreset(LlmProvider.OPENAI)
                            baseUrl = LlmProvider.OPENAI.defaultBaseUrl
                        },
                        label = { Text("OpenAI", fontSize = 11.sp) }
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Endpoint & Credentials", fontWeight = FontWeight.SemiBold)
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
                                    label = {
                                        Text(
                                            when (state.provider) {
                                                LlmProvider.GEMINI -> "Gemini API Key (AIza...)"
                                                LlmProvider.NVIDIA -> "NVIDIA API Key (nvapi-...)"
                                                LlmProvider.ROUTER_9_SMART, LlmProvider.ROUTER_9_COMBO -> "9Router API Key"
                                                else -> "API Key"
                                            }
                                        )
                                    },
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
                                            "Hardware-encrypted in KeyStore",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp
                                        )
                                    } else {
                                        Text(
                                            "AES-GCM secured",
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
                                    Text("Model & Persona Selection", fontWeight = FontWeight.SemiBold)
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
                                                    text = {
                                                        Column {
                                                            Text(model.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            Text(
                                                                "${model.displayName} (${numberFormat.format(model.contextWindowTokens)} tokens)",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                    text = { Text(agent.name + " · persona") },
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

/**
 * Dedicated in-chat interactive LLM Provider Switcher Bar (Compact 50% size).
 * Precision micro-design: 1-row layout, 11dp icons, 9.5sp crisp typography, inline model selector.
 */
@Composable
private fun ChatProviderSwitcherBar(
    currentProvider: LlmProvider,
    currentModel: String,
    models: List<ModelDescriptor>,
    hasSavedKey: Boolean,
    onSelectProvider: (LlmProvider) -> Unit,
    onSelectModel: (String) -> Unit,
    onConfigureKey: (LlmProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    var modelPickerOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Horizontal micro-chips for providers
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Google Gemini
                MicroProviderChip(
                    name = "Gemini",
                    modelSuffix = if (currentProvider == LlmProvider.GEMINI) currentModel.removePrefix("gemini-").take(10) else null,
                    icon = Icons.Default.AutoAwesome,
                    brandColor = Color(0xFF4285F4),
                    selected = currentProvider == LlmProvider.GEMINI,
                    onChipClick = { onSelectProvider(LlmProvider.GEMINI) },
                    onDropdownClick = { modelPickerOpen = true }
                )

                // 2. NVIDIA NIM
                MicroProviderChip(
                    name = "NVIDIA NIM",
                    modelSuffix = if (currentProvider == LlmProvider.NVIDIA) currentModel.substringAfterLast("/").take(10) else null,
                    icon = Icons.Default.Memory,
                    brandColor = Color(0xFF76B900),
                    selected = currentProvider == LlmProvider.NVIDIA,
                    onChipClick = { onSelectProvider(LlmProvider.NVIDIA) },
                    onDropdownClick = { modelPickerOpen = true }
                )

                // 3. 9Router
                MicroProviderChip(
                    name = "9Router",
                    modelSuffix = if (currentProvider == LlmProvider.ROUTER_9_SMART) currentModel.take(10) else null,
                    icon = Icons.Default.Hub,
                    brandColor = Color(0xFF00E5FF),
                    selected = currentProvider == LlmProvider.ROUTER_9_SMART,
                    onChipClick = { onSelectProvider(LlmProvider.ROUTER_9_SMART) },
                    onDropdownClick = { modelPickerOpen = true }
                )

                // 4. 9Router (server combo)
                MicroProviderChip(
                    name = "9Router (server combo)",
                    modelSuffix = if (currentProvider == LlmProvider.ROUTER_9_COMBO) currentModel.take(10) else null,
                    icon = Icons.Default.AltRoute,
                    brandColor = Color(0xFF7C4DFF),
                    selected = currentProvider == LlmProvider.ROUTER_9_COMBO,
                    onChipClick = { onSelectProvider(LlmProvider.ROUTER_9_COMBO) },
                    onDropdownClick = { modelPickerOpen = true }
                )

                // 5. OpenAI
                MicroProviderChip(
                    name = "OpenAI",
                    modelSuffix = if (currentProvider == LlmProvider.OPENAI) currentModel.take(8) else null,
                    icon = Icons.Default.Bolt,
                    brandColor = Color(0xFF10A37F),
                    selected = currentProvider == LlmProvider.OPENAI,
                    onChipClick = { onSelectProvider(LlmProvider.OPENAI) },
                    onDropdownClick = { modelPickerOpen = true }
                )

                // 6. Claude
                MicroProviderChip(
                    name = "Claude",
                    modelSuffix = if (currentProvider == LlmProvider.ANTHROPIC) currentModel.removePrefix("claude-").take(8) else null,
                    icon = Icons.Default.Psychology,
                    brandColor = Color(0xFFD97706),
                    selected = currentProvider == LlmProvider.ANTHROPIC,
                    onChipClick = { onSelectProvider(LlmProvider.ANTHROPIC) },
                    onDropdownClick = { modelPickerOpen = true }
                )
            }

            Spacer(Modifier.width(4.dp))

            // Micro Key Status Pill
            Surface(
                onClick = { onConfigureKey(currentProvider) },
                shape = RoundedCornerShape(6.dp),
                color = if (hasSavedKey) Color(0xFF00E676).copy(alpha = 0.12f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(0.75.dp, if (hasSavedKey) Color(0xFF00E676).copy(alpha = 0.4f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (hasSavedKey) Icons.Default.CheckCircle else Icons.Default.Key,
                        contentDescription = "Configure Key",
                        tint = if (hasSavedKey) Color(0xFF00E676) else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = if (hasSavedKey) "Key" else "Set Key",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasSavedKey) Color(0xFF00E676) else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // Model Dropdown menu anchored to the switcher bar
            DropdownMenu(
                expanded = modelPickerOpen,
                onDismissRequest = { modelPickerOpen = false }
            ) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    model.id,
                                    fontWeight = if (model.id == currentModel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = if (model.id == currentModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${model.displayName} · ${(model.contextWindowTokens / 1_000)}k ctx",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onSelectModel(model.id)
                            modelPickerOpen = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MicroProviderChip(
    name: String,
    modelSuffix: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    brandColor: Color,
    selected: Boolean,
    onChipClick: () -> Unit,
    onDropdownClick: () -> Unit
) {
    Surface(
        onClick = onChipClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) brandColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        border = BorderStroke(
            width = if (selected) 1.dp else 0.75.dp,
            color = if (selected) brandColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) brandColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = name,
                fontSize = 9.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (selected && !modelSuffix.isNullOrBlank()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "· $modelSuffix",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = brandColor
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select Model",
                    tint = brandColor,
                    modifier = Modifier
                        .size(11.dp)
                        .clickable(onClick = onDropdownClick)
                )
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
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (attachment.mimeType.startsWith("audio/")) Icons.Default.Mic
                                else if (attachment.mimeType.startsWith("image/")) Icons.Default.Image
                                else Icons.Default.AttachFile,
                                null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                attachment.name + (if (attachment.durationMs > 0) " · " + attachment.durationMs / 1_000 + "s" else ""),
                                fontSize = 10.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            if (attachment.mimeType.startsWith("audio/")) {
                                IconButton(onClick = onPlayVoice, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.PlayArrow, "Play voice message", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                Text(message.content, color = textColor, fontSize = 13.sp, lineHeight = 18.sp)
                if (!isUser && !message.isError) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.VolumeUp, "Speak message", modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun playVoiceMessage(context: Context, attachment: ChatAttachment) {
    require(attachment.data.isNotBlank() && attachment.data.length <= 4 * 1024 * 1024) {
        "Voice payload is unavailable or exceeds the playback limit."
    }
    val tempFile = File.createTempFile("matrix_voice_play_", ".m4a", context.cacheDir)
    val player = MediaPlayer()
    try {
        FileOutputStream(tempFile).use { it.write(Base64.decode(attachment.data, Base64.DEFAULT)) }
        player.setDataSource(tempFile.absolutePath)
        player.setOnCompletionListener { player.release(); tempFile.delete() }
        player.setOnErrorListener { _, _, _ -> player.release(); tempFile.delete(); true }
        player.prepare()
        player.start()
    } catch (error: Exception) {
        player.release()
        tempFile.delete()
        throw error
    }
}

private fun loadChatAttachment(context: Context, uri: Uri): ChatAttachment {
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val isText = mimeType.startsWith("text/") || mimeType == "application/json"
    var name = "attachment"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            name = cursor.getString(nameIndex) ?: "attachment"
        }
    }
    require(isText || mimeType in setOf("image/png", "image/jpeg", "image/webp", "application/pdf")) {
        "Unsupported file type. Select PNG, JPEG, WebP, PDF or UTF-8 text."
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 5 * 1024 * 1024) { "Attachment exceeds the 5 MB limit." }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
        ?: throw IllegalStateException("Could not read attachment file.")
    require(bytes.isNotEmpty()) { "The selected file is empty." }
    if (mimeType.startsWith("image/")) {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0 &&
            options.outWidth.toLong() * options.outHeight <= 40_000_000) { "Image is invalid or exceeds 40 megapixels." }
    }
    if (mimeType == "application/pdf") require(bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-") { "Invalid PDF document." }
    if (isText) require(!bytes.contains(0)) { "Binary content cannot be attached as text." }
    val data = if (isText) String(bytes, Charsets.UTF_8) else Base64.encodeToString(bytes, Base64.NO_WRAP)
    return ChatAttachment(name = name, mimeType = mimeType, data = data, isText = isText)
}
