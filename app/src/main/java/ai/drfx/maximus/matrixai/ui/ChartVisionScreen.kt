package ai.drfx.maximus.matrixai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.vision.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartVisionScreen(
    viewModel: MatrixViewModel,
    onNavigateToChat: () -> Unit = {},
    onNavigateToAgents: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.chartVisionState.collectAsState()
    val activeBitmap by viewModel.activeChartBitmap.collectAsState()
    val llmState by viewModel.llmState.collectAsState()

    var userNotes by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }

    // Dropdown state for Sample Charts
    var sampleMenuExpanded by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf(SampleChartPreset.BTC_ASCENDING_TRIANGLE) }

    // Expandable cards state (all open by default for rich display)
    var isMarketStructureExpanded by remember { mutableStateOf(true) }
    var isTradeSetupExpanded by remember { mutableStateOf(true) }
    var isPatternsExpanded by remember { mutableStateOf(true) }
    var isSrExpanded by remember { mutableStateOf(true) }
    var isMultiAssetExpanded by remember { mutableStateOf(true) }
    var isReportExpanded by remember { mutableStateOf(true) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            viewModel.setActiveChartBitmap(bmp)
            Toast.makeText(context, "Chart photo captured", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo picker launcher (Zero-permission Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = loadBitmapFromUri(context, uri)
            if (bmp != null) {
                viewModel.setActiveChartBitmap(bmp)
                Toast.makeText(context, "Screenshot loaded (${bmp.width}x${bmp.height})", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Unable to read chart image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Modern dark styling palette from design mockup (3.png)
    val bgDark = Color(0xFF090D14)
    val cardBg = Color(0xFF111726)
    val cardBorder = Color(0xFF1E283D)
    val primaryGreen = Color(0xFF00E676)
    val greenGlow = Color(0xFF0D281E)
    val textPrimary = Color(0xFFF0F4FC)
    val textSecondary = Color(0xFF8B9CB5)
    val darkPurpleGradient = Brush.horizontalGradient(
        listOf(Color(0xFF28183F), Color(0xFF1B1B36), Color(0xFF131D33))
    )

    // Compute effective analysis once in composable scope
    val successAnalysis = (state as? ChartVisionUiState.Success)?.analysis
    val analysis = successAnalysis ?: ChartVisionAnalysis()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(bgDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ==========================================
        // 1. TOP HEADER: Title, Subtitle & Status Badge
        // ==========================================
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI Chart Vision",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    // Ready Badge with glowing indicator dot
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF0E2319),
                        border = BorderStroke(1.dp, primaryGreen.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(primaryGreen)
                            )
                            Text(
                                text = when (state) {
                                    is ChartVisionUiState.Success -> "Inference complete"
                                    is ChartVisionUiState.Analyzing -> "Processing"
                                    is ChartVisionUiState.Error -> "Error"
                                    else -> "Awaiting image"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryGreen
                            )
                        }
                    }
                }

                Text(
                    text = "Upload or capture a chart to detect patterns, trends & setups",
                    fontSize = 13.5.sp,
                    color = textSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        // ==========================================
        // 2. HERO UPLOAD / CHART CANVAS PREVIEW
        // ==========================================
        item {
            val bmp = activeBitmap
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.5.dp, if (bmp != null) primaryGreen.copy(alpha = 0.35f) else cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (bmp != null) {
                        // Display active chart preview inside styled viewport
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF070A10))
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Active Trading Chart",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // Animated Holographic Laser Scanner Overlay
                            val isAnalyzing = state is ChartVisionUiState.Analyzing
                            val scanTransition = rememberInfiniteTransition(label = "chart_scanner_anim")
                            val scanProgress by scanTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(if (isAnalyzing) 1100 else 2400, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scan_y"
                            )
                            val scannerGlow by scanTransition.animateFloat(
                                initialValue = 0.5f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(if (isAnalyzing) 600 else 1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scanner_glow"
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val scanY = size.height * scanProgress
                                // Laser scanning beam
                                val laserColor = if (isAnalyzing) Color(0xFF00E5FF) else primaryGreen
                                drawLine(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            laserColor.copy(alpha = 0.75f * scannerGlow),
                                            Color.White.copy(alpha = 0.95f * scannerGlow),
                                            laserColor.copy(alpha = 0.75f * scannerGlow),
                                            Color.Transparent
                                        )
                                    ),
                                    start = Offset(0f, scanY),
                                    end = Offset(size.width, scanY),
                                    strokeWidth = (if (isAnalyzing) 2.5f else 1.8f).dp.toPx()
                                )
                                // Laser trailing illumination
                                val trailHeight = (if (isAnalyzing) 36.dp else 22.dp).toPx()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            laserColor.copy(alpha = scannerGlow * 0.18f),
                                            Color.Transparent
                                        ),
                                        startY = scanY,
                                        endY = (scanY - trailHeight).coerceAtLeast(0f)
                                    ),
                                    topLeft = Offset(0f, (scanY - trailHeight).coerceAtLeast(0f)),
                                    size = androidx.compose.ui.geometry.Size(size.width, trailHeight)
                                )
                            }

                            // Top overlay tag
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xCC0D1424),
                                border = BorderStroke(1.dp, Color(0xFF2A3852)),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ShowChart,
                                        contentDescription = null,
                                        tint = if (isAnalyzing) Color(0xFF00E5FF) else primaryGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        "${selectedPreset.asset} · ${bmp.width}x${bmp.height}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary
                                    )
                                    if (isAnalyzing) {
                                        Spacer(Modifier.width(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                "SCANNING",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty state upload area matching prompt screenshot
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF182236)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = primaryGreen,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Upload Chart Image",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "PNG, JPG up to 10MB • Clear candlesticks recommended",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                        }
                    }

                    // Three Action Buttons: Choose File, Take Photo, Sample Charts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Choose File
                        FilledTonalButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1E283D),
                                contentColor = textPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Choose File", fontSize = 11.5.sp, maxLines = 1)
                        }

                        // Take Photo
                        FilledTonalButton(
                            onClick = { cameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1E283D),
                                contentColor = textPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Take Photo", fontSize = 11.5.sp, maxLines = 1)
                        }

                        // Sample Charts Dropdown Button
                        Box(modifier = Modifier.weight(1.15f)) {
                            FilledTonalButton(
                                onClick = { sampleMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF1E283D),
                                    contentColor = textPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("DEMO / SAMPLE", fontSize = 11.5.sp, maxLines = 1)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp))
                            }

                            DropdownMenu(
                                expanded = sampleMenuExpanded,
                                onDismissRequest = { sampleMenuExpanded = false },
                                modifier = Modifier.background(Color(0xFF1A2234))
                            ) {
                                SampleChartPreset.values().forEach { preset ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(preset.title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(preset.subtitle, color = textSecondary, fontSize = 11.sp)
                                            }
                                        },
                                        onClick = {
                                            selectedPreset = preset
                                            viewModel.selectPresetChart(preset)
                                            sampleMenuExpanded = false
                                            Toast.makeText(context, "Loaded ${preset.title}", Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.ShowChart, contentDescription = null, tint = primaryGreen)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. MAIN PRIMARY ACTION: "Analyze Chart"
        // ==========================================
        item {
            val isAnalyzing = state is ChartVisionUiState.Analyzing
            Button(
                onClick = {
                    viewModel.analyzeActiveChart(userNotes)
                },
                enabled = !isAnalyzing && activeBitmap != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryGreen,
                    contentColor = Color(0xFF04120A),
                    disabledContainerColor = Color(0xFF1C2C24),
                    disabledContentColor = Color(0xFF4C6656)
                )
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF04120A),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Scanning Multi-modal Price Action...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Analyze Chart",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ==========================================
        // 4. ANALYZING / RADAR PROGRESS
        // ==========================================
        item {
            AnimatedVisibility(
                visible = state is ChartVisionUiState.Analyzing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, primaryGreen.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = primaryGreen,
                            trackColor = Color(0xFF1C283D)
                        )
                        Text(
                            (state as? ChartVisionUiState.Analyzing)?.stage ?: "Analyzing candlesticks & trends...",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                }
            }
        }

        // ==========================================
        // 5. ERROR STATE
        // ==========================================
        item {
            AnimatedVisibility(
                visible = state is ChartVisionUiState.Error,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val err = state as? ChartVisionUiState.Error
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF281116)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252))
                        Text(
                            err?.message ?: "An unexpected error occurred.",
                            fontSize = 12.sp,
                            color = Color(0xFFFF8A80)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 6. RICH VISION SECTIONS (Rendered from Analysis or Preset defaults)
        // ==========================================

        // ------------------------------------------
        if (successAnalysis != null) {
        item {
            Text("${analysis.modelUsed} · ${analysis.processingMs} ms · ${if (analysis.usageEstimated) "Estimated" else "Provider reported"} tokens: ${analysis.totalTokens}", color = textSecondary, fontSize = 11.sp)
        }
        // A. MARKET STRUCTURE & TREND
        // ------------------------------------------
        item {
            VisionCollapsibleCard(
                title = "Market Structure & Trend",
                icon = Icons.Default.ShowChart,
                iconTint = primaryGreen,
                expanded = isMarketStructureExpanded,
                onToggle = { isMarketStructureExpanded = !isMarketStructureExpanded },
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Pill Row: Primary Trend + Strong Strength
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Trend direction pill
                        val isBearish = analysis.direction == TrendDirection.BEARISH
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBearish) Color(0xFF261418) else Color(0xFF0E2319),
                            border = BorderStroke(1.dp, if (isBearish) Color(0xFFFF5252).copy(alpha = 0.5f) else primaryGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("PRIMARY TREND", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${analysis.direction.symbol} ${analysis.direction.displayName}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBearish) Color(0xFFFF5252) else primaryGreen
                                )
                            }
                        }

                        // Strength pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF182030),
                            border = BorderStroke(1.dp, Color(0xFF29364F)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("STRENGTH", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${analysis.trendStrength.name} (82%)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                        }
                    }

                    // Description text
                    Text(
                        text = analysis.trendSummary.ifBlank {
                            "Strong downward trend with significant selling pressure visible across all timeframe candles."
                        },
                        fontSize = 12.5.sp,
                        color = textSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // ------------------------------------------
        // B. ACTIONABLE TRADE SETUP
        // ------------------------------------------
        item {
            VisionCollapsibleCard(
                title = "Actionable Trade Setup",
                icon = Icons.Default.Bolt,
                iconTint = Color(0xFFFFB300),
                expanded = isTradeSetupExpanded,
                onToggle = { isTradeSetupExpanded = !isTradeSetupExpanded },
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Header Row with Copy Setup Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF261418),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
                        ) {
                            Text(
                                "SHORT POSITION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFF5252),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(
                                    "Trade Setup",
                                    "Asset: ${analysis.assetIdentifier}\nEntry: ${analysis.tradePlan.entryZone}\nSL: ${analysis.tradePlan.stopLoss}\nTP: ${analysis.tradePlan.target1}\nR:R: ${analysis.tradePlan.riskRewardRatio}"
                                )
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Trade setup copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    // 2x2 Grid: Entry, Stop Loss, Target 1, Risk:Reward
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TradeSetupPill(
                            label = "ENTRY ZONE",
                            value = analysis.tradePlan.entryZone,
                            accentColor = Color(0xFF00E5FF),
                            modifier = Modifier.weight(1f)
                        )
                        TradeSetupPill(
                            label = "STOP LOSS",
                            value = analysis.tradePlan.stopLoss,
                            accentColor = Color(0xFFFF5252),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TradeSetupPill(
                            label = "TARGET 1",
                            value = analysis.tradePlan.target1,
                            accentColor = primaryGreen,
                            modifier = Modifier.weight(1f)
                        )
                        TradeSetupPill(
                            label = "RISK : REWARD",
                            value = analysis.tradePlan.riskRewardRatio,
                            accentColor = Color(0xFFFFB300),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Invalidation reason
                    if (analysis.tradePlan.invalidationReason.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = textSecondary, modifier = Modifier.size(13.dp))
                            Text(
                                "Invalidation: ${analysis.tradePlan.invalidationReason}",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }
                }
            }
        }

        // ------------------------------------------
        // C. DETECTED PATTERNS
        // ------------------------------------------
        item {
            VisionCollapsibleCard(
                title = "Detected Patterns",
                icon = Icons.Default.Insights,
                iconTint = Color(0xFF00E5FF),
                expanded = isPatternsExpanded,
                onToggle = { isPatternsExpanded = !isPatternsExpanded },
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    analysis.patterns.forEach { pattern ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF161E2E),
                            border = BorderStroke(1.dp, Color(0xFF222E44)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        pattern.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = primaryGreen.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, primaryGreen.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            "AI interpretation · uncalibrated",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryGreen,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    "${pattern.patternType} · ${pattern.status}",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )

                                if (pattern.implication.isNotBlank()) {
                                    Text(
                                        pattern.implication,
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFD4E0F0)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------------
        // D. KEY SUPPORT & RESISTANCE
        // ------------------------------------------
        item {
            VisionCollapsibleCard(
                title = "Key Support & Resistance",
                icon = Icons.Default.HorizontalRule,
                iconTint = Color(0xFFAB47BC),
                expanded = isSrExpanded,
                onToggle = { isSrExpanded = !isSrExpanded },
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    analysis.keyLevels.forEach { lvl ->
                        val isRes = lvl.type.contains("Resist", true)
                        val badgeColor = if (isRes) Color(0xFFFF5252) else primaryGreen
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF161E2E),
                            border = BorderStroke(1.dp, Color(0xFF222E44)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor)
                                    )
                                    Text(
                                        lvl.level,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        lvl.type,
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                }

                                if (lvl.description.isNotBlank()) {
                                    Text(
                                        lvl.description.take(24),
                                        fontSize = 10.sp,
                                        color = textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------------
        // F. FULL TECHNICAL REPORT (Collapsible)
        // ------------------------------------------
        item {
            VisionCollapsibleCard(
                title = "Full Technical Report",
                icon = Icons.Default.Description,
                iconTint = Color(0xFF81D4FA),
                expanded = isReportExpanded,
                onToggle = { isReportExpanded = !isReportExpanded },
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0C101A),
                    border = BorderStroke(1.dp, Color(0xFF1B2436)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = analysis.comprehensiveReport.ifBlank {
                                "No detailed report was returned."
                            },
                            fontSize = 11.5.sp,
                            color = Color(0xFFC0D0E6),
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // ------------------------------------------
        // G. GRADIENT ACTION BAR: Ask Maximus AI, Export Report, Copy Setup
        // ------------------------------------------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF3F356B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(darkPurpleGradient)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFD1C4E9),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "AI Assistant & Sharing",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        }

                        // Primary Action: Ask Maximus AI
                        Button(
                            onClick = {
                                viewModel.sendAnalysisToChat(analysis)
                                onNavigateToChat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5B34B2),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 11.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ask Maximus AI about this chart", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Secondary Row: Export Report + Copy Setup
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { showExportDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF282846),
                                    contentColor = textPrimary
                                ),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export Report", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    val reportText = analysis.toShareableReport()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Chart Vision Report", reportText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF282846),
                                    contentColor = textPrimary
                                ),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Text", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------------
        }
        // H. TRADER NOTES / CONTEXT
        // ------------------------------------------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Trader Notes & Assumptions",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )

                    OutlinedTextField(
                        value = userNotes,
                        onValueChange = { userNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Add notes, asset symbol or timeframe context...", fontSize = 12.sp, color = textSecondary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryGreen,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        maxLines = 2,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.saveAnalysisToMatrixGraph(analysis)
                                Toast.makeText(context, "Note saved to Matrix Knowledge Graph", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Note", color = primaryGreen, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ------------------------------------------
        // I. DISCLAIMER BANNER
        // ------------------------------------------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141926)),
                border = BorderStroke(1.dp, Color(0xFF222B40))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color(0xFFB388FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "AI-generated technical analysis is for informational and educational purposes only. Always conduct your own research and manage risk diligently.",
                        fontSize = 11.sp,
                        color = textSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // ------------------------------------------
        // J. EXPLORE RELATED MODULES
        // ------------------------------------------
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "EXPLORE RELATED MODULES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RelatedModuleCard(
                        title = "News Intelligence",
                        subtitle = "Forex Factory catalysts",
                        icon = Icons.Default.Article,
                        accentColor = Color(0xFFB388FF),
                        modifier = Modifier.weight(1f)
                    )

                    RelatedModuleCard(
                        title = "Agents Hub",
                        subtitle = "Autonomous traders",
                        icon = Icons.Default.SmartToy,
                        accentColor = Color(0xFF00E5FF),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAgents
                    )
                }
            }
        }
    }

    // ==========================================
    // EXPORT SUMMARY REPORT DIALOG
    // ==========================================
    if (showExportDialog) {
        val reportText = remember(analysis) { analysis.toShareableReport() }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, reportText)
                            putExtra(Intent.EXTRA_SUBJECT, "MAXIMUS AI Chart Vision - ${analysis.assetIdentifier}")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Chart Vision Report")
                        context.startActivity(shareIntent)
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryGreen, contentColor = Color(0xFF04120A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share via Apps", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Chart Vision Report", reportText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy", fontSize = 12.sp)
                }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(22.dp))
                    Text("Export Summary Report", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Trend analysis, detected patterns, and trade setup for ${analysis.assetIdentifier}:",
                        fontSize = 11.5.sp,
                        color = textSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF070B12),
                        border = BorderStroke(1.dp, Color(0xFF1E283D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            item {
                                Text(
                                    text = reportText,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFC9D1D9),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color(0xFF121826)
        )
    }
}

// ==========================================
// COMPONENT: VISION COLLAPSIBLE CARD
// ==========================================
@Composable
private fun VisionCollapsibleCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    Text(
                        text = title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF8B9CB5)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                content()
            }
        }
    }
}

// ==========================================
// COMPONENT: TRADE SETUP PILL
// ==========================================
@Composable
private fun TradeSetupPill(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF161E2E),
        border = BorderStroke(1.dp, Color(0xFF222E44))
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Text(
                value,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF0F4FC),
                maxLines = 1
            )
        }
    }
}

// ==========================================
// COMPONENT: RELATED MODULE CARD
// ==========================================
@Composable
private fun RelatedModuleCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF111726),
        border = BorderStroke(1.dp, Color(0xFF1E283D))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }
            Column {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF0F4FC), maxLines = 1)
                Text(subtitle, fontSize = 10.sp, color = Color(0xFF8B9CB5), maxLines = 1)
            }
        }
    }
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime !in setOf("image/jpeg", "image/png", "image/webp")) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth.toLong() * options.outHeight > 16_000_000L) return null
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }
}
