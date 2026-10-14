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
import androidx.compose.ui.res.stringResource
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.voice.SpeechLanguage
import ai.drfx.maximus.matrixai.voice.SpeechLanguagePreferenceStore
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import ai.drfx.maximus.matrixai.ui.theme.LanguagePreferenceStore
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
    var status by remember(context) { mutableStateOf(context.getString(R.string.eleven_status_connect)) }
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
            catch (e: Exception) { status = context.getString(R.string.eleven_status_error) }
            finally { busy = false }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.eleven_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.eleven_intro),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = apiKeyEntry, onValueChange = { apiKeyEntry = it.take(512) },
            modifier = Modifier.fillMaxWidth().testTag("elevenlabs_api_key"),
            label = { Text(stringResource(R.string.eleven_key)) },
            placeholder = { Text(stringResource(if (hasKey) R.string.eleven_saved_key
                else R.string.eleven_paste_key)) },
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
                        status = context.getString(R.string.eleven_status_verified, account.tier)
                    }
                }
            ) { Text(stringResource(R.string.eleven_verify)) }
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
                    status = context.getString(R.string.eleven_status_removed)
                }
            ) { Text(stringResource(R.string.eleven_remove)) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.eleven_use_manual), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.eleven_auto_local),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = active, enabled = hasKey && voiceId.isNotBlank() && !busy,
                onCheckedChange = {
                    store.selectEleven(it)
                    active = it
                    status = context.getString(if (it) R.string.eleven_status_enabled
                        else R.string.eleven_status_local)
                })
        }

        quota?.let { q ->
            Text(stringResource(R.string.eleven_allowance, q.remaining, q.limit, q.used, q.tier),
                style = MaterialTheme.typography.titleSmall)
            if (q.limit > 0) LinearProgressIndicator(
                progress = { (q.used.toFloat() / q.limit.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.eleven_allowance_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedButton(enabled = !busy && hasKey, onClick = {
            execute {
                voices = client.voices(store.key())
                if (voices.isEmpty()) {
                    status = context.getString(R.string.eleven_status_no_voices)
                } else {
                    status = context.getString(R.string.eleven_status_voice_count, voices.size)
                }
            }
        }) { Text(stringResource(R.string.eleven_load_voices)) }

        Box {
            OutlinedButton(
                enabled = !busy && voices.isNotEmpty(), onClick = { voicePickerOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(voices.firstOrNull { it.id == voiceId }?.name
                    ?: if (voiceId.isNotBlank())
                        stringResource(R.string.eleven_saved_voice, voiceId.take(10))
                    else stringResource(R.string.eleven_select_voice))
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
                Text(stringResource(R.string.eleven_model, model))
            }
            DropdownMenu(expanded = modelPickerOpen, onDismissRequest = { modelPickerOpen = false }) {
                listOf(ElevenLabsClient.DEFAULT_MODEL, ElevenLabsClient.LOW_LATENCY_MODEL, ElevenLabsClient.PERSIAN_MODEL).forEach { id ->
                    DropdownMenuItem(text = { Text(id) }, onClick = {
                        store.saveModel(id); model = id; modelPickerOpen = false
                    })
                }
            }
        }

        Text(stringResource(R.string.eleven_persian_advice),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(enabled = !busy && hasKey && voiceId.isNotBlank(), onClick = {
            execute {
                val speech = SpeechLanguagePreferenceStore(context).selected()
                val interfaceLanguage = when (speech) {
                    SpeechLanguage.PERSIAN -> AppLanguage.PERSIAN
                    SpeechLanguage.ARABIC -> AppLanguage.ARABIC
                    SpeechLanguage.TURKISH -> AppLanguage.TURKISH
                    SpeechLanguage.RUSSIAN -> AppLanguage.RUSSIAN
                    SpeechLanguage.ENGLISH -> AppLanguage.ENGLISH
                }
                val sample = LanguagePreferenceStore.localizedContext(
                    context, interfaceLanguage
                ).getString(R.string.eleven_sample_text)
                val bytes = client.synthesize(store.key(), voiceId, model, sample)
                player.play(bytes)
                status = context.getString(R.string.eleven_status_sample)
                quota = runCatching { client.quota(store.key()) }.getOrNull()
            }
        }, modifier = Modifier.fillMaxWidth().testTag("elevenlabs_test_voice")) {
            Text(stringResource(R.string.eleven_generate_sample))
        }
        OutlinedButton(onClick = {
            activeJob?.cancel()
            player.stop()
            status = context.getString(R.string.eleven_status_stopped)
        }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.speech_stop_audio))
        }

        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.eleven_privacy),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
