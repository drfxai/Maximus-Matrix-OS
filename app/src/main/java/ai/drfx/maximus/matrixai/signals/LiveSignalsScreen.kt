package ai.drfx.maximus.matrixai.signals

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.database.entities.TradingSignalEntity
import ai.drfx.maximus.matrixai.ui.MatrixViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val SignalGreen = Color(0xFF00E676)
private val SignalGreenBg = Color(0x1F00E676)
private val SignalRed = Color(0xFFFF1744)
private val SignalRedBg = Color(0x1FFF1744)
private val SignalGold = Color(0xFFFFD600)
private val SignalCyan = Color(0xFF00E5FF)
private val SignalPurple = Color(0xFFB388FF)
private val SignalDarkCard = Color(0xFF0D131A)
private val SignalBorder = Color(0xFF1B2838)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSignalsScreen(
    viewModel: MatrixViewModel,
    onNavigateToChat: () -> Unit = {},
    onNavigateToVision: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val signalsService = viewModel.liveSignalsService

    val signals by signalsService.filteredSignals.collectAsState(initial = emptyList())
    val filter by signalsService.filter.collectAsState()
    val stats by signalsService.signalStats.collectAsState(initial = SignalStats())
    val isStreaming by signalsService.isStreaming.collectAsState()

    var selectedSignalForDetail by remember { mutableStateOf<TradingSignalEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showWebhookSheet by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }

    val webhookServer = signalsService.webhookServer
    val serverState by webhookServer.serverState.collectAsState()
    val notificationService = signalsService.notificationService
    val notifSettings by notificationService?.settings?.collectAsState() ?: remember { mutableStateOf(null) }

    // Listen for incoming live webhooks to show real-time visual feedback
    LaunchedEffect(Unit) {
        webhookServer.onSignalReceivedEvent.collect { newSig ->
            Toast.makeText(
                context,
                "⚡ Webhook Ingested: ${newSig.direction} ${newSig.symbol} @ ${newSig.entryPrice}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val assetClasses = listOf("ALL", "Crypto", "Forex", "Commodities", "Indices", "Equities")
    val statusFilters = listOf("ALL", "ACTIVE", "TP_HIT", "STOPPED_OUT")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveRadarPulsingBeacon(
                                color = if (isStreaming) SignalGreen else SignalGold
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "LIVE SIGNALS MATRIX",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Text(
                            "Autonomous Quant Engine · Room Verified",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Webhook Gateway Button
                        FilledTonalButton(
                            onClick = { showWebhookSheet = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (serverState.isRunning) Color(0x3300E676) else Color(0x22FFFFFF),
                                contentColor = if (serverState.isRunning) SignalGreen else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Bolt, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                if (serverState.isRunning) ":${serverState.port}" else "Webhook",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        // High-Confidence Push & Sound Alert Button
                        IconButton(
                            onClick = { showNotificationDialog = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = if (notifSettings?.notificationsEnabled == true) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                    contentDescription = "Push Notifications & Sound Alerts",
                                    tint = if (notifSettings?.notificationsEnabled == true) SignalGreen else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (notifSettings?.notificationsEnabled == true) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(SignalGreen)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(2.dp))

                        IconButton(
                            onClick = { signalsService.toggleStreaming() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                if (isStreaming) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Toggle Stream",
                                tint = if (isStreaming) SignalGreen else SignalGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    signalsService.resetDefaults()
                                    Toast.makeText(context, "Signals reset & refreshed from Room", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        }

                        FilledTonalButton(
                            onClick = { showCreateDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Search & Filter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filter.searchQuery,
                        onValueChange = { signalsService.setSearchQuery(it) },
                        placeholder = { Text("Search symbol (e.g. XAU/USD, BTC)...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (filter.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { signalsService.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )

                    Spacer(Modifier.width(6.dp))

                    FilterChip(
                        selected = filter.showOnlyWebhook,
                        onClick = { signalsService.toggleWebhookOnlyFilter() },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Bolt,
                                    null,
                                    tint = if (filter.showOnlyWebhook) SignalCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text("Webhooks", fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier.height(48.dp)
                    )

                    Spacer(Modifier.width(6.dp))

                    FilterChip(
                        selected = filter.showOnlyBookmarked,
                        onClick = { signalsService.toggleBookmarkedFilter() },
                        label = {
                            Icon(
                                if (filter.showOnlyBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (filter.showOnlyBookmarked) SignalGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.height(48.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Asset Class Horizontal Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(assetClasses) { asset ->
                        FilterChip(
                            selected = filter.assetClass.equals(asset, ignoreCase = true),
                            onClick = { signalsService.setAssetClass(asset) },
                            label = { Text(asset, fontSize = 11.sp) },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
        ) {
            // Live Metric Banner
            item {
                SignalMetricsCard(stats = stats)
            }

            // Status Filter Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    statusFilters.forEach { st ->
                        val isSelected = filter.status.equals(st, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { signalsService.setStatusFilter(st) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (st) {
                                    "ALL" -> "All"
                                    "ACTIVE" -> "Active"
                                    "TP_HIT" -> "TP Hit"
                                    "STOPPED_OUT" -> "SL"
                                    else -> st
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Signal List Items
            if (signals.isEmpty()) {
                item {
                    EmptySignalsPlaceholder(
                        hasFilter = filter.searchQuery.isNotBlank() || filter.assetClass != "ALL" || filter.showOnlyBookmarked,
                        onClearFilters = {
                            signalsService.setAssetClass("ALL")
                            signalsService.setStatusFilter("ALL")
                            signalsService.setSearchQuery("")
                        }
                    )
                }
            } else {
                items(signals, key = { it.id }) { signal ->
                    SignalCardItem(
                        signal = signal,
                        onBookmarkToggle = {
                            coroutineScope.launch {
                                signalsService.toggleBookmark(signal.id, signal.isBookmarked)
                            }
                        },
                        onDetailClick = { selectedSignalForDetail = signal },
                        onAskAiClick = {
                            viewModel.appendEvent(
                                ai.drfx.maximus.matrixai.agent.MatrixEvent(
                                    missionId = "live_signal_${signal.symbol}",
                                    type = ai.drfx.maximus.matrixai.agent.MatrixEventType.TOOL_STARTED,
                                    sourceNode = "signals_hub",
                                    message = "Analyzing trade setup for ${signal.symbol} (${signal.direction} @ ${signal.entryPrice})"
                                )
                            )
                            onNavigateToChat()
                        }
                    )
                }
            }
        }
    }

    // Signal Detail Bottom Sheet
    selectedSignalForDetail?.let { signal ->
        SignalDetailSheet(
            signal = signal,
            onDismiss = { selectedSignalForDetail = null },
            onNavigateToChat = onNavigateToChat,
            onNavigateToVision = onNavigateToVision,
            onDelete = {
                coroutineScope.launch {
                    signalsService.deleteSignal(signal.id)
                    selectedSignalForDetail = null
                    Toast.makeText(context, "Signal removed from Room", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Create Signal Dialog
    if (showCreateDialog) {
        CreateSignalDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { symbol, assetClass, direction, type, tf, entry, sl, tp1, tp2, rr, prob, notes ->
                coroutineScope.launch {
                    signalsService.createSignal(
                        symbol = symbol,
                        assetClass = assetClass,
                        direction = direction,
                        signalType = type,
                        timeframe = tf,
                        entryPrice = entry,
                        stopLoss = sl,
                        takeProfit1 = tp1,
                        takeProfit2 = tp2,
                        riskRewardRatio = rr,
                        winProbability = prob,
                        strategyName = "User Custom: $type Setup",
                        aiRationale = notes
                    )
                    showCreateDialog = false
                    Toast.makeText(context, "Saved to Room Database!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Notification & Sound Alert Configuration Dialog
    if (showNotificationDialog && notificationService != null) {
        SignalNotificationDialog(
            notificationService = notificationService,
            onDismiss = { showNotificationDialog = false }
        )
    }

    // Dedicated Webhook Gateway Hub Sheet (TradingView & Cloudflare)
    if (showWebhookSheet) {
        SignalWebhookHubSheet(
            webhookServer = webhookServer,
            notificationService = notificationService,
            onDismiss = { showWebhookSheet = false }
        )
    }
}

@Composable
private fun SignalMetricsCard(stats: SignalStats) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "MAXIMUS QUANT PERFORMANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    stats.netPipsOrPercent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SignalGreen
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumnItem(title = "Win Rate", value = "${stats.winRate}%", color = SignalGreen)
                MetricColumnItem(title = "Profit Factor", value = "${stats.profitFactor}", color = SignalCyan)
                MetricColumnItem(title = "Avg R:R", value = stats.averageRiskReward, color = SignalPurple)
                MetricColumnItem(title = "Active Sets", value = "${stats.activeSignals}", color = SignalGold)
            }
        }
    }
}

@Composable
private fun MetricColumnItem(title: String, value: String, color: Color) {
    Column {
        Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SignalCardItem(
    signal: TradingSignalEntity,
    onBookmarkToggle: () -> Unit,
    onDetailClick: () -> Unit,
    onAskAiClick: () -> Unit
) {
    val isBuy = signal.direction.uppercase() == "BUY"
    val accentColor = if (isBuy) SignalGreen else SignalRed
    val accentBg = if (isBuy) SignalGreenBg else SignalRedBg

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (signal.status == "ACTIVE") accentColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetailClick() }
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header Row: Symbol, Direction Badge, Timeframe, Status & Bookmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Direction badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentBg,
                        border = BorderStroke(1.dp, accentColor)
                    ) {
                        Text(
                            text = signal.direction,
                            color = accentColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = signal.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = signal.timeframe,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Webhook Source Badge
                    val isWebhookSignal = signal.authorAgent.contains("Webhook", ignoreCase = true) ||
                            signal.authorAgent.contains("TradingView", ignoreCase = true) ||
                            signal.authorAgent.contains("Cloudflare", ignoreCase = true) ||
                            signal.strategyName.contains("TradingView", ignoreCase = true) ||
                            signal.strategyName.contains("Cloudflare", ignoreCase = true)

                    if (isWebhookSignal) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0x2900E5FF),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Bolt, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    if (signal.authorAgent.contains("Cloudflare", ignoreCase = true)) "CLOUDFLARE" else "TRADINGVIEW",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge
                    val (statusText, statusColor) = when (signal.status) {
                        "ACTIVE" -> "ACTIVE" to SignalGreen
                        "TP1_HIT" -> "TP1 HIT ✓" to SignalCyan
                        "TP2_HIT" -> "TP2 HIT ✓" to SignalGreen
                        "STOPPED_OUT" -> "STOPPED OUT" to SignalRed
                        else -> signal.status to MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            if (signal.status == "ACTIVE") {
                                val pulseTrans = rememberInfiniteTransition(label = "signal_active_pulse")
                                val pAlpha by pulseTrans.animateFloat(
                                    initialValue = 0.35f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(700, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "active_dot_alpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SignalGreen.copy(alpha = pAlpha))
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = statusText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (signal.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (signal.isBookmarked) SignalGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Pricing Matrix
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceParamItem("Entry", signal.entryPrice.toString())
                PriceParamItem("Current", signal.currentPrice.toString(), isHighlight = true, highlightColor = accentColor)
                PriceParamItem("Stop Loss", signal.stopLoss.toString(), isHighlight = true, highlightColor = SignalRed)
                PriceParamItem("TP1", signal.takeProfit1.toString(), isHighlight = true, highlightColor = SignalGreen)
                if (signal.takeProfit2 > 0) {
                    PriceParamItem("TP2", signal.takeProfit2.toString(), isHighlight = true, highlightColor = SignalCyan)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Strategy & Confluences
            Text(
                text = signal.strategyName,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = signal.aiRationale,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(10.dp))

            // Card Footer: Win Probability & Quick AI Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = SignalGold, modifier = Modifier.size(14.dp))
                    Text(
                        "AI Win Prob: ${signal.winProbability}% · R:R ${signal.riskRewardRatio}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    OutlinedButton(
                        onClick = onAskAiClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ask AI", fontSize = 10.sp)
                    }

                    Spacer(Modifier.width(6.dp))

                    FilledTonalButton(
                        onClick = onDetailClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Details", fontSize = 10.sp)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceParamItem(label: String, value: String, isHighlight: Boolean = false, highlightColor: Color = Color.Unspecified) {
    Column {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isHighlight) highlightColor else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignalDetailSheet(
    signal: TradingSignalEntity,
    onDismiss: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToVision: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${signal.symbol} · ${signal.direction}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "${signal.strategyName} · ${signal.assetClass}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "P(${signal.winProbability}%)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Pricing Detail Grid
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Entry Price", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(signal.entryPrice.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stop Loss", fontSize = 12.sp, color = SignalRed)
                        Text(signal.stopLoss.toString(), fontWeight = FontWeight.Bold, color = SignalRed, fontSize = 12.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Take Profit 1", fontSize = 12.sp, color = SignalGreen)
                        Text(signal.takeProfit1.toString(), fontWeight = FontWeight.Bold, color = SignalGreen, fontSize = 12.sp)
                    }
                    if (signal.takeProfit2 > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Take Profit 2", fontSize = 12.sp, color = SignalCyan)
                            Text(signal.takeProfit2.toString(), fontWeight = FontWeight.Bold, color = SignalCyan, fontSize = 12.sp)
                        }
                    }
                    signal.takeProfit3?.let { tp3 ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Take Profit 3 (Runner)", fontSize = 12.sp, color = SignalPurple)
                            Text(tp3.toString(), fontWeight = FontWeight.Bold, color = SignalPurple, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Risk to Reward", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(signal.riskRewardRatio, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text("Confluence Factors", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                signal.confluenceFactors,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(14.dp))

            Text("AI Deep Rationale", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                signal.aiRationale,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(14.dp))

            Text("Originating Agent", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                signal.authorAgent,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToVision,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Visibility, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chart Vision", fontSize = 11.sp)
                }

                Button(
                    onClick = onNavigateToChat,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Consult AI", fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = SignalRed),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete Signal From Room", fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CreateSignalDialog(
    onDismiss: () -> Unit,
    onCreate: (
        symbol: String,
        assetClass: String,
        direction: String,
        type: String,
        tf: String,
        entry: Double,
        sl: Double,
        tp1: Double,
        tp2: Double,
        rr: String,
        prob: Int,
        notes: String
    ) -> Unit
) {
    var symbol by remember { mutableStateOf("XAU/USD") }
    var assetClass by remember { mutableStateOf("Commodities") }
    var direction by remember { mutableStateOf("BUY") }
    var type by remember { mutableStateOf("ORDER_BLOCK") }
    var timeframe by remember { mutableStateOf("M15") }
    var entry by remember { mutableStateOf("2865.00") }
    var sl by remember { mutableStateOf("2855.00") }
    var tp1 by remember { mutableStateOf("2880.00") }
    var tp2 by remember { mutableStateOf("2895.00") }
    var notes by remember { mutableStateOf("Breakout retest with institutional order flow confluence.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Real-Time Signal", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text("Symbol (e.g. BTC/USDT)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = direction,
                        onValueChange = { direction = it },
                        label = { Text("Direction (BUY/SELL)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeframe,
                        onValueChange = { timeframe = it },
                        label = { Text("Timeframe (M5/H1)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = entry,
                        onValueChange = { entry = it },
                        label = { Text("Entry Price") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sl,
                        onValueChange = { sl = it },
                        label = { Text("Stop Loss") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tp1,
                        onValueChange = { tp1 = it },
                        label = { Text("TP1") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tp2,
                        onValueChange = { tp2 = it },
                        label = { Text("TP2") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("AI Confluence & Rationale") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val e = entry.toDoubleOrNull() ?: 0.0
                val s = sl.toDoubleOrNull() ?: 0.0
                val t1 = tp1.toDoubleOrNull() ?: 0.0
                val t2 = tp2.toDoubleOrNull() ?: 0.0
                onCreate(symbol, assetClass, direction, type, timeframe, e, s, t1, t2, "1:3.0", 88, notes)
            }) {
                Text("Save to Room")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptySignalsPlaceholder(
    hasFilter: Boolean,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.SignalCellularAlt,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No signals matching current criteria",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (hasFilter) "Try broadening your filter criteria or reset." else "No signals currently in database.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            if (hasFilter) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onClearFilters) {
                    Text("Clear Filters")
                }
            }
        }
    }
}

@Composable
private fun LiveRadarPulsingBeacon(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beacon_radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_alpha"
    )

    Box(
        modifier = modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp * pulseScale)
                .clip(CircleShape)
                .background(color.copy(alpha = pulseAlpha))
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}
