package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.studio.remote.RemoteWorkerCapabilities
import ai.drfx.maximus.matrixai.studio.remote.RemoteWorkerClient
import ai.drfx.maximus.matrixai.studio.remote.RemoteWorkerProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RemoteWorkerPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { RemoteWorkerProfileStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var endpoint by remember { mutableStateOf(store.endpoint()) }
    var token by remember { mutableStateOf("") }
    var capabilities by remember { mutableStateOf<RemoteWorkerCapabilities?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val cyan = Color(0xFF48CEE8)

    Surface(
        modifier = modifier.fillMaxWidth().testTag("remote_worker_panel"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, cyan.copy(alpha = 0.32f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(stringResource(R.string.runtime_title), style = MaterialTheme.typography.titleMedium, color = cyan)
            Text(stringResource(R.string.runtime_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = endpoint, onValueChange = { endpoint = it.take(512); capabilities = null },
                label = { Text(stringResource(R.string.runtime_url)) },
                placeholder = { Text("https://worker.example.com") },
                enabled = !busy, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("runtime_url_input")
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it.take(4096); capabilities = null },
                label = { Text(stringResource(R.string.runtime_token)) },
                placeholder = { Text(stringResource(R.string.runtime_token_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                enabled = !busy, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("runtime_token_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        capabilities = null
                        status = ""
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val saved = store.credentialFor(endpoint)
                                    val credential = token.ifBlank { saved }
                                    require(credential.isNotBlank()) {
                                        "Enter a new token when adding or changing the Worker endpoint."
                                    }
                                    val verified = RemoteWorkerClient.discover(endpoint, credential)
                                    // Persist only after a successful authenticated HTTPS handshake.
                                    store.save(endpoint, credential)
                                    verified
                                }
                                capabilities = result
                                token = ""
                                status = context.getString(R.string.runtime_connected)
                            } catch (error: Exception) {
                                status = error.message ?: context.getString(R.string.runtime_failed)
                            } finally { busy = false }
                        }
                    },
                    enabled = !busy && endpoint.isNotBlank(),
                    modifier = Modifier.testTag("runtime_connect_button")
                ) { Icon(Icons.Default.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.runtime_connect)) }
                OutlinedButton(
                    onClick = {
                        store.forget()
                        endpoint = ""
                        token = ""
                        capabilities = null
                        status = context.getString(R.string.runtime_disconnected)
                    },
                    enabled = !busy && store.hasCredential()
                ) { Text(stringResource(R.string.runtime_forget)) }
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                when {
                    capabilities != null -> status
                    status.isNotBlank() -> status
                    store.hasCredential() -> stringResource(R.string.runtime_saved_unverified)
                    else -> stringResource(R.string.runtime_disconnected)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (capabilities != null) Color(0xFF54D9B0)
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            capabilities?.let { worker ->
                Text(worker.platform.uppercase() + " · " + worker.workerId.take(12) + "…",
                    style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.runtime_discovery_only),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.runtime_no_execution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
