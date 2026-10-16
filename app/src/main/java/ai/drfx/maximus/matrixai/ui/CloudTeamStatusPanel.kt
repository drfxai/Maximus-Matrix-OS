package ai.drfx.maximus.matrixai.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.studio.connections.CloudTeamHealthClient
import ai.drfx.maximus.matrixai.studio.connections.DeveloperCredentialVault
import ai.drfx.maximus.matrixai.studio.connections.DevelopmentService
import kotlinx.coroutines.launch

/** Independent opt-in cloud status connection. No Github/Cloudflare admin token is reused. */
@Composable
internal fun CloudTeamStatusPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(
        "maximus_cloud_team_status_v1", Context.MODE_PRIVATE) }
    val vault = remember(context) { DeveloperCredentialVault(context) }
    val client = remember { CloudTeamHealthClient() }
    val scope = rememberCoroutineScope()
    var origin by remember { mutableStateOf(prefs.getString("origin", "").orEmpty()) }
    var token by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(vault.configured(DevelopmentService.CLOUD_AGENT)) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    val unavailable = stringResource(R.string.cloud_agent_unavailable)

    ElevatedCard(modifier.fillMaxWidth().testTag("cloud_team_status_panel")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cloud_agent_title),
                style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.cloud_agent_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = origin, onValueChange = { origin = it.take(240) },
                label = { Text(stringResource(R.string.cloud_agent_endpoint)) },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = token, onValueChange = { token = it.take(128) },
                label = { Text(stringResource(R.string.cloud_agent_token)) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                enabled = !busy, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    try {
                        val verified = CloudTeamHealthClient.origin(origin)
                        if (token.isNotBlank()) {
                            require(token.length in 32..128 && token.none { it.isWhitespace() })
                            vault.save(DevelopmentService.CLOUD_AGENT, token)
                            token = ""
                        }
                        check(vault.configured(DevelopmentService.CLOUD_AGENT))
                        check(prefs.edit().putString("origin", verified).commit())
                        origin = verified
                        connected = true
                        feedback = ""
                    } catch (_: Exception) { feedback = unavailable }
                }, enabled = !busy && origin.isNotBlank()) {
                    Text(stringResource(R.string.cloud_agent_save))
                }
                Button(onClick = {
                    if (busy || !connected) return@Button
                    busy = true
                    scope.launch {
                        feedback = try {
                            val result = client.health(origin,
                                vault.snapshot(DevelopmentService.CLOUD_AGENT).token)
                            context.getString(R.string.cloud_agent_ready, result.configuredTeams)
                        } catch (_: Exception) { unavailable }
                        finally { busy = false }
                    }
                }, enabled = !busy && connected && origin.isNotBlank()) {
                    Text(stringResource(R.string.cloud_agent_check))
                }
            }
            if (feedback.isNotBlank()) Text(feedback,
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                runCatching {
                    vault.clear(DevelopmentService.CLOUD_AGENT)
                    check(prefs.edit().remove("origin").commit())
                    origin = ""; token = ""; connected = false; feedback = ""
                }.onFailure { feedback = unavailable }
            }, enabled = !busy && connected) {
                Text(stringResource(R.string.cloud_agent_disconnect))
            }
        }
    }
}
