package ai.drfx.maximus.matrixai.ui

import androidx.compose.ui.res.stringResource

import ai.drfx.maximus.matrixai.R

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
    var status by remember(context) { mutableStateOf(context.getString(R.string.hindsight_status_optional)) }

    LaunchedEffect(projectId) {
        settings.setActiveProject(projectId.orEmpty())
        bridge.forgetCache()
        if (projectId != null && settings.enabled()) {
            try {
                val count = bridge.refresh()
                status = context.getString(R.string.hindsight_status_model_handoff, count)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status = context.getString(R.string.hindsight_status_remote_unavailable)
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.hindsight_title),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.hindsight_summary),
            style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.hindsight_bank,
            projectId?.let(HindsightMemoryClient::bankFor)
                ?: stringResource(R.string.hindsight_select_project)),
            style = MaterialTheme.typography.labelSmall)

        OutlinedTextField(
            value = url, onValueChange = { url = it.take(200) },
            label = { Text(stringResource(R.string.hindsight_endpoint)) },
            modifier = Modifier.fillMaxWidth().testTag("hindsight_endpoint"),
            singleLine = true, enabled = !busy
        )
        OutlinedTextField(
            value = keyInput, onValueChange = { keyInput = it.take(512) },
            label = { Text(stringResource(R.string.hindsight_key)) },
            placeholder = { Text(if (settings.hasKey())
                stringResource(R.string.hindsight_key_saved_hint) else "hsk_...") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("hindsight_key"),
            singleLine = true, enabled = !busy
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                try {
                    settings.configure(url, keyInput.trim().ifBlank { null })
                    autoShare = settings.autoShare()
                    keyInput = ""
                    bridge.forgetCache()
                    status = context.getString(R.string.hindsight_status_config_saved)
                } catch (_: Exception) {
                    status = context.getString(R.string.hindsight_status_invalid_config)
                }
            }) { Text(stringResource(R.string.hindsight_save)) }

            OutlinedButton(enabled = !busy, onClick = {
                settings.disconnect()
                bridge.forgetCache()
                enabled = false
                autoShare = false
                url = settings.endpoint()
                keyInput = ""
                status = context.getString(R.string.hindsight_status_disconnected)
            }) { Text(stringResource(R.string.data_disconnect)) }
        }
        Row {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.hindsight_enable), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.hindsight_privacy_local_only),
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
                        status = context.getString(if (checked)
                            R.string.hindsight_status_enabled else R.string.hindsight_status_disabled)
                        if (checked && projectId != null) {
                            scope.launch {
                                try {
                                    val count = bridge.refresh()
                                    status = context.getString(R.string.hindsight_status_next_model, count)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    status = context.getString(R.string.hindsight_status_none_loaded)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        status = context.getString(R.string.hindsight_status_enable_failed)
                    }
                }
            )
        }

        Row {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.hindsight_share), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.hindsight_autoshare_privacy),
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = autoShare, enabled = enabled, onCheckedChange = {
                settings.setAutoShare(it)
                autoShare = it
                status = context.getString(if (it)
                    R.string.hindsight_status_share_enabled
                    else R.string.hindsight_status_share_manual)
            })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = enabled && projectId != null && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val count = bridge.refresh(force = true)
                        status = context.getString(R.string.hindsight_status_recalled, count)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        status = context.getString(R.string.hindsight_status_recall_failed)
                    }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.hindsight_recall)) }
            OutlinedButton(enabled = enabled && projectId != null && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        bridge.storeCheckpoint(requireNotNull(projectId))
                        val count = bridge.refresh()
                        status = context.getString(R.string.hindsight_status_checkpoint_retained, count)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        status = context.getString(R.string.hindsight_status_checkpoint_failed)
                    }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.hindsight_sync)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.hindsight_safety_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
