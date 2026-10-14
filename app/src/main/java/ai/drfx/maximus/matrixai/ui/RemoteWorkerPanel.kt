package ai.drfx.maximus.matrixai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
import ai.drfx.maximus.matrixai.studio.remote.RemoteDebugBuildClient
import ai.drfx.maximus.matrixai.studio.remote.RemoteDebugJob
import kotlinx.coroutines.delay
import java.io.File
import ai.drfx.maximus.matrixai.studio.remote.RemoteWorkerCapabilities
import ai.drfx.maximus.matrixai.studio.remote.RemoteWorkerClient
import ai.drfx.maximus.matrixai.studio.remote.RemoteWorkerProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RemoteWorkerPanel(projectId: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { RemoteWorkerProfileStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val projects = remember(context) { ProjectWorkspaceStore(context.applicationContext) }
    var endpoint by remember { mutableStateOf(store.endpoint()) }
    var token by remember { mutableStateOf("") }
    var capabilities by remember { mutableStateOf<RemoteWorkerCapabilities?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDebugBuild by remember { mutableStateOf(false) }
    var debugJob by remember(projectId) { mutableStateOf<RemoteDebugJob?>(null) }
    var status by remember { mutableStateOf("") }
    val cyan = Color(0xFF48CEE8)
    val downloadDebug = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        val current = debugJob
        if (uri != null && current?.state == "succeeded" && !busy) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val key = store.credentialFor(endpoint)
                        require(key.isNotBlank()) { "Connect this Worker again." }
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Cannot write to selected destination.")
                        output.use { RemoteDebugBuildClient.download(endpoint, key, current, it) }
                    }
                    status = "SHA-256 verified debug APK exported. No release signing used."
                } catch (error: Exception) {
                    status = error.message ?: "Debug APK download failed."
                } finally { busy = false }
            }
        }
    }
    LaunchedEffect(debugJob?.id, endpoint) {
        val id = debugJob?.id ?: return@LaunchedEffect
        repeat(200) {
            delay(3_000)
            try {
                val key = store.credentialFor(endpoint)
                if (key.isBlank()) return@LaunchedEffect
                val current = withContext(Dispatchers.IO) {
                    RemoteDebugBuildClient.status(endpoint, key, id)
                }
                if (current.projectId != projectId) return@LaunchedEffect
                debugJob = current
                if (current.finished) {
                    status = if (current.state == "succeeded")
                        "Debug APK ready. Download after checksum verification."
                    else current.error.ifBlank { "Isolated debug build failed." }
                    return@LaunchedEffect
                }
            } catch (error: Exception) {
                status = error.message ?: "Cannot check debug build status."
                return@LaunchedEffect
            }
        }
    }

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
                if (worker.executionEnabled && worker.operations.containsAll(
                    listOf("jobs.submit", "jobs.status", "jobs.artifact"))) {
                    Text("Explicit debug builds run in the owner's isolated, offline Worker. " +
                        "No production signing key is transferred.",
                        style = MaterialTheme.typography.bodySmall)
                    Button(
                        enabled = !busy && projectId != null,
                        onClick = { confirmDebugBuild = true },
                        modifier = Modifier.testTag("runtime_build_debug_button")
                    ) { Text("Build Debug APK") }
                    debugJob?.let { job ->
                        Text("Debug build: " + job.state + " · " + job.id.take(10),
                            style = MaterialTheme.typography.bodySmall)
                        if (job.state == "succeeded") {
                            OutlinedButton(onClick = {
                                downloadDebug.launch("maximus-project-debug.apk")
                            }, enabled = !busy) { Text("Download verified Debug APK") }
                        }
                    }
                } else {
                    Text(stringResource(R.string.runtime_discovery_only),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (capabilities?.executionEnabled != true) {
                Text(stringResource(R.string.runtime_no_execution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirmDebugBuild) {
        AlertDialog(
            onDismissRequest = { confirmDebugBuild = false },
            title = { Text("Approve isolated debug build?") },
            text = { Text("The selected project's source ZIP will be sent to your authenticated " +
                "remote Worker and built offline in a rootless container. Do not upload secret " +
                "keys or confidential projects to a Worker you do not control. " +
                "This operation never signs a production APK.") },
            confirmButton = {
                Button(onClick = {
                    confirmDebugBuild = false
                    val selected = projectId ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val tokenValue = store.credentialFor(endpoint)
                                require(tokenValue.isNotBlank()) { "Worker credentials unavailable." }
                                val archive = File.createTempFile("maximus-studio-", ".zip", context.cacheDir)
                                try {
                                    archive.outputStream().use { projects.exportZip(selected, it) }
                                    require(archive.length() in 1..(8L * 1024L * 1024L)) {
                                        "Source ZIP must fit the 8 MB Worker limit."
                                    }
                                    RemoteDebugBuildClient.submit(
                                        endpoint, tokenValue, selected, archive.readBytes()
                                    )
                                } finally { archive.delete() }
                            }
                            debugJob = result
                            status = "Authenticated debug build accepted. Checking job status."
                        } catch (error: Exception) {
                            status = error.message ?: "Remote debug build could not start."
                        } finally { busy = false }
                    }
                }) { Text("Approve and build") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDebugBuild = false }) { Text("Cancel") }
            }
        )
    }
}
