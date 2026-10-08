package ai.drfx.maximus.matrixai.signals

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val WebhookGreen = Color(0xFF00E676)
private val WebhookGreenBg = Color(0x1F00E676)
private val WebhookRed = Color(0xFFFF1744)
private val WebhookRedBg = Color(0x1FFF1744)
private val WebhookCyan = Color(0xFF00E5FF)
private val WebhookAmber = Color(0xFFFF9100)
private val WebhookPurple = Color(0xFFB388FF)
private val WebhookDarkSurface = Color(0xFF0C131D)
private val WebhookCardBg = Color(0xFF131D2A)
private val WebhookBorder = Color(0xFF1E2E42)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalWebhookHubSheet(
    webhookServer: SignalWebhookServer,
    notificationService: SignalNotificationService? = webhookServer.notificationService,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val serverState by webhookServer.serverState.collectAsState()
    val logs by webhookServer.logs.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Live Logs", "Test Simulator", "TradingView", "Cloudflare", "Alerts & Audio")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = WebhookDarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF4A5D73)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (serverState.isRunning) WebhookGreenBg else WebhookRedBg)
                            .border(1.dp, if (serverState.isRunning) WebhookGreen else WebhookRed, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (serverState.isRunning) WebhookGreen else WebhookRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "DEDICATED WEBHOOK GATEWAY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (serverState.isRunning) WebhookGreen else WebhookRed)
                            )
                        }
                        Text(
                            if (serverState.isRunning) "Listening on Port ${serverState.port} · Ready for Signals" else "Gateway Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (serverState.isRunning) WebhookGreen else Color(0xFF90A4AE)
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { webhookServer.toggleServer() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (serverState.isRunning) Color(0x33FF1744) else Color(0x3300E676),
                        contentColor = if (serverState.isRunning) WebhookRed else WebhookGreen
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (serverState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (serverState.isRunning) "Stop" else "Start",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Webhook URL Showcase Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WebhookCardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, WebhookBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "WEBHOOK INGESTION ENDPOINTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = WebhookCyan,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (serverState.requireSecret) WebhookAmber else Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (serverState.requireSecret) "Secret Enabled" else "Open Access",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (serverState.requireSecret) WebhookAmber else Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Local LAN URL
                    WebhookUrlRow(
                        label = "Local WiFi LAN",
                        url = webhookServer.getLocalWebhookUrl(),
                        onCopy = { copyToClipboard(context, webhookServer.getLocalWebhookUrl(), "Local Webhook URL") }
                    )

                    Spacer(Modifier.height(6.dp))

                    // Emulator URL
                    WebhookUrlRow(
                        label = "Android Emulator",
                        url = webhookServer.getEmulatorWebhookUrl(),
                        onCopy = { copyToClipboard(context, webhookServer.getEmulatorWebhookUrl(), "Emulator Webhook URL") }
                    )

                    Spacer(Modifier.height(8.dp))

                    // Secret Key Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0A0F16))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Secret Token:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                serverState.secretToken,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = WebhookAmber,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, serverState.secretToken, "Secret Token")
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, tint = WebhookAmber, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    val newToken = "maximus_sec_" + UUID.randomUUID().toString().take(8)
                                    webhookServer.setSecretToken(newToken)
                                    Toast.makeText(context, "New secret token generated", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tab Navigation
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = WebhookCyan,
                divider = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                color = if (selectedTab == index) WebhookCyan else Color(0xFF90A4AE)
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Tab Content
            when (selectedTab) {
                0 -> WebhookLogsTab(logs = logs, serverState = serverState, onClearLogs = { webhookServer.clearLogs() })
                1 -> WebhookTestSimulatorTab(webhookServer = webhookServer)
                2 -> WebhookTradingViewGuideTab(webhookServer = webhookServer)
                3 -> WebhookCloudflareGuideTab(webhookServer = webhookServer)
                4 -> WebhookAlertsTab(notificationService = notificationService)
            }
        }
    }
}

@Composable
private fun WebhookUrlRow(
    label: String,
    url: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF090E15))
            .border(1.dp, Color(0xFF1E2B3C), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B), fontSize = 10.sp)
            Text(
                url,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.ContentCopy, null, tint = WebhookCyan, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun WebhookLogsTab(
    logs: List<WebhookLogEntry>,
    serverState: WebhookServerState,
    onClearLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Stats Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricPill("Total Hits", serverState.totalReceived.toString(), Color.White, Modifier.weight(1f))
            MetricPill("Success", serverState.totalSuccess.toString(), WebhookGreen, Modifier.weight(1f))
            MetricPill("Errors", serverState.totalErrors.toString(), if (serverState.totalErrors > 0) WebhookRed else Color(0xFF90A4AE), Modifier.weight(1f))
            if (logs.isNotEmpty()) {
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFF90A4AE), modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = Color(0xFF4A5D73),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No Webhook Signals Ingested Yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF90A4AE),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Send a signal via TradingView or use the Test Simulator tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    WebhookLogCard(log)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun MetricPill(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = WebhookCardBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WebhookBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B), fontSize = 10.sp)
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun WebhookLogCard(log: WebhookLogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val isSuccess = log.statusCode in 200..299
    val timeStr = remember(log.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(log.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = WebhookCardBg),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isSuccess) Color(0x3300E676) else Color(0x33FF1744))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSuccess) WebhookGreenBg else WebhookRedBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${log.statusCode} ${log.method}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSuccess) WebhookGreen else WebhookRed,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        log.symbol ?: log.path,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (log.direction != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (log.direction == "BUY") WebhookGreenBg else WebhookRedBg)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                log.direction,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = if (log.direction == "BUY") WebhookGreen else WebhookRed,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Text(
                    timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                log.statusMessage,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSuccess) Color(0xFF81C784) else Color(0xFFE57373),
                fontSize = 11.sp
            )

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = WebhookBorder)
                Spacer(Modifier.height(8.dp))

                Text("Client IP: ${log.sourceIp}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF90A4AE))
                if (log.parsedSignalId != null) {
                    Text("Room Signal ID: ${log.parsedSignalId}", style = MaterialTheme.typography.labelSmall, color = WebhookCyan)
                }

                Spacer(Modifier.height(6.dp))
                Text("Raw Payload Ingested:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF090E15))
                        .padding(8.dp)
                ) {
                    Text(
                        log.rawPayload,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WebhookTestSimulatorTab(webhookServer: SignalWebhookServer) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val testPresets = listOf(
        "TradingView BTC Buy" to """{
  "ticker": "BTCUSDT",
  "action": "BUY",
  "price": 68420.50,
  "sl": 67100.00,
  "tp1": 69800.00,
  "tp2": 71500.00,
  "timeframe": "15m",
  "strategy": "TradingView SuperTrend Pro Matrix",
  "confluence": "Bullish Order Block + 200 EMA Retest",
  "secret": "${webhookServer.serverState.collectAsState().value.secretToken}"
}""",
        "TradingView Gold Short" to """{
  "ticker": "XAUUSD",
  "action": "SELL",
  "price": 2648.80,
  "sl": 2662.00,
  "tp1": 2628.00,
  "tp2": 2605.00,
  "timeframe": "1H",
  "strategy": "Maximus Liquidity Sweep Scalper",
  "confluence": "Asian High sweep, Bearish fair value gap",
  "secret": "${webhookServer.serverState.collectAsState().value.secretToken}"
}""",
        "Cloudflare Worker Relay" to """{
  "source": "cloudflare",
  "worker": "maximus-signal-relay-edge",
  "signal": {
    "symbol": "NVDA",
    "direction": "BUY",
    "price": 128.40,
    "sl": 123.50,
    "tp1": 136.00,
    "tp2": 142.50,
    "timeframe": "4H",
    "strategy": "Cloudflare Relayed Breakout Alert",
    "confluence": "Volume expansion above resistance, earnings momentum",
    "agent": "Cloudflare Worker Gateway"
  }
}"""
    )

    var selectedPresetIndex by remember { mutableIntStateOf(0) }
    var customPayload by remember { mutableStateOf(testPresets[0].second) }
    var dispatchStatus by remember { mutableStateOf<String?>(null) }
    var isSuccessStatus by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "SIMULATE INCOMING WEBHOOKS",
                style = MaterialTheme.typography.labelSmall,
                color = WebhookCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                "Test the ingestion pipeline immediately without external network setup. Signals will parse, validate, and persist directly into Room DB.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF90A4AE),
                fontSize = 12.sp
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                testPresets.forEachIndexed { index, pair ->
                    FilterChip(
                        selected = selectedPresetIndex == index,
                        onClick = {
                            selectedPresetIndex = index
                            customPayload = pair.second
                            dispatchStatus = null
                        },
                        label = { Text(pair.first, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0x3300E5FF),
                            selectedLabelColor = WebhookCyan,
                            containerColor = WebhookCardBg,
                            labelColor = Color(0xFF90A4AE)
                        )
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = customPayload,
                onValueChange = { customPayload = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WebhookCyan,
                    unfocusedBorderColor = WebhookBorder,
                    focusedContainerColor = Color(0xFF090E15),
                    unfocusedContainerColor = Color(0xFF090E15),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFE2E8F0)
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }

        item {
            Button(
                onClick = {
                    coroutineScope.launch {
                        val source = testPresets.getOrNull(selectedPresetIndex)?.first ?: "Simulator"
                        val result = webhookServer.dispatchTestWebhook(customPayload, source)
                        when (result) {
                            is WebhookParseResult.Success -> {
                                isSuccessStatus = true
                                dispatchStatus = "✓ Ingested ${result.signal.direction} ${result.signal.symbol} at ${result.signal.entryPrice} into Room DB (ID: ${result.signal.id})"
                                Toast.makeText(context, "Signal Ingested & Saved to Room DB!", Toast.LENGTH_SHORT).show()
                            }
                            is WebhookParseResult.Failure -> {
                                isSuccessStatus = false
                                dispatchStatus = "✕ Ingestion Failed: ${result.errorMessage}"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WebhookGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dispatch Test Webhook Payload", fontWeight = FontWeight.Bold)
            }
        }

        if (dispatchStatus != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccessStatus) WebhookGreenBg else WebhookRedBg
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSuccessStatus) WebhookGreen else WebhookRed)
                ) {
                    Text(
                        dispatchStatus ?: "",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSuccessStatus) WebhookGreen else WebhookRed,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun WebhookTradingViewGuideTab(webhookServer: SignalWebhookServer) {
    val context = LocalContext.current
    val secret = webhookServer.serverState.collectAsState().value.secretToken

    val tradingViewTemplate = """{
  "ticker": "{{ticker}}",
  "action": "{{strategy.order.action}}",
  "price": {{close}},
  "sl": {{strategy.position_avg_price * 0.985}},
  "tp1": {{strategy.position_avg_price * 1.03}},
  "tp2": {{strategy.position_avg_price * 1.06}},
  "timeframe": "{{interval}}",
  "strategy": "TradingView Matrix Alert",
  "confluence": "Pine Script strategy condition met",
  "secret": "$secret"
}"""

    val pineScriptSnippet = """// Paste in your TradingView Pine Script strategy:
if (buyCondition)
    strategy.entry("Long", strategy.long)
    alert('{"ticker":"' + syminfo.ticker + '","action":"BUY","price":' + str.tostring(close) + ',"sl":' + str.tostring(close * 0.985) + ',"tp1":' + str.tostring(close * 1.03) + ',"secret":"$secret"}', alert.freq_once_per_bar_close)"""

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "TRADINGVIEW INTEGRATION RECIPE",
                style = MaterialTheme.typography.labelSmall,
                color = WebhookCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Configure TradingView alerts to trigger instant signals into your Maximus Matrix trading app via HTTP Webhook.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF90A4AE),
                fontSize = 12.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WebhookCardBg),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WebhookBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("1. Alert Configuration Steps", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    GuideStepItem("1", "On your TradingView chart, click the Alert icon (or press Alt + A).")
                    GuideStepItem("2", "Select your Condition (Indicator, Strategy, or Price Crossing).")
                    GuideStepItem("3", "In the Notifications tab, check 'Webhook URL'.")
                    GuideStepItem("4", "Paste your Webhook URL (from the header above or via Cloudflare Tunnel).")
                    GuideStepItem("5", "In the Message tab, paste the JSON payload template below.")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WebhookCardBg),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WebhookBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TradingView Alert JSON Body", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { copyToClipboard(context, tradingViewTemplate, "TradingView JSON Template") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = WebhookCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF090E15))
                            .padding(8.dp)
                    ) {
                        Text(
                            tradingViewTemplate,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = WebhookAmber,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WebhookCardBg),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WebhookBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pine Script Code Snippet", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { copyToClipboard(context, pineScriptSnippet, "Pine Script Snippet") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = WebhookCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF090E15))
                            .padding(8.dp)
                    ) {
                        Text(
                            pineScriptSnippet,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = WebhookPurple,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun WebhookCloudflareGuideTab(webhookServer: SignalWebhookServer) {
    val context = LocalContext.current
    val secret = webhookServer.serverState.collectAsState().value.secretToken

    val workerCode = """/**
 * Cloudflare Worker: Maximus Signals Webhook Gateway
 * Deploy on Cloudflare Workers (Free Tier) to receive external HTTPS alerts
 * and forward them into your Maximus Matrix Android app.
 */
export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return new Response(JSON.stringify({ error: "Only POST accepted" }), {
        status: 405,
        headers: { "Content-Type": "application/json" }
      });
    }

    try {
      const alertData = await request.json();
      
      // Standardize payload for Maximus Matrix App
      const payload = {
        source: "cloudflare",
        worker: "maximus-signals-relay-edge",
        ticker: alertData.ticker || alertData.symbol || "BTCUSDT",
        action: (alertData.action || alertData.direction || "BUY").toUpperCase(),
        price: parseFloat(alertData.price || alertData.close || 0),
        sl: parseFloat(alertData.sl || alertData.stop_loss || 0),
        tp1: parseFloat(alertData.tp1 || alertData.take_profit_1 || 0),
        tp2: parseFloat(alertData.tp2 || alertData.take_profit_2 || 0),
        timeframe: alertData.timeframe || "15m",
        strategy: alertData.strategy || "Cloudflare Worker Relayed Alert",
        confluence: alertData.confluence || "Cloudflare Edge Ingestion",
        secret: "$secret"
      };

      // Forward to Maximus Android App via Cloudflare Tunnel or Public IP
      // Replace TARGET_URL with your Cloudflare Tunnel URL
      const TARGET_URL = env.TARGET_WEBHOOK_URL || "${webhookServer.getLocalWebhookUrl()}";
      
      const response = await fetch(TARGET_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-webhook-secret": "$secret",
          "cf-worker": "maximus-signals-gateway"
        },
        body: JSON.stringify(payload)
      });

      const responseText = await response.text();
      return new Response(responseText, {
        status: response.status,
        headers: { "Content-Type": "application/json" }
      });
    } catch (err) {
      return new Response(JSON.stringify({ error: err.message }), {
        status: 500,
        headers: { "Content-Type": "application/json" }
      });
    }
  }
};"""

    val tunnelCommand = "cloudflared tunnel --url http://localhost:8080"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "CLOUDFLARE EDGE WORKER & TUNNEL",
                style = MaterialTheme.typography.labelSmall,
                color = WebhookCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Deploy a zero-cost Cloudflare Worker to act as an HTTPS bridge for TradingView, or expose your local device securely using Cloudflare Tunnel.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF90A4AE),
                fontSize = 12.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WebhookCardBg),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WebhookBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cloudflare Tunnel (cloudflared)", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { copyToClipboard(context, tunnelCommand, "Tunnel Command") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = WebhookCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Run on your host machine to get a public HTTPS URL (e.g. https://xyz.trycloudflare.com) that routes straight to this app:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF90A4AE),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF090E15))
                            .padding(8.dp)
                    ) {
                        Text(
                            tunnelCommand,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = WebhookGreen,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WebhookCardBg),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WebhookBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cloudflare Worker Script", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { copyToClipboard(context, workerCode, "Cloudflare Worker Code") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = WebhookCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF090E15))
                            .padding(8.dp)
                    ) {
                        Text(
                            workerCode,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF81D4FA),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GuideStepItem(num: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0x3300E5FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(num, style = MaterialTheme.typography.labelSmall, color = WebhookCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCFD8DC), fontSize = 11.sp)
    }
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

@Composable
private fun WebhookAlertsTab(notificationService: SignalNotificationService?) {
    val context = LocalContext.current
    if (notificationService == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Signal Notification Service Initializing...",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
        return
    }

    val settings by notificationService.settings.collectAsState()
    val recentAlerts by notificationService.recentAlerts.collectAsState()
    val totalTriggered by notificationService.totalAlertsTriggered.collectAsState()

    var notifsEnabled by remember(settings) { mutableStateOf(settings.notificationsEnabled) }
    var soundEnabled by remember(settings) { mutableStateOf(settings.soundAlertsEnabled) }
    var vibrateEnabled by remember(settings) { mutableStateOf(settings.vibrateEnabled) }
    var minConfidence by remember(settings) { mutableIntStateOf(settings.minConfidenceThreshold) }

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            Toast.makeText(context, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Permission Card if on Android 13+ and not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x33FF9100),
                    border = BorderStroke(1.dp, WebhookAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Push Notifications Restricted",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WebhookAmber
                            )
                            Text(
                                "Android requires runtime permission to deliver lock screen and banner trade signals.",
                                fontSize = 11.sp,
                                color = Color(0xFFCFD8DC)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            colors = ButtonDefaults.buttonColors(containerColor = WebhookAmber),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Grant", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Service Header Card
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WebhookCardBg,
                border = BorderStroke(1.dp, WebhookBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(WebhookGreenBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.NotificationsActive, null, tint = WebhookGreen, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "High-Confidence Notification Engine",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "Channel: high_confidence_trading_signals",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF90A4AE)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (notifsEnabled) WebhookGreenBg else Color(0x22FFFFFF)
                        ) {
                            Text(
                                if (notifsEnabled) "ACTIVE" else "MUTED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (notifsEnabled) WebhookGreen else Color.Gray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL TRIGGERED", fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            Text("$totalTriggered Alerts", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = WebhookGreen)
                        }
                        Column {
                            Text("TRIGGER THRESHOLD", fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            Text("≥$minConfidence% Probability", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = WebhookCyan)
                        }
                        Column {
                            Text("SOUND CHIME", fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            Text(if (soundEnabled) "Enabled" else "Muted", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (soundEnabled) WebhookCyan else Color.Gray)
                        }
                    }
                }
            }
        }

        // Confidence Threshold Setting
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WebhookCardBg,
                border = BorderStroke(1.dp, WebhookBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "FILTER THRESHOLD: PROBABILITY ≥ $minConfidence%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WebhookCyan,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "When signals arrive from TradingView or Cloudflare webhooks, push notifications and audio chimes trigger exclusively if the signal confidence meets or exceeds this threshold.",
                        fontSize = 11.sp,
                        color = Color(0xFF90A4AE)
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(70, 75, 80, 85, 90, 95).forEach { threshold ->
                            val selected = minConfidence == threshold
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) Color(0x3300E5FF) else Color(0xFF0D141E),
                                border = BorderStroke(1.dp, if (selected) WebhookCyan else WebhookBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        minConfidence = threshold
                                        notificationService.updateSettings(minConfidence = threshold)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$threshold%",
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) WebhookCyan else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Alert Toggles (Notifications, Sound, Vibration)
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WebhookCardBg,
                border = BorderStroke(1.dp, WebhookBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    // Push Notification Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, null, tint = if (notifsEnabled) WebhookGreen else Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("System Push Notifications", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Status bar alert with Entry, TP targets & Stop Loss", fontSize = 10.sp, color = Color(0xFF90A4AE))
                            }
                        }
                        Switch(
                            checked = notifsEnabled,
                            onCheckedChange = {
                                notifsEnabled = it
                                notificationService.updateSettings(enabled = it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WebhookGreen)
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = WebhookBorder)

                    // Sound Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, null, tint = if (soundEnabled) WebhookCyan else Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Audible Alert Chime", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Plays notification chime & system tone immediately", fontSize = 10.sp, color = Color(0xFF90A4AE))
                            }
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = {
                                soundEnabled = it
                                notificationService.updateSettings(sound = it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WebhookCyan)
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = WebhookBorder)

                    // Vibration Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Vibration, null, tint = if (vibrateEnabled) WebhookPurple else Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Haptic Vibration", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Custom multi-pulse tactile vibration", fontSize = 10.sp, color = Color(0xFF90A4AE))
                            }
                        }
                        Switch(
                            checked = vibrateEnabled,
                            onCheckedChange = {
                                vibrateEnabled = it
                                notificationService.updateSettings(vibrate = it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = WebhookPurple)
                        )
                    }
                }
            }
        }

        // Live Test Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val fired = notificationService.triggerTestAlert(confidence = 92)
                        Toast.makeText(
                            context,
                            if (fired) "⚡ High-Confidence Alert Fired! (Notification + Sound)" else "Alert simulated",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = WebhookGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Bolt, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Test Push + Sound", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        notificationService.playAudioAlert()
                        Toast.makeText(context, "🔊 Playing audio alert chime...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, WebhookCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WebhookCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play Sound Only", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        // Triggered Alerts History Feed
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TRIGGERED ALERTS HISTORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90A4AE),
                    letterSpacing = 1.sp
                )
                if (recentAlerts.isNotEmpty()) {
                    Text(
                        "Clear Log",
                        fontSize = 11.sp,
                        color = WebhookRed,
                        modifier = Modifier.clickable { notificationService.clearAlertHistory() }
                    )
                }
            }
        }

        if (recentAlerts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WebhookCardBg,
                    border = BorderStroke(1.dp, WebhookBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No high-confidence webhook alerts triggered yet.\nTap 'Test Push + Sound' above or send a webhook from TradingView.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            items(recentAlerts) { alert ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WebhookCardBg,
                    border = BorderStroke(1.dp, WebhookBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (alert.direction == "BUY") "🟢" else "🔴", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${alert.symbol} ${alert.direction}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "@ ${alert.entryPrice}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF81D4FA)
                                    )
                                }
                                Text(
                                    "${alert.source} • ${alert.formattedTime}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF90A4AE)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = WebhookGreenBg
                            ) {
                                Text(
                                    "${alert.winProbability}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WebhookGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (alert.soundPlayed) {
                                    Icon(Icons.Default.VolumeUp, null, tint = WebhookCyan, modifier = Modifier.size(11.dp))
                                }
                                if (alert.notificationPosted) {
                                    Spacer(Modifier.width(2.dp))
                                    Icon(Icons.Default.Notifications, null, tint = WebhookGreen, modifier = Modifier.size(11.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
