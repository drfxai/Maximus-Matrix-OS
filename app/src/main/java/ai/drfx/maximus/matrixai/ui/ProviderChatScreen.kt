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
import ai.drfx.maximus.matrixai.voice.SpeechLanguage
import ai.drfx.maximus.matrixai.voice.SpeechLanguagePreferenceStore
import ai.drfx.maximus.matrixai.voice.SpeechLanguagePolicy
import ai.drfx.maximus.matrixai.voice.SpeechPlaybackPolicy
import androidx.lifecycle.compose.currentStateAsState
import ai.drfx.maximus.matrixai.voice.SpeechTranscriptGuard
import ai.drfx.maximus.matrixai.ui.theme.LanguagePreferenceStore
import androidx.compose.ui.res.stringResource
import ai.drfx.maximus.matrixai.R
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.*
import ai.drfx.maximus.matrixai.voice.ElevenLabsClient
import ai.drfx.maximus.matrixai.voice.ElevenAudioPlayer
import ai.drfx.maximus.matrixai.voice.ElevenVoiceSettings
import ai.drfx.maximus.matrixai.vision.ImageInputPipeline
import androidx.compose.foundation.background
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderChatScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.llmState.collectAsState()
    val pendingAction by viewModel.pendingAgentConfirmation.collectAsState()
    pendingAction?.let { request ->
        AlertDialog(onDismissRequest = { viewModel.approveAgentAction(request.id, false) },
            title = { Text(stringResource(R.string.chat_approve_action)) },
            text = { Text(request.action.tool + "\n" + request.action.arguments.entries.joinToString("\n") { "${it.key}: ${it.value}" }) },
            confirmButton = { TextButton(onClick = { viewModel.approveAgentAction(request.id, true) }) { Text(stringResource(R.string.graph_approve)) } },
            dismissButton = { TextButton(onClick = { viewModel.approveAgentAction(request.id, false) }) { Text(stringResource(R.string.graph_reject)) } })
    }
    val messages by viewModel.chatMessages.collectAsState()
    val sessions by viewModel.chatSessions.collectAsState()
    val activeSessionId by viewModel.activeChatSessionId.collectAsState()
    var sessionsOpen by remember { mutableStateOf(false) }
    var workspaceControlsOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var sessionTitle by remember { mutableStateOf("") }
    val sendable = state.status in setOf(ConnectionStatus.CONNECTED, ConnectionStatus.CONFIGURED, ConnectionStatus.DEGRADED)
    val context = LocalContext.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val metrics = state.tokenMetrics
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    val clipboardManager = LocalClipboardManager.current
    val reactionStore = remember(context) { ChatFeedbackStore(context.applicationContext) }
    var pendingExport by remember { mutableStateOf<ChatExport?>(null) }
    var copiedKey by remember { mutableStateOf("") }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val item = pendingExport
        pendingExport = null
        if (uri != null && item != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(item.bytes) }
                    ?: error(context.getString(R.string.conv_export_destination))
            }.onSuccess {
                android.widget.Toast.makeText(context, context.getString(R.string.conv_exported), android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(context, context.getString(R.string.conv_export_failed), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    var baseUrl by remember(state.baseUrl) {
        mutableStateOf(state.baseUrl.ifBlank { state.provider.defaultBaseUrl })
    }
    var apiKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var modelExpanded by remember { mutableStateOf(false) }
    var agentExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val voicePlayback = remember(context) { VoicePlaybackController(context) }
    val elevenSettings = remember(context) { ElevenVoiceSettings(context.applicationContext) }
    val elevenClient = remember { ElevenLabsClient() }
    val elevenPlayback = remember(context) { ElevenAudioPlayer(context.applicationContext) }
    var elevenJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var elevenBusy by remember { mutableStateOf(false) }
    DisposableEffect(elevenPlayback) { onDispose { elevenJob?.cancel(); elevenPlayback.stop() } }
    DisposableEffect(voicePlayback) { onDispose { voicePlayback.stop() } }
    var settingsOpen by remember { mutableStateOf(false) }
    var tokenDetailsOpen by remember { mutableStateOf(false) }
    var quickKeyDialogOpen by remember { mutableStateOf(false) }
    var confirmDirectProvider by remember { mutableStateOf(false) }
    var quickKeyTargetProvider by remember { mutableStateOf(state.provider) }
    var quickKeyInput by remember { mutableStateOf("") }

    var attachment by remember { mutableStateOf<ChatAttachment?>(null) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.provider) { apiKey = ""; quickKeyInput = ""; attachment = null }
    val listState = rememberLazyListState()
    var followLatest by remember(activeSessionId) { mutableStateOf(true) }
    val showJumpToLatest = messages.isNotEmpty() && listState.canScrollForward

    // Auto-follow fresh messages only while the reader stays at the bottom.
    // A real user drag away from the latest message suspends auto-follow.
    LaunchedEffect(listState, activeSessionId) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Stop || interaction is DragInteraction.Cancel) {
                followLatest = !listState.canScrollForward
            }
        }
    }
    LaunchedEffect(messages.size, state.isGenerating, activeSessionId) {
        if (followLatest && messages.isNotEmpty()) {
            val last = messages.lastIndex + if (state.isGenerating) 1 else 0
            listState.animateScrollToItem(last)
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
            attachmentError = context.getString(R.string.conv_recording_start_failed)
        }
    }
    fun finishRecording() {
        try {
            attachment = recorder.stop()
            attachmentError = null
        } catch (error: Exception) {
            attachmentError = context.getString(R.string.conv_recording_save_failed)
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
    val speechPreference = remember(context) { SpeechLanguagePreferenceStore(context) }
    var selectedSpeechLanguage by remember { mutableStateOf(speechPreference.selected()) }
    var speechFollowsAppLanguage by remember { mutableStateOf(speechPreference.explicitChoice() == null) }
    // Capture the language that was actually requested for this recognition turn.
    var recordingSpeechLanguage by remember { mutableStateOf(speechPreference.selected()) }
    var voiceLanguageMenuOpen by remember { mutableStateOf(false) }
    var voiceConversation by remember { mutableStateOf(false) }
    var voiceReplies by remember { mutableStateOf(false) }
    val speechPlaybackPolicy = remember { SpeechPlaybackPolicy() }
    var recognitionInProgress by remember { mutableStateOf(false) }
    var speechReady by remember { mutableStateOf(false) }
    var speechVoiceInstallNeeded by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { result -> speechReady = result == TextToSpeech.SUCCESS }
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }
    fun stopVoicePlayback() {
        tts?.stop()
        voicePlayback.stop()
        elevenJob?.cancel()
        elevenPlayback.stop()
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            stopVoicePlayback()
            startRecording()
        } else attachmentError = context.getString(R.string.speech_microphone_permission_required)
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val speechLifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    DisposableEffect(lifecycleOwner, recorder, voicePlayback, tts) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE ||
                event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                tts?.stop()
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                recorder.cancel()
                recording = false
                voicePlayback.stop()
                elevenJob?.cancel()
                elevenPlayback.stop()
                tts?.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(messages.lastOrNull()?.timestampMs, voiceReplies, speechReady,
        state.isGenerating, selectedSpeechLanguage, speechLifecycleState, recognitionInProgress, recording) {
        val last = messages.lastOrNull()
        if (!state.isGenerating && voiceReplies && speechReady && last?.role == "assistant" &&
            !last.isError && last.content.isNotBlank() && speechPlaybackPolicy.mayPlay(
                last.timestampMs,
                speechLifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED,
                recognitionInProgress || recording
            )) {
            val language = SpeechLanguagePolicy.preferredTtsLanguage(
                last.content, selectedSpeechLanguage
            ).locale()
            val engine = tts
            if (engine == null || engine.isLanguageAvailable(language) < TextToSpeech.LANG_AVAILABLE ||
                engine.setLanguage(language) < TextToSpeech.LANG_AVAILABLE) {
                speechVoiceInstallNeeded = true
                attachmentError = context.getString(R.string.speech_tts_missing, language.toLanguageTag())
                return@LaunchedEffect
            }
            speechVoiceInstallNeeded = false
            if (engine.speak(last.content, TextToSpeech.QUEUE_FLUSH, null,
                    "reply-" + last.timestampMs) == TextToSpeech.SUCCESS) {
                speechPlaybackPolicy.markSpoken(last.timestampMs)
            } else attachmentError = context.getString(R.string.brain_voice_playback_failed)
        }
    }
    fun send(text: String = prompt, fromVoice: Boolean = false) {
        if (state.isGenerating || !sendable ||
            (text.isBlank() && attachment == null)) return
        if (viewModel.sendChat(text, attachment, fromVoice)) {
            followLatest = true
            prompt = ""
            attachment = null
            attachmentError = null
        }
    }
    fun load(uri: Uri) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { loadChatAttachment(context, uri) } }
                .onSuccess { attachment = it; attachmentError = null }
                .onFailure { attachmentError = context.getString(R.string.speech_attachment_read_error) }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::load)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::load)
    }
    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        recognitionInProgress = false
        if (result.resultCode == Activity.RESULT_OK) {
            val selection = SpeechTranscriptGuard.choose(
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
                recordingSpeechLanguage
            )
            if (selection.text != null) {
                if (voiceConversation && selection.mayAutomaticallySend &&
                    sendable && !state.isGenerating) {
                    send(selection.text, fromVoice = true)
                } else {
                    // Some Android recognizers ignore EXTRA_LANGUAGE. Wrong-script
                    // output is kept editable, but NEVER auto-sent to the AI.
                    prompt = selection.text
                    attachmentError = if (selection.scriptMatchesLanguage)
                        context.getString(R.string.speech_transcript_review)
                    else context.getString(R.string.speech_wrong_script_review,
                        recordingSpeechLanguage.label)
                }
            } else attachmentError = context.getString(R.string.speech_no_result)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ConversationTelemetryRibbon(
            state = state,
            onProviderSettings = { settingsOpen = true },
            onTokenDetails = { tokenDetailsOpen = true }
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { sessionsOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text(sessions.firstOrNull { it.id == activeSessionId }?.title
                        ?: stringResource(R.string.speech_conversation_default),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp))
                }
                DropdownMenu(expanded = sessionsOpen, onDismissRequest = { sessionsOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.speech_new)) },
                        enabled = !state.isGenerating,
                        onClick = { sessionsOpen = false; viewModel.createChatSession() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.speech_rename)) },
                        enabled = sessions.any { it.id == activeSessionId },
                        onClick = {
                            sessionsOpen = false
                            sessionTitle = sessions.firstOrNull { it.id == activeSessionId }?.title.orEmpty()
                            renameOpen = true
                        })
                    DropdownMenuItem(text = { Text(stringResource(R.string.speech_delete)) },
                        enabled = sessions.any { it.id == activeSessionId },
                        onClick = { sessionsOpen = false; deleteOpen = true })
                    if (sessions.isNotEmpty()) HorizontalDivider()
                    sessions.forEach { session ->
                        DropdownMenuItem(text = { Text(session.title + " · " + session.provider) },
                            onClick = { viewModel.switchChatSession(session.id); sessionsOpen = false })
                    }
                }
            }
            TextButton(onClick = { workspaceControlsOpen = !workspaceControlsOpen },
                modifier = Modifier.testTag("chat_workspace_controls_toggle"),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (workspaceControlsOpen) R.string.focus_controls_hide
                    else R.string.focus_controls_show), fontSize = 12.sp)
            }
        }
        if (messages.lastOrNull()?.isError == true && !state.isGenerating && sendable) {
            TextButton(onClick = {
                messages.lastOrNull { it.role == "user" }?.let { viewModel.sendChat(it.content, it.attachment, false) }
            }) { Text(stringResource(R.string.speech_retry)) }
        }
        // Optional controls share a bounded viewport so the conversation retains space.
        AnimatedVisibility(visible = workspaceControlsOpen && !keyboardVisible, enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                                        else stringResource(R.string.chat_connect_provider, state.provider.displayName),
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
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            Icon(Icons.Default.Tune, stringResource(R.string.chat_provider_settings), modifier = Modifier.size(16.dp))
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
                                    text = stringResource(R.string.conv_usage_used, numberFormat.format(metrics.consumedTurnTotalTokens)),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.conv_context_left, numberFormat.format(metrics.remainingContextTokens)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { tts?.stop(); voicePlayback.stop(); elevenJob?.cancel(); elevenPlayback.stop() }) { Icon(Icons.Default.Stop, stringResource(R.string.speech_stop_audio)) }
                        IconButton(
                            onClick = {
                                if (!voiceReplies) speechPlaybackPolicy.skipExistingReply(messages.lastOrNull()?.timestampMs)
                                voiceReplies = !voiceReplies
                                if (!voiceReplies) {
                                    voiceConversation = false
                                    tts?.stop()
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                if (voiceReplies) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                if (voiceReplies) stringResource(R.string.speech_disable_replies) else stringResource(R.string.speech_enable_replies),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (messages.isNotEmpty()) {
                            IconButton(
                                onClick = viewModel::clearChat,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Delete, stringResource(R.string.speech_clear_conversation),
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().testTag("speech_language_controls")
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            TextButton(onClick = { voiceLanguageMenuOpen = true },
                                modifier = Modifier.testTag("voice_language_picker")) {
                                Icon(Icons.Default.KeyboardVoice, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.speech_language_label) + ": " +
                                        selectedSpeechLanguage.label +
                                        (if (speechFollowsAppLanguage)
                                            " · " + stringResource(R.string.speech_follow_app_short)
                                         else ""),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = voiceLanguageMenuOpen,
                                onDismissRequest = { voiceLanguageMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.speech_follow_app_language)) },
                                    onClick = {
                                        speechPreference.set(null)
                                        speechFollowsAppLanguage = true
                                        selectedSpeechLanguage = SpeechLanguage.fromApp(
                                            LanguagePreferenceStore(context).load()
                                        )
                                        speechVoiceInstallNeeded = false
                                        voiceLanguageMenuOpen = false
                                        tts?.stop()
                                    }
                                )
                                HorizontalDivider()
                                SpeechLanguage.entries.forEach { language ->
                                    DropdownMenuItem(
                                        text = { Text(language.label + " · " + language.tag) },
                                        onClick = {
                                            speechPreference.set(language)
                                            selectedSpeechLanguage = language
                                            speechFollowsAppLanguage = false
                                            speechVoiceInstallNeeded = false
                                            voiceLanguageMenuOpen = false
                                            tts?.stop()
                                        }
                                    )
                                }
                            }
                        }
                        Text(stringResource(R.string.speech_conversation_label), fontSize = 12.sp)
                        MotionSwitch(
                            checked = voiceConversation,
                            onCheckedChange = {
                                voiceConversation = it
                                if (it && !voiceReplies) {
                                    speechPlaybackPolicy.skipExistingReply(messages.lastOrNull()?.timestampMs)
                                    voiceReplies = true
                                }
                            },
                            modifier = Modifier.height(36.dp).testTag("voice_conversation_toggle")
                        )
                    }
                }
                if (voiceConversation) {
                    Text(
                        stringResource(R.string.speech_conversation_hint),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

                // A missing catalog-confirmed free route should never strand a configured
                // user in an opaque error. Manual provider usage requires a separate tap
                // and explicit cost warning; no previous prompt is replayed.
                if (state.smartComboEnabled && state.smartComboOffers == 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.chat_combo_unavailable),
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                smartComboStatusLabel(state),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = viewModel::refreshSmartCombo,
                                    enabled = !state.smartComboRefreshing && !state.isGenerating) {
                                    Text(stringResource(R.string.api_refresh), fontSize = 12.sp)
                                }
                                Button(onClick = { confirmDirectProvider = true },
                                    enabled = !state.isGenerating && state.hasSavedKey &&
                                        state.selectedModel.isNotBlank()) {
                                    Text(stringResource(R.string.chat_manual), fontSize = 12.sp)
                                }
                            }
                            Text(stringResource(R.string.chat_combo_not_quota),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

            }
        }
        if (state.smartComboEnabled && state.smartComboOffers == 0 && (!workspaceControlsOpen || keyboardVisible)) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(start = 8.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.chat_combo_unavailable), fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text(smartComboStatusLabel(state), fontSize = 12.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { workspaceControlsOpen = true }) {
                        Text(stringResource(R.string.focus_details), fontSize = 12.sp)
                    }
                }
            }
        }

        // Conversation messages area with a floating jump-to-latest action.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                border = null,
                shape = RoundedCornerShape(12.dp)
            ) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 32.dp), contentAlignment = Alignment.TopCenter) {
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
                            stringResource(R.string.workspaces_chat_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            stringResource(R.string.focus_provider_configured) + ": " +
                                state.provider.displayName + " · " +
                                state.selectedModel.ifBlank { stringResource(R.string.chat_not_configured) },
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            stringResource(R.string.chat_start_hint),
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
                                Text(stringResource(R.string.chat_enter_provider_key, state.provider.displayName), fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages) { message ->
                        ChatBubble(
                            message = message,
                            sessionId = activeSessionId,
                            feedbackStore = reactionStore,
                            copied = copiedKey == reactionStore.key(activeSessionId, message.timestampMs, message.content),
                            onCopy = {
                                val exported = runCatching { ChatPresentation.export(message.content) }.getOrNull()
                                clipboardManager.setText(
                                    AnnotatedString(exported?.bytes?.toString(Charsets.UTF_8) ?: message.content)
                                )
                                copiedKey = reactionStore.key(activeSessionId, message.timestampMs, message.content)
                            },
                            onDownload = {
                                runCatching { ChatPresentation.export(message.content) }
                                    .onSuccess { pendingExport = it; exportPicker.launch(it.name) }
                                    .onFailure { attachmentError = context.getString(R.string.conv_export_unavailable) }
                            },
                            onSpeak = {
                            if (elevenSettings.elevenSelected()) {
                                if (!elevenBusy) {
                                    val voiceId = elevenSettings.selectedVoice()
                                    val modelId = elevenSettings.selectedModel()
                                    val key = elevenSettings.key()
                                    if (!ElevenLabsClient.validVoiceId(voiceId) || key.isBlank()) {
                                        attachmentError = context.getString(R.string.conv_eleven_setup)
                                    } else if (message.content.length > ElevenLabsClient.MAX_SPOKEN_CHARACTERS) {
                                        attachmentError = context.getString(R.string.conv_eleven_limit, ElevenLabsClient.MAX_SPOKEN_CHARACTERS)
                                    } else {
                                        elevenBusy = true
                                        tts?.stop()
                                        elevenPlayback.stop()
                                        elevenJob = scope.launch {
                                            try {
                                                // Only a deliberate Speak tap uploads the selected response text.
                                                val mp3 = elevenClient.synthesize(key, voiceId, modelId, message.content)
                                                elevenPlayback.play(mp3)
                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (error: Exception) {
                                                attachmentError = context.getString(R.string.conv_eleven_failed)
                                            } finally {
                                                elevenBusy = false
                                            }
                                        }
                                    }
                                }
                            } else if (speechReady) {
                                val language = SpeechLanguagePolicy.preferredTtsLanguage(
                                    message.content, selectedSpeechLanguage
                                ).locale()
                                if ((tts?.isLanguageAvailable(language) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE) {
                                    tts?.language = language
                                    tts?.speak(message.content, TextToSpeech.QUEUE_FLUSH, null, "replay-" + message.timestampMs)
                                } else {
                                    speechVoiceInstallNeeded = true
                                    attachmentError = context.getString(
                                        R.string.speech_tts_missing, language.toLanguageTag()
                                    )
                                }
                            }
                        }, onPlayVoice = {
                            message.attachment?.let { voice ->
                                runCatching { voicePlayback.play(voice) }
                                    .onFailure { attachmentError = context.getString(R.string.brain_voice_playback_failed) }
                            }
                        })
                    }
                    if (state.isGenerating) {
                        item {
                            GeneratingPulse(state.provider.displayName)
                        }
                    }
                }
            }
            // Close the message Surface before placing the floating action above it.
            }
            val jumpAlpha by animateFloatAsState(
                targetValue = if (showJumpToLatest) 1f else 0f,
                animationSpec = tween(durationMillis = 190),
                label = "latest_button_alpha"
            )
            val jumpScale by animateFloatAsState(
                targetValue = if (showJumpToLatest) 1f else 0.85f,
                animationSpec = tween(durationMillis = 190),
                label = "latest_button_scale"
            )
            if (showJumpToLatest || jumpAlpha > 0.01f) {
                Surface(
                    onClick = {
                        followLatest = true
                        scope.launch {
                            val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            listState.animateScrollToItem(last)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 15.dp, bottom = 15.dp)
                        .size(48.dp)
                        .graphicsLayer(alpha = jumpAlpha, scaleX = jumpScale, scaleY = jumpScale)
                        .testTag("chat_jump_to_latest"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 5.dp,
                    shadowElevation = 8.dp,
                    enabled = showJumpToLatest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.chat_go_to_latest_message),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        if (state.isGenerating) {
            TextButton(onClick = { viewModel.cancelChat() }) { Text(stringResource(R.string.chat_stop)) }
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
        if (attachment?.mimeType?.startsWith("image/") == true &&
            ModelImagePolicy.requiresUnverifiedRouterAttempt(
                state.provider, state.models.firstOrNull { it.id == state.selectedModel }
            )) {
            Text(
                stringResource(R.string.chat_unverified_router_vision),
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 10.sp
            )
        }
        if (attachment?.mimeType?.startsWith("audio/") == true &&
            state.provider != LlmProvider.GEMINI && state.provider != LlmProvider.OPENAI) {
            Text(stringResource(R.string.speech_recorded_audio_requires_model),
                color = MaterialTheme.colorScheme.tertiary, fontSize = 10.sp)
        }
        if (recording) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.speech_recording_status, recordingSeconds),
                    color = MaterialTheme.colorScheme.error, fontSize = 11.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { recorder.cancel(); recording = false }) { Text(stringResource(R.string.chat_discard), fontSize = 11.sp) }
            }
        }
        if (attachmentError != null) {
            Text(attachmentError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
        if (speechVoiceInstallNeeded) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                    }.onFailure {
                        attachmentError = context.getString(R.string.speech_tts_setup_unavailable)
                    }
                }
            ) {
                Text(stringResource(R.string.speech_install_tts))
            }
        }

        // Compact message input field; screen-level IME insets keep it above the keyboard.
        Surface(
            modifier = Modifier.semantics { contentDescription = context.getString(R.string.chat_composer_description) },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .65f))
        ) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = context.getString(R.string.chat_input_description) },
                    maxLines = 3,
                    enabled = !recording,
                    placeholder = { Text(stringResource(R.string.speech_input_hint), fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { imagePicker.launch("image/*") }, enabled = !recording, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Image, stringResource(R.string.speech_attach_image), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { filePicker.launch(arrayOf("text/*", "application/pdf", "application/json")) }, enabled = !recording, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.AttachFile, stringResource(R.string.speech_attach_document), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        tts?.stop()
                        voicePlayback.stop()
                        elevenJob?.cancel()
                        elevenPlayback.stop()
                        recordingSpeechLanguage = selectedSpeechLanguage
                        attachmentError = null
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, recordingSpeechLanguage.tag)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recordingSpeechLanguage.tag)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.speech_prompt))
                            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                        recognitionInProgress = true
                        try { voiceInput.launch(intent) }
                        catch (e: Exception) {
                            recognitionInProgress = false
                            attachmentError = context.getString(R.string.speech_recognizer_unavailable)
                        }
                    }, enabled = !recording && !recognitionInProgress, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardVoice, stringResource(R.string.speech_dictate), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        stopVoicePlayback()
                        if (recording) finishRecording()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED) startRecording()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }, enabled = !recognitionInProgress, modifier = Modifier.size(36.dp)) {
                        Icon(if (recording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord,
                            if (recording) stringResource(R.string.speech_stop_audio) else stringResource(R.string.speech_record),
                            tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                    }

                    Spacer(Modifier.weight(1f))

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
                        Icon(Icons.Default.Send, stringResource(R.string.speech_send), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (confirmDirectProvider) {
        AlertDialog(
            onDismissRequest = { confirmDirectProvider = false },
            title = { Text(stringResource(R.string.chat_combo_confirm_title,
                state.provider.displayName)) },
            text = {
                Text(stringResource(R.string.chat_combo_confirm_body,
                    state.provider.displayName, state.selectedModel))
            },
            confirmButton = {
                Button(onClick = {
                    confirmDirectProvider = false
                    viewModel.setSmartComboEnabled(false)
                }) { Text(stringResource(R.string.chat_combo_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDirectProvider = false }) {
                    Text(stringResource(R.string.chat_combo_keep_free))
                }
            }
        )
    }
    if (renameOpen) {
        AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text(stringResource(R.string.chat_rename)) },
            text = { OutlinedTextField(value = sessionTitle, onValueChange = { sessionTitle = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.renameChatSession(activeSessionId, sessionTitle); renameOpen = false }) { Text(stringResource(R.string.chat_save)) } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text(stringResource(R.string.studio_cancel)) } })
    }
    if (deleteOpen) {
        AlertDialog(onDismissRequest = { deleteOpen = false }, title = { Text(stringResource(R.string.chat_delete_confirm)) },
            text = { Text(stringResource(R.string.chat_delete_confirm_desc)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteChatSession(activeSessionId); deleteOpen = false }) { Text(stringResource(R.string.speech_delete)) } },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.studio_cancel)) } })
    }
    // Quick In-Chat API Key Entry Dialog
    if (quickKeyDialogOpen) {
        AlertDialog(
            onDismissRequest = { quickKeyDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.chat_connect_provider, quickKeyTargetProvider.displayName), fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when (quickKeyTargetProvider) {
                            LlmProvider.GEMINI -> stringResource(R.string.conv_gemini_hint)
                            LlmProvider.NVIDIA -> stringResource(R.string.conv_nvidia_hint)
                            LlmProvider.ROUTER_9_SMART -> stringResource(R.string.conv_router_hint)
                            LlmProvider.ROUTER_9_COMBO -> stringResource(R.string.conv_combo_hint)
                            else -> stringResource(R.string.chat_enter_provider_key, quickKeyTargetProvider.displayName)
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    if (quickKeyTargetProvider.isNineRouter || quickKeyTargetProvider == LlmProvider.OPENAI_COMPATIBLE) {
                        OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text(stringResource(R.string.chat_trusted_endpoint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(
                        value = quickKeyInput,
                        onValueChange = { quickKeyInput = it },
                        label = { Text(stringResource(R.string.chat_api_key)) },
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
                            Text(stringResource(R.string.chat_paste), fontSize = 11.sp)
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
                    Text(stringResource(R.string.chat_save_connect))
                }
            },
            dismissButton = {
                TextButton(onClick = { quickKeyDialogOpen = false }) {
                    Text(stringResource(R.string.studio_cancel))
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
                        Text(stringResource(R.string.chat_token_inspector), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        stringResource(R.string.chat_context_percent, (metrics.contextUsagePercent * 100).toInt()),
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
                            Text(stringResource(R.string.api_provider_label), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.provider.displayName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.api_model_label), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.selectedModel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.api_model_limit), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.api_token_count, numberFormat.format(metrics.contextCapacity)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.api_stat_turn), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.chat_turn_tokens, numberFormat.format(metrics.consumedTurnTotalTokens), numberFormat.format(metrics.consumedTurnInputTokens), numberFormat.format(metrics.consumedTurnOutputTokens)), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.api_stat_context_left), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.api_token_count, numberFormat.format(metrics.remainingContextTokens)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.api_stat_session), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.api_token_count, numberFormat.format(metrics.consumedSessionTokens)), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.api_stat_lifetime), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.api_token_count, numberFormat.format(metrics.consumedLifetimeTokens)), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (metrics.remainingBudgetTokens != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.api_local_budget_remaining), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${numberFormat.format(metrics.remainingBudgetTokens)} / ${numberFormat.format(metrics.monthlyTokenBudget)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Button(
                    onClick = { tokenDetailsOpen = false },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(stringResource(R.string.chat_close_inspector))
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
                Text(stringResource(R.string.chat_provider_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.chat_engine_preset_hint),
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
                        label = { Text(stringResource(R.string.chat_server_combo), fontSize = 11.sp) }
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
                                Text(stringResource(R.string.chat_endpoint_credentials), fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    label = { Text(stringResource(R.string.chat_baseurl)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    label = {
                                        Text(
                                            when (state.provider) {
                                                LlmProvider.GEMINI -> stringResource(R.string.chat_gemini_api_key)
                                                LlmProvider.NVIDIA -> stringResource(R.string.chat_nvidia_api_key)
                                                LlmProvider.ROUTER_9_SMART, LlmProvider.ROUTER_9_COMBO ->
                                                    stringResource(R.string.chat_router_api_key)
                                                else -> stringResource(R.string.chat_generic_api_key)
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
                                            stringResource(R.string.chat_keystore_encryption),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp
                                        )
                                    } else {
                                        Text(
                                            stringResource(R.string.chat_aes_encryption),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Button(onClick = { viewModel.detectApi(baseUrl, apiKey) }) {
                                        Icon(Icons.Default.Refresh, stringResource(R.string.chat_detect_api))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(if (state.status == ConnectionStatus.DETECTING) R.string.conv_detecting else R.string.chat_detect_api))
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
                                    Text(stringResource(R.string.chat_model_persona), fontWeight = FontWeight.SemiBold)
                                    Box(Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { modelExpanded = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(state.selectedModel.ifBlank { stringResource(R.string.chat_select_model) }, maxLines = 1)
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
                                                    ?: stringResource(R.string.chat_select_agent),
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
                            Text(stringResource(R.string.chat_disconnect_key))
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
                    name = stringResource(R.string.chat_server_combo),
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
                color = if (hasSavedKey) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(0.75.dp, if (hasSavedKey) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (hasSavedKey) Icons.Default.CheckCircle else Icons.Default.Key,
                        contentDescription = stringResource(R.string.chat_configure_key_description),
                        tint = if (hasSavedKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = stringResource(if (hasSavedKey) R.string.conv_key_ready else R.string.conv_key_setup),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasSavedKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onTertiaryContainer
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
    val chipColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        label = "provider-selection")
    Surface(
        onClick = onChipClick,
        shape = RoundedCornerShape(12.dp),
        color = chipColor,
        border = BorderStroke(
            width = if (selected) 1.dp else 0.75.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    contentDescription = stringResource(R.string.chat_select_model),
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
private fun GeneratingPulse(provider: String) {
    val animation = rememberInfiniteTransition(label = "generation")
    val opacity by animation.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(820, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "thinking_alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { index ->
            Box(Modifier.size((6 + index * 2).dp).graphicsLayer(alpha = opacity)
                .background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        Text(stringResource(R.string.chat_generating_provider, provider), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    sessionId: String,
    feedbackStore: ChatFeedbackStore,
    copied: Boolean,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    onSpeak: () -> Unit,
    onPlayVoice: () -> Unit
) {
    val isUser = message.role == "user"
    val background = if (message.isError) MaterialTheme.colorScheme.errorContainer
    else if (isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.background
    val textColor = if (message.isError) MaterialTheme.colorScheme.onErrorContainer
    else if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    val key = remember(sessionId, message.timestampMs, message.content) {
        feedbackStore.key(sessionId, message.timestampMs, message.content)
    }
    var reaction by remember(key) { mutableStateOf(feedbackStore.get(key)) }
    val draft = remember(message.content) { if (isUser) null else ChatPresentation.draft(message.content) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = background,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 3.dp,
                bottomEnd = if (isUser) 3.dp else 16.dp
            ),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 1f)
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                message.attachment?.let { attachment ->
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(9.dp)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (attachment.mimeType.startsWith("audio/")) Icons.Default.Mic
                                else if (attachment.mimeType.startsWith("image/")) Icons.Default.Image
                                else Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(attachment.name, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            if (attachment.mimeType.startsWith("audio/")) {
                                IconButton(onClick = onPlayVoice, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.PlayArrow, stringResource(R.string.chat_play_voice), modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
                if (draft == null) {
                    Text(message.content, color = textColor, fontSize = 13.sp, lineHeight = 19.sp)
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f))
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.chat_tool_proposal), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(draft.tool + (draft.path?.let { " · $it" } ?: ""),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(draft.description, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.chat_tool_no_file),
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!isUser && !message.isError) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        @Composable fun action(
                            icon: androidx.compose.ui.graphics.vector.ImageVector,
                            name: String, selected: Boolean = false, clicked: () -> Unit
                        ) {
                            val interactions = remember { MutableInteractionSource() }
                            val pressed by interactions.collectIsPressedAsState()
                            val scale by animateFloatAsState(if (pressed) .86f else if (selected) 1.12f else 1f,
                                animationSpec = spring(dampingRatio = .5f), label = "reaction-scale")
                            val haptics = LocalHapticFeedback.current
                            IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); clicked() },
                                interactionSource = interactions, modifier = Modifier.size(48.dp).scale(scale)) {
                                Icon(icon, contentDescription = name, modifier = Modifier.size(20.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        action(if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                            if (copied) stringResource(R.string.chat_copied) else stringResource(R.string.chat_copy), copied, onCopy)
                        action(Icons.Default.FileDownload, stringResource(R.string.chat_download), false, onDownload)
                        action(Icons.Default.ThumbUp, stringResource(R.string.chat_helpful), reaction == "like") {
                            reaction = if (reaction == "like") "" else "like"; feedbackStore.set(key, reaction)
                        }
                        action(Icons.Default.ThumbDown, stringResource(R.string.chat_not_helpful), reaction == "dislike") {
                            reaction = if (reaction == "dislike") "" else "dislike"; feedbackStore.set(key, reaction)
                        }
                        action(Icons.Default.Favorite, stringResource(R.string.chat_love), reaction == "love") {
                            reaction = if (reaction == "love") "" else "love"; feedbackStore.set(key, reaction)
                        }
                        action(Icons.Default.VolumeUp, stringResource(R.string.chat_speak_response), false, onSpeak)
                    }
                    if (copied) Text(stringResource(R.string.chat_copied_clipboard), fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** One playback owner per screen; stop, completion, error and disposal all remove cache bytes. */
private class VoicePlaybackController(private val context: Context) {
    private var player: MediaPlayer? = null
    private var file: File? = null
    fun stop() {
        player?.let { active -> runCatching { active.stop() }; active.release() }
        player = null
        file?.delete()
        file = null
    }
    fun play(attachment: ChatAttachment) {
        stop()
        require(attachment.data.isNotBlank() && attachment.data.length <= 4 * 1024 * 1024) { context.getString(R.string.conv_voice_payload) }
        val temp = File.createTempFile("matrix_voice_play_", ".m4a", context.cacheDir)
        file = temp
        val active = MediaPlayer()
        player = active
        try {
            FileOutputStream(temp).use { it.write(Base64.decode(attachment.data, Base64.DEFAULT)) }
            active.setDataSource(temp.absolutePath)
            active.setOnPreparedListener { if (player === it) it.start() }
            active.setOnCompletionListener { if (player === it) stop() }
            active.setOnErrorListener { failed, _, _ -> if (player === failed) stop(); true }
            active.prepareAsync()
        } catch (error: Exception) { stop(); throw error }
    }
}

private fun loadChatAttachment(context: Context, uri: Uri): ChatAttachment {
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val isText = mimeType.startsWith("text/") || mimeType == "application/json"
    var name = context.getString(R.string.conv_attachment_default)
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            name = cursor.getString(nameIndex) ?: context.getString(R.string.conv_attachment_default)
        }
    }
    if (mimeType.startsWith("image/") || name.lowercase().endsWith(".heic") ||
        name.lowercase().endsWith(".heif") || name.lowercase().endsWith(".bmp")) {
        val prepared = ImageInputPipeline.prepare(context, uri, name)
        return ChatAttachment(name = prepared.name, mimeType = "image/jpeg",
            data = Base64.encodeToString(prepared.jpeg, Base64.NO_WRAP), isText = false)
    }
    require(isText || mimeType in setOf("image/png", "image/jpeg", "image/webp", "application/pdf")) {
        context.getString(R.string.conv_attachment_unsupported)
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 5 * 1024 * 1024) { context.getString(R.string.conv_attachment_size) }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
        ?: throw IllegalStateException(context.getString(R.string.conv_attachment_read))
    require(bytes.isNotEmpty()) { context.getString(R.string.conv_attachment_empty) }
    if (mimeType.startsWith("image/")) {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0 &&
            options.outWidth.toLong() * options.outHeight <= 40_000_000) { context.getString(R.string.conv_image_invalid) }
    }
    if (mimeType == "application/pdf") require(bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-") { context.getString(R.string.conv_pdf_invalid) }
    if (isText) require(!bytes.contains(0)) { context.getString(R.string.conv_text_binary) }
    val data = if (isText) String(bytes, Charsets.UTF_8) else Base64.encodeToString(bytes, Base64.NO_WRAP)
    return ChatAttachment(name = name, mimeType = mimeType, data = data, isText = isText)
}
