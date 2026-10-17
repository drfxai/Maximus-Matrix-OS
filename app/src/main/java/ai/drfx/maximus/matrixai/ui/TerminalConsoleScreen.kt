package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.agent.MatrixEvent
import ai.drfx.maximus.matrixai.agent.MatrixEventType
import ai.drfx.maximus.matrixai.terminal.TerminalCommandPolicy
import ai.drfx.maximus.matrixai.terminal.TerminalIntent
import ai.drfx.maximus.matrixai.terminal.TerminalHistoryEntry
import ai.drfx.maximus.matrixai.terminal.TerminalHistoryStore
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * A ZimZilla-inspired, original Android console UI. Not a shell emulator.
 * Observed events are read from the existing Matrix runtime. Commands may only
 * delegate to the existing permission-gated mission controller. This feature
 * does not import or execute any upstream third-party Python code or credentials.
 */
private val ConsoleGreen = Color(0xFF5CF0BC)
private val ConsoleCyan = Color(0xFF58C1D7)
private val ConsoleAmber = Color(0xFFE4B34D)
private val ConsoleError = Color(0xFFE96E91)

private enum class ConsoleTab { EVENTS, ROUTING, TEAM, HOST }

@Composable
fun TerminalConsoleScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val events by viewModel.events.collectAsState()
    val state by viewModel.llmState.collectAsState()
    val status by viewModel.status.collectAsState()
    val approval by viewModel.pendingAgentConfirmation.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val historyStore = remember(context) { TerminalHistoryStore(context.applicationContext) }
    val availableProjects = remember(context) { ProjectWorkspaceStore(context.applicationContext).projects() }
    var selectedHostProject by remember { mutableStateOf<String?>(null) }
    var selectHostProject by remember { mutableStateOf(false) }

    var tab by remember { mutableStateOf(ConsoleTab.EVENTS) }
    var command by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<TerminalHistoryEntry>() }
    var sessionReady by remember { mutableStateOf(false) }
    var sessionError by remember { mutableStateOf(false) }

    LaunchedEffect(historyStore) {
        try {
            val restored = withContext(Dispatchers.IO) { historyStore.load() }
            history.clear()
            history.addAll(restored)
            sessionReady = true
        } catch (_: Exception) {
            sessionError = true
        }
    }
    // Writes are coalesced; a failed/undecryptable session is never overwritten.
    val historySnapshot = history.toList()
    LaunchedEffect(sessionReady, historySnapshot) {
        if (sessionReady) {
            delay(250)
            try {
                withContext(Dispatchers.IO) { historyStore.save(historySnapshot) }
                sessionError = false
            } catch (_: Exception) {
                sessionError = true
            }
        }
    }

    val help = stringResource(R.string.term_help_text)
    val unavailable = stringResource(R.string.term_shell_blocked)
    val toolInfo = stringResource(R.string.term_tools_explanation)
    val noMission = stringResource(R.string.term_busy)
    val unknownCommand = stringResource(R.string.term_unknown)
    val submission = stringResource(R.string.term_mission_submitted)
    val limitText = stringResource(R.string.term_input_limit)
    val eventCount = stringResource(R.string.term_observed_count, events.size)
    val teamSelected = stringResource(R.string.term_team_selected)
    val comboSelected = stringResource(R.string.term_combo_selected)
    val unknownModel = stringResource(R.string.api_unknown)
    val savedCount = stringResource(R.string.term_history_count, history.size)
    val historyEmpty = stringResource(R.string.term_history_empty)

    fun addLine(input: String, message: String) {
        val line = TerminalHistoryEntry(
            command = TerminalCommandPolicy.redact(input).take(500),
            output = TerminalCommandPolicy.redact(message).take(1200))
        history.add(line)
        if (history.size > 30) history.removeAt(0)
    }

    fun submit() {
        val typed = command.trim()
        if (typed.isEmpty()) return
        command = ""
        when (val parsed = TerminalCommandPolicy.parse(typed)) {
            TerminalIntent.Help -> addLine(typed, help)
            TerminalIntent.Status -> addLine(typed, status)
            TerminalIntent.Model -> addLine(typed,
                state.provider.displayName + " / " + state.selectedModel.ifBlank { unknownModel })
            TerminalIntent.Tools -> addLine(typed, toolInfo)
            TerminalIntent.Events -> {
                tab = ConsoleTab.EVENTS
                addLine(typed, eventCount)
            }
            TerminalIntent.Clear -> history.clear()
            TerminalIntent.History -> addLine(typed,
                history.takeLast(10).joinToString("\n") { it.command }.ifBlank { historyEmpty })
            TerminalIntent.Team -> {
                tab = ConsoleTab.TEAM
                addLine(typed, teamSelected)
            }
            TerminalIntent.SmartCombo -> {
                tab = ConsoleTab.ROUTING
                addLine(typed, comboSelected)
            }
            TerminalIntent.ShellBlocked -> addLine(typed, unavailable)
            is TerminalIntent.Unsupported -> addLine(typed,
                if (parsed.name == "too_long") limitText else unknownCommand + ": " + parsed.name)
            is TerminalIntent.Mission -> {
                if (status == "EXECUTING" || status == "PLANNING" || status == "CONFIRM") {
                    addLine(typed, noMission)
                } else {
                    tab = ConsoleTab.EVENTS
                    viewModel.runMission(parsed.objective)
                    addLine(typed, submission)
                }
            }
        }
    }

    approval?.let { request ->
        AlertDialog(
            onDismissRequest = { viewModel.approveAgentAction(request.id, false) },
            title = { Text(stringResource(R.string.term_approval_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(request.action.tool, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.term_approval_description))
                    request.action.arguments.forEach { (key, value) ->
                        Text(key + ": " + TerminalCommandPolicy.redact(value).take(220),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.approveAgentAction(request.id, true) }) {
                Text(stringResource(R.string.graph_approve))
            } },
            dismissButton = { TextButton(onClick = { viewModel.approveAgentAction(request.id, false) }) {
                Text(stringResource(R.string.graph_reject))
            } }
        )
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("terminal_console_feed"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ConsoleHeader(status, state.smartComboEnabled) }
            item {
                Surface(shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, null, tint = ConsoleCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(stringResource(R.string.term_route_title),
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(state.provider.displayName + "  •  " +
                            state.selectedModel.ifBlank { stringResource(R.string.api_unknown) },
                            fontWeight = FontWeight.Medium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConsoleMetric(stringResource(R.string.term_session_tokens),
                                state.tokenMetrics.consumedSessionTokens.toString(),
                                Modifier.weight(1f))
                            ConsoleMetric(stringResource(R.string.term_context_remaining),
                                if (state.selectedModel.isBlank()) "—"
                                else state.tokenMetrics.remainingContextTokens.toString(),
                                Modifier.weight(1f))
                        }
                        Text(stringResource(R.string.term_usage_note),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ConsoleTab.EVENTS to stringResource(R.string.term_tab_events),
                        ConsoleTab.ROUTING to stringResource(R.string.term_tab_combo),
                        ConsoleTab.TEAM to stringResource(R.string.term_tab_team),
                        ConsoleTab.HOST to stringResource(R.string.term_tab_host)
                    ).forEach { (key, title) ->
                        FilterChip(
                            selected = tab == key,
                            onClick = { tab = key },
                            label = { Text(title, maxLines = 1) },
                            modifier = Modifier
                        )
                    }
                }
            }
            when (tab) {
                ConsoleTab.EVENTS -> {
                    item {
                        ConsoleSectionHeading(
                            title = stringResource(R.string.term_runtime_title),
                            subtitle = stringResource(R.string.term_runtime_subtitle) + " • " + savedCount,
                            trailing = {
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(
                                        events.joinToString("\n") { entry ->
                                            entry.type.name + " • " +
                                                TerminalCommandPolicy.redact(entry.message)
                                        }))
                                }, enabled = events.isNotEmpty()) {
                                    Icon(Icons.Default.ContentCopy, stringResource(R.string.term_copy_log),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                    items(history.asReversed(), key = { "history-" + it.id }) {
                        ConsoleLocalOutput(it)
                    }
                    if (events.isEmpty()) {
                        item {
                            Text(stringResource(R.string.term_no_events),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        items(events.asReversed().take(80), key = { it.id }) { event ->
                            ConsoleEventRow(event)
                        }
                    }
                }
                ConsoleTab.ROUTING -> {
                    item { SmartComboCommandDashboard(state) }
                    item {
                        Text(stringResource(R.string.term_combo_note),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                ConsoleTab.HOST -> {
                    item {
                        Text(stringResource(R.string.term_host_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Box {
                            OutlinedButton(onClick = { selectHostProject = true },
                                modifier = Modifier.fillMaxWidth()) {
                                Text(availableProjects.firstOrNull { it.id == selectedHostProject }?.name
                                    ?: stringResource(R.string.term_host_select_project))
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = selectHostProject,
                                onDismissRequest = { selectHostProject = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.term_host_no_project)) },
                                    onClick = { selectedHostProject = null; selectHostProject = false }
                                )
                                availableProjects.forEach { project ->
                                    DropdownMenuItem(text = { Text(project.name) },
                                        onClick = { selectedHostProject = project.id; selectHostProject = false })
                                }
                            }
                        }
                    }
                    item { PhoneHarnessPanel(projectId = selectedHostProject) }
                    item { RemoteWorkerPanel(projectId = selectedHostProject) }
                }
                ConsoleTab.TEAM -> {
                    item {
                        Text(stringResource(R.string.term_team_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { AgentTeamPanel() }
                    item { CloudTeamStatusPanel() }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f))) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                if (!sessionReady || sessionError) {
                    Text(stringResource(if (sessionError) R.string.term_history_unavailable
                        else R.string.term_history_loading),
                        color = if (sessionError) ConsoleError else ConsoleAmber,
                        style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Terminal, null, tint = ConsoleGreen, modifier = Modifier.size(22.dp))
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it.take(4_050) },
                        modifier = Modifier.weight(1f).testTag("terminal_command_input"),
                        placeholder = { Text(stringResource(R.string.term_hint), maxLines = 1) },
                        minLines = 1,
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface),
                        shape = RoundedCornerShape(14.dp)
                    )
                    FilledIconButton(onClick = { submit() },
                        enabled = command.isNotBlank() && sessionReady,
                        modifier = Modifier.size(48.dp).testTag("terminal_submit")) {
                        Icon(Icons.Default.ArrowUpward, stringResource(R.string.term_send))
                    }
                }
                Text(stringResource(R.string.term_composer_note),
                    modifier = Modifier.padding(start = 30.dp, top = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ConsoleHeader(status: String, smartCombo: Boolean) {
    Surface(shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ConsoleGreen.copy(alpha = .33f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, null, tint = ConsoleGreen, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("MAXIMUS TERMINAL", fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp, color = ConsoleGreen)
                    Text(stringResource(R.string.term_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(50),
                    color = ConsoleGreen.copy(alpha = .10f)) {
                    Text(stringResource(R.string.term_safe_mode),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        color = ConsoleGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FiberManualRecord, null,
                    tint = when (status) {
                        "READY" -> ConsoleGreen
                        "ATTENTION" -> ConsoleError
                        else -> ConsoleAmber
                    }, modifier = Modifier.size(12.dp))
                Text(status, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(if (smartCombo) stringResource(R.string.term_combo_on)
                    else stringResource(R.string.term_combo_off),
                    color = ConsoleCyan, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ConsoleMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
        shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, color = ConsoleCyan)
        }
    }
}

@Composable
private fun ConsoleSectionHeading(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
        trailing()
    }
}

@Composable
private fun ConsoleLocalOutput(line: TerminalHistoryEntry) {
    Surface(shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("> " + line.command, color = ConsoleGreen,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text(line.output, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ConsoleEventRow(event: MatrixEvent) {
    val tone = when (event.type) {
        MatrixEventType.MISSION_FAILED, MatrixEventType.MODEL_FAILED,
        MatrixEventType.VALIDATION_FAILED -> ConsoleError
        MatrixEventType.CONFIRMATION_REQUIRED -> ConsoleAmber
        MatrixEventType.MISSION_COMPLETED, MatrixEventType.TOOL_COMPLETED,
        MatrixEventType.VALIDATION_PASSED -> ConsoleGreen
        else -> ConsoleCyan
    }
    val stamp = remember(event.timestampMs) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestampMs))
    }
    Surface(shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Circle, null, modifier = Modifier.size(8.dp), tint = tone)
                Text(stamp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(event.type.name.replace('_', ' '), color = tone, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, maxLines = 1)
            }
            Text(TerminalCommandPolicy.redact(event.message).take(1_200),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Text(event.sourceNode,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
