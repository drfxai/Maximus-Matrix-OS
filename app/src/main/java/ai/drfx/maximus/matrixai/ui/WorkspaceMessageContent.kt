package ai.drfx.maximus.matrixai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.R

/** Fences are parsed without interpreting HTML, tools, or executing model output. */
internal data class MessagePart(val text: String, val language: String? = null)
internal fun messageParts(text: String): List<MessagePart> {
    val parts = mutableListOf<MessagePart>()
    val prose = StringBuilder()
    val code = StringBuilder()
    var language: String? = null
    text.lineSequence().forEach { line ->
        if (language == null && line.trimStart().startsWith("```")) {
            if (prose.isNotEmpty()) { parts += MessagePart(prose.toString().trimEnd()); prose.clear() }
            language = line.trim().removePrefix("```").take(40)
        } else if (language != null && line.trim() == "```") {
            parts += MessagePart(code.toString().removeSuffix("\n"), language)
            code.clear(); language = null
        } else if (language != null) code.append(line).append('\n')
        else prose.append(line).append('\n')
    }
    if (language != null) parts += MessagePart(code.toString().removeSuffix("\n"), language)
    if (prose.isNotEmpty()) parts += MessagePart(prose.toString().trimEnd())
    return parts
}

@Composable
internal fun WorkspaceMessageContent(text: String, color: Color) {
    val parts = remember(text) { messageParts(text) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parts.forEach { part ->
            if (part.language == null) SelectionContainer {
                Text(part.text, color = color, fontSize = 15.sp, lineHeight = 23.sp)
            } else WorkspaceCodeCard(part)
        }
    }
}

@Composable
private fun WorkspaceCodeCard(part: MessagePart) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember(part.text) { mutableStateOf(false) }
    var payload by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                ?: error("Destination unavailable")
        }.onFailure {
            android.widget.Toast.makeText(context, context.getString(R.string.conv_export_failed), android.widget.Toast.LENGTH_LONG).show()
        }
        payload = ""
    }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1800); copied = false } }
    val accent = MaterialTheme.colorScheme.tertiary
    val highlighted = remember(part.text, accent) {
        buildAnnotatedString {
            append(part.text)
            Regex("\\b(class|fun|val|var|if|else|return|import|package|override|def|const|let|function|export|async|await|public|private|true|false|null)\\b")
                .findAll(part.text).forEach { addStyle(SpanStyle(color = accent), it.range.first, it.range.last + 1) }
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(part.language.orEmpty().ifBlank { "Code" }, Modifier.weight(1f), maxLines = 1, fontSize = 12.sp)
                IconButton(onClick = { clipboard.setText(AnnotatedString(part.text)); copied = true }) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        stringResource(R.string.chat_copy), Modifier.size(18.dp), tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = {
                    payload = part.text
                    val extension = when (part.language?.lowercase()) {
                        "kotlin", "kt" -> "kt"; "python", "py" -> "py"; "javascript", "js" -> "js"
                        "typescript", "ts" -> "ts"; "json" -> "json"; "html" -> "html"
                        "css" -> "css"; "bash", "sh" -> "sh"; else -> "txt"
                    }
                    export.launch("maximus-code.$extension")
                }) { Icon(Icons.Default.FileDownload, stringResource(R.string.chat_download), Modifier.size(18.dp)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectionContainer {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    Text((1..part.text.lines().size).joinToString("\n"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 20.sp)
                    Spacer(Modifier.width(16.dp))
                    Text(highlighted, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 20.sp, softWrap = false)
                }
            }
        }
    }
}
