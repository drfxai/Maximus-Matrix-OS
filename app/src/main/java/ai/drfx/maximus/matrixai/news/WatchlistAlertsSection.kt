package ai.drfx.maximus.matrixai.news

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.ui.MatrixViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WatchlistAlertsSection(
    viewModel: MatrixViewModel,
    onNavigateToChat: () -> Unit = {},
    onOpenSettingsDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notifService = viewModel.notificationService
    val activeWatchlist by notifService.activeWatchlist.collectAsState()
    val recentAlerts by notifService.recentAlerts.collectAsState()
    val isScanning by viewModel.isScanningAlerts.collectAsState()
    val notifsEnabled by notifService.notificationsEnabled.collectAsState()

    // Notification permission requester (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "Real-time market notifications enabled", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C101C)),
        border = BorderStroke(1.2.dp, Brush.horizontalGradient(listOf(Color(0xFFB388FF), Color(0xFF00E5FF)))),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Watchlist Alerts Title + Scan Now Action + Config Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF23153C),
                        border = BorderStroke(1.dp, Color(0xFFB388FF).copy(alpha = 0.6f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Radar,
                                contentDescription = null,
                                tint = Color(0xFFB388FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Watchlist Alert Hub",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            // Live Pulse Indicator
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isScanning) Color(0xFFFF9100) else Color(0xFF6B7280))
                            )
                        }
                        Text(
                            "Alerts unavailable: no verified market trigger configured",
                            fontSize = 10.5.sp,
                            color = Color(0xFF8B9CB5)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenSettingsDialog,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Configure Watchlist", tint = Color(0xFFC084FC), modifier = Modifier.size(18.dp))
                    }

                    // Scan Now Button
                    FilledTonalButton(
                        onClick = { viewModel.scanWatchlistAlertsNow() },
                        enabled = !isScanning,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E1738),
                            contentColor = Color(0xFFB388FF)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color(0xFFB388FF),
                                strokeWidth = 1.5.dp
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Scanning...", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Scan AI", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Watchlist Asset Chips (Interactive: Tap to toggle monitoring)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MONITORED ASSETS (${activeWatchlist.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C8BA1),
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        "Tap symbol to toggle",
                        fontSize = 9.5.sp,
                        color = Color(0xFF6B7280)
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(notifService.availableWatchlistAssets) { item ->
                        val isMonitored = activeWatchlist.contains(item.symbol)
                        val accent = when (item.category) {
                            NewsCategory.GOLD -> Color(0xFFFFD54F)
                            NewsCategory.CRYPTO -> Color(0xFFFF9100)
                            NewsCategory.FOREX -> Color(0xFF60A5FA)
                            NewsCategory.CENTRAL_BANKS -> Color(0xFFEC407A)
                            else -> Color(0xFF00E5FF)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMonitored) accent.copy(alpha = 0.18f) else Color(0xFF101420),
                            border = BorderStroke(1.dp, if (isMonitored) accent.copy(alpha = 0.7f) else Color(0xFF222B3D)),
                            modifier = Modifier.clickable {
                                viewModel.toggleWatchlistSymbol(item.symbol)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isMonitored) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(10.dp))
                                }
                                Text(
                                    item.symbol,
                                    fontSize = 11.sp,
                                    fontWeight = if (isMonitored) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isMonitored) Color.White else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }

            // Real-Time Alerts Feed
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LATEST MARKET-MOVING ALERTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C8BA1),
                        letterSpacing = 0.5.sp
                    )

                    if (recentAlerts.isNotEmpty()) {
                        Text(
                            "${recentAlerts.size} triggered",
                            fontSize = 9.5.sp,
                            color = Color(0xFFB388FF),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (recentAlerts.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF090D18),
                        border = BorderStroke(1.dp, Color(0xFF1E283D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(24.dp))
                            Text("No major market-moving alerts detected yet", fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                            Text("No verified data-driven alerts available", fontSize = 10.sp, color = Color(0xFF6B7280))
                        }
                    }
                } else {
                    recentAlerts.take(3).forEach { alert ->
                        WatchlistAlertCard(
                            alert = alert,
                            onAskChat = {
                                val prompt = "Alert on ${alert.assetSymbol}: '${alert.headline}'. Analysis: ${alert.aiReasoning}. How should I manage risk and position trades right now?"
                                viewModel.sendChat(prompt)
                                onNavigateToChat()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WatchlistAlertCard(
    alert: MarketAlertNotification,
    onAskChat: () -> Unit
) {
    val urgencyColor = Color(alert.urgency.colorHex)
    val timeAgoStr = remember(alert.timestampMs) {
        val diff = System.currentTimeMillis() - alert.timestampMs
        val mins = (diff / 60000).coerceAtLeast(1)
        if (mins < 60) "${mins}m ago" else "${mins / 60}h ago"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF090E1A),
        border = BorderStroke(1.dp, urgencyColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row: Asset Tag + Urgency Pill + Time Ago
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E283D),
                        border = BorderStroke(1.dp, Color(0xFF2C3B59))
                    ) {
                        Text(
                            text = alert.assetSymbol,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = urgencyColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, urgencyColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = alert.urgency.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = urgencyColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        timeAgoStr,
                        fontSize = 9.5.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            // Headline
            Text(
                text = alert.headline,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 16.sp
            )

            // AI Reasoning
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF121828),
                border = BorderStroke(1.dp, Color(0xFF1E263C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(13.dp))
                        Text("AI Volatility Impact Analysis", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC084FC))
                    }
                    Text(
                        text = alert.aiReasoning,
                        fontSize = 10.sp,
                        color = Color(0xFFD1D5DB),
                        lineHeight = 14.sp
                    )
                }
            }

            // Footer Row: Volatility Forecast + Sentiment + Ask Maximus Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Volatility: ${alert.estimatedVolatilityPips}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFFD54F)
                    )
                    Text("•", fontSize = 9.sp, color = Color(0xFF4B5563))
                    Text(
                        "${alert.sentiment.symbol} ${alert.sentiment.displayName}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alert.sentiment == MarketSentiment.BULLISH) Color(0xFF00E676) else if (alert.sentiment == MarketSentiment.BEARISH) Color(0xFFFF5252) else Color(0xFF94A3B8)
                    )
                }

                TextButton(
                    onClick = onAskChat,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color(0xFFB388FF), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Ask AI", fontSize = 10.sp, color = Color(0xFFB388FF), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WatchlistAlertSettingsDialog(
    viewModel: MatrixViewModel,
    onDismiss: () -> Unit
) {
    val notifService = viewModel.notificationService
    val activeWatchlist by notifService.activeWatchlist.collectAsState()
    val notifsEnabled by notifService.notificationsEnabled.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFFB388FF))
                Text("Watchlist Alert Settings", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // System notification toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("System Push Notifications", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Send real-time alerts to Android notification tray", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                    Switch(
                        checked = notifsEnabled,
                        onCheckedChange = { viewModel.setMarketAlertsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF7C4DFF)
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFF1E283D))

                Text("Manage Watchlist Symbols (${activeWatchlist.size} active)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB388FF))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(notifService.availableWatchlistAssets) { item ->
                        val isChecked = activeWatchlist.contains(item.symbol)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isChecked) Color(0xFF161E30) else Color(0xFF0F1422),
                            border = BorderStroke(1.dp, if (isChecked) Color(0xFF384E75) else Color(0xFF1B2436)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleWatchlistSymbol(item.symbol) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.symbol, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                    Text(item.name, fontSize = 9.5.sp, color = Color(0xFF94A3B8))
                                }

                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { viewModel.toggleWatchlistSymbol(item.symbol) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF7C4DFF))
                                )
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xFF0B101E)
    )
}
