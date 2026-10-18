package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R

internal data class WorkspaceChoice(val id: String, val title: String, val detail: String, val tags: List<String> = emptyList())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceChoiceSheet(title: String, choices: List<WorkspaceChoice>, selected: String,
    onDismiss: () -> Unit, onSelect: (String) -> Unit, confirmSelection: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    var pendingSelection by remember(selected) { mutableStateOf(selected) }
    var tag by remember { mutableStateOf("") }
    val tags = remember(choices) { choices.flatMap { it.tags }.distinct().sorted() }
    var query by remember { mutableStateOf("") }
    val filtered = remember(choices, query, tag) { choices.filter {
        (tag.isEmpty() || tag in it.tags) && (it.title.contains(query, ignoreCase = true) || it.detail.contains(query, ignoreCase = true))
    } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.78f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.ui_close)) }
            }
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(16.dp), placeholder = { Text(stringResource(R.string.ui_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) })
            if (tags.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(tag.isEmpty(), { tag = "" }, label = { Text(stringResource(R.string.ui_all)) })
                tags.forEach { item -> FilterChip(tag == item, { tag = item }, label = { Text(item) }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, content = actions)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { choice ->
                    Surface(onClick = { if (confirmSelection) pendingSelection = choice.id else onSelect(choice.id) }, shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (choice.id == (if (confirmSelection) pendingSelection else selected)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(choice.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                if (choice.detail.isNotBlank()) Text(choice.detail, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                            if (choice.id == (if (confirmSelection) pendingSelection else selected)) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (confirmSelection) Button(onClick = { onSelect(pendingSelection) },
                enabled = choices.any { it.id == pendingSelection }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(stringResource(R.string.ui_use_model))
            }
        }
    }
}
