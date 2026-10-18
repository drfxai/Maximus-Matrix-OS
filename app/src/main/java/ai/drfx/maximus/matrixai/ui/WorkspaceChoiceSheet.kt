package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.R

internal data class WorkspaceChoice(val id: String, val title: String,
    val detail: String, val tags: List<String> = emptyList())

/** Searchable model, project and session picker: selection only; no fabricated model telemetry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceChoiceSheet(title: String, choices: List<WorkspaceChoice>, selected: String,
    onDismiss: () -> Unit, onSelect: (String) -> Unit,
    confirmSelection: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    var pendingSelection by remember(selected) { mutableStateOf(selected) }
    var tag by remember { mutableStateOf("") }
    val tags = remember(choices) { choices.flatMap { it.tags }.distinct().sorted() }
    var query by remember { mutableStateOf("") }
    val filtered = remember(choices, query, tag) { choices.filter {
        (tag.isEmpty() || tag in it.tags) &&
        (it.title.contains(query, ignoreCase = true) || it.detail.contains(query, ignoreCase = true))
    } }
    val gold = MaterialTheme.colorScheme.primary
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.84f).padding(horizontal = 15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.ui_close))
                }
            }
            OutlinedTextField(query, { query = it.take(100) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text(stringResource(R.string.ui_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) })
            if (tags.isNotEmpty()) Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(tag.isEmpty(), { tag = "" }, label = { Text(stringResource(R.string.ui_all)) })
                tags.forEach { item ->
                    FilterChip(tag == item, { tag = item }, label = { Text(item) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                content = actions)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered, key = { it.id }) { choice ->
                    val chosen = choice.id == (if (confirmSelection) pendingSelection else selected)
                    Surface(onClick = {
                        if (confirmSelection) pendingSelection = choice.id else onSelect(choice.id)
                    }, shape = RoundedCornerShape(17.dp),
                        color = if (chosen) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(if (chosen) 1.5.dp else 1.dp,
                            if (chosen) gold else MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Icon(when {
                                    choice.tags.any { it.contains("vision", true) || it.contains("image", true) } -> Icons.Default.Image
                                    choice.tags.any { it.contains("local", true) || it.contains("offline", true) } -> Icons.Default.Storage
                                    choice.tags.any { it.contains("code", true) } -> Icons.Default.Code
                                    else -> Icons.Default.Layers
                                }, null, Modifier.padding(9.dp).size(23.dp),
                                    tint = if (chosen) gold else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(choice.title, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (choice.tags.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        choice.tags.take(3).forEach { label ->
                                            Surface(shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                border = BorderStroke(.5.dp,
                                                    MaterialTheme.colorScheme.outlineVariant)) {
                                                Text(label, Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    fontSize = 9.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                                if (choice.detail.isNotBlank()) Text(choice.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (chosen) Icon(Icons.Default.CheckCircle, null, tint = gold,
                                modifier = Modifier.size(25.dp))
                            else Icon(Icons.Default.RadioButtonUnchecked, null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(23.dp))
                        }
                    }
                }
            }
            if (confirmSelection) {
                Button(onClick = { onSelect(pendingSelection) },
                    enabled = choices.any { it.id == pendingSelection },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 15.dp)) {
                    Text(stringResource(R.string.ui_use_model), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}
