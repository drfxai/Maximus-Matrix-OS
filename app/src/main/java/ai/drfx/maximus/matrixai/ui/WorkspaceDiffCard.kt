package ai.drfx.maximus.matrixai.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.R

/** Position-based local preview, never claimed to be a verified Git patch. */
@Composable
internal fun WorkspaceDiffCard(before: String, after: String) {
    val rows = remember(before, after) {
        val old = before.lines()
        val new = after.lines()
        buildList {
            repeat(maxOf(old.size, new.size).coerceAtMost(100)) { index ->
                if (old.getOrNull(index) == new.getOrNull(index)) add("  " + new[index])
                else {
                    old.getOrNull(index)?.let { add("- $it") }
                    new.getOrNull(index)?.let { add("+ $it") }
                }
            }
        }
    }
    val plainText = remember(rows) { rows.joinToString("\n") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember(plainText) { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(exportText.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open destination")
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.conv_export_failed), Toast.LENGTH_LONG).show()
            }
        }
        exportText = ""
    }
    LaunchedEffect(copied) {
        if (copied) { kotlinx.coroutines.delay(1400); copied = false }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.design_proposed_edit),
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp)
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(plainText))
                    copied = true
                }) { Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    stringResource(R.string.chat_copy), modifier = Modifier.size(20.dp)) }
                IconButton(onClick = {
                    exportText = plainText
                    export.launch("maximus-local-change.diff")
                }) { Icon(Icons.Default.FileDownload, stringResource(R.string.chat_download),
                    modifier = Modifier.size(20.dp)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().heightIn(max = 245.dp)
                .horizontalScroll(rememberScrollState()).padding(vertical = 8.dp, horizontal = 10.dp)) {
                rows.forEachIndexed { index, row ->
                    val color = when (row.firstOrNull()) {
                        '+' -> MaterialTheme.colorScheme.primary
                        '-' -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(Modifier.fillMaxWidth().background(
                        color.copy(alpha = if (row.startsWith("  ")) 0f else .13f))
                        .padding(vertical = 2.dp)) {
                        Text((index + 1).toString().padStart(3, ' '),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(row, color = color, fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp, lineHeight = 17.sp, softWrap = false)
                    }
                }
            }
        }
    }
}
