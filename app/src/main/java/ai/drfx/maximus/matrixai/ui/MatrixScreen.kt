package ai.drfx.maximus.matrixai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.drfx.maximus.matrixai.news.NewsIntelligenceScreen
import ai.drfx.maximus.matrixai.signals.LiveSignalsScreen
import ai.drfx.maximus.matrixai.ui.theme.AppThemeMode

private enum class Destination(val label: String) {
    MATRIX("Matrix"),
    SIGNALS("Signals"),
    VISION("Vision"),
    NEWS("News"),
    CHAT("Chat"),
    AGENTS("Agents"),
    DATA("Data"),
    CONTROL("Control")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MatrixScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: MatrixViewModel = viewModel()
) {
    var destination by remember { mutableStateOf(Destination.MATRIX) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible

    BackHandler(enabled = destination != Destination.MATRIX) {
        destination = Destination.MATRIX
    }

    val isSubDestination = destination in listOf(
        Destination.CHAT,
        Destination.AGENTS,
        Destination.DATA,
        Destination.CONTROL
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
                    modifier = Modifier.testTag("main_navigation_bar"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    val isMatrixSelected = destination == Destination.MATRIX
                    val matrixScale by animateFloatAsState(
                        targetValue = if (isMatrixSelected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "matrix_scale"
                    )
                    NavigationBarItem(
                        selected = isMatrixSelected,
                        onClick = { destination = Destination.MATRIX },
                        icon = {
                            Box {
                                Icon(
                                    Icons.Default.Hub,
                                    contentDescription = "Neural Brain",
                                    modifier = Modifier.scale(matrixScale)
                                )
                                NavPulsingBeacon(
                                    color = Color(0xFF38C79B),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-2).dp)
                                )
                            }
                        },
                        label = { Text("Brain", fontWeight = if (isMatrixSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("nav_item_matrix")
                    )

                    val isSignalsSelected = destination == Destination.SIGNALS
                    val signalsScale by animateFloatAsState(
                        targetValue = if (isSignalsSelected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "signals_scale"
                    )
                    NavigationBarItem(
                        selected = isSignalsSelected,
                        onClick = { destination = Destination.SIGNALS },
                        icon = {
                            Box {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = "Live Signals",
                                    modifier = Modifier.scale(signalsScale)
                                )
                                // Animated glowing live indicator
                                NavPulsingBeacon(
                                    color = Color(0xFF00E676),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-2).dp)
                                )
                            }
                        },
                        label = { Text("Signals", fontWeight = if (isSignalsSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("nav_item_signals")
                    )

                    val isVisionSelected = destination == Destination.VISION
                    val visionScale by animateFloatAsState(
                        targetValue = if (isVisionSelected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "vision_scale"
                    )
                    NavigationBarItem(
                        selected = isVisionSelected,
                        onClick = { destination = Destination.VISION },
                        icon = {
                            Box {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = "Chart Vision",
                                    modifier = Modifier.scale(visionScale)
                                )
                                NavPulsingBeacon(
                                    color = Color(0xFF00E5FF),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-2).dp)
                                )
                            }
                        },
                        label = { Text("Vision", fontWeight = if (isVisionSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("nav_item_vision")
                    )

                    val isNewsSelected = destination == Destination.NEWS
                    val newsScale by animateFloatAsState(
                        targetValue = if (isNewsSelected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "news_scale"
                    )
                    NavigationBarItem(
                        selected = isNewsSelected,
                        onClick = { destination = Destination.NEWS },
                        icon = {
                            Icon(
                                Icons.Default.Article,
                                contentDescription = "News Intelligence",
                                modifier = Modifier.scale(newsScale)
                            )
                        },
                        label = { Text("News", fontWeight = if (isNewsSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("nav_item_news")
                    )

                    // 5th Destination: Contextual "More / Sub-Workspace" item
                    val moreIcon: ImageVector = when (destination) {
                        Destination.CHAT -> Icons.AutoMirrored.Filled.Chat
                        Destination.AGENTS -> Icons.Default.Person
                        Destination.DATA -> Icons.Default.Storage
                        Destination.CONTROL -> Icons.Default.Settings
                        else -> Icons.Default.Widgets
                    }
                    val moreLabel: String = when (destination) {
                        Destination.CHAT -> "Chat"
                        Destination.AGENTS -> "Agents"
                        Destination.DATA -> "Data"
                        Destination.CONTROL -> "Control"
                        else -> "More"
                    }
                    val moreScale by animateFloatAsState(
                        targetValue = if (isSubDestination) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "more_scale"
                    )

                    NavigationBarItem(
                        selected = isSubDestination,
                        onClick = { showMoreSheet = true },
                        icon = {
                            Icon(
                                moreIcon,
                                contentDescription = moreLabel,
                                modifier = Modifier.scale(moreScale)
                            )
                        },
                        label = { Text(moreLabel, fontWeight = if (isSubDestination) FontWeight.Bold else FontWeight.Normal) },
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
                            "SCALPER PRO WORKSPACES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            "Select an AI trading tool or engine",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showMoreSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close sheet")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Workspace Cards
                WorkspaceItemRow(
                    title = "Maximus AI Chat",
                    subtitle = "Conversational multi-model trader & risk copilot",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    accentColor = Color(0xFFB388FF),
                    isSelected = destination == Destination.CHAT,
                    onClick = {
                        destination = Destination.CHAT
                        showMoreSheet = false
                    }
                )

                WorkspaceItemRow(
                    title = "Agent Factory",
                    subtitle = "Autonomous quant agents, planners & validation lab",
                    icon = Icons.Default.Person,
                    accentColor = Color(0xFF38C79B),
                    isSelected = destination == Destination.AGENTS,
                    onClick = {
                        destination = Destination.AGENTS
                        showMoreSheet = false
                    }
                )

                WorkspaceItemRow(
                    title = "Data Center",
                    subtitle = "Company filings, documents & Matrix knowledge memory",
                    icon = Icons.Default.Storage,
                    accentColor = Color(0xFFAAC56A),
                    isSelected = destination == Destination.DATA,
                    onClick = {
                        destination = Destination.DATA
                        showMoreSheet = false
                    }
                )

                WorkspaceItemRow(
                    title = "Control Hub & FinOps",
                    subtitle = "Multi-provider AI gateway, tokens & appearance settings",
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
