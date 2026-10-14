package ai.drfx.maximus.matrixai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHarnessService
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHostPolicy
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHostSession
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneRuntimeInspector
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneRuntimeStatus
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneBuildSession
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val harnessAqua = Color(0xFF5AE4D6)
private val harnessIndigo = Color(0xFF7469E9)
private val harnessNight = Color(0xFF16243B)

/**
 * A self-hosted phone workspace dashboard, not a fake PRoot shell.
 * User consent remains distinct for starting the server and sharing source.
 */
@Composable
fun PhoneHarnessPanel(projectId: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var running by remember { mutableStateOf(PhoneHostSession.active != null) }
    var shared by remember { mutableStateOf(PhoneHostSession.sharedProject()) }
    var status by remember { mutableStateOf("") }
    var confirmShare by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmBuild by remember { mutableStateOf(false) }
    val phoneBuilder = remember(context) { PhoneBuildSession.forContext(context) }
    val buildState by phoneBuilder.state.collectAsState()
    var diagnostics by remember { mutableStateOf(PhoneRuntimeInspector.inspect(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            running = PhoneHostSession.active != null
            shared = PhoneHostSession.sharedProject()
            if (!running && PhoneHostSession.lastError.isNotBlank()) {
                status = PhoneHostSession.lastError
            }
            delay(1200)
        }
    }

    Column(
        modifier.fillMaxWidth().testTag("phone_harness_panel").animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Compact visual identity: high contrast and battery-friendly static gradient.
        Surface(shape = RoundedCornerShape(22.dp), color = harnessNight) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(harnessNight, Color(0xFF273E60), Color(0xFF244957))))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(13.dp)) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = harnessAqua,
                            modifier = Modifier.padding(11.dp).size(25.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("MOBILE HARNESS", color = Color.White, fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium)
                        Text("YOUR PHONE · YOUR SERVER", color = harnessAqua,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(shape = RoundedCornerShape(100.dp),
                        color = if (running) Color(0xFF174A45) else Color.White.copy(alpha = 0.12f)) {
                        Text(if (running) "● LIVE" else "● OFFLINE", color = if (running) harnessAqua else Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Text("A private development workspace hosted on your Android device.",
                    color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = harnessAqua, modifier = Modifier.size(15.dp))
                    Text("127.0.0.1:${PhoneHostPolicy.PORT}  ·  Same-device only",
                        color = Color.White.copy(alpha = 0.83f), style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !running,
                        onClick = {
                            status = ""
                            try {
                                context.startForegroundService(Intent(context, PhoneHarnessService::class.java).apply {
                                    action = PhoneHarnessService.ACTION_START
                                })
                                status = "Starting private phone server…"
                            } catch (e: Exception) {
                                status = e.message ?: "Could not start the server."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = harnessAqua, contentColor = harnessNight),
                        modifier = Modifier.weight(1f).testTag("phone_harness_start")
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start server")
                    }
                    OutlinedButton(
                        enabled = running, onClick = { confirmStop = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f).testTag("phone_harness_stop")
                    ) {
                        Icon(Icons.Default.Stop, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            HarnessCapabilityTile(
                "WORKSPACES", "On phone", Icons.Default.FolderOpen, true, Modifier.weight(1f))
            HarnessCapabilityTile(
                "SOURCE API", if (running) "Online" else "Standby",
                Icons.Default.Hub, running, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            HarnessCapabilityTile(
                "LINUX / PTY", "Not installed", Icons.Default.Terminal, false, Modifier.weight(1f))
            HarnessCapabilityTile(
                "APK BUILDS", if (buildState.verifiedTools) "Tools verified" else "Needs toolchain",
                Icons.Default.Build, buildState.verifiedTools, Modifier.weight(1f))
        }

        ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Text("Build Debug APK on this phone", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                }
                Text(buildState.message, style = MaterialTheme.typography.bodySmall)
                Text("ARM64 Android 9+ · offline Gradle · verified native PRoot toolchain required. " +
                    "Build scripts execute with this app's privileges; use only trusted projects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { phoneBuilder.verifyTools() },
                        enabled = !buildState.busy, modifier = Modifier.weight(1f)
                            .testTag("phone_build_verify")) { Text("Verify tools") }
                    Button(onClick = { confirmBuild = true },
                        enabled = !buildState.busy && buildState.verifiedTools && projectId != null,
                        modifier = Modifier.weight(1f).testTag("phone_build_start")) {
                        Text("Build APK")
                    }
                }
                if (buildState.busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { phoneBuilder.cancel() }) {
                        Text("Cancel build")
                    }
                }
                if (buildState.logTail.isNotBlank()) {
                    var showLog by remember { mutableStateOf(false) }
                    TextButton(onClick = { showLog = !showLog }) {
                        Text(if (showLog) "Hide build log" else "Show build log")
                    }
                    if (showLog) Text(buildState.logTail.takeLast(6000),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (buildState.phase == "succeeded") {
                    Text("APK SHA-256: " + buildState.apkSha256.orEmpty(),
                        style = MaterialTheme.typography.labelSmall)
                    Text("Verified APK is ready for export from the local build session.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Private access", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text("One-time session key · never saved", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Starting the server does not share any source files. Approve one project separately.",
                    style = MaterialTheme.typography.bodySmall)
                AnimatedVisibility(visible = running) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(onClick = {
                            val secret = PhoneHostSession.token()
                            if (secret.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Temporary local access key", secret))
                                status = "Temporary token copied. Clear the clipboard after pairing."
                            }
                        }, modifier = Modifier.fillMaxWidth().testTag("phone_harness_copy_token")) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy temporary local token")
                        }
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Approved project", fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (shared == null) "None — source remains private"
                                    else "Selected ID: " + shared.orEmpty().take(8) + "…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (shared == null) {
                                Button(
                                    onClick = { confirmShare = true },
                                    enabled = projectId != null,
                                    modifier = Modifier.testTag("phone_harness_approve_share")
                                ) { Text("Approve") }
                            } else {
                                OutlinedButton(onClick = {
                                    PhoneHostSession.setSharedProject(null)
                                    shared = null
                                    status = "Source sharing revoked."
                                }) { Text("Revoke") }
                            }
                        }
                    }
                }
                if (!running) {
                    Text("Start the server to enable local pairing or per-project export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = harnessIndigo)
                    Spacer(Modifier.width(8.dp))
                    Text("Runtime readiness", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { diagnostics = PhoneRuntimeInspector.inspect(context) }) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Scan")
                    }
                }
                Text(diagnostics.headline, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                val storageGiB = (diagnostics.availableStorageBytes / 1073741824.0)
                val readiness = if (diagnostics.nativeRuntimeReady) 1f else if (
                    diagnostics.supportedForOptionalArm64Runtime
                ) 0.25f else 0f
                LinearProgressIndicator(
                    progress = { readiness }, modifier = Modifier.fillMaxWidth().height(5.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = harnessIndigo
                )
                Text(
                    "Android API ${diagnostics.androidApi} · ${diagnostics.architecture} · " +
                        "${diagnostics.memoryClassMiB} MiB app memory class · " +
                        "${String.format(java.util.Locale.US, "%.1f", storageGiB)} GiB free storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (diagnostics.supportedForOptionalArm64Runtime)
                        "ARM64 host eligible. A verified Linux bundle, native launcher and execution tests are still required."
                    else
                        "Optional ARM64 Linux runtime requires compatible hardware and Android 9+. Base Matrix remains usable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (status.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp)) {
                Text(status, Modifier.padding(12.dp).testTag("phone_harness_status"),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Security: local GET-only workspace API · bounded ZIP export · no public network listener, " +
            "shell commands, signing keys or hidden source uploads. On-phone Ubuntu/PRoot and model " +
            "inference are separate optional engines.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmBuild) AlertDialog(
        onDismissRequest = { confirmBuild = false },
        title = { Text("Run project build scripts on this phone?") },
        text = { Text("Gradle scripts can execute code with the app's privileges. " +
            "PRoot is not a security sandbox. Only build source you own or trust. " +
            "The build is offline and may use significant battery, memory and storage.") },
        confirmButton = {
            Button(onClick = {
                confirmBuild = false
                try { phoneBuilder.build(requireNotNull(projectId)) }
                catch (e: Exception) { status = e.message ?: "Could not start local build." }
            }) { Text("Build trusted project") }
        },
        dismissButton = { TextButton(onClick = { confirmBuild = false }) { Text("Cancel") } }
    )
    if (confirmShare) AlertDialog(
        onDismissRequest = { confirmShare = false },
        icon = { Icon(Icons.Default.FolderZip, null) },
        title = { Text("Share selected project locally?") },
        text = { Text("A trusted same-phone client with the temporary key can read this project's manifest " +
            "and download its source ZIP. Remove any confidential files first. Other workspaces remain private.") },
        confirmButton = {
            Button(onClick = {
                confirmShare = false
                try {
                    PhoneHostSession.setSharedProject(requireNotNull(projectId))
                    shared = projectId
                    status = "Only the selected project is approved for export."
                } catch (e: Exception) {
                    status = e.message ?: "Project authorization failed."
                }
            }) { Text("Approve one project") }
        },
        dismissButton = { TextButton(onClick = { confirmShare = false }) { Text("Cancel") } }
    )
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text("Stop private phone server?") },
        text = { Text("This closes the local listener, revokes active source sharing and rotates the " +
            "temporary key on the next start.") },
        confirmButton = {
            Button(onClick = {
                confirmStop = false
                try {
                    context.startService(Intent(context, PhoneHarnessService::class.java).apply {
                        action = PhoneHarnessService.ACTION_STOP
                    })
                    status = "Stopping and revoking the current session…"
                } catch (e: Exception) {
                    status = e.message ?: "Could not stop server."
                }
            }) { Text("Stop and revoke") }
        },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep running") } }
    )
}

@Composable
private fun HarnessCapabilityTile(
    label: String,
    state: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(state, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
