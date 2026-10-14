package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.studio.memory.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explicit external sharing. All user models recall the SAME bank when authorized.
 * Bank ID is not a secret; access always requires a separately authorized Hindsight key.
 */
@Composable
internal fun HindsightMemoryPanel(
    projectId: String?,
    settings: HindsightMemorySettings,
    memory: ProjectSharedMemory,
    onContinue: (String) -> Boolean
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var keyInput by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(settings.hasKey()) }
    var bankInput by remember(projectId) {
        mutableStateOf(projectId?.let(settings::bank).orEmpty())
    }
    var active by remember(projectId, hasKey) {
        mutableStateOf(projectId?.let(settings::enabledFor) ?: false)
    }
    var lesson by remember(projectId) { mutableStateOf("") }
    var question by remember(projectId) { mutableStateOf("Which decisions, failed attempts and next tasks matter?") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Not connected. Cloud sharing is off by default.") }
    var recalled by remember(projectId) { mutableStateOf<List<ProjectMemory>>(emptyList()) }

    fun launchTask(task: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { task() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = e.message ?: "Shared memory unavailable." }
            finally { busy = false }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("HINDSIGHT · SHARED PROJECT MEMORY", style = MaterialTheme.typography.titleMedium)
        Text(
            "One authorized memory bank for every model working on this project. Source files and checkpoints stay local; approved experience notes may be uploaded to Hindsight Cloud.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = keyInput, onValueChange = { keyInput = it.take(512) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("Your Hindsight Cloud API key") },
            placeholder = { Text(if (hasKey) "Key stored with Android Keystore" else "Enter your own API key") },
            enabled = !busy, singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                runCatching {
                    settings.saveKey(keyInput.trim())
                    keyInput = ""
                    hasKey = true
                    status = "Encrypted key saved. Cloud access is unverified until Recall/Retain succeeds."
                }.onFailure { status = it.message ?: "Could not save key." }
            }, enabled = !busy && keyInput.isNotBlank()) { Text("Save key") }
            OutlinedButton(onClick = {
                runCatching {
                    settings.removeKey()
                    keyInput = ""
                    hasKey = false
                    active = false
                    recalled = emptyList()
                    status = "Local key revoked. Remote memories remain in your Hindsight bank."
                }.onFailure { status = it.message ?: "Could not revoke key." }
            }, enabled = !busy && hasKey) { Text("Revoke key") }
        }
        OutlinedTextField(
            value = bankInput, onValueChange = { bankInput = it.lowercase().take(96) },
            label = { Text("Shared project bank ID (same across models/devices)") },
            modifier = Modifier.fillMaxWidth(), enabled = !busy && projectId != null, singleLine = true
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = {
                val id = projectId ?: return@OutlinedButton
                runCatching {
                    settings.setBank(id, bankInput)
                    recalled = emptyList()
                    status = "Bank association saved. Other models require their own authorized access."
                }.onFailure { status = it.message ?: "Invalid bank ID." }
            }, enabled = !busy && projectId != null && HindsightClient.validBankId(bankInput)) {
                Text("Use this bank")
            }
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(bankInput)) },
                enabled = bankInput.isNotBlank()
            ) { Text("Copy bank ID") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Enable shared cloud memory", style = MaterialTheme.typography.titleSmall)
                Text("Only explicitly approved notes are uploaded; Recall adds history to model handoff.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = active,
                enabled = projectId != null && hasKey && !busy,
                onCheckedChange = { chosen ->
                    val id = projectId ?: return@Switch
                    runCatching {
                        settings.enable(id, chosen)
                        active = chosen
                        status = if (chosen) "Shared project memory is enabled. No data has been uploaded."
                        else "Shared memory disabled locally; cloud bank content is unchanged."
                    }.onFailure { status = it.message ?: "Could not change memory settings." }
                }
            )
        }

        OutlinedTextField(
            value = lesson, onValueChange = { lesson = it.take(1200) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Approved outcome / lesson / decision") },
            placeholder = { Text("A test passed, a design decision was accepted, or an approach failed...") },
            minLines = 2, maxLines = 4, enabled = active && !busy
        )
        Button(
            enabled = active && !busy && projectId != null && lesson.isNotBlank(),
            onClick = {
                val id = projectId ?: return@Button
                launchTask {
                    val outcome = withContext(Dispatchers.IO) { memory.publish(id, lesson) }
                    lesson = ""
                    status = "Approved memory retained in ${outcome.bankId}; revision ${outcome.revision.take(10)}."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Retain approved checkpoint & experience") }

        OutlinedTextField(
            value = question, onValueChange = { question = it.take(500) },
            label = { Text("What should the next model know?") },
            modifier = Modifier.fillMaxWidth(), enabled = active && !busy,
            minLines = 1, maxLines = 3
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = active && !busy && projectId != null && question.isNotBlank(),
                onClick = {
                    val id = projectId ?: return@OutlinedButton
                    launchTask {
                        val result = withContext(Dispatchers.IO) {
                            HindsightClient().recall(settings.key(), settings.bank(id), question)
                        }
                        recalled = result.entries
                        status = "Recall completed: ${result.entries.size} relevant memory notes."
                    }
                }
            ) { Text("Recall") }
            Button(
                enabled = projectId != null && !busy,
                onClick = {
                    val id = projectId ?: return@Button
                    launchTask {
                        val prompt = withContext(Dispatchers.IO) { memory.resume(id, question.ifBlank { "Continue project" }) }
                        val sent = onContinue(prompt)
                        status = if (sent) "Verified checkpoint and shared knowledge sent to the selected model."
                        else "Connect a model before resuming."
                    }
                }
            ) { Text("Continue with model") }
        }
        recalled.take(5).forEach { note ->
            Text("[${note.kind}] ${note.text.take(350)}", style = MaterialTheme.typography.bodySmall)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Remote memories are untrusted historical notes. Always verify source revisions and tests. Memory does not authorize code execution, payment or disclosure of keys.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
