package ai.drfx.maximus.matrixai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.logging.AppLogStore
import ai.drfx.maximus.matrixai.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries by AppLogStore.entries.collectAsState()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Diagnostics", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Theme, runtime diagnostics and a copyable redacted log for bug reports.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance", fontWeight = FontWeight.SemiBold)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AppThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = AppThemeMode.entries.size)
                            ) {
                                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Copyable App Log", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Credentials are redacted before entries are stored or copied.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                        Row {
                            IconButton(onClick = {
                                val text = AppLogStore.exportText()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("MAXIMUS AI Diagnostic Log", text))
                                copied = true
                            }) {
                                Icon(Icons.Default.ContentCopy, "Copy log")
                            }
                            IconButton(onClick = {
                                AppLogStore.clear()
                                copied = false
                            }) {
                                Icon(Icons.Default.Delete, "Clear log")
                            }
                        }
                    }
                    if (copied) {
                        Text("Diagnostic log copied.", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "No diagnostic entries yet. API discovery, chat requests, model errors, data-center operations and Matrix events will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        } else {
            items(entries.reversed().take(120)) { entry ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f))
                ) {
                    SelectionContainer {
                        Column(Modifier.padding(11.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    entry.level,
                                    color = when (entry.level) {
                                        "ERROR" -> MaterialTheme.colorScheme.error
                                        "WARN" -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(entry.source, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(entry.message, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
