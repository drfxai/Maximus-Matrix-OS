package ai.drfx.maximus.matrixai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
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
import ai.drfx.maximus.matrixai.studio.PineSourceDetector
import androidx.compose.ui.platform.testTag

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

/** Replaces the source of an existing Pine Studio draft, e.g. the one that asked Chat for a revision. */
class PineUpdateTarget(val draftName: String, val apply: (String) -> Unit)

/** Fenced parts, with any unfenced Pine script in the prose split out as its own code part. */
internal fun messagePartsWithPine(text: String): List<MessagePart> = messageParts(text).flatMap { part ->
    if (part.language != null) listOf(part)
    else PineSourceDetector.splitProse(part.text).map { (segment, pine) -> MessagePart(segment, if (pine) "pine" else null) }
}

@Composable
internal fun WorkspaceMessageContent(
    text: String,
    color: Color,
    onOpenPine: ((String) -> Unit)? = null,
    onPreviewPine: ((String) -> Unit)? = null,
    updatePine: PineUpdateTarget? = null
) {
    val parts = remember(text) { messagePartsWithPine(text) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parts.forEach { part ->
            if (part.language == null) LongProse(part.text, color)
            else WorkspaceCodeCard(part, onOpenPine = onOpenPine, onPreviewPine = onPreviewPine, updatePine = updatePine)
        }
        val html = parts.firstOrNull {
            it.language != null && CodeLanguageCatalog.resolve(it.language, it.text).canPreviewHtml
        }
        if (html != null) {
            val css = parts.firstOrNull { it.language?.lowercase() in setOf("css", "scss") }?.text.orEmpty()
            val js = parts.firstOrNull {
                it.language?.lowercase() in setOf("js", "javascript")
            }?.text.orEmpty()
            WorkspaceOfflineHtmlPreview(html.text, css, js)
        }
    }
}

@Composable
internal fun WorkspaceCodeCard(
    part: MessagePart,
    title: String? = null,
    onOpenPine: ((String) -> Unit)? = null,
    onPreviewPine: ((String) -> Unit)? = null,
    updatePine: PineUpdateTarget? = null
) {
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
    val language = remember(part.language, part.text) {
        CodeLanguageCatalog.resolve(part.language, part.text)
    }
    var fullScreen by remember(part.text) { mutableStateOf(false) }
    val lineCount = remember(part.text) { part.text.count { it == '\n' } + 1 }
    val highlight: (String) -> AnnotatedString = if (language.extension == "pine") ::highlightPineLine else { line -> highlightCodeLine(line) }
    Surface(shape = RoundedCornerShape(12.dp), color = MaximusReference.navy,
        border = BorderStroke(1.dp, MaximusReference.stroke), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title ?: "source.${language.extension}", maxLines = 1, fontSize = 12.sp, color = MaximusReference.white)
                    if (lineCount > 1) Text("%,d lines".format(lineCount), maxLines = 1, fontSize = 10.sp, color = MaximusReference.muted)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF142E49),
                    border = BorderStroke(1.dp, MaximusReference.stroke)
                ) {
                    Text(language.label, Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = MaximusReference.gold, fontSize = 10.sp)
                }
                IconButton(onClick = { fullScreen = true }, modifier = Modifier.testTag("code_card_expand")) {
                    Icon(Icons.Default.OpenInFull, "Open code full screen", Modifier.size(17.dp))
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(part.text)); copied = true }) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        stringResource(R.string.chat_copy), Modifier.size(18.dp), tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = {
                    payload = part.text
                    val extension = language.extension
                    export.launch(title?.takeIf { it.matches(Regex("[A-Za-z0-9_.-]{1,80}")) && !it.startsWith(".") } ?: "maximus-code.$extension")
                }) { Icon(Icons.Default.FileDownload, stringResource(R.string.chat_download), Modifier.size(18.dp)) }
            }
            if (language.extension == "pine" && onOpenPine != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onOpenPine(part.text) }, modifier = Modifier.weight(1f)
                        .testTag("chat_open_in_pine")) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open in Pine Studio", fontSize = 12.sp, maxLines = 1)
                    }
                    if (onPreviewPine != null) TextButton(onClick = { onPreviewPine(part.text) },
                        modifier = Modifier.weight(1f).testTag("chat_preview_pine_chart")) {
                        Icon(Icons.Default.CandlestickChart, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Preview on chart", fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (updatePine != null) TextButton(onClick = { updatePine.apply(part.text) },
                    modifier = Modifier.fillMaxWidth().testTag("chat_update_pine_draft")) {
                    Icon(Icons.Default.SyncAlt, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Replace code in “${updatePine.draftName.take(32)}”", fontSize = 12.sp, maxLines = 1)
                }
            }
            HorizontalDivider(color = MaximusReference.stroke)
            // Virtualized: only visible rows are laid out, so 10k+ line replies stay smooth.
            LargeCodeView(part.text, maxHeight = 340.dp, highlight = highlight)
        }
    }
    if (fullScreen) LargeCodeDialog(title ?: "source.${language.extension}", part.text,
        onDismiss = { fullScreen = false }, highlight = highlight, actions = {
            IconButton(onClick = { clipboard.setText(AnnotatedString(part.text)); copied = true }) {
                Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, stringResource(R.string.chat_copy))
            }
            if (language.extension == "pine" && onOpenPine != null) IconButton(onClick = { fullScreen = false; onOpenPine(part.text) }) {
                Icon(Icons.Default.OpenInNew, "Open in Pine Studio")
            }
        })
}

private const val PROSE_PREVIEW_CHARS = 4_000

/** Very long prose (a pasted log or unfenced source) shows a preview until expanded. */
@Composable
private fun LongProse(text: String, color: Color) {
    var expanded by remember(text) { mutableStateOf(false) }
    val long = text.length > PROSE_PREVIEW_CHARS * 2
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SelectionContainer {
            Text(if (long && !expanded) text.take(PROSE_PREVIEW_CHARS).trimEnd() + " …" else text,
                color = color, fontSize = 15.sp, lineHeight = 23.sp)
        }
        if (long) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(if (expanded) "Show less" else "Show all · ${LargeSourceLabel(text)}", fontSize = 12.sp)
        }
    }
}

private fun LargeSourceLabel(text: String) = ai.drfx.maximus.matrixai.studio.LargeSource.sizeLabel(text)

