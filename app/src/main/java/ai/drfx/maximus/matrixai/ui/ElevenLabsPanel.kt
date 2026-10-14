package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.voice.ElevenAudioPlayer
import ai.drfx.maximus.matrixai.voice.ElevenLabsClient
import ai.drfx.maximus.matrixai.voice.ElevenQuota
import ai.drfx.maximus.matrixai.voice.ElevenVoice
import ai.drfx.maximus.matrixai.voice.ElevenVoiceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Own-key voice integration. No implicit paid calls, no shared gateway credential in the APK. */
@Composable
fun ElevenLabsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { ElevenVoiceSettings(context.applicationContext) }
    val client = remember { ElevenLabsClient() }
    val player = remember(context) { ElevenAudioPlayer(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var apiKeyEntry by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(store.hasKey()) }
    var active by remember { mutableStateOf(store.elevenSelected()) }
    var voiceId by remember { mutableStateOf(store.selectedVoice()) }
    var model by remember { mutableStateOf(store.selectedModel()) }
    var quota by remember { mutableStateOf<ElevenQuota?>(null) }
    var voices by remember { mutableStateOf<List<ElevenVoice>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Connect your own account to verify its allowance and voice catalog.") }
    var voicePickerOpen by remember { mutableStateOf(false) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(player) {
        onDispose { activeJob?.cancel(); player.stop() }
    }

    fun execute(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        activeJob = scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { status = e.message ?: "Voice service unavailable." }
            finally { busy = false }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ElevenLabs Voice Studio", style = MaterialTheme.typography.headlineSmall)
        Text("Natural multilingual text-to-speech for chat responses. This is separate from the LLM Smart Combo router.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = apiKeyEntry, onValueChange = { apiKeyEntry = it.take(512) },
            modifier = Modifier.fillMaxWidth().testTag("elevenlabs_api_key"),
            label = { Text("Your ElevenLabs API key") },
            placeholder = { Text(if (hasKey) "Saved securely — enter to replace" else "Paste your own API key") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !busy, singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy && (apiKeyEntry.isNotBlank() || hasKey),
                onClick = {
                    execute {
                        val candidate = apiKeyEntry.trim().ifBlank { store.key() }
                        val account = client.quota(candidate)
                        if (apiKeyEntry.isNotBlank()) {
                            store.saveKey(candidate)
                            apiKeyEntry = ""
                        }
                        hasKey = true
                        quota = account
                        status = "Account verified. Reported plan: " + account.tier
                    }
                }
            ) { Text("Verify & Save") }
            OutlinedButton(
                enabled = !busy && hasKey,
                onClick = {
                    activeJob?.cancel()
                    player.stop()
                    store.clear()
                    apiKeyEntry = ""
                    hasKey = false
                    active = false
                    quota = null
                    voices = emptyList()
                    voiceId = ""
                    status = "ElevenLabs key and selections removed."
                }
            ) { Text("Remove key") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use ElevenLabs for manually played chat replies", style = MaterialTheme.typography.titleSmall)
                Text("Off by default. Automatic voice replies stay on Android device TTS.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = active, enabled = hasKey && voiceId.isNotBlank() && !busy,
                onCheckedChange = {
                    store.selectEleven(it)
                    active = it
                    status = if (it) "ElevenLabs enabled for explicit Speak actions." else "Local speech selected."
                })
        }

        quota?.let { q ->
            Text("Reported allowance: ${q.remaining} remaining / ${q.limit} · used ${q.used} · ${q.tier}",
                style = MaterialTheme.typography.titleSmall)
            if (q.limit > 0) LinearProgressIndicator(
                progress = { (q.used.toFloat() / q.limit.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Provider snapshot, not LLM tokens. Different voices/models can consume multiple credits per character; final charges depend on your account.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedButton(enabled = !busy && hasKey, onClick = {
            execute {
                voices = client.voices(store.key())
                if (voices.isEmpty()) {
                    status = "No voices returned for this account."
                } else {
                    status = "${voices.size} voices loaded. Select one for speech."
                }
            }
        }) { Text("Load available voices") }

        Box {
            OutlinedButton(
                enabled = !busy && voices.isNotEmpty(), onClick = { voicePickerOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(voices.firstOrNull { it.id == voiceId }?.name
                    ?: if (voiceId.isNotBlank()) "Saved voice: ${voiceId.take(10)}…" else "Select voice")
            }
            DropdownMenu(expanded = voicePickerOpen, onDismissRequest = { voicePickerOpen = false }) {
                voices.forEach { voice ->
                    DropdownMenuItem(text = { Text(voice.name) }, onClick = {
                        store.saveVoice(voice.id)
                        voiceId = voice.id
                        voicePickerOpen = false
                    })
                }
            }
        }

        Box {
            OutlinedButton(onClick = { modelPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Speech model: " + model)
            }
            DropdownMenu(expanded = modelPickerOpen, onDismissRequest = { modelPickerOpen = false }) {
                listOf(ElevenLabsClient.DEFAULT_MODEL, ElevenLabsClient.LOW_LATENCY_MODEL).forEach { id ->
                    DropdownMenuItem(text = { Text(id) }, onClick = {
                        store.saveModel(id); model = id; modelPickerOpen = false
                    })
                }
            }
        }

        Button(enabled = !busy && hasKey && voiceId.isNotBlank(), onClick = {
            execute {
                val sample = "Hello from Maximus Matrix OS."
                val bytes = client.synthesize(store.key(), voiceId, model, sample)
                player.play(bytes)
                status = "Playing voice sample. Credits may have been used."
                quota = runCatching { client.quota(store.key()) }.getOrNull()
            }
        }, modifier = Modifier.fillMaxWidth().testTag("elevenlabs_test_voice")) {
            Text("Generate & play sample (uses account allowance)")
        }
        OutlinedButton(onClick = {
            activeJob?.cancel()
            player.stop()
            status = "Voice playback stopped."
        }, modifier = Modifier.fillMaxWidth()) { Text("Stop audio") }

        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text("Only explicit sample or Speak actions send text to ElevenLabs. No automatic paid fallback. Never paste a shared production provider key into a public APK.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
