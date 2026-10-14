package ai.drfx.maximus.matrixai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHarnessService
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHostPolicy
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHostSession
import kotlinx.coroutines.delay

/** On-device Mobile Harness gateway; no local Linux runtime is implied. */
@Composable
fun PhoneHarnessPanel(projectId: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var running by remember { mutableStateOf(PhoneHostSession.active != null) }
    var shared by remember { mutableStateOf(PhoneHostSession.sharedProject()) }
    var message by remember { mutableStateOf("") }
    var confirmShare by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            running = PhoneHostSession.active != null
            shared = PhoneHostSession.sharedProject()
            if (!running && PhoneHostSession.lastError.isNotEmpty()) {
                message = PhoneHostSession.lastError
            }
            delay(750)
        }
    }

    ElevatedCard(modifier.fillMaxWidth().testTag("phone_harness_panel")) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Mobile Harness · Phone Server", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your phone hosts a real, authenticated local development API. " +
                    "Only apps on this same phone can reach it. " +
                    "Linux/terminal execution, APK compilation and access from other devices " +
                    "are not yet enabled.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (running) "Running · http://127.0.0.1:${PhoneHostPolicy.PORT}"
                else "Stopped · nothing is exposed",
                style = MaterialTheme.typography.labelMedium,
                color = if (running) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !running,
                    onClick = {
                        try {
                            context.startForegroundService(
                                Intent(context, PhoneHarnessService::class.java).apply {
                                    action = PhoneHarnessService.ACTION_START
                                }
                            )
                            message = "Starting local phone server…"
                        } catch (error: Exception) {
                            message = error.message ?: "Could not start phone server."
                        }
                    },
                    modifier = Modifier.testTag("phone_harness_start")
                ) { Text("Start phone server") }
                OutlinedButton(
                    enabled = running,
                    onClick = {
                        context.startService(
                            Intent(context, PhoneHarnessService::class.java).apply {
                                action = PhoneHarnessService.ACTION_STOP
                            }
                        )
                        message = "Stopping phone server…"
                    },
                    modifier = Modifier.testTag("phone_harness_stop")
                ) { Text("Stop") }
            }
            if (running) {
                Text(
                    "Access requires a new random bearer token each time the server starts. " +
                        "The token is kept in memory and is not an AI API key.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = {
                    val value = PhoneHostSession.token()
                    if (value.isNotEmpty()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Maximus phone host access", value)
                        )
                        message = "One-time token copied. Share only with a trusted local client."
                    }
                }) { Text("Copy temporary access token") }

                if (shared != null) {
                    Text(
                        "Source sharing enabled for: " + shared!!.take(12) + "…",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = {
                        PhoneHostSession.setSharedProject(null)
                        shared = null
                        message = "Project source sharing disabled."
                    }) { Text("Revoke source sharing") }
                } else {
                    Button(
                        enabled = projectId != null,
                        onClick = { confirmShare = true },
                        modifier = Modifier.testTag("phone_harness_approve_share")
                    ) { Text("Allow selected project export") }
                }
            }
            if (message.isNotEmpty()) {
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Read-only routes: /v1/capabilities, /v1/projects and approved " +
                    "/v1/projects/{id}/manifest or /export. No shell or upload routes. " +
                    "External Wi-Fi/tunnel access requires a separate authenticated TLS gateway.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmShare) AlertDialog(
        onDismissRequest = { confirmShare = false },
        title = { Text("Allow local client to read this project's source?") },
        text = {
            Text("Any same-phone client holding the temporary access token can download " +
                "the selected project's ZIP while sharing is enabled. " +
                "Remove credentials and confidential files first. " +
                "This permission is not granted to other projects.")
        },
        confirmButton = {
            Button(onClick = {
                confirmShare = false
                try {
                    PhoneHostSession.setSharedProject(requireNotNull(projectId))
                    shared = projectId
                    message = "Source export approved for this project only."
                } catch (error: Exception) {
                    message = error.message ?: "Could not approve sharing."
                }
            }) { Text("Approve export") }
        },
        dismissButton = {
            TextButton(onClick = { confirmShare = false }) { Text("Cancel") }
        }
    )
}
