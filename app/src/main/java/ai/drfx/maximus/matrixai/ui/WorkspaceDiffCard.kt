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
import ai.drfx.maximus.matrixai.studio.LineDiff
import androidx.compose.ui.text.SpanStyle

/** Local line diff (Myers) for review, never claimed to be a verified Git patch. */
@Composable
internal fun WorkspaceDiffCard(before: String, after: String) {
    val diff = remember(before, after) { LineDiff.compute(before, after) }
    val plainText = remember(diff) { diff.render() }
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
    val added = MaterialTheme.colorScheme.primary
    val removed = MaterialTheme.colorScheme.error
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text("+${diff.added}", color = added, fontSize = 11.sp)
                Spacer(Modifier.width(5.dp))
                Text("−${diff.removed}", color = removed, fontSize = 11.sp)
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
            if (diff.rows.isEmpty()) Text("No changes.", Modifier.padding(10.dp), color = muted, fontSize = 11.sp)
            else LargeCodeView(plainText, maxHeight = 320.dp, showLineNumbers = false, highlight = { row ->
                val color = when (row.firstOrNull()) { '+' -> added; '-' -> removed; '@' -> muted.copy(alpha = .7f); else -> muted }
                AnnotatedString(row, listOf(AnnotatedString.Range(SpanStyle(color = color), 0, row.length)))
            })
            if (diff.truncated) Text("Diff view truncated; the full change is saved exactly as edited.",
                Modifier.padding(8.dp), color = muted, fontSize = 10.sp)
        }
    }
}
