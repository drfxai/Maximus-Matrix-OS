package ai.drfx.maximus.matrixai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import ai.drfx.maximus.matrixai.studio.PineStudioStore
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.drfx.maximus.matrixai.news.NewsIntelligenceScreen
import ai.drfx.maximus.matrixai.signals.LiveSignalsScreen
import ai.drfx.maximus.matrixai.ui.theme.AppThemeMode
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import ai.drfx.maximus.matrixai.R
import androidx.compose.ui.res.stringResource

private enum class Destination(val label: String) {
    MATRIX("Matrix"),
    SIGNALS("Signals"),
    VISION("Vision"),
    NEWS("News"),
    STUDIO("Studio"),
    PINE("Pine Studio"),
    CHAT("Chat"),
    AGENTS("Personas"),
    DATA("Data"),
    CONTROL("Control"),
    TOOLS("Tools"),
    OPERATIONS("Operations"),
    TERMINAL("Terminal")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MatrixScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    selectedLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    viewModel: MatrixViewModel = viewModel()
) {
    var destination by remember { mutableStateOf(Destination.MATRIX) }
    val context = LocalContext.current
    var pineDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    // The Pine draft that last asked Chat for a revision, so a reply can update it in place.
    var pineRevisionDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var pineOpenChart by remember { mutableStateOf(false) }
    // Resolved off the main thread: drafts can hold megabytes of source, and this screen recomposes often.
    val pineRevisionTarget by produceState<Pair<String, String>?>(null, pineRevisionDraftId, destination) {
        val id = pineRevisionDraftId
        value = if (id == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { PineStudioStore(context).list().firstOrNull { it.id == id }?.let { it.id to it.name } }.getOrNull()
        }
    }
    fun importPine(source: String, chart: Boolean) {
        try {
            pineDraftId = PineStudioStore(context).importSource(source).id
            pineOpenChart = chart
            destination = Destination.PINE
        } catch (_: Exception) {
            Toast.makeText(context, "Pine source could not be saved. Please retry.", Toast.LENGTH_LONG).show()
        }
    }
    var workSection by remember { mutableIntStateOf(0) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var returnToHub by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    BackHandler(enabled = (destination != Destination.MATRIX || returnToHub) && !isImeVisible) {
        // A screen opened from the Hub launcher returns to the Hub, not to the Brain.
        if (returnToHub && destination != Destination.TOOLS && destination != Destination.CONTROL &&
            destination != Destination.OPERATIONS) {
            returnToHub = false
            destination = Destination.TOOLS
            return@BackHandler
        }
        returnToHub = false
        destination = when (destination) {
            Destination.PINE -> Destination.STUDIO
            Destination.CONTROL, Destination.OPERATIONS -> Destination.TOOLS
            else -> Destination.MATRIX
        }
    }


    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!isImeVisible) {
                NavigationBar(
                    modifier = Modifier.testTag("main_navigation_bar").semantics { contentDescription = "Main navigation" },
                    containerColor = Color.Black,
                    tonalElevation = 0.dp
                ) {
                    val primaryDestinations = listOf(
                        Triple(Destination.MATRIX, R.string.nav_brain, Icons.Outlined.Psychology),
                        Triple(Destination.CHAT, R.string.nav_chat, Icons.Outlined.ChatBubbleOutline),
                        Triple(Destination.STUDIO, R.string.ui_work, Icons.Default.WorkOutline),
                        Triple(Destination.AGENTS, R.string.design_agents, Icons.Outlined.Groups)
                    )
                    primaryDestinations.forEach { (target, label, icon) ->
                        val tag = when (target) {
                            Destination.MATRIX -> "nav_item_matrix"
                            Destination.CHAT -> "nav_item_chat"
                            Destination.AGENTS -> "nav_item_agents"
                            else -> "nav_item_studio"
                        }
                        val accessibilityLabel = when (target) {
                            Destination.MATRIX -> "Neural Brain"
                            Destination.STUDIO -> "Project Studio"
                            else -> stringResource(label)
                        }
                        NavigationBarItem(
                            selected = destination == target || (target == Destination.STUDIO && destination == Destination.PINE),
                            onClick = { returnToHub = false; destination = target },
                            icon = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(23.dp))
                                    Spacer(Modifier.height(2.dp))
                                    Box(Modifier.width(25.dp).height(2.dp).background(
                                        if (destination == target || (target == Destination.STUDIO && destination == Destination.PINE))
                                            MaximusReference.gold else Color.Transparent))
                                }
                            },
                            label = { Text(stringResource(label), maxLines = 1, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaximusReference.gold,
                                selectedTextColor = MaximusReference.gold,
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag(tag).semantics { contentDescription = accessibilityLabel }
                        )
                    }
                    val moreLabel = stringResource(R.string.nav_more)
                    NavigationBarItem(
                        selected = destination !in primaryDestinations.map { it.first } && destination != Destination.PINE,
                        onClick = { destination = Destination.TOOLS },
                        icon = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(23.dp))
                                Spacer(Modifier.height(2.dp))
                                Box(Modifier.width(25.dp).height(2.dp).background(
                                    if (destination !in primaryDestinations.map { it.first } && destination != Destination.PINE)
                                        MaximusReference.gold else Color.Transparent))
                            }
                        },
                        label = { Text(stringResource(R.string.ui_hub), fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaximusReference.gold,
                            selectedTextColor = MaximusReference.gold, indicatorColor = Color.Transparent),
                        modifier = Modifier.testTag("nav_item_more").semantics { contentDescription = moreLabel }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    fadeIn(tween(160)).togetherWith(fadeOut(tween(100)))
                },
                label = "workspace_screen_transition",
                modifier = Modifier.fillMaxSize()
            ) { targetDest ->
                when (targetDest) {
                    Destination.MATRIX -> MatrixGraphScreen(
                        viewModel = viewModel,
                        onNavigateToVision = { destination = Destination.VISION },
                        onNavigateToNews = { destination = Destination.NEWS },
                        onNavigateToSignals = { destination = Destination.SIGNALS },
                        onNavigateToChat = { destination = Destination.CHAT },
                        onNavigateToAgents = { destination = Destination.AGENTS },
                        onNavigateToData = { destination = Destination.DATA },
                        onNavigateToControl = { destination = Destination.CONTROL },
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.SIGNALS -> LiveSignalsScreen(
                        viewModel = viewModel,
                        onNavigateToChat = { destination = Destination.CHAT },
                        onNavigateToVision = { destination = Destination.VISION },
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.VISION -> ChartVisionScreen(
                        viewModel = viewModel,
                        onNavigateToChat = { destination = Destination.CHAT },
                        onNavigateToAgents = { destination = Destination.AGENTS },
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.TOOLS -> ToolsHubScreen(
                        viewModel = viewModel,
                        onWork = { workSection = it; destination = Destination.STUDIO },
                        onChat = { destination = Destination.CHAT },
                        onControl = { destination = Destination.CONTROL },
                        onMore = { showMoreSheet = true },
                        onOperations = { destination = Destination.OPERATIONS },
                        onOpen = { target ->
                            returnToHub = true
                            destination = when (target) {
                                HubTarget.BRAIN -> Destination.MATRIX
                                HubTarget.CHAT -> Destination.CHAT
                                HubTarget.WORK -> Destination.STUDIO
                                HubTarget.PINE -> { pineDraftId = null; Destination.PINE }
                                HubTarget.PERSONAS -> Destination.AGENTS
                                HubTarget.SIGNALS -> Destination.SIGNALS
                                HubTarget.VISION -> Destination.VISION
                                HubTarget.NEWS -> Destination.NEWS
                                HubTarget.CONTROL -> Destination.CONTROL
                                HubTarget.OPERATIONS -> Destination.OPERATIONS
                                HubTarget.DATA -> Destination.DATA
                                HubTarget.TERMINAL -> Destination.TERMINAL
                            }
                        }
                    )
                    Destination.OPERATIONS -> OperationsScreen(viewModel,
                        onBack = { destination = Destination.TOOLS },
                        onWork = { workSection = it; destination = Destination.STUDIO })
                    Destination.STUDIO -> ProjectStudioScreen(
                        initialSection = workSection,
                        onChat = { destination = Destination.CHAT },
                        onPine = { pineDraftId = null; destination = Destination.PINE },
                        projectTeamRun = viewModel.projectTeamRun.collectAsState().value,
                        projectTeamInvalid = viewModel.projectTeamInvalid.collectAsState().value,
                        onDiscardInvalidTeam = viewModel::discardInvalidProjectTeam,
                        projectTeamMessage = viewModel.projectTeamMessage.collectAsState().value,
                        onPrepareTeam = viewModel::prepareProjectTeam,
                        onApproveTeam = viewModel::approveProjectTeam,
                        onCancelTeam = viewModel::cancelProjectTeam,
                        onPlanWithAi = { prompt ->
                            val submitted = viewModel.sendChat(prompt)
                            if (submitted) destination = Destination.CHAT
                            submitted
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.NEWS -> NewsIntelligenceScreen(
                        viewModel = viewModel,
                        onNavigateToChat = { destination = Destination.CHAT },
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.CHAT -> ProviderChatScreen(
                        viewModel = viewModel,
                        onWork = { destination = Destination.STUDIO },
                        onHub = { destination = Destination.TOOLS },
                        onSmartCombo = { destination = Destination.CONTROL },
                        onOpenPine = { source -> importPine(source, chart = false) },
                        onPreviewPine = { source -> importPine(source, chart = true) },
                        pineUpdate = pineRevisionTarget?.let { (targetId, targetName) ->
                            PineUpdateTarget(targetName) { source ->
                                    try {
                                        val store = PineStudioStore(context)
                                        val current = store.list().firstOrNull { it.id == targetId } ?: error("Draft unavailable.")
                                        store.update(targetId, code = ai.drfx.maximus.matrixai.studio.PineSourceImport
                                            .validateReplacement(current.code, source))
                                        pineDraftId = targetId
                                        pineOpenChart = true
                                        destination = Destination.PINE
                                        Toast.makeText(context, "Draft updated. The previous code is kept in Versions.", Toast.LENGTH_SHORT).show()
                                    } catch (error: Exception) {
                                        Toast.makeText(context, error.message?.takeIf { it.startsWith("This reply") }
                                            ?: "Draft could not be updated. Use Open in Pine Studio instead.", Toast.LENGTH_LONG).show()
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.AGENTS -> AgentsScreen(
                        viewModel = viewModel,
                        onChat = { destination = Destination.CHAT },
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.PINE -> PineStudioScreen(
                        initialDraftId = pineDraftId,
                        openChart = pineOpenChart,
                        onChartOpened = { pineOpenChart = false },
                        onRevisionRequested = { pineRevisionDraftId = it },
                        onBack = { destination = Destination.STUDIO },
                        onAskAi = { prompt ->
                            val submitted = viewModel.sendChat(prompt)
                            if (submitted) destination = Destination.CHAT
                            submitted
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.DATA -> DataCenterScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.CONTROL -> ControlHubScreen(
                        viewModel = viewModel,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        selectedLanguage = selectedLanguage,
                        onLanguageChange = onLanguageChange,
                        onHub = { destination = Destination.TOOLS },
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.TERMINAL -> TerminalConsoleScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet for "More Workspaces"
    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            stringResource(R.string.workspaces_title),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            stringResource(R.string.workspaces_subtitle),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showMoreSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close sheet")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                WorkspaceItemRow(
                    title = stringResource(R.string.nav_signals),
                    subtitle = stringResource(R.string.brain_graph_action_signals),
                    icon = Icons.Default.Bolt,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isSelected = destination == Destination.SIGNALS,
                    onClick = { destination = Destination.SIGNALS; showMoreSheet = false }
                )
                WorkspaceItemRow(
                    title = stringResource(R.string.nav_vision),
                    subtitle = stringResource(R.string.brain_graph_action_vision),
                    icon = Icons.Default.Visibility,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    isSelected = destination == Destination.VISION,
                    onClick = { destination = Destination.VISION; showMoreSheet = false }
                )

                // Workspace Cards
                WorkspaceItemRow(
                    title = stringResource(R.string.workspaces_chat_title),
                    subtitle = stringResource(R.string.workspaces_chat_desc),
                    icon = Icons.AutoMirrored.Filled.Chat,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isSelected = destination == Destination.CHAT,
                    onClick = {
                        destination = Destination.CHAT
                        showMoreSheet = false
                    }
                )

                WorkspaceItemRow(
                    title = stringResource(R.string.workspaces_news_title),
                    subtitle = stringResource(R.string.workspaces_news_desc),
                    icon = Icons.Default.Article,
                    accentColor = Color(0xFFE4B34D),
                    isSelected = destination == Destination.NEWS,
                    onClick = {
                        destination = Destination.NEWS
                        showMoreSheet = false
                    }
                )

                WorkspaceItemRow(
                    title = stringResource(R.string.workspaces_persona_title),
                    subtitle = stringResource(R.string.workspaces_persona_desc),
                    icon = Icons.Default.Person,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isSelected = destination == Destination.AGENTS,
                    onClick = {
                        destination = Destination.AGENTS
                        showMoreSheet = false
                    }
                )

                WorkspaceItemRow(
                    title = stringResource(R.string.workspaces_data_title),
                    subtitle = stringResource(R.string.workspaces_data_desc),
                    icon = Icons.Default.Storage,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isSelected = destination == Destination.DATA,
                    onClick = {
                        destination = Destination.DATA
                        showMoreSheet = false
                    }
                )

                WorkspaceItemRow(
                    title = stringResource(R.string.term_tab_events),
                    subtitle = stringResource(R.string.term_subtitle),
                    icon = Icons.Default.Terminal,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isSelected = destination == Destination.TERMINAL,
                    onClick = {
                        destination = Destination.TERMINAL
                        showMoreSheet = false
                    }
                )

                WorkspaceItemRow(
                    title = stringResource(R.string.workspaces_control_title),
                    subtitle = stringResource(R.string.workspaces_control_desc),
                    icon = Icons.Default.Settings,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isSelected = destination == Destination.CONTROL,
                    onClick = {
                        destination = Destination.CONTROL
                        showMoreSheet = false
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkspaceItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.2.dp, accentColor) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = .18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "ACTIVE",
                                color = accentColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
