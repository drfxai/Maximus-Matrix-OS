package ai.drfx.maximus.matrixai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneBuildSession
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneRuntimeInstallSession
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneRuntimeSetupService
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneLocalBuildService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
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
import ai.drfx.maximus.matrixai.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHarnessService
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHostPolicy
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHostSession
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneRuntimeInspector
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneRuntimeStatus
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
    var previousProject by remember { mutableStateOf(projectId) }
    LaunchedEffect(projectId) {
        if (previousProject != projectId) {
            PhoneHostSession.active?.let { server ->
                val revision = server.invalidateAuthorization()
                withContext(Dispatchers.IO) { server.approveProject(null, revision) }
            }
            shared = null
            previousProject = projectId
        }
    }
    var status by remember { mutableStateOf("") }
    var confirmShare by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmBuild by remember { mutableStateOf(false) }
    var exportHash by remember { mutableStateOf<String?>(null) }
    var showBuildLog by remember { mutableStateOf(false) }
    val installer = remember(context) { PhoneRuntimeInstallSession.forContext(context) }
    val builder = remember(context) { PhoneBuildSession.forContext(context) }
    val installState by installer.state.collectAsState()
    val buildState by builder.state.collectAsState()
    val uiScope = rememberCoroutineScope()
    val saveApk = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? ->
        if (uri != null && !buildState.busy && buildState.apkSha256 != null) {
            val selectedHash = exportHash
            uiScope.launch {
                try {
                    require(selectedHash != null) { context.getString(R.string.phone_host_export_failed) }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            builder.exportVerifiedApk(output, selectedHash)
                        } ?: error(context.getString(R.string.phone_host_export_unwritable))
                    }
                    status = context.getString(R.string.phone_host_export_success)
                } catch (e: Exception) {
                    status = e.message ?: context.getString(R.string.phone_host_export_failed)
                }
            }
        }
    }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    var diagnostics by remember { mutableStateOf(PhoneRuntimeInspector.inspect(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = System.currentTimeMillis()
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
                        Text(stringResource(R.string.phone_harness_title), color = Color.White, fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.phone_harness_subtitle), color = harnessAqua,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(shape = RoundedCornerShape(100.dp),
                        color = if (running) Color(0xFF174A45) else Color.White.copy(alpha = 0.12f)) {
                        Text(if (running) "● " + stringResource(R.string.phone_host_live)
                            else "● " + stringResource(R.string.phone_host_disconnected),
                            color = if (running) harnessAqua else Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Text(stringResource(R.string.phone_workspace_desc),
                    color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = harnessAqua, modifier = Modifier.size(15.dp))
                    Text(stringResource(R.string.phone_host_same_device, PhoneHostPolicy.PORT),
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
                                status = context.getString(R.string.phone_host_server_starting)
                            } catch (e: Exception) {
                                status = e.message ?: context.getString(R.string.phone_host_server_start_failed)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = harnessAqua, contentColor = harnessNight),
                        modifier = Modifier.weight(1f).testTag("phone_harness_start")
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.phone_start_server))
                    }
                    OutlinedButton(
                        enabled = running, onClick = { confirmStop = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f).testTag("phone_harness_stop")
                    ) {
                        Icon(Icons.Default.Stop, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.phone_stop_server))
                    }
                }
            }
        }

        PluginStorePanel()
        PhoneLinuxPanel()
        PhoneBuildRequestsPanel()
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            HarnessCapabilityTile(
                stringResource(R.string.phone_workspaces), stringResource(R.string.phone_host_on_phone),
                Icons.Default.FolderOpen, true, Modifier.weight(1f))
            HarnessCapabilityTile(
                stringResource(R.string.phone_source_api),
                if (running) stringResource(R.string.phone_host_online)
                else stringResource(R.string.phone_host_standby),
                Icons.Default.Hub, running, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            HarnessCapabilityTile(
                stringResource(R.string.phone_console_linux),
                stringResource(if (buildState.verifiedTools) R.string.phone_console_probed else R.string.phone_console_unverified),
                Icons.Default.Terminal, buildState.verifiedTools, Modifier.weight(1f))
            HarnessCapabilityTile(
                stringResource(R.string.phone_apk_builds), if (buildState.verifiedTools) stringResource(R.string.phone_available) else stringResource(R.string.phone_needs_setup),
                Icons.Default.Build, buildState.verifiedTools, Modifier.weight(1f))
        }

        ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.phone_install_title),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.phone_install_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val ready = installer.installed()
                Text(if (ready) stringResource(R.string.phone_installed)
                    else stringResource(R.string.phone_install_required),
                    style = MaterialTheme.typography.bodySmall)
                if (installState.phase != "idle") {
                    Text(installState.message, style = MaterialTheme.typography.bodySmall,
                        color = if (installState.phase == "failed" || installState.phase == "interrupted")
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!ready && !installState.busy && installer.storedDownloadBytes() > 0L) {
                    Text(
                        stringResource(R.string.phone_saved_resuming,
                            installer.storedDownloadBytes() / (1024L * 1024L)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (installState.busy) {
                    LinearProgressIndicator(
                        progress = { installState.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                    val total = installState.total
                    if (total > 0) {
                        val transferred = installState.transferred / (1024L * 1024L)
                        Text(stringResource(R.string.phone_host_transfer_mib, transferred,
                                total / (1024L * 1024L)),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = {
                        context.startService(Intent(context, PhoneRuntimeSetupService::class.java).apply {
                            action = PhoneRuntimeSetupService.CANCEL
                        })
                    }) { Text(stringResource(R.string.phone_cancel_install)) }
                }
                if (!ready && !installState.busy) {
                    Button(
                        onClick = {
                            try {
                                context.startForegroundService(
                                    Intent(context, PhoneRuntimeSetupService::class.java).apply {
                                        action = PhoneRuntimeSetupService.START
                                    }
                                )
                                status = context.getString(R.string.phone_host_download_starting)
                            } catch (e: Exception) {
                                status = e.message ?: context.getString(R.string.phone_host_download_start_failed)
                            }
                        }, modifier = Modifier.fillMaxWidth().testTag("phone_build_install")
                    ) {
                        Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (installState.phase in setOf("cancelled", "interrupted", "failed") ||
                            installer.storedDownloadBytes() > 0L)
                            stringResource(R.string.phone_resume)
                        else stringResource(R.string.phone_install))
                    }
                    Text(stringResource(R.string.phone_download_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (ready) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { builder.verifyTools() },
                            enabled = !buildState.busy && !installState.busy,
                            modifier = Modifier.weight(1f).testTag("phone_build_verify")
                        ) { Text(stringResource(R.string.phone_verify)) }
                        Button(
                            onClick = { confirmBuild = true },
                            enabled = projectId != null && buildState.verifiedTools &&
                                !buildState.busy && !installState.busy,
                            modifier = Modifier.weight(1f).testTag("phone_build_start")
                        ) { Text(stringResource(R.string.phone_build)) }
                    }
                }
                Surface(
                    color = harnessNight, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("phone_build_console")
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, null, tint = harnessAqua, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.phone_console_title), color = Color.White,
                                style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (buildState.busy && buildState.startedAtMs > 0L) {
                                Text(stringResource(R.string.phone_console_elapsed,
                                    ((clock - buildState.startedAtMs).coerceAtLeast(0L) / 1000L)),
                                    color = harnessAqua, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val phaseLabel = when (buildState.phase) {
                            "restoring" -> R.string.phone_console_restoring
                            "verifying" -> R.string.phone_console_verifying
                            "building" -> R.string.phone_console_building
                            "succeeded" -> R.string.phone_console_artifact
                            "ready" -> R.string.phone_console_probed
                            "failed", "unavailable" -> R.string.phone_console_failed
                            "cancelled" -> R.string.phone_console_cancelled
                            else -> R.string.phone_console_unverified
                        }
                        Text(stringResource(phaseLabel), color = harnessAqua,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (buildState.projectId != null) {
                            Text(stringResource(R.string.phone_console_project, buildState.projectId!!.take(8)),
                                color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                        }
                        if (buildState.apkBytes > 0L) {
                            Text(stringResource(R.string.phone_console_artifact_bytes, buildState.apkBytes),
                                color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(buildState.message, style = MaterialTheme.typography.bodySmall,
                    color = if (buildState.phase == "failed" || buildState.phase == "unavailable")
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                if (buildState.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = {
                        context.startService(Intent(context, PhoneLocalBuildService::class.java).apply {
                            action = PhoneLocalBuildService.CANCEL
                        })
                    }) { Text(stringResource(R.string.phone_cancel_build)) }
                }
                if (buildState.logTail.isNotBlank()) {
                    TextButton(onClick = { showBuildLog = !showBuildLog }) {
                        Text(if (showBuildLog) stringResource(R.string.phone_hide_log) else stringResource(R.string.phone_build_log))
                    }
                    if (showBuildLog) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(10.dp)) {
                            SelectionContainer {
                                Text(buildState.logTail.takeLast(8000),
                                    Modifier.padding(10.dp).testTag("phone_build_log"),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (!buildState.busy && buildState.apkSha256 != null) {
                    Text(stringResource(R.string.phone_host_sha_label, buildState.apkSha256.orEmpty()),
                        style = MaterialTheme.typography.labelSmall)
                    Button(
                        onClick = {
                            exportHash = buildState.apkSha256
                            saveApk.launch("Maximus-Project-debug.apk")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("phone_build_export")
                    ) {
                        Icon(Icons.Default.SaveAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.phone_save_apk))
                    }
                }
                Text(stringResource(R.string.phone_review_security),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.phone_private), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.phone_one_time), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.phone_no_share),
                    style = MaterialTheme.typography.bodySmall)
                AnimatedVisibility(visible = running) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(onClick = {
                            val secret = PhoneHostSession.token()
                            if (secret.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(
                                    context.getString(R.string.phone_host_clipboard_label), secret))
                                status = context.getString(R.string.phone_host_token_copied)
                            }
                        }, modifier = Modifier.fillMaxWidth().testTag("phone_harness_copy_token")) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.phone_copy_token))
                        }
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.phone_shared_project), fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (shared == null) stringResource(R.string.phone_host_no_shared_source)
                                    else stringResource(R.string.phone_host_shared_id, shared.orEmpty().take(8)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (shared == null) {
                                Button(
                                    onClick = { confirmShare = true },
                                    enabled = projectId != null,
                                    modifier = Modifier.testTag("phone_harness_approve_share")
                                ) { Text(stringResource(R.string.phone_approve)) }
                            } else {
                                OutlinedButton(onClick = {
                                    val server = PhoneHostSession.active
                                    val revision = server?.invalidateAuthorization()
                                    uiScope.launch {
                                        withContext(Dispatchers.IO) { if (server != null && revision != null) server.approveProject(null, revision) }
                                        shared = null
                                        status = context.getString(R.string.phone_host_share_revoked)
                                    }
                                }) { Text(stringResource(R.string.phone_revoke)) }
                            }
                        }
                    }
                }
                if (!running) {
                    Text(stringResource(R.string.phone_offline_desc),
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
                    Text(stringResource(R.string.phone_readiness), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { diagnostics = PhoneRuntimeInspector.inspect(context) }) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.phone_scan))
                    }
                }
                val readinessLabel = when {
                    !diagnostics.supportedForOptionalArm64Runtime ->
                        R.string.phone_host_linux_unavailable
                    !diagnostics.installedProotExecutable ->
                        R.string.phone_host_linux_not_installed
                    !diagnostics.verifiedRootfsPresent ->
                        R.string.phone_host_rootfs_unverified
                    buildState.verifiedTools -> R.string.phone_console_probed
                    else -> R.string.phone_host_execution_unverified
                }
                Text(stringResource(readinessLabel), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.phone_console_boundary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val storageGiB = (diagnostics.availableStorageBytes / 1073741824.0)
                val readiness = if (buildState.verifiedTools) 1f else if (
                    diagnostics.supportedForOptionalArm64Runtime
                ) 0.25f else 0f
                LinearProgressIndicator(
                    progress = { readiness }, modifier = Modifier.fillMaxWidth().height(5.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = harnessIndigo
                )
                Text(
                    stringResource(R.string.phone_host_device_stats,
                        diagnostics.androidApi, diagnostics.architecture,
                        diagnostics.memoryClassMiB,
                        String.format(java.util.Locale.US, "%.1f", storageGiB)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (diagnostics.supportedForOptionalArm64Runtime)
                        stringResource(R.string.phone_host_arm64_ready_hint)
                    else
                        stringResource(R.string.phone_host_arm64_unsupported),
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
        Text(stringResource(R.string.phone_host_security_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmBuild) AlertDialog(
        onDismissRequest = { confirmBuild = false },
        title = { Text(stringResource(R.string.phone_build_confirm)) },
        text = { Text(stringResource(R.string.phone_host_build_warning)) },
        confirmButton = {
            Button(onClick = {
                confirmBuild = false
                try {
                    context.startForegroundService(
                        Intent(context, PhoneLocalBuildService::class.java).apply {
                            action = PhoneLocalBuildService.BUILD
                            putExtra(PhoneLocalBuildService.PROJECT_ID, requireNotNull(projectId))
                        }
                    )
                    status = context.getString(R.string.phone_host_compiling)
                } catch (e: Exception) {
                    status = e.message ?: context.getString(R.string.phone_host_build_start_failed)
                }
            }) { Text(stringResource(R.string.phone_approve_build)) }
        },
        dismissButton = { TextButton(onClick = { confirmBuild = false }) {
            Text(stringResource(R.string.phone_host_cancel))
        } }
    )
    if (confirmShare) AlertDialog(
        onDismissRequest = { confirmShare = false },
        icon = { Icon(Icons.Default.FolderZip, null) },
        title = { Text(stringResource(R.string.phone_share_confirm)) },
        text = { Text(stringResource(R.string.phone_host_share_warning)) },
        confirmButton = {
            Button(onClick = {
                confirmShare = false
                try {
                    val server = requireNotNull(PhoneHostSession.active)
                    val revision = server.invalidateAuthorization()
                    uiScope.launch {
                        runCatching { withContext(Dispatchers.IO) { check(server.approveProject(requireNotNull(projectId), revision)) } }
                            .onSuccess {
                                shared = projectId
                                status = context.getString(R.string.phone_host_share_approved)
                            }.onFailure { status = context.getString(R.string.phone_host_share_failed) }
                    }
                } catch (e: Exception) {
                    status = e.message ?: context.getString(R.string.phone_host_share_failed)
                }
            }) { Text(stringResource(R.string.phone_share_approve)) }
        },
        dismissButton = { TextButton(onClick = { confirmShare = false }) {
            Text(stringResource(R.string.phone_host_cancel))
        } }
    )
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text(stringResource(R.string.phone_stop_confirm)) },
        text = { Text(stringResource(R.string.phone_host_stop_warning)) },
        confirmButton = {
            Button(onClick = {
                confirmStop = false
                try {
                    context.startService(Intent(context, PhoneHarnessService::class.java).apply {
                        action = PhoneHarnessService.ACTION_STOP
                    })
                    status = context.getString(R.string.phone_host_stopping)
                } catch (e: Exception) {
                    status = e.message ?: context.getString(R.string.phone_host_stop_failed)
                }
            }) { Text(stringResource(R.string.phone_stop_revoke)) }
        },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.phone_keep_running)) } }
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
