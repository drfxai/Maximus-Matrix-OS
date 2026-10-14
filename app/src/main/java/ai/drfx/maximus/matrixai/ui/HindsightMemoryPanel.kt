package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.memory.HindsightMemoryClient
import ai.drfx.maximus.matrixai.memory.HindsightMemorySettings
import ai.drfx.maximus.matrixai.memory.SharedProjectMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Explicit consent and privacy controls, no remote calls while merely browsing a project.
 * Cloud and user-controlled self-hosted HTTPS are both supported.
 */
@Composable
internal fun HindsightMemoryPanel(projectId: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember(context) { HindsightMemorySettings(context.applicationContext) }
    val bridge = remember(context) { SharedProjectMemory(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(settings.endpoint()) }
    var keyInput by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(settings.enabled()) }
    var autoShare by remember { mutableStateOf(settings.autoShare()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Remote project memory is optional and disabled by default.") }

    LaunchedEffect(projectId) {
        settings.setActiveProject(projectId.orEmpty())
        bridge.forgetCache()
        if (projectId != null && settings.enabled()) {
            runCatching { bridge.refresh() }
                .onSuccess { status = "$it historical facts ready for model handoff." }
                .onFailure { status = "Local checkpoint is available; remote recall is offline or unconfigured." }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("HINDSIGHT · SHARED PROJECT MEMORY",
            style = MaterialTheme.typography.titleMedium)
        Text("Retain / Recall across different LLM models. Each project uses an isolated memory bank. Models never receive your Hindsight key.",
            style = MaterialTheme.typography.bodySmall)
        Text("Bank: " + (projectId?.let(HindsightMemoryClient::bankFor) ?: "Select a project"),
            style = MaterialTheme.typography.labelSmall)

        OutlinedTextField(
            value = url, onValueChange = { url = it.take(200) },
            label = { Text("Hindsight HTTPS endpoint") },
            modifier = Modifier.fillMaxWidth().testTag("hindsight_endpoint"),
            singleLine = true, enabled = !busy
        )
        OutlinedTextField(
            value = keyInput, onValueChange = { keyInput = it.take(512) },
            label = { Text("Your Hindsight API key (optional for private edge)") },
            placeholder = { Text(if (settings.hasKey()) "Saved securely — enter to replace" else "hsk_...") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("hindsight_key"),
            singleLine = true, enabled = !busy
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                try {
                    settings.configure(url, keyInput.trim().ifBlank { null })
                    keyInput = ""
                    bridge.forgetCache()
                    status = "Connection configuration encrypted and saved. Verify recall before use."
                } catch (error: Exception) { status = error.message ?: "Invalid configuration." }
            }) { Text("Save connection") }

            OutlinedButton(enabled = !busy, onClick = {
                settings.clear()
                settings.setActiveProject(projectId.orEmpty())
                bridge.forgetCache()
                enabled = false
                autoShare = false
                url = settings.endpoint()
                keyInput = ""
                status = "Connection and access key removed; remote memories must be deleted separately."
            }) { Text("Disconnect") }
        }
        Row {
            Column(Modifier.weight(1f)) {
                Text("Enable Hindsight memory", style = MaterialTheme.typography.titleSmall)
                Text("Keeps encrypted short progress locally; only approved checkpoints sync remotely unless auto-share is separately enabled.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    try {
                        settings.setEnabled(checked)
                        enabled = checked
                        if (!checked) {
                            settings.setAutoShare(false)
                            autoShare = false
                            bridge.forgetCache()
                        }
                        status = if (checked) "Shared project memory enabled. Loading prior project facts…" else "Shared memory disabled."
                        if (checked && projectId != null) {
                            scope.launch {
                                runCatching { bridge.refresh() }
                                    .onSuccess { status = "$it historical facts available to the next model." }
                                    .onFailure { status = "No remote memories loaded yet. Local checkpoint remains available." }
                            }
                        }
                    } catch (error: Exception) { status = error.message ?: "Cannot enable memory." }
                }
            )
        }

        Row {
            Column(Modifier.weight(1f)) {
                Text("Automatically share model progress", style = MaterialTheme.typography.titleSmall)
                Text("Optional: sends short task/response excerpts after successful chats. These may contain private information. Leave off for sensitive projects.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = autoShare, enabled = enabled, onCheckedChange = {
                settings.setAutoShare(it)
                autoShare = it
                status = if (it) "Progress sharing enabled for the selected project."
                    else "Only manually approved checkpoints will be uploaded."
            })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = enabled && projectId != null && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val count = bridge.refresh(force = true)
                        status = "$count project memories recalled. Models can use them after switching."
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status = e.message ?: "Recall failed." }
                    finally { busy = false }
                }
            }) { Text("Recall now") }
            OutlinedButton(enabled = enabled && projectId != null && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        bridge.storeCheckpoint(requireNotNull(projectId))
                        val count = bridge.refresh()
                        status = "Verified checkpoint retained; $count relevant memories ready."
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status = e.message ?: "Checkpoint sync unavailable." }
                    finally { busy = false }
                }
            }) { Text("Sync checkpoint") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text("Memory is untrusted reference data, never permission to execute tools. If the server is unavailable, local project checkpoints still work. Disconnecting does not delete cloud memories.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
