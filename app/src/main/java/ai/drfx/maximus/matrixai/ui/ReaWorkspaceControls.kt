package ai.drfx.maximus.matrixai.ui

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.rea.ReaHostCommands
import ai.drfx.maximus.matrixai.rea.ReaMode
import ai.drfx.maximus.matrixai.rea.ReaModeStore
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneLinuxService
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneLinuxSessions
import java.util.UUID

/**
 * Same app-scoped control on Chat, Pine and Work. OFF/ON/ALWAYS_ON are global.
 * ON persists for this process, ALWAYS_ON across process restarts. Neither starts
 * a background REA service or silently runs npm or reverse engineering.
 */
@Composable
internal fun ReaWorkspaceControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { ReaModeStore.get(context) }
    val mode by store.mode.collectAsState()
    val linux = remember(context.applicationContext) { PhoneLinuxSessions.get(context) }
    val linuxState by linux.state.collectAsState()
    var requested by remember { mutableStateOf<String?>(null) }
    var lastSessionId by remember { mutableStateOf<String?>(null) }
    var startFailure by remember { mutableStateOf(false) }
    var analyzeOpen by remember { mutableStateOf(false) }
    var analyzePath by remember { mutableStateOf("") }
    var analyzeError by remember { mutableStateOf<String?>(null) }

    Column(modifier.testTag("rea_workspace_controls"), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("REA", fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
            for (choice in ReaMode.entries) {
                FilterChip(
                    selected = mode == choice,
                    onClick = { store.select(choice) },
                    modifier = Modifier.height(32.dp).testTag("rea_mode_" + choice.name.lowercase()),
                    label = {
                        Text(when (choice) {
                            ReaMode.OFF -> "Off"
                            ReaMode.ON -> "On"
                            ReaMode.ALWAYS_ON -> "Always on"
                        }, fontSize = 10.sp, maxLines = 1)
                    }
                )
            }
            TextButton(onClick = { requested = ReaHostCommands.CHECK },
                modifier = Modifier.height(32.dp).testTag("rea_check_engine")) {
                Text("Check engine", fontSize = 10.sp)
            }
            TextButton(onClick = { requested = ReaHostCommands.INSTALL },
                modifier = Modifier.height(32.dp).testTag("rea_install_engine")) {
                Text("Install", fontSize = 10.sp)
            }
            TextButton(onClick = { analyzeOpen = true },
                modifier = Modifier.height(32.dp).testTag("rea_analyze_javascript")) {
                Text("Analyze JS", fontSize = 10.sp)
            }
        }
        if (mode != ReaMode.OFF) {
            Text(if (mode == ReaMode.ALWAYS_ON)
                "REA analysis policy · persistent across restarts and provider changes · CLI execution requires a verified Linux host."
            else "REA analysis policy · this session, including provider changes · CLI execution requires a verified Linux host.",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (lastSessionId != null && linuxState.id == lastSessionId) {
            Text("REA Linux operation: " + linuxState.phase +
                (linuxState.exitCode?.let { " · exit $it" } ?: "") +
                ". Inspect the Linux console for actual output.",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("rea_host_operation_status"))
        }
        if (startFailure) {
            Text("Could not start the approved Linux operation; check the phone Linux plugin.",
                color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
        }
    }

    if (analyzeOpen) {
        AlertDialog(
            onDismissRequest = { analyzeOpen = false },
            title = { Text("Analyze JavaScript with REA") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Enter a user-authorized JavaScript/Electron directory or archive in Phone Linux /root/workspace. REA CLI must already be installed and executable; this is not a Pine compiler.")
                OutlinedTextField(value = analyzePath, onValueChange = {
                    analyzePath = it.take(250)
                    analyzeError = null
                }, label = { Text("Linux target path") }, singleLine = true,
                    placeholder = { Text("/root/workspace/my-app") })
                analyzeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = {
                runCatching { ReaHostCommands.analyzeJavascript(analyzePath) }
                    .onSuccess { requested = it; analyzeOpen = false; analyzeError = null }
                    .onFailure { analyzeError = it.message ?: "Invalid target path." }
            }) { Text("Review command") } },
            dismissButton = { TextButton(onClick = { analyzeOpen = false }) { Text("Cancel") } }
        )
    }

    requested?.let { command ->
        val installing = command == ReaHostCommands.INSTALL
        val checking = command == ReaHostCommands.CHECK
        AlertDialog(
            onDismissRequest = { requested = null },
            title = { Text(when {
                installing -> "Install REA on Phone Linux?"
                checking -> "Check REA on Phone Linux?"
                else -> "Run REA analysis on Phone Linux?"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(when {
                        installing -> "This downloads and installs the official rea-agents npm package inside your existing PRoot Linux runtime. It needs compatible Node.js/npm, network access and the Phone Linux plugin. Review before executing. No analysis target or model credentials are passed."
                        checking -> "This checks Node.js, npm and the REA CLI inside the existing PRoot Linux runtime. It does not install software or submit a model request."
                        else -> "REA will read the specific local JavaScript target shown below. Run only on content you own or are authorized to analyze. Output is retained in the Phone Linux session console."
                    })
                    Text(command, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("An ON/Always on setting does not prove the CLI/MCP server is running. Native analysis tools need separate setup and permissions.",
                        fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = {
                requested = null
                startFailure = false
                val sessionId = UUID.randomUUID().toString()
                runCatching {
                    context.startForegroundService(Intent(context, PhoneLinuxService::class.java).apply {
                        action = PhoneLinuxService.RUN
                        putExtra(PhoneLinuxService.ID, sessionId)
                        putExtra(PhoneLinuxService.COMMAND, command)
                    })
                }.onSuccess { lastSessionId = sessionId }
                    .onFailure { startFailure = true }
            }) { Text(when {
                installing -> "Approve installation"
                checking -> "Run check"
                else -> "Approve analysis"
            }) } },
            dismissButton = { TextButton(onClick = { requested = null }) { Text("Cancel") } }
        )
    }
}
