package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.studio.connections.DeveloperCredentialVault
import ai.drfx.maximus.matrixai.studio.connections.DevelopmentService

private val HubGold = Color(0xFFF4C35A)
private val HubOrange = Color(0xFFFF8D38)
private val HubRed = Color(0xFFF05262)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolsHubScreen(
    viewModel: MatrixViewModel,
    onWork: (Int) -> Unit,
    onChat: () -> Unit,
    onControl: () -> Unit,
    onMore: () -> Unit,
    onOperations: () -> Unit = {},
    onOpen: (HubTarget) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    var showConversations by remember { mutableStateOf(false) }
    var showPluginManagement by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("workspace_appearance", 0) }
    val vault = remember(context) { DeveloperCredentialVault(context.applicationContext) }
    var companion by remember { mutableStateOf(prefs.getBoolean("companion", true)) }
    val sessions by viewModel.chatSessions.collectAsState()
    val activeId by viewModel.activeChatSessionId.collectAsState()
    val searchTitle = stringResource(R.string.ui_web_search)
    val imageTitle = stringResource(R.string.ui_image_studio)
    val githubTitle = stringResource(R.string.plugin_github)
    val cloudflareTitle = stringResource(R.string.plugin_cloudflare)
    val matches: (String) -> Boolean = { search.isBlank() || it.contains(search, ignoreCase = true) }

    LazyColumn(modifier.fillMaxSize().testTag("tools_hub_screen"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMore) {
                    Icon(Icons.Default.Menu, stringResource(R.string.nav_more))
                }
                WorkspaceBrand(Modifier.weight(1f))
                IconButton(onClick = onControl, modifier = Modifier.testTag("hub_control_open")) {
                    Icon(Icons.Default.Settings, stringResource(R.string.nav_control))
                }
            }
        }
        item {
            Text(stringResource(R.string.ui_hub), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold)
        }
        item {
            OutlinedTextField(search, { search = it.take(100) }, Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(16.dp),
                placeholder = { Text(stringResource(R.string.ui_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        val sections = hubSections.mapNotNull { (title, tiles) ->
            tiles.filter { matches(it.label) || matches(it.detail) || matches(title) }
                .takeIf { it.isNotEmpty() }?.let { title to it }
        }
        sections.forEach { (title, tiles) ->
            item(key = "hub_section_$title") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    tiles.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { tile ->
                                HubLauncherTile(tile, Modifier.weight(1f)) {
                                    when (tile.target) {
                                        HubTarget.CONTROL -> onControl()
                                        HubTarget.OPERATIONS -> onOperations()
                                        HubTarget.CHAT -> onChat()
                                        else -> onOpen(tile.target)
                                    }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (search.isNotBlank() && sections.isEmpty()) item {
            Text("No workspace matches “$search”. Tools below are still searchable.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        item {
            OutlinedButton(onClick = onOperations, modifier = Modifier.fillMaxWidth()
                .then(Modifier.testTag("operations_open"))) {
                Text("Operations · Agents, Pulse, Memory & Runtime")
            }
        }
        item {
            MaximusFramedCard {
                Text("AI & developer controls", color = HubGold, fontWeight = FontWeight.Bold)
                HubInstalledRow(Icons.Default.Tune, "Smart Combo & API Control",
                    "Routing, provider credentials, usage and model settings", onClick = onControl)
                HubInstalledRow(Icons.Default.Code, "Developer connections",
                    "GitHub and Cloudflare · inspect authorization and verified results",
                    onClick = { onWork(4) })
            }
        }
        if (matches(searchTitle) || matches(imageTitle)) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (matches(searchTitle)) HubFeature(
                    searchTitle, stringResource(R.string.design_web_detail),
                    Icons.Default.Search, stringResource(R.string.ui_tool_unavailable),
                    HubOrange, Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                if (matches(imageTitle)) HubFeature(
                    imageTitle, stringResource(R.string.design_image_detail),
                    Icons.Default.Image, stringResource(R.string.ui_tool_unavailable),
                    HubOrange, Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
            }
        }
        if (matches(githubTitle) || matches(cloudflareTitle)) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (matches(githubTitle)) HubFeature(
                    githubTitle, stringResource(R.string.design_github_detail),
                    Icons.Default.Code,
                    stringResource(if (vault.configured(DevelopmentService.GITHUB)) R.string.dev_saved else R.string.dev_not_connected),
                    if (vault.configured(DevelopmentService.GITHUB)) HubGold else HubOrange,
                    Modifier.weight(1f), onClick = { onWork(4) })
                else Spacer(Modifier.weight(1f))
                if (matches(cloudflareTitle)) HubFeature(
                    cloudflareTitle, stringResource(R.string.design_cloud_detail),
                    Icons.Default.Cloud,
                    stringResource(if (vault.configured(DevelopmentService.CLOUDFLARE)) R.string.dev_saved else R.string.dev_not_connected),
                    if (vault.configured(DevelopmentService.CLOUDFLARE)) HubGold else HubOrange,
                    Modifier.weight(1f), onClick = { onWork(4) })
                else Spacer(Modifier.weight(1f))
            }
        }
        item {
            Surface(shape = RoundedCornerShape(19.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.design_installed_tools),
                            Modifier.weight(1f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showPluginManagement = !showPluginManagement }) {
                            Text(stringResource(R.string.design_manage_tools))
                            Icon(if (showPluginManagement) Icons.Default.ExpandLess else Icons.Default.ChevronRight, null)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    HubInstalledRow(Icons.Default.Description, stringResource(R.string.design_files),
                        stringResource(R.string.design_files_detail), onClick = { onWork(1) })
                    HubInstalledRow(Icons.Default.Code, stringResource(R.string.design_code_artifacts),
                        stringResource(R.string.design_code_detail), onClick = { onWork(1) })
                    HubInstalledRow(Icons.Default.Android, stringResource(R.string.plugin_apk),
                        stringResource(R.string.design_apk_detail), onClick = { onWork(2) })
                    HubInstalledRow(Icons.Default.Terminal, stringResource(R.string.linux_title),
                        stringResource(R.string.design_linux_detail),
                        actionLabel = stringResource(R.string.design_check_runtime), warning = true,
                        onClick = { onWork(3) })
                    if (showPluginManagement) {
                        HorizontalDivider()
                        PluginStorePanel()
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(19.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (companion) WorkspaceCompanion(modifier = Modifier.size(76.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ui_companion), fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.design_companion_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(companion, {
                            companion = it
                            prefs.edit().putBoolean("companion", it).apply()
                        }, modifier = Modifier.height(42.dp))
                    }
                    Text(stringResource(R.string.design_companion_states),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            R.string.design_idle to HubGold,
                            R.string.design_working to HubOrange,
                            R.string.design_done to Color(0xFF38C79B),
                            R.string.design_error to HubRed
                        ).forEach { (title, tone) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, tone.copy(alpha = .45f))) {
                                    Box(Modifier.fillMaxWidth().height(54.dp),
                                        contentAlignment = Alignment.Center) {
                                        Image(painterResource(R.drawable.maximus_companion), null,
                                            Modifier.size(47.dp))
                                    }
                                }
                                Text(stringResource(title), fontSize = 10.sp, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            HubInstalledRow(Icons.Default.ChatBubbleOutline,
                stringResource(R.string.ui_conversations),
                stringResource(R.string.design_conversations_detail),
                actionLabel = stringResource(R.string.ui_open),
                onClick = { showConversations = true })
        }
        item {
            HubInstalledRow(Icons.Default.AltRoute, stringResource(R.string.dash_combo_title),
                stringResource(R.string.design_combo_detail),
                actionLabel = stringResource(R.string.ui_open),
                onClick = onControl)
        }
    }

    if (showConversations) {
        var sessionQuery by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { showConversations = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.77f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_conversations), Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showConversations = false }) {
                        Icon(Icons.Default.Close, stringResource(R.string.ui_close))
                    }
                }
                OutlinedTextField(sessionQuery, { sessionQuery = it.take(100) },
                    Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(stringResource(R.string.design_search_conversations)) })
                Button(onClick = {
                    viewModel.createChatSession()
                    showConversations = false
                    onChat()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.design_new_chat))
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sessions.filter { it.title.contains(sessionQuery, ignoreCase = true) }, key = { it.id }) { session ->
                        Surface(onClick = {
                            viewModel.switchChatSession(session.id)
                            showConversations = false
                            onChat()
                        }, shape = RoundedCornerShape(12.dp),
                            color = if (session.id == activeId)
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ChatBubbleOutline, null, tint = HubGold)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(session.provider, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubFeature(title: String, detail: String, icon: ImageVector,
    status: String, tone: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(17.dp)
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().heightIn(min = 134.dp).padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, maxLines = 2, fontSize = 13.sp)
                    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
            Surface(shape = RoundedCornerShape(50), color = tone.copy(alpha = .08f),
                border = BorderStroke(1.dp, tone)) {
                Text(status, Modifier.align(Alignment.CenterHorizontally)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                    color = tone, fontSize = 10.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (onClick == null) Surface(modifier, shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content)
    else Surface(onClick = onClick, modifier = modifier, shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content)
}

@Composable
private fun HubInstalledRow(icon: ImageVector, title: String, detail: String,
    actionLabel: String = stringResource(R.string.design_open_workspace),
    warning: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 69.dp).padding(vertical = 9.dp, horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Icon(icon, null, Modifier.padding(9.dp).size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(detail, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.width(4.dp))
            Text(actionLabel, fontSize = 10.sp,
                color = if (warning) HubOrange else HubGold, maxLines = 2)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Destinations the Hub launcher can open; MatrixScreen maps them to its own navigation. */
internal enum class HubTarget { BRAIN, CHAT, WORK, PINE, PERSONAS, SIGNALS, VISION, NEWS, CONTROL, OPERATIONS, DATA, TERMINAL }

private data class HubTile(val target: HubTarget, val label: String, val detail: String, val icon: ImageVector, val tone: Color)

private val hubSections = listOf(
    "Workspaces" to listOf(
        HubTile(HubTarget.CHAT, "Chat", "Assistant conversations", Icons.Default.ChatBubbleOutline, HubGold),
        HubTile(HubTarget.WORK, "Work", "Projects, files and code", Icons.Default.WorkOutline, HubGold),
        HubTile(HubTarget.PINE, "Pine", "Pine Script studio and chart", Icons.Default.ShowChart, HubOrange),
        HubTile(HubTarget.PERSONAS, "Personas", "Agents and roles", Icons.Default.Groups, HubGold)
    ),
    "Markets" to listOf(
        HubTile(HubTarget.SIGNALS, "Signals", "Live market signals", Icons.Default.Insights, HubOrange),
        HubTile(HubTarget.VISION, "Vision", "Chart and image analysis", Icons.Default.Visibility, HubOrange),
        HubTile(HubTarget.NEWS, "News", "News intelligence", Icons.Default.Newspaper, HubOrange),
        HubTile(HubTarget.BRAIN, "Brain", "Neural Galaxy graph", Icons.Default.Hub, HubGold)
    ),
    "System" to listOf(
        HubTile(HubTarget.CONTROL, "Control", "Smart Combo, API keys and models", Icons.Default.Tune, HubGold),
        HubTile(HubTarget.OPERATIONS, "Ops", "Agents, pulse, memory and runtime", Icons.Default.MonitorHeart, HubGold),
        HubTile(HubTarget.DATA, "Data", "Storage and evidence", Icons.Default.Storage, HubGold),
        HubTile(HubTarget.TERMINAL, "Terminal", "Local shell runtime", Icons.Default.Terminal, HubRed)
    )
)

@Composable
private fun HubLauncherTile(tile: HubTile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.testTag("hub_tile_" + tile.target.name.lowercase()),
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(38.dp).background(tile.tone.copy(alpha = .13f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center) {
                Icon(tile.icon, tile.detail, Modifier.size(21.dp), tint = tile.tone)
            }
            Text(tile.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
