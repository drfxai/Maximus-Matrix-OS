package ai.drfx.maximus.matrixai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.logging.AppLogEntry
import ai.drfx.maximus.matrixai.logging.AppLogStore
import ai.drfx.maximus.matrixai.llm.ConnectionStatus
import ai.drfx.maximus.matrixai.ui.theme.AppThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    viewModel: MatrixViewModel,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries by AppLogStore.entries.collectAsState()
    val llm by viewModel.llmState.collectAsState()
    val runtime by viewModel.status.collectAsState()
    val events by viewModel.events.collectAsState()
    val context = LocalContext.current
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var audit by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val errors = entries.count { it.level == "ERROR" }
    val ai = entries.count { it.source in setOf("API", "CHAT", "MATRIX") }
    val data = entries.count { it.source == "DATA" }
    val filtered = entries.filter { entry ->
        (when (filter) {
            "Errors" -> entry.level == "ERROR"
            "AI & Core" -> entry.source in setOf("API", "CHAT", "MATRIX")
            "Data" -> entry.source == "DATA"
            else -> true
        }) && (query.isBlank() || (entry.level + " " + entry.source + " " + entry.message)
            .contains(query.trim(), ignoreCase = true))
    }.takeLast(120).reversed()

    fun shareReport(title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MAXIMUS AI diagnostics")
            putExtra(Intent.EXTRA_TEXT, AppLogStore.exportText())
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("Clear diagnostic logs?") },
        text = { Text("This removes the in-memory entries for the current session.") },
        confirmButton = { TextButton(onClick = {
            AppLogStore.clear(); confirmClear = false; copied = false
        }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })

    androidx.compose.foundation.lazy.LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Diagnostics & Telemetry", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold)
                    Text("Runtime logs & error reporting",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                IconButton(onClick = { onThemeModeChange(if (themeMode == AppThemeMode.DARK)
                    AppThemeMode.LIGHT else AppThemeMode.DARK) }) {
                    Icon(if (themeMode == AppThemeMode.DARK) Icons.Default.DarkMode else Icons.Default.LightMode,
                        "Toggle light and dark theme")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { shareReport("Report an issue") },
                    Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Default.BugReport, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp)); Text("Report Error", fontSize = 11.sp)
                }
                Button(onClick = { shareReport("Export diagnostics") },
                    Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Default.Share, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp)); Text("Export Report", fontSize = 11.sp)
                }
            }
        }
        item {
            Text("ENGINE TELEMETRY", color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        TelemetryCell("Matrix Runtime", runtime, Modifier.weight(1f))
                        TelemetryCell("AI Provider", llm.provider.name.replace('_', ' '), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        TelemetryCell("Connection", llm.status.name, Modifier.weight(1f))
                        TelemetryCell("Model", llm.selectedModel.ifBlank { "None" }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        TelemetryCell("Matrix Events", events.size.toString(), Modifier.weight(1f))
                        TelemetryCell("Privacy", "REDACTED", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Subsystem Health", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(audit ?: if (llm.status == ConnectionStatus.CONNECTED)
                            "Provider connected · runtime " + runtime else "Provider offline · runtime " + runtime,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        audit = "Checked: " + entries.size + " entries · " + errors +
                            " errors · provider " + llm.status.name
                        AppLogStore.info("AUDIT", "Local audit completed")
                    }) { Text("Run Audit", fontSize = 11.sp) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("All" to entries.size, "Errors" to errors, "AI & Core" to ai, "Data" to data).forEach { (name, count) ->
                    FilterChip(selected = filter == name, onClick = { filter = name },
                        label = { Text(name + " (" + count + ")", fontSize = 11.sp) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.weight(1f),
                    singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search logs & traces", fontSize = 12.sp) })
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MAXIMUS AI Diagnostic Log", AppLogStore.exportText()))
                    copied = true
                }) { Icon(Icons.Default.ContentCopy, "Copy redacted logs") }
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Default.Delete, "Clear logs", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (copied) Text("Redacted report copied.", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        }
        item {
            Surface(Modifier.fillMaxWidth().heightIn(min = 220.dp), shape = RoundedCornerShape(17.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                SelectionContainer {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (filtered.isEmpty()) {
                            Text("No matching entries. Runtime activity will appear here.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                        filtered.forEach { entry ->
                            LogLine(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
private fun LogLine(entry: AppLogEntry) {
    val timestamp = remember(entry.timestampMs) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestampMs))
    }
    Text("[" + timestamp + "] [" + entry.source + "] " + entry.level + "  " + entry.message,
        color = when (entry.level) {
            "ERROR" -> MaterialTheme.colorScheme.error
            "WARN" -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurface
        }, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp)
}
