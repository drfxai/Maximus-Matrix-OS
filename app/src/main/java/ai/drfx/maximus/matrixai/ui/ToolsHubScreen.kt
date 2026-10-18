package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R

@Composable
internal fun ToolsHubScreen(onWork: (Int) -> Unit, onChat: () -> Unit, onControl: () -> Unit,
    onMore: () -> Unit, modifier: Modifier = Modifier) {
    var search by remember { mutableStateOf("") }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("workspace_appearance", 0) }
    var companion by remember { mutableStateOf(prefs.getBoolean("companion", true)) }
    val github = stringResource(R.string.plugin_github)
    val cloudflare = stringResource(R.string.plugin_cloudflare)
    val apk = stringResource(R.string.plugin_apk)
    val linux = stringResource(R.string.linux_title)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            WorkspaceBrand(Modifier.weight(1f))
            IconButton(onClick = onMore) { Icon(Icons.Default.Menu, stringResource(R.string.nav_more)) }
            IconButton(onClick = onControl) { Icon(Icons.Default.Settings, stringResource(R.string.nav_control)) }
        } }
        item { Text(stringResource(R.string.ui_tools), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(16.dp), placeholder = { Text(stringResource(R.string.ui_search)) },
            leadingIcon = { Icon(Icons.Default.Search, null) }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (github.contains(search, true)) HubTool(github, Icons.Default.Code, Modifier.weight(1f)) { onWork(4) }
                if (cloudflare.contains(search, true)) HubTool(cloudflare, Icons.Default.Cloud, Modifier.weight(1f)) { onWork(4) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (apk.contains(search, true)) HubTool(apk, Icons.Default.Android, Modifier.weight(1f)) { onWork(2) }
                if (linux.contains(search, true)) HubTool(linux, Icons.Default.Terminal, Modifier.weight(1f)) { onWork(3) }
            }
        }
        item { PluginStorePanel() }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (companion) WorkspaceCompanion()
                    Text(stringResource(R.string.ui_companion), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(companion, { companion = it; prefs.edit().putBoolean("companion", it).apply() })
                }
            }
        }
        item { HubTool(stringResource(R.string.ui_conversations), Icons.Default.ChatBubbleOutline, Modifier.fillMaxWidth(), onChat) }
        item { HubTool(stringResource(R.string.dash_combo_title), Icons.Default.AltRoute, Modifier.fillMaxWidth(), onControl) }
    }
}

@Composable
private fun HubTool(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.ui_open), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
