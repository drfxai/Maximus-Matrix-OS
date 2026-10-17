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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    CHAT("Chat"),
    AGENTS("Personas"),
    DATA("Data"),
    CONTROL("Control"),
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
    var showMoreSheet by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    BackHandler(enabled = destination != Destination.MATRIX && !isImeVisible) {
        destination = Destination.MATRIX
    }

    val isSubDestination = destination in listOf(
        Destination.CHAT,
        Destination.AGENTS,
        Destination.DATA,
        Destination.CONTROL,
        Destination.TERMINAL
    )

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
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    val primaryDestinations = listOf(
                        Triple(Destination.MATRIX, R.string.nav_brain, Icons.Default.Hub),
                        Triple(Destination.CHAT, R.string.nav_chat, Icons.AutoMirrored.Filled.Chat),
                        Triple(Destination.AGENTS, R.string.design_agents, Icons.Default.Groups),
                        Triple(Destination.STUDIO, R.string.nav_studio, Icons.Default.Code)
                    )
                    primaryDestinations.forEach { (target, label, icon) ->
                        val tag = when (target) {
                            Destination.MATRIX -> "nav_item_matrix"
                            Destination.CHAT -> "nav_item_chat"
                            Destination.AGENTS -> "nav_item_agents"
                            else -> "nav_item_studio"
                        }
                        NavigationBarItem(
                            selected = destination == target,
                            onClick = { destination = target },
                            icon = { Icon(icon, when (target) { Destination.MATRIX -> "Neural Brain"; Destination.STUDIO -> "Project Studio"; else -> stringResource(label) }) },
                            label = { Text(stringResource(label), maxLines = 1, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                            ),
                            modifier = Modifier.testTag(tag)
                        )
                    }
                    NavigationBarItem(
                        selected = destination !in primaryDestinations.map { it.first },
                        onClick = { showMoreSheet = true },
                        icon = { Icon(Icons.Default.Widgets, stringResource(R.string.nav_more)) },
                        label = { Text(stringResource(R.string.nav_more), fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_item_more")
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
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { fullWidth -> if (forward) fullWidth / 4 else -fullWidth / 4 }
                    ) + fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
                            targetOffsetX = { fullWidth -> if (forward) -fullWidth / 5 else fullWidth / 5 }
                        ) + fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 0.98f)
                    )
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
                    Destination.STUDIO -> ProjectStudioScreen(
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
                        modifier = Modifier.fillMaxSize()
                    )
                    Destination.AGENTS -> AgentsScreen(
                        viewModel = viewModel,
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
            containerColor = MaterialTheme.colorScheme.surface,
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
                    subtitle = stringResource(R.string.nav_signals),
                    icon = Icons.Default.Bolt,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isSelected = destination == Destination.SIGNALS,
                    onClick = { destination = Destination.SIGNALS; showMoreSheet = false }
                )
                WorkspaceItemRow(
                    title = stringResource(R.string.nav_vision),
                    subtitle = stringResource(R.string.nav_vision),
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
                    accentColor = Color(0xFFB388FF),
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
                    accentColor = Color(0xFF38C79B),
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
                    accentColor = Color(0xFFAAC56A),
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
                    accentColor = Color(0xFF5CF0BC),
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
                    accentColor = Color(0xFFD664A2),
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
private fun NavPulsingBeacon(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nav_beacon_anim")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nav_beacon_alpha"
    )

    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = beaconAlpha))
    )
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
    val infiniteTransition = rememberInfiniteTransition(label = "workspace_active_anim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_tag_pulse"
    )

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
                                .background(accentColor.copy(alpha = pulseAlpha))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "ACTIVE",
                                color = Color.White,
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
