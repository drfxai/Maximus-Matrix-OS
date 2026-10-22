package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ai.drfx.maximus.matrixai.studio.LargeSource
import kotlinx.coroutines.launch

private val codeKeywords = Regex(
    "\\b(class|fun|val|var|if|else|for|while|switch|return|import|package|override|def|const|let|function|export|" +
        "async|await|public|private|true|false|null|na|and|or|not|indicator|strategy|library|method|type|input)\\b"
)
private val codeTokens = Regex("//[^\n]*|#[^\n]*$|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b")

/** Single-line highlighter: cheap enough to run per visible row of a 20k line file. */
internal fun highlightCodeLine(line: String, keyword: Color = MaximusReference.gold): AnnotatedString = buildAnnotatedString {
    append(line)
    if (line.length > 2_000) return@buildAnnotatedString
    codeKeywords.findAll(line).forEach { addStyle(SpanStyle(color = keyword), it.range.first, it.range.last + 1) }
    codeTokens.findAll(line).forEach { match ->
        val value = match.value
        val color = when {
            value.startsWith("//") || value.startsWith("#") -> Color(0xFF718397)
            value.first().isDigit() -> Color(0xFF8FD3FF)
            else -> Color(0xFF9BE59B)
        }
        addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
    }
}

/**
 * Virtualized, line-numbered code viewer. Only visible rows are laid out, so 10k+ line sources
 * scroll smoothly in Chat, Pine Studio and Work; copy/export act on the full text elsewhere.
 */
@Composable
internal fun LargeCodeView(
    code: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 360.dp,
    fillHeight: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    markedLine: Int? = null,
    showLineNumbers: Boolean = true,
    highlight: (String) -> AnnotatedString = { highlightCodeLine(it) }
) {
    val index = remember(code) { LargeSource.LineIndex(code) }
    val widest = remember(code) {
        var best = 0
        var current = 0
        for (c in code) if (c == '\n') { best = maxOf(best, current); current = 0 } else current++
        maxOf(best, current).coerceAtMost(1_200)
    }
    val density = LocalDensity.current
    val fontSize = 12.sp
    val rowHeight = with(density) { 18.sp.toDp() }
    val charWidth = with(density) { (fontSize * 0.62f).toDp() }
    val digits = index.lineCount.toString().length
    val gutter = if (showLineNumbers) charWidth * (digits + 1) + 8.dp else 6.dp
    val contentWidth = gutter + charWidth * (widest + 2) + 12.dp
    val naturalHeight = rowHeight * index.lineCount + 12.dp
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.fillMaxWidth().then(
            if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(minOf(naturalHeight, maxHeight)))) {
            val viewport = maxWidth
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                LazyColumn(state = listState,
                    modifier = Modifier.width(maxOf(viewport, contentWidth)).fillMaxHeight()
                        .testTag("large_code_view"),
                    contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(index.lineCount) { i ->
                        val lineNumber = i + 1
                        val line = index.line(lineNumber)
                        val styled = remember(line) { highlight(line) }
                        Row(Modifier.fillMaxWidth().height(rowHeight)
                            .background(if (lineNumber == markedLine) MaximusReference.gold.copy(alpha = .16f) else Color.Transparent),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (showLineNumbers) Text(lineNumber.toString().padStart(digits), Modifier.width(gutter).padding(start = 4.dp),
                                color = MaximusReference.muted.copy(alpha = .6f), fontFamily = FontFamily.Monospace,
                                fontSize = fontSize, maxLines = 1, softWrap = false)
                            else Spacer(Modifier.width(gutter))
                            Text(styled, color = MaximusReference.white, fontFamily = FontFamily.Monospace,
                                fontSize = fontSize, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/** Search and go-to-line bar shared by the viewer and editor dialogs. */
@Composable
private fun CodeFindBar(
    query: String,
    onQuery: (String) -> Unit,
    status: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGoTo: (Int) -> Unit
) {
    var goTo by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactCodeField(query, onQuery, "Find", Modifier.weight(1f), onDone = onNext)
        Text(status, color = MaximusReference.muted, fontSize = 10.sp, maxLines = 1)
        IconButton(onClick = onPrevious, Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Previous match") }
        IconButton(onClick = onNext, Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Next match") }
        CompactCodeField(goTo, { goTo = it.filter(Char::isDigit).take(7) }, "Line", Modifier.width(64.dp),
            numeric = true, onDone = { goTo.toIntOrNull()?.let(onGoTo) })
    }
}

@Composable
private fun CompactCodeField(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    onDone: () -> Unit
) {
    Surface(modifier.height(34.dp), shape = RoundedCornerShape(9.dp), color = MaximusReference.navy,
        border = BorderStroke(1.dp, MaximusReference.stroke)) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = MaximusReference.muted, fontSize = 12.sp)
            BasicTextField(value, onValue, singleLine = true,
                textStyle = TextStyle(color = MaximusReference.white, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(MaximusReference.gold),
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                    imeAction = if (numeric) ImeAction.Go else ImeAction.Search),
                keyboardActions = KeyboardActions(onGo = { onDone() }, onSearch = { onDone() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint })
        }
    }
}

/** Full-screen read-only viewer with search and go-to-line for very long code. */
@Composable
internal fun LargeCodeDialog(
    title: String,
    code: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    highlight: (String) -> AnnotatedString = { highlightCodeLine(it) }
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val index = remember(code) { LargeSource.LineIndex(code) }
    var query by remember { mutableStateOf("") }
    var offset by remember { mutableIntStateOf(-1) }
    var marked by remember { mutableStateOf<Int?>(null) }
    val matches = remember(code, query) { LargeSource.countMatches(code, query) }
    fun reveal(line: Int) {
        marked = line.coerceIn(1, index.lineCount)
        scope.launch { listState.scrollToItem((line - 4).coerceAtLeast(0)) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF030B14)) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close code viewer") }
                    Column(Modifier.weight(1f)) {
                        Text(title, color = MaximusReference.white, fontSize = 14.sp, maxLines = 1)
                        Text(LargeSource.sizeLabel(code), color = MaximusReference.muted, fontSize = 10.sp)
                    }
                    actions()
                }
                CodeFindBar(query, { query = it.take(200); offset = -1 },
                    if (query.isEmpty()) "" else "$matches",
                    onPrevious = {
                        val hit = LargeSource.findPrevious(code, query, if (offset < 0) code.length else offset)
                        if (hit >= 0) { offset = hit; reveal(index.lineOf(hit)) }
                    },
                    onNext = {
                        val hit = LargeSource.find(code, query, offset + 1)
                        if (hit >= 0) { offset = hit; reveal(index.lineOf(hit)) }
                    },
                    onGoTo = ::reveal)
                LargeCodeView(code, Modifier.weight(1f).padding(top = 4.dp), fillHeight = true,
                    listState = listState, markedLine = marked, highlight = highlight)
            }
        }
    }
}

/**
 * Full-screen plain-text editor for large sources: no decoration or visual transformation on the
 * field, find/replace, go-to-line and a live cursor position. Edits stay local until Save.
 */
@Composable
internal fun LargeCodeEditorDialog(
    title: String,
    initial: String,
    maxChars: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    check: ((String) -> String?)? = null
) {
    var value by remember(initial) { mutableStateOf(TextFieldValue(initial)) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var replaceOpen by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val text = value.text
    val cursorLine = remember(text, value.selection.start) {
        var line = 1
        val end = value.selection.start.coerceAtMost(text.length)
        for (i in 0 until end) if (text[i] == '\n') line++
        line
    }
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val matches = remember(text, query) { LargeSource.countMatches(text, query) }
    fun select(start: Int, end: Int) {
        value = value.copy(selection = TextRange(start, end))
        layout?.let { result ->
            val top = result.getLineTop(result.getLineForOffset(start.coerceAtMost(result.layoutInput.text.length)))
            scope.launch { scroll.animateScrollTo((top - 160f).toInt().coerceAtLeast(0)) }
        }
    }
    fun dismiss() { if (text != initial) confirmDiscard = true else onDismiss() }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF030B14)) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::dismiss) { Icon(Icons.Default.Close, "Close editor") }
                    Column(Modifier.weight(1f)) {
                        Text(title, color = MaximusReference.white, fontSize = 14.sp, maxLines = 1)
                        Text("Ln $cursorLine / %,d · %,d chars".format(lineCount, text.length) +
                            if (text != initial) " · unsaved" else "",
                            color = if (text.length > maxChars) MaterialTheme.colorScheme.error else MaximusReference.muted,
                            fontSize = 10.sp, maxLines = 1)
                    }
                    IconButton(onClick = { replaceOpen = !replaceOpen }) { Icon(Icons.Default.FindReplace, "Find and replace") }
                    if (check != null) IconButton(onClick = { checkResult = check(text) ?: "No problems found by the local check." }) {
                        Icon(Icons.Default.Rule, "Check source")
                    }
                    Button(onClick = { onSave(text) }, enabled = text.length <= maxChars,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaximusReference.gold, contentColor = Color.Black),
                        modifier = Modifier.height(36.dp).testTag("large_code_save")) { Text("Save") }
                }
                CodeFindBar(query, { query = it.take(200) }, if (query.isEmpty()) "" else "$matches",
                    onPrevious = {
                        val hit = LargeSource.findPrevious(text, query, value.selection.start)
                        if (hit >= 0) select(hit, hit + query.length)
                    },
                    onNext = {
                        val hit = LargeSource.find(text, query, value.selection.end)
                        if (hit >= 0) select(hit, hit + query.length)
                    },
                    onGoTo = { line ->
                        val start = LargeSource.LineIndex(text).startOf(line)
                        select(start, start)
                    })
                if (replaceOpen) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactCodeField(replacement, { replacement = it.take(2_000) }, "Replace with", Modifier.weight(1f), onDone = {})
                    TextButton(enabled = query.isNotEmpty() && matches > 0, onClick = {
                        val selected = text.substring(value.selection.min, value.selection.max)
                        if (selected.equals(query, ignoreCase = true)) {
                            val next = text.replaceRange(value.selection.min, value.selection.max, replacement)
                            if (next.length <= maxChars) value = TextFieldValue(next, TextRange(value.selection.min + replacement.length))
                        } else LargeSource.find(text, query, value.selection.end).takeIf { it >= 0 }
                            ?.let { select(it, it + query.length) }
                    }) { Text("Replace", fontSize = 12.sp) }
                    TextButton(enabled = query.isNotEmpty() && matches > 0, onClick = {
                        val next = text.replace(query, replacement, ignoreCase = true)
                        if (next.length <= maxChars) value = TextFieldValue(next, TextRange(0))
                    }) { Text("All", fontSize = 12.sp) }
                }
                checkResult?.let { message ->
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp).clickable { checkResult = null },
                        shape = RoundedCornerShape(8.dp), color = MaximusReference.navy,
                        border = BorderStroke(1.dp, MaximusReference.stroke)) {
                        Text(message, Modifier.padding(8.dp), color = MaximusReference.white, fontSize = 11.sp, maxLines = 4)
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        BasicTextField(
                            value = value,
                            onValueChange = { next -> if (next.text.length <= maxChars) value = next },
                            textStyle = TextStyle(color = MaximusReference.white, fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp, lineHeight = 19.sp),
                            cursorBrush = SolidColor(MaximusReference.gold),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                            onTextLayout = { layout = it },
                            modifier = Modifier.fillMaxWidth().testTag("large_code_editor")
                                .semantics { contentDescription = "$title source editor" }
                        )
                    }
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard changes?") },
        text = { Text("Unsaved edits in this editor will be lost.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })
}
