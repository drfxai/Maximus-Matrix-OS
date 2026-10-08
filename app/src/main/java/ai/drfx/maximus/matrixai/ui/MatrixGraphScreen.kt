package ai.drfx.maximus.matrixai.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.speech.RecognizerIntent
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ai.drfx.maximus.matrixai.llm.ConnectionStatus
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.*

private val GraphAccent = Color(0xFF38C79B)
private val GraphVisionCyan = Color(0xFF00E5FF)
private val GraphNewsAmber = Color(0xFFFF9100)
private val GraphAlertsCrimson = Color(0xFFFF1744)
private val GraphSignalsGreen = Color(0xFF00E676)
private val GraphChatViolet = Color(0xFFB388FF)
private val GraphBlue = Color(0xFF578CDB)
private val GraphPurple = Color(0xFF9B72DA)
private val GraphGold = Color(0xFFE4B739)
private val GraphRed = Color(0xFFD664A2)
private val GraphCyan = Color(0xFF6ABCC9)
private val GraphOrange = Color(0xFFE99148)
private val GraphLime = Color(0xFFAAC56A)

private data class LiveNode(
    val id: String,
    val label: String,
    val group: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val radius: Float,
    val color: Color,
    val description: String,
    val relations: String,
    val hub: Boolean = false,
    val lobe: String = "CORTEX",
    val anatomicalRegion: String = ""
)
private data class LiveEdge(val from: String, val to: String, val relation: String)
private data class MeshPoint(val point: SpacePoint, val parent: String, val color: Color)
private enum class GraphPanel { INSPECTOR, FILTERS }

private data class QuickActionItem(
    val titleLine1: String,
    val titleLine2: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accentColor: Color,
    val promptText: String = "",
    val isVision: Boolean = false,
    val isNews: Boolean = false,
    val isAlerts: Boolean = false,
    val isSignals: Boolean = false,
    val isChat: Boolean = false,
    val isAgents: Boolean = false,
    val isData: Boolean = false,
    val isControl: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatrixGraphScreen(
    viewModel: MatrixViewModel,
    onNavigateToVision: () -> Unit = {},
    onNavigateToNews: () -> Unit = {},
    onNavigateToSignals: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToAgents: () -> Unit = {},
    onNavigateToData: () -> Unit = {},
    onNavigateToControl: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val light = MaterialTheme.colorScheme.background.luminance() > .5f
    val bg = if (light) Color(0xFFF4F7FA) else Color(0xFF050608)
    val panelColor = if (light) Color(0xF5FFFFFF) else Color(0xF5101217)
    val ink = if (light) Color(0xFF182434) else Color(0xFFE5E9EF)
    val muted = if (light) Color(0xFF586778) else Color(0xFF8D939F)
    val border = if (light) Color(0xFFD7DFE8) else Color(0xFF262930)
    val events by viewModel.events.collectAsState()
    val status by viewModel.status.collectAsState()
    val llm by viewModel.llmState.collectAsState()
    val dataCenter by viewModel.dataCenter.collectAsState()
    val recentAlerts by viewModel.notificationService.recentAlerts.collectAsState()
    val watchlist by viewModel.notificationService.activeWatchlist.collectAsState()
    val activeSignalsCount by viewModel.signalRepository.observeActiveSignalCount().collectAsState(initial = 8)
    val chartVisionState by viewModel.chartVisionState.collectAsState()
    val topology = remember(llm.status, llm.provider, llm.selectedModel, dataCenter.status.connected, recentAlerts.size, watchlist.size, activeSignalsCount, chartVisionState) {
        buildTopology(
            llmConnected = llm.status == ConnectionStatus.CONNECTED,
            provider = llm.provider.name.replace('_', ' '),
            model = llm.selectedModel,
            dataConnected = dataCenter.status.connected,
            alertsCount = recentAlerts.size,
            watchlistCount = watchlist.size,
            activeSignalsCount = activeSignalsCount,
            visionReady = true
        )
    }
    val nodes = topology.first
    val edges = topology.second
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = nodes.find { it.id == selectedId }
    var selectedLobe by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var hiddenGroups by remember { mutableStateOf(emptySet<String>()) }
    var openPanel by remember { mutableStateOf<GraphPanel?>(null) }
    var rotation by remember { mutableStateOf(GraphRotation.defaultBrainView()) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var autoRotate by remember { mutableStateOf(true) }
    var touching by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var labels by remember { mutableStateOf(true) }
    var mesh by remember { mutableStateOf(true) }
    var spread by remember { mutableFloatStateOf(1f) }
    var linkOpacity by remember { mutableFloatStateOf(.28f) }
    var mission by remember { mutableStateOf("") }
    var pendingVoice by remember { mutableStateOf<String?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!words.isNullOrBlank()) { mission = words; pendingVoice = words; voiceError = null }
            else voiceError = "No command was recognized. Please try again."
        }
    }
    fun reset() {
        rotation = GraphRotation.defaultBrainView()
        zoom = 1f
        selectedId = null
        selectedLobe = null
        lastTouch = SystemClock.uptimeMillis()
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, autoRotate) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (isActive) {
                withFrameNanos { now ->
                    val dt = if (previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)
                    previous = now
                    if (autoRotate && !touching && selectedId == null && openPanel == null && SystemClock.uptimeMillis() - lastTouch > 2200L)
                        rotation = rotation.orbit(yaw = dt * .045f)
                }
            }
        }
    }
    if (pendingVoice != null) AlertDialog(onDismissRequest = { pendingVoice = null },
        title = { Text("Execute voice command?") }, text = { Text(pendingVoice.orEmpty()) },
        confirmButton = { TextButton(onClick = { pendingVoice?.let(viewModel::runMission); mission = ""; pendingVoice = null },
            enabled = status != "EXECUTING" && status != "PLANNING") { Text("Execute") } },
        dismissButton = { TextButton(onClick = { pendingVoice = null }) { Text("Edit or cancel") } })

    fun getActionForNode(node: LiveNode): Pair<String, () -> Unit>? {
        return when {
            node.id == "signals_hub" || node.id.startsWith("signal_") -> "Open Live Signals" to onNavigateToSignals
            node.id == "vision_hub" || node.id.startsWith("vision_") -> "Open Chart Vision" to onNavigateToVision
            node.id == "news_hub" || node.id.startsWith("news_") -> "Open News Intelligence" to onNavigateToNews
            node.id == "alerts_hub" || node.id.startsWith("alert_") -> "View Market Alerts" to onNavigateToNews
            node.id == "chat_hub" || node.id.startsWith("chat_") -> "Open AI Chat" to onNavigateToChat
            node.id == "agents" || node.group == "AGENT RUNTIME" -> "Open Agents Hub" to onNavigateToAgents
            node.id == "company" || node.group == "DATA & KNOWLEDGE" -> "Open Data Center" to onNavigateToData
            node.id == "api" || node.group == "FINOPS & CONTROL" -> "Open Control Hub" to onNavigateToControl
            node.id == "models" || node.id == "provider" || node.id == "model" -> "Configure AI Gateway" to onNavigateToControl
            else -> null
        }
    }

    fun getIconForNode(node: LiveNode): androidx.compose.ui.graphics.vector.ImageVector {
        return when {
            node.id == "signals_hub" || node.id.startsWith("signal_") -> Icons.Default.Bolt
            node.id == "vision_hub" || node.id.startsWith("vision_") -> Icons.Default.Visibility
            node.id == "news_hub" || node.id.startsWith("news_") -> Icons.Default.Article
            node.id == "alerts_hub" || node.id.startsWith("alert_") -> Icons.Default.NotificationsActive
            node.id == "chat_hub" || node.id.startsWith("chat_") -> Icons.AutoMirrored.Filled.Chat
            node.id == "agents" || node.group == "AGENT RUNTIME" -> Icons.Default.Person
            node.id == "company" || node.group == "DATA & KNOWLEDGE" -> Icons.Default.Storage
            node.id == "api" || node.group == "FINOPS & CONTROL" -> Icons.Default.Settings
            node.id == "genome" || node.id.startsWith("indicator_") || node.id.startsWith("strategy_") -> Icons.Default.Science
            node.id == "pine" || node.id.startsWith("sanitizer") || node.id.startsWith("classifier") -> Icons.Default.Code
            else -> Icons.Default.Hub
        }
    }

    val inspector: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MAXIMUS MATRIX OS", color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("${nodes.size} modules · ${edges.size} neural connections", color = muted, fontSize = 10.sp)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search matrix nodes…", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, "Clear search", Modifier.size(14.dp))
                        }
                    }
                }
            )
            Text("INSPECTOR", color = muted, fontSize = 9.sp, letterSpacing = 1.sp)
            Surface(color = bg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, border)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selected != null) {
                        val action = getActionForNode(selected)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(graphTone(selected.color, light), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selected.lobe, color = graphTone(selected.color, light), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                if (selected.anatomicalRegion.isNotEmpty()) {
                                    Text(selected.anatomicalRegion, color = muted, fontSize = 8.5.sp)
                                }
                            }
                            Text("${edges.count { it.from == selected.id || it.to == selected.id }} axons", color = muted, fontSize = 9.sp)
                        }
                        Text(selected.label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        Text(selected.description, color = muted, fontSize = 10.5.sp, lineHeight = 15.sp)

                        if (action != null) {
                            Button(
                                onClick = {
                                    openPanel = null
                                    action.second()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = graphTone(selected.color, light)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(getIconForNode(selected), null, modifier = Modifier.size(15.dp), tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text(action.first, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        // Connected neighbor chips
                        val neighbors = edges
                            .filter { it.from == selected.id || it.to == selected.id }
                            .map { if (it.from == selected.id) it.to else it.from }
                            .mapNotNull { nid -> nodes.find { it.id == nid } }
                            .distinctBy { it.id }

                        if (neighbors.isNotEmpty()) {
                            Text("CONNECTED MODULES (${neighbors.size})", color = muted, fontSize = 8.5.sp, letterSpacing = 0.8.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                neighbors.forEach { neighbor ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (light) Color(0xFFE8EEF5) else Color(0xFF141923),
                                        border = BorderStroke(1.dp, graphTone(neighbor.color, light).copy(alpha = 0.45f)),
                                        modifier = Modifier.clickable { selectedId = neighbor.id }
                                    ) {
                                        Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(5.dp).background(graphTone(neighbor.color, light), CircleShape))
                                            Spacer(Modifier.width(5.dp))
                                            Text(neighbor.label, fontSize = 9.5.sp, color = ink)
                                        }
                                    }
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { selectedId = null }) { Text("Clear focus", fontSize = 10.sp) }
                        }
                    } else {
                        Text("Explore the Matrix", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Tap any module node to inspect its live state, trigger actions, or explore connections. Orbit with one finger, pinch to zoom, or search below.",
                            color = muted, fontSize = 10.5.sp, lineHeight = 15.sp)
                    }
                }
            }
            Text(if (search.isBlank()) "TOP APPLICATION HUBS" else "SEARCH RESULTS", color = muted, fontSize = 9.sp, letterSpacing = 1.sp)
            nodes.filter { if (search.isBlank()) it.hub else it.label.contains(search, true) || it.group.contains(search, true) }.forEach { node ->
                Row(Modifier.fillMaxWidth().clickable { selectedId = node.id; hiddenGroups = hiddenGroups - node.group; openPanel = null }
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(graphTone(node.color, light), CircleShape))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(node.label, color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(node.group, color = muted, fontSize = 8.5.sp)
                    }
                    Text(edges.count { it.from == node.id || it.to == node.id }.toString(), color = muted, fontSize = 10.sp)
                }
            }
            if (search.isNotBlank() && nodes.none { it.label.contains(search, true) || it.group.contains(search, true) }) Text("No matching modules", color = muted, fontSize = 11.sp)
            HorizontalDivider(color = border)
            Text("DISPLAY & RENDERING", color = muted, fontSize = 9.sp, letterSpacing = 1.sp)
            Text("Cluster spread", color = muted, fontSize = 11.sp)
            Slider(spread, { spread = it }, valueRange = .65f..1.5f)
            Text("Link visibility", color = muted, fontSize = 11.sp)
            Slider(linkOpacity, { linkOpacity = it }, valueRange = .12f.. .7f)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(labels, { labels = it }); Text("Node labels", color = ink, fontSize = 11.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(mesh, { mesh = it }); Text("Ambient neural mesh", color = ink, fontSize = 11.sp) }
            Text("Mesh particles are visual depth detail; node spheres represent actual live application modules.", color = muted, fontSize = 9.5.sp)
        }
    }
    val filters: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("FILTER", color = muted, fontSize = 9.sp, letterSpacing = 1.sp)
            nodes.groupBy { it.group }.forEach { (group, members) ->
                Row(Modifier.fillMaxWidth().clickable {
                    hiddenGroups = if (group in hiddenGroups) hiddenGroups - group else hiddenGroups + group
                    if (selected?.group in hiddenGroups) selectedId = null
                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(if (group in hiddenGroups) muted.copy(alpha = .3f) else graphTone(members.first().color, light), CircleShape))
                    Text(group.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f).padding(horizontal = 7.dp),
                        color = if (group in hiddenGroups) muted.copy(alpha = .5f) else ink, fontSize = 10.sp)
                    Text(members.size.toString(), color = muted, fontSize = 9.sp)
                }
            }
            TextButton(onClick = { hiddenGroups = emptySet(); search = "" }) { Text("Show all", fontSize = 11.sp) }
        }
    }

    val effectiveHidden = remember(hiddenGroups, selectedLobe, nodes) {
        if (selectedLobe == null) hiddenGroups
        else hiddenGroups + nodes.filter { it.lobe != selectedLobe }.map { it.group }.toSet()
    }

    Column(modifier.background(bg)) {
        // Bio-cybernetic Neural Telemetry Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MAXIMUS AI", color = ink, fontSize = 16.sp, letterSpacing = 1.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = graphTone(GraphAccent, light).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "CEREBRAL CORTEX",
                            color = graphTone(GraphAccent, light),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            letterSpacing = 0.6.sp
                        )
                    }
                }
                Text("DUAL-HEMISPHERE 3D GRAPH · 48.2 Hz BIO-PULSE", color = muted, fontSize = 8.5.sp, letterSpacing = .6.sp)
            }
            Box(Modifier.size(7.dp).background(if (status == "READY") GraphAccent else GraphGold, CircleShape))
            Text(status, Modifier.padding(start = 6.dp), color = graphTone(GraphAccent, light), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        // Horizontal Anatomical Lobe Filter Chips
        val lobeFilters = remember {
            listOf(
                "ALL" to "All Lobes",
                "FRONTAL LOBE" to "🧠 Frontal",
                "PARIETAL LOBE" to "⚡ Parietal",
                "OCCIPITAL LOBE" to "👁️ Occipital",
                "TEMPORAL LOBE" to "🌐 Temporal",
                "LIMBIC SYSTEM" to "🧬 Limbic",
                "CEREBELLAR" to "⚙️ Cerebellar"
            )
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(lobeFilters) { (lobeKey, lobeTitle) ->
                val isSelected = (selectedLobe == null && lobeKey == "ALL") || selectedLobe == lobeKey
                val chipBg = if (isSelected) graphTone(GraphAccent, light) else (if (light) Color(0xFFE8EEF5) else Color(0xFF141923))
                val chipText = if (isSelected) Color.White else ink

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = chipBg,
                    border = if (!isSelected) BorderStroke(1.dp, border) else null,
                    modifier = Modifier.clickable {
                        selectedLobe = if (lobeKey == "ALL" || selectedLobe == lobeKey) null else lobeKey
                        if (selected != null && selectedLobe != null && selected.lobe != selectedLobe) {
                            selectedId = null
                        }
                    }
                ) {
                    Text(
                        lobeTitle,
                        color = chipText,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 760.dp
            val isCompact = maxHeight < 300.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) Surface(Modifier.width(205.dp).fillMaxHeight().padding(start = 8.dp, bottom = 8.dp),
                    color = panelColor, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, border)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) { inspector() }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    NeuralScene(
                        nodes = nodes,
                        edges = edges,
                        rotation = rotation,
                        zoom = zoom,
                        spread = spread,
                        linkOpacity = linkOpacity,
                        labels = labels,
                        mesh = mesh,
                        hidden = effectiveHidden,
                        search = search,
                        selected = selectedId,
                        light = light,
                        onGesture = { dx, dy, magnification, roll ->
                            rotation = rotation.orbit(pitch = dy * .006f, yaw = dx * .006f, roll = roll * PI.toFloat() / 180f)
                            zoom = (zoom * magnification).coerceIn(.55f, 3.5f)
                            lastTouch = SystemClock.uptimeMillis()
                        },
                        onTouch = { touching = it; lastTouch = SystemClock.uptimeMillis() },
                        onSelect = { selectedId = it },
                        onReset = { reset() }
                    )

                    // Anatomical Camera Controls Floating HUD
                    Surface(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 5.dp),
                        color = panelColor,
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, border),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            if (!wide) {
                                IconButton(
                                    onClick = { openPanel = GraphPanel.INSPECTOR },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Search, "Search and inspect nodes", tint = ink, modifier = Modifier.size(16.dp))
                                }
                            }
                            TextButton(
                                onClick = {
                                    rotation = GraphRotation.superiorDorsal()
                                    zoom = 1f
                                    lastTouch = SystemClock.uptimeMillis()
                                },
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("Superior", color = ink, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = {
                                    rotation = GraphRotation.frontalAnterior()
                                    zoom = 1f
                                    lastTouch = SystemClock.uptimeMillis()
                                },
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("Frontal", color = ink, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = {
                                    rotation = GraphRotation.lateralLeft()
                                    zoom = 1f
                                    lastTouch = SystemClock.uptimeMillis()
                                },
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("Lateral", color = ink, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = { reset() },
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("Reset", color = graphTone(GraphAccent, light), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { autoRotate = !autoRotate },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (autoRotate) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (autoRotate) "Pause rotation" else "Start rotation",
                                    tint = graphTone(GraphPurple, light),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (!wide) {
                                IconButton(
                                    onClick = { openPanel = GraphPanel.FILTERS },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.FilterList, "Filter node groups", tint = ink, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (!isCompact) {
                        Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "${nodes.count { it.group !in effectiveHidden }} NEURONS · ${edges.count { edge -> nodes.none { it.group in effectiveHidden && (it.id == edge.from || it.id == edge.to) } }} AXONS",
                                color = muted,
                                fontSize = 8.sp,
                                letterSpacing = .6.sp
                            )
                            Text("Drag X/Y · Twist Z · Pinch zoom", color = muted, fontSize = 10.sp)
                            Text(
                                if (selected != null) "NEURON LOCKED" else if (autoRotate) "AUTO BIO-ORBIT" else "MANUAL ORBIT",
                                color = graphTone(GraphAccent, light),
                                fontSize = 8.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    if (!wide && !isCompact) IntelligenceRing(status, light, Modifier.align(Alignment.BottomEnd).padding(10.dp).size(78.dp))
                    if (!wide && selected != null) {
                        val action = getActionForNode(selected)
                        Surface(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 54.dp, start = 10.dp, end = 10.dp)
                                .fillMaxWidth(),
                            color = panelColor,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.2.dp, graphTone(selected.color, light).copy(alpha = 0.85f)),
                            shadowElevation = 6.dp
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(7.dp).background(graphTone(selected.color, light), CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            selected.lobe,
                                            color = graphTone(selected.color, light),
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )
                                        if (selected.anatomicalRegion.isNotEmpty()) {
                                            Text(selected.anatomicalRegion, color = muted, fontSize = 8.sp)
                                        }
                                    }
                                    Text("${edges.count { it.from == selected.id || it.to == selected.id }} axons", color = muted, fontSize = 8.5.sp)
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(onClick = { selectedId = null }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, "Clear focus", tint = muted, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text(selected.label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                Text(selected.description, color = muted, fontSize = 10.5.sp, maxLines = 2, lineHeight = 14.sp)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (action != null) {
                                        Button(
                                            onClick = action.second,
                                            colors = ButtonDefaults.buttonColors(containerColor = graphTone(selected.color, light)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(getIconForNode(selected), null, modifier = Modifier.size(13.dp), tint = Color.White)
                                            Spacer(Modifier.width(4.dp))
                                            Text(action.first, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { openPanel = GraphPanel.INSPECTOR },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp),
                                        border = BorderStroke(1.dp, border)
                                    ) {
                                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(12.dp), tint = ink)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Inspector", fontSize = 10.sp, color = ink)
                                    }
                                }
                            }
                        }
                    }
                }
                if (wide) Column(Modifier.width(174.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                    Surface(color = panelColor, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, border)) {
                        Column(Modifier.padding(10.dp)) { filters() }
                    }
                    Spacer(Modifier.height(20.dp))
                    IntelligenceRing(status, light, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(if (llm.status == ConnectionStatus.CONNECTED) "MODEL CONNECTED" else "MODEL OFFLINE", Modifier.align(Alignment.CenterHorizontally),
                        color = muted, fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
            if (openPanel != null) AlertDialog(onDismissRequest = { openPanel = null }, containerColor = panelColor,
                title = { Text(if (openPanel == GraphPanel.INSPECTOR) "Matrix inspector" else "Node filters", color = ink) },
                text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { if (openPanel == GraphPanel.INSPECTOR) inspector() else filters() } },
                confirmButton = { TextButton(onClick = { openPanel = null }) { Text("Done") } })
        }
        Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = "Mission composer" }, color = panelColor,
            shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (mission.isBlank()) {
                    Text(events.firstOrNull()?.message ?: "Your matrix is ready. Explore a node or describe a mission.",
                        color = muted, fontSize = 11.sp, maxLines = 1, lineHeight = 15.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(mission, { mission = it },
                        Modifier.weight(1f).semantics { contentDescription = "Mission input" }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus() }),
                        placeholder = { Text("Ask MAXIMUS…", fontSize = 12.sp) }, shape = RoundedCornerShape(25.dp))
                    IconButton(onClick = {
                        try { voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the mission to execute")) }
                        catch (e: Exception) { voiceError = "Voice recognition is unavailable on this device." }
                    }) { Icon(Icons.Default.Mic, "Speak a mission command", tint = graphTone(GraphPurple, light)) }
                    IconButton(onClick = { viewModel.runMission(mission); mission = "" },
                        enabled = mission.isNotBlank() && status != "EXECUTING" && status != "PLANNING") {
                        Icon(Icons.Default.ArrowUpward, "Run mission", tint = if (mission.isBlank()) muted else graphTone(GraphAccent, light))
                    }
                }
                voiceError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }

                val quickActions = remember {
                    listOf(
                        QuickActionItem(
                            titleLine1 = "Live",
                            titleLine2 = "Signals",
                            icon = Icons.Default.Bolt,
                            accentColor = Color(0xFF00E676),
                            promptText = "Review active high-probability live trading signals, order blocks, win rates, and TP/SL levels stored in Room.",
                            isSignals = true
                        ),
                        QuickActionItem(
                            titleLine1 = "Analyze",
                            titleLine2 = "Chart",
                            icon = Icons.Default.BarChart,
                            accentColor = Color(0xFF00E5FF),
                            promptText = "Analyze trading chart price action, identify market structure, detect dynamic trendlines and patterns, and construct an actionable trade plan.",
                            isVision = true
                        ),
                        QuickActionItem(
                            titleLine1 = "News",
                            titleLine2 = "Intelligence",
                            icon = Icons.Default.Article,
                            accentColor = Color(0xFFFF9100),
                            promptText = "Summarize today's financial markets news, macroeconomic catalysts, and economic calendar releases.",
                            isNews = true
                        ),
                        QuickActionItem(
                            titleLine1 = "Market",
                            titleLine2 = "Alerts",
                            icon = Icons.Default.NotificationsActive,
                            accentColor = Color(0xFFFF1744),
                            promptText = "Check real-time market-moving catalysts and breaking volatility alerts for watched asset pairs.",
                            isAlerts = true
                        ),
                        QuickActionItem(
                            titleLine1 = "Maximus",
                            titleLine2 = "Chat",
                            icon = Icons.AutoMirrored.Filled.Chat,
                            accentColor = Color(0xFFB388FF),
                            promptText = "Chat with Maximus AI for conversational market analysis and risk management guidance.",
                            isChat = true
                        ),
                        QuickActionItem(
                            titleLine1 = "Agent",
                            titleLine2 = "Factory",
                            icon = Icons.Default.Person,
                            accentColor = Color(0xFF38C79B),
                            promptText = "Open Agent Factory to configure autonomous trading, vision, news, and research agents.",
                            isAgents = true
                        ),
                        QuickActionItem(
                            titleLine1 = "Data",
                            titleLine2 = "Center",
                            icon = Icons.Default.Storage,
                            accentColor = Color(0xFFAAC56A),
                            promptText = "Access Data Center storage, documents, and Matrix Graph knowledge memory.",
                            isData = true
                        ),
                        QuickActionItem(
                            titleLine1 = "Control",
                            titleLine2 = "Hub",
                            icon = Icons.Default.Settings,
                            accentColor = Color(0xFFD664A2),
                            promptText = "Configure AI Gateway models, API keys, and FinOps token usage parameters.",
                            isControl = true
                        ),
                        QuickActionItem(
                            titleLine1 = "Build",
                            titleLine2 = "Strategy",
                            icon = Icons.Default.Science,
                            accentColor = Color(0xFF6ABCC9),
                            promptText = "Build an algorithmic trading strategy with clear entry rules, risk management, stop loss, and take profit targets."
                        ),
                        QuickActionItem(
                            titleLine1 = "Generate",
                            titleLine2 = "Pine",
                            icon = Icons.Default.Code,
                            accentColor = Color(0xFF00E676),
                            promptText = "Generate a TradingView Pine Script v5 strategy with entry signals, trailing stop, and position sizing."
                        ),
                        QuickActionItem(
                            titleLine1 = "Generate",
                            titleLine2 = "MQL5",
                            icon = Icons.Default.Description,
                            accentColor = Color(0xFFFFD54F),
                            promptText = "Generate a MetaTrader 5 MQL5 Expert Advisor with automated order execution, stop loss, and risk parameters."
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 2.dp)
                ) {
                    items(quickActions) { action ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (light) Color(0xFFF1F5F9) else Color(0xFF0D1219),
                            border = BorderStroke(1.2.dp, action.accentColor.copy(alpha = if (light) 0.65f else 0.85f)),
                            modifier = Modifier.clickable {
                                when {
                                    action.isSignals -> onNavigateToSignals()
                                    action.isVision -> onNavigateToVision()
                                    action.isNews || action.isAlerts -> onNavigateToNews()
                                    action.isChat -> onNavigateToChat()
                                    action.isAgents -> onNavigateToAgents()
                                    action.isData -> onNavigateToData()
                                    action.isControl -> onNavigateToControl()
                                    else -> mission = action.promptText
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(action.accentColor.copy(alpha = if (light) 0.08f else 0.07f))
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = action.accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (action.isSignals || action.isVision || action.isAlerts) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(action.accentColor)
                                                .align(Alignment.TopEnd)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = action.titleLine1,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ink,
                                        lineHeight = 13.sp
                                    )
                                    Text(
                                        text = action.titleLine2,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ink,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntelligenceRing(status: String, light: Boolean, modifier: Modifier) {
    val purple = graphTone(GraphPurple, light)
    val infiniteTransition = rememberInfiniteTransition(label = "intelligence_ring_anim")
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    Box(modifier.semantics { contentDescription = "MAXIMUS engine $status" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * .40f
            drawCircle(Brush.radialGradient(listOf(purple.copy(alpha = .18f * pulseGlow), Color.Transparent), radius = r * 1.3f), r * 1.3f)
            repeat(64) { i ->
                val a = i * PI.toFloat() / 32f
                val unit = Offset(cos(a), sin(a))
                drawLine(purple.copy(alpha = if (i % 4 == 0) .85f else .35f), center + unit * r, center + unit * (r + if (i % 4 == 0) 6.dp.toPx() else 3.dp.toPx()), 1.dp.toPx())
            }
            drawCircle(purple.copy(alpha = .45f), r * .91f, style = Stroke(1.dp.toPx()))
            drawArc(purple, 200f, 210f, false, Offset(center.x - r * .85f, center.y - r * .85f), Size(r * 1.7f, r * 1.7f), style = Stroke(2.dp.toPx()))
            drawCircle(graphTone(GraphAccent, light).copy(alpha = .45f), r * .54f, style = Stroke(.7.dp.toPx()))

            // Animated sweeping radar scan beam
            val sweepRad = Math.toRadians(radarAngle.toDouble()).toFloat()
            val sweepUnit = Offset(cos(sweepRad), sin(sweepRad))
            drawLine(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.85f), purple.copy(alpha = 0.7f), Color.Transparent),
                    center = center + sweepUnit * (r * 0.7f),
                    radius = r * 0.4f
                ),
                start = center,
                end = center + sweepUnit * r,
                strokeWidth = 1.8.dp.toPx()
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, purple.copy(alpha = 0.28f)),
                    center = center
                ),
                startAngle = radarAngle - 50f,
                sweepAngle = 50f,
                useCenter = true,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("M.A.X.", color = if (light) Color(0xFF40365F) else Color(0xFFD9CBF7), fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text("48 Hz", color = graphTone(GraphAccent, light), fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun world(node: LiveNode, spread: Float): SpacePoint =
    SpacePoint(node.x * spread, node.y * spread, node.z * spread)

@Composable
private fun NeuralScene(
    nodes: List<LiveNode>,
    edges: List<LiveEdge>,
    rotation: GraphRotation,
    zoom: Float,
    spread: Float,
    linkOpacity: Float,
    labels: Boolean,
    mesh: Boolean,
    hidden: Set<String>,
    search: String,
    selected: String?,
    light: Boolean,
    onGesture: (Float, Float, Float, Float) -> Unit,
    onTouch: (Boolean) -> Unit,
    onSelect: (String?) -> Unit,
    onReset: () -> Unit
) {
    val points = remember(nodes, spread) {
        nodes.associate { it.id to world(it, spread) }
    }
    val lookup = remember(nodes) { nodes.associateBy { it.id } }

    // Anatomically structured cerebral cortex surface scaffolding & glial astrocyte network
    val scaffold = remember(spread) {
        val meshPoints = mutableListOf<MeshPoint>()
        val hemispheres = listOf(-1f, 1f)

        for (s in hemispheres) {
            val lateralCleft = s * 22f // Longitudinal cerebral fissure
            for (i in 0 until 90) {
                val u = (i + 0.5f) / 90f
                val theta = acos(1f - 2f * u) // 0 to PI
                val phi = (i * 2.3999632f) % (2f * PI.toFloat())

                // Cerebral hemisphere dimensions
                val rx = 65f
                val ry = 75f
                val rz = 98f

                // Biological gyri and sulci surface convolutions
                val gyriFold = 1f + 0.08f * sin(6f * theta) * cos(8f * phi) + 0.04f * sin(12f * phi)

                var x = lateralCleft + s * (abs(sin(theta) * cos(phi)) * rx * gyriFold)
                var y = -cos(theta) * ry * gyriFold
                var z = sin(theta) * sin(phi) * rz * gyriFold

                // Cerebellar inferior-posterior bulge
                if (y > 30f && z < -20f) {
                    x *= 0.85f
                    z -= 12f
                }

                val p = SpacePoint(x * spread, y * spread, z * spread)
                val color = when {
                    z > 40f -> GraphAccent       // Frontal Lobe
                    y < -55f -> GraphSignalsGreen // Parietal Lobe
                    z < -35f && y < 30f -> GraphVisionCyan // Occipital Lobe
                    y > 35f -> GraphCyan        // Cerebellum
                    s < 0 -> GraphChatViolet    // Left Temporal
                    else -> GraphNewsAmber       // Right Temporal
                }
                meshPoints.add(MeshPoint(p, "cortex", color))
            }
        }

        // Corpus callosum bridging fiber bundle
        for (c in 0 until 18) {
            val t = c / 18f
            val x = (t - 0.5f) * 38f
            val y = -14f - 6f * sin(t * PI.toFloat())
            val z = ((c % 5) - 2) * 7f
            meshPoints.add(MeshPoint(SpacePoint(x * spread, y * spread, z * spread), "corpus_callosum", GraphAccent))
        }

        meshPoints
    }

    val radius = remember(points, scaffold) {
        max(points.values.maxOf { it.length }, scaffold.maxOf { it.point.length })
    }
    val visible = remember(nodes, hidden) { nodes.filter { it.group !in hidden } }
    val neighbors = remember(selected, edges) {
        edges.filter { it.from == selected || it.to == selected }.flatMap { listOf(it.from, it.to) }.toSet()
    }
    val gesture by rememberUpdatedState(onGesture)
    val touch by rememberUpdatedState(onTouch)
    val select by rememberUpdatedState(onSelect)
    val reset by rememberUpdatedState(onReset)
    val currentRotation by rememberUpdatedState(rotation)
    val currentZoom by rememberUpdatedState(zoom)
    val currentVisible by rememberUpdatedState(visible)
    val currentPoints by rememberUpdatedState(points)
    val currentRadius by rememberUpdatedState(radius)
    val density = LocalDensity.current.density

    val infiniteTransition = rememberInfiniteTransition(label = "cerebral_bio_rhythm")
    val synapticClock by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 62.8318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "synaptic_clock"
    )
    val sparkPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spark_phase_1"
    )
    val sparkPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spark_phase_2"
    )
    val sparkPhase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spark_phase_3"
    )
    val membraneBreathing by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "membrane_breathing"
    )
    val synapticRipple by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "synaptic_ripple"
    )

    Canvas(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription =
                    "Interactive 3D cerebral cortex node graph. Orbit, pinch to zoom, tap any neuron to inspect."
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        touch(event.changes.any { it.pressed })
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, scale, twist ->
                    gesture(pan.x / density, pan.y / density, scale, twist)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { reset() },
                    onTap = { tap ->
                        val hits = currentVisible.map { node ->
                            node to projectGraph(
                                currentPoints.getValue(node.id),
                                currentRotation,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                currentRadius,
                                currentZoom
                            )
                        }.filter { (_, p) ->
                            (tap - Offset(p.x, p.y)).getDistance() <= 24.dp.toPx()
                        }
                        val hit = hits.minWithOrNull(
                            compareBy<Pair<LiveNode, GraphProjection>> { (_, p) ->
                                (tap - Offset(p.x, p.y)).getDistance()
                            }.thenByDescending { it.second.z }
                        )
                        select(hit?.first?.id)
                    }
                )
            }
    ) {
        // 1. Deep atmospheric intracranial cerebrospinal glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (light) Color(0x1800E5FF) else Color(0x220A1E35),
                    if (light) Color(0x0C38C79B) else Color(0x14061122),
                    Color.Transparent
                ),
                center = center,
                radius = size.minDimension * 0.78f
            ),
            radius = size.minDimension * 0.78f,
            center = center
        )

        val projected = points.mapValues { projectGraph(it.value, rotation, size.width, size.height, radius, zoom) }
        fun at(id: String) = projected.getValue(id).let { Offset(it.x, it.y) }
        fun emphasized(node: LiveNode) =
            (selected == null || node.id in neighbors) && (search.isBlank() || node.label.contains(search, true))

        // 2. Glial / Astrocyte Neural Dust tracing the cerebral cortex surface
        if (mesh) {
            val projectedMesh = scaffold.map {
                it to projectGraph(it.point, rotation, size.width, size.height, radius, zoom)
            }.sortedBy { it.second.z }

            projectedMesh.forEach { (particle, p) ->
                val pCenter = Offset(p.x, p.y)
                val tone = graphTone(particle.color, light)
                val pHash = ((particle.point.x * 73 + particle.point.y * 31 + particle.point.z * 19).toInt() and 0x7FFFFFFF)
                val pPhase = (pHash % 1000) / 1000f * 6.28318f
                val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(synapticClock * 2.2f + pPhase))
                val baseR = (1.4f + (pHash and 3) * 0.35f).dp.toPx() * p.perspective * sqrt(zoom)

                // Soft astrocyte bioluminescence
                drawCircle(
                    color = tone.copy(alpha = (if (light) 0.28f else 0.38f) * linkOpacity * twinkle),
                    radius = baseR * 1.8f,
                    center = pCenter
                )
                drawCircle(
                    color = lerp(tone, Color.White, 0.45f).copy(alpha = (if (light) 0.60f else 0.80f) * linkOpacity * twinkle),
                    radius = baseR,
                    center = pCenter
                )
            }
        }

        // Precompute 3D curved control points and 2D projections for every axon nerve fiber
        val projectedEdges = edges.mapNotNull { edge ->
            val from = lookup[edge.from] ?: return@mapNotNull null
            val to = lookup[edge.to] ?: return@mapNotNull null
            if (from.group in hidden || to.group in hidden) return@mapNotNull null

            val pFrom3D = points.getValue(edge.from)
            val pTo3D = points.getValue(edge.to)
            val isInterhemispheric = (pFrom3D.x * pTo3D.x < 0f) || (edge.from == "maximus" || edge.to == "maximus")

            val ctrl3D = if (isInterhemispheric) {
                // Arches naturally through the corpus callosum / central bridge
                SpacePoint(
                    (pFrom3D.x + pTo3D.x) * 0.32f,
                    min(pFrom3D.y, pTo3D.y) - 18f * spread,
                    (pFrom3D.z + pTo3D.z) * 0.5f + 12f * spread
                )
            } else {
                // Arches outward along the cerebral cortex mantle
                val mid = (pFrom3D + pTo3D) * 0.5f
                val norm = mid.normalized()
                mid + norm * (16f * spread)
            }

            val pProjFrom = projected.getValue(edge.from)
            val pProjTo = projected.getValue(edge.to)
            val pProjCtrl = projectGraph(ctrl3D, rotation, size.width, size.height, radius, zoom)

            Triple(edge, from to to, Triple(pProjFrom, pProjCtrl, pProjTo))
        }

        // 3. Curved Axon Nerve Bundles & Electric Action Potential Sparks
        projectedEdges.forEach { (edge, nodePair, projTriple) ->
            val (from, to) = nodePair
            val (pFrom, pCtrl, pTo) = projTriple

            val focused = selected != null && (edge.from == selected || edge.to == selected)
            val alpha = if (focused) 0.92f else if (selected != null) 0.04f else linkOpacity
            if (alpha <= 0.02f) return@forEach

            val fromTone = graphTone(from.color, light)
            val toTone = graphTone(to.color, light)
            val edgeTone = lerp(fromTone, toTone, 0.5f)

            val pA = Offset(pFrom.x, pFrom.y)
            val pC = Offset(pCtrl.x, pCtrl.y)
            val pB = Offset(pTo.x, pTo.y)

            // Curved 3D Axon Pathway
            val axonPath = Path().apply {
                moveTo(pA.x, pA.y)
                quadraticTo(pC.x, pC.y, pB.x, pB.y)
            }

            // A. Myelin Sheath Outer Glow
            drawPath(
                path = axonPath,
                color = edgeTone.copy(alpha = alpha * (if (focused) 0.30f else 0.12f)),
                style = Stroke(
                    width = (if (focused) 2.6.dp else 1.4.dp).toPx(),
                    cap = StrokeCap.Round
                )
            )

            // B. Core Neural Axon Fiber
            drawPath(
                path = axonPath,
                color = lerp(edgeTone, Color.White, 0.20f).copy(alpha = alpha * (if (focused) 0.85f else 0.45f)),
                style = Stroke(
                    width = (if (focused) 1.2.dp else 0.7.dp).toPx(),
                    cap = StrokeCap.Round
                )
            )

            // C. Electric Action Potential Sparks Traveling Along the Nerve
            val edgeHash = ((edge.from.hashCode() xor (edge.to.hashCode() * 43)) and 0x7FFFFFFF)
            val edgeOffset = (edgeHash % 1000) / 1000f
            val pulseCount = if (focused || (from.hub && to.hub)) 2 else 1

            for (pIdx in 0 until pulseCount) {
                val baseProgress = when ((pIdx + (edgeHash and 1)) % 3) {
                    0 -> sparkPhase1
                    1 -> sparkPhase2
                    else -> sparkPhase3
                }
                val t = (baseProgress + edgeOffset + pIdx * 0.45f) % 1f
                val u = 1f - t

                // Exact 2D position along quadratic Bezier curve
                val headX = u * u * pA.x + 2f * u * t * pC.x + t * t * pB.x
                val headY = u * u * pA.y + 2f * u * t * pC.y + t * t * pB.y
                val headPos = Offset(headX, headY)

                // Tangent vector along curve for electric comet tail orientation
                val tanX = 2f * u * (pC.x - pA.x) + 2f * t * (pB.x - pC.x)
                val tanY = 2f * u * (pC.y - pA.y) + 2f * t * (pB.y - pC.y)
                val tanLen = sqrt(tanX * tanX + tanY * tanY).coerceAtLeast(0.001f)
                val normX = -tanY / tanLen
                val normY = tanX / tanLen

                // Comet tail trailing behind the spark head
                val tailFraction = (t - 0.16f).coerceAtLeast(0f)
                val uTail = 1f - tailFraction
                val tailX = uTail * uTail * pA.x + 2f * uTail * tailFraction * pC.x + tailFraction * tailFraction * pB.x
                val tailY = uTail * uTail * pA.y + 2f * uTail * tailFraction * pC.y + tailFraction * tailFraction * pB.y
                val tailPos = Offset(tailX, tailY)

                // Depolarization wave intensity (eases in, peaks in mid-axon, flashes into terminal)
                val pulseIntensity = sin(t * PI.toFloat()).coerceIn(0f, 1f)
                val sparkAlpha = (if (focused) 0.95f else 0.75f) * pulseIntensity * alpha

                if (sparkAlpha > 0.04f) {
                    // Streaming electrical plasma comet tail
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                edgeTone.copy(alpha = sparkAlpha * 0.35f),
                                Color.White.copy(alpha = sparkAlpha * 0.85f)
                            ),
                            start = tailPos,
                            end = headPos
                        ),
                        start = tailPos,
                        end = headPos,
                        strokeWidth = (if (focused) 1.8.dp else 1.1.dp).toPx(),
                        cap = StrokeCap.Round
                    )

                    // Saltatory conduction micro-spark discharges (nodes of Ranvier electric crackle)
                    val jitter1 = ((sin(t * 37f + edgeHash) * 2.2f).dp.toPx())
                    val dischargePos = Offset(headX + normX * jitter1, headY + normY * jitter1)
                    drawCircle(
                        color = Color.White.copy(alpha = sparkAlpha * 0.70f),
                        radius = (if (focused) 1.1.dp else 0.8.dp).toPx(),
                        center = dischargePos
                    )
                    drawLine(
                        color = Color.White.copy(alpha = sparkAlpha * 0.45f),
                        start = headPos,
                        end = dischargePos,
                        strokeWidth = 0.6.dp.toPx()
                    )

                    // Radiant outer coronal bloom of the spark head
                    drawCircle(
                        color = edgeTone.copy(alpha = sparkAlpha * 0.25f),
                        radius = (if (focused) 4.5.dp else 3.2.dp).toPx(),
                        center = headPos
                    )

                    // High-energy ionization spark head
                    drawCircle(
                        color = lerp(edgeTone, Color.White, 0.60f).copy(alpha = sparkAlpha * 0.85f),
                        radius = (if (focused) 2.2.dp else 1.6.dp).toPx(),
                        center = headPos
                    )

                    // Pure white-hot electric spark core
                    drawCircle(
                        color = Color.White.copy(alpha = sparkAlpha),
                        radius = (if (focused) 1.2.dp else 0.8.dp).toPx(),
                        center = headPos
                    )

                    // Synaptic terminal excitation flash (when spark arrives at destination soma)
                    if (t > 0.88f) {
                        val arrivalFactor = (t - 0.88f) / 0.12f
                        val destCenter = pB
                        drawCircle(
                            color = toTone.copy(alpha = sparkAlpha * 0.30f * arrivalFactor),
                            radius = (4.dp.toPx() + 6.dp.toPx() * arrivalFactor),
                            center = destCenter,
                            style = Stroke(0.8.dp.toPx())
                        )
                    }
                }
            }
        }

        // 4. Volumetric 3D Neurons (Cellular Somas) with Realistic Bio-Lighting
        val labelRects = mutableListOf<android.graphics.RectF>()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        visible.sortedBy { projected.getValue(it.id).z }.forEach { node ->
            val p = projected.getValue(node.id)
            val center = Offset(p.x, p.y)
            val alpha = if (emphasized(node)) 1f else 0.15f
            val tone = graphTone(node.color, light)

            // Anatomically scaled 3D spherical radius
            val baseR = when {
                node.id == "maximus" -> 11.5f
                node.hub -> 8.2f
                else -> 4.2f
            }
            val r = baseR.dp.toPx() * p.perspective * sqrt(zoom)

            val nodeHash = ((node.id.hashCode() and 0x7FFFFFFF) % 1000) / 1000f
            val nodePhase = nodeHash * 6.28318f
            val breathing = 0.96f + 0.08f * sin(synapticClock * 1.8f + nodePhase)
            val effectiveR = r * breathing

            // A. Biological Synaptic Membrane Bloom
            val bloomR = effectiveR * (if (node.hub) 1.45f else 1.20f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tone.copy(alpha = 0.14f * alpha),
                        tone.copy(alpha = 0.03f * alpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = bloomR
                ),
                radius = bloomR,
                center = center
            )

            // B. Expanding Excitatory Postsynaptic Potential (EPSP) Ripple
            if ((node.hub || node.id == selected) && alpha > 0.35f) {
                val waveOffset = (nodeHash * 0.7f) % 1f
                val waveProg = (synapticRipple + waveOffset) % 1f
                val waveR = effectiveR * (1.05f + 1.30f * waveProg)
                val waveA = (1f - waveProg) * 0.12f * alpha
                drawCircle(
                    color = tone.copy(alpha = waveA),
                    radius = waveR,
                    center = center,
                    style = Stroke(width = (0.6f * (1f - waveProg * 0.3f)).dp.toPx())
                )
            }

            // C. Focus Selection Ring
            if (node.id == selected) {
                val haloR = effectiveR + 4.dp.toPx() + 5.dp.toPx() * membraneBreathing
                val haloA = (0.35f * (1f - membraneBreathing)).coerceIn(0f, 1f)
                drawCircle(
                    color = tone.copy(alpha = haloA),
                    radius = haloR,
                    center = center,
                    style = Stroke(1.2.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = effectiveR * 2.2f,
                    center = center
                )
            }

            // D. Radiating Dendritic Spines / Boutons connecting to membrane
            if (node.hub && alpha > 0.4f) {
                for (s in 0 until 6) {
                    val angle = s * (PI.toFloat() / 3f) + nodePhase
                    val spineInner = Offset(center.x + cos(angle) * effectiveR * 0.85f, center.y + sin(angle) * effectiveR * 0.85f)
                    val spineOuter = Offset(center.x + cos(angle) * effectiveR * 1.25f, center.y + sin(angle) * effectiveR * 1.25f)
                    drawLine(
                        color = tone.copy(alpha = 0.28f * alpha),
                        start = spineInner,
                        end = spineOuter,
                        strokeWidth = 0.8.dp.toPx()
                    )
                    drawCircle(
                        color = lerp(tone, Color.White, 0.4f).copy(alpha = 0.35f * alpha),
                        radius = 0.9.dp.toPx(),
                        center = spineOuter
                    )
                }
            }

            // E. Volumetric 3D Sphere with Biological Subsurface Scattering Shading
            val highlightOffset = Offset(
                center.x - effectiveR * 0.30f,
                center.y - effectiveR * 0.32f
            )
            val sphereShader = Brush.radialGradient(
                colors = listOf(
                    lerp(tone, Color.White, 0.38f).copy(alpha = 0.95f * alpha), // Key light specular core
                    lerp(tone, Color.White, 0.15f).copy(alpha = 0.90f * alpha), // Translucent cytoplasm
                    tone.copy(alpha = 0.95f * alpha),                           // Main biological soma tone
                    lerp(tone, Color.Black, if (light) 0.32f else 0.58f).copy(alpha = alpha), // Shadow limb
                    lerp(tone, Color.White, 0.22f).copy(alpha = 0.35f * alpha)  // Fresnel back-rim reflection
                ),
                center = highlightOffset,
                radius = effectiveR * 1.38f
            )
            drawCircle(
                brush = sphereShader,
                radius = effectiveR,
                center = center
            )

            // F. Dense Bioluminescent Nucleus / Mitochondrial Organelle Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f * alpha),
                        tone.copy(alpha = 0.45f * alpha),
                        Color.Transparent
                    ),
                    center = highlightOffset,
                    radius = effectiveR * 0.45f
                ),
                radius = effectiveR * 0.45f,
                center = highlightOffset
            )

            // G. Sharp Moist Specular Pinpoint Reflection (Living cell membrane)
            drawCircle(
                color = Color.White.copy(alpha = 0.92f * alpha),
                radius = (effectiveR * 0.18f).coerceAtLeast(1.2.dp.toPx()),
                center = highlightOffset
            )

            // H. Fine Bioluminescent Membrane Outer Rim
            drawCircle(
                color = lerp(tone, Color.White, 0.30f).copy(alpha = 0.28f * alpha),
                radius = effectiveR,
                center = center,
                style = Stroke(width = 0.5.dp.toPx())
            )

            // I. Clear Anatomical Node Typography
            if ((labels && node.hub || node.id == selected || search.isNotBlank() && node.label.contains(search, true)) && alpha > 0.25f) {
                paint.textSize = (if (node.id == selected) 11.5.sp else if (node.id == "maximus") 11.sp else 9.5.sp).toPx()
                paint.color = (if (light) Color(0xFF1E2A3A) else Color(0xFFEDF2F7)).copy(alpha = alpha).toArgb()
                paint.setShadowLayer(
                    3.dp.toPx(), 0f, 1.5.dp.toPx(),
                    if (light) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                )
                val width = paint.measureText(node.label)
                val rect = android.graphics.RectF(
                    center.x - width / 2 - 4,
                    center.y + effectiveR + 3,
                    center.x + width / 2 + 4,
                    center.y + effectiveR + paint.textSize + 7
                )
                if (node.id == selected || labelRects.none { android.graphics.RectF.intersects(it, rect) }) {
                    drawContext.canvas.nativeCanvas.drawText(
                        node.label,
                        center.x,
                        center.y + effectiveR + paint.textSize + 4,
                        paint
                    )
                    labelRects += rect
                }
            }
        }
    }
}

private fun graphTone(color: Color, light: Boolean): Color {
    if (!light) return color
    return when(color) {
        GraphAccent -> Color(0xFF087F68)
        GraphVisionCyan -> Color(0xFF00838F)
        GraphNewsAmber -> Color(0xFFE65100)
        GraphAlertsCrimson -> Color(0xFFC62828)
        GraphChatViolet -> Color(0xFF6A1B9A)
        GraphBlue -> Color(0xFF3469B7)
        GraphPurple -> Color(0xFF794AB3)
        GraphGold -> Color(0xFF9C7208)
        GraphRed -> Color(0xFFAD427F)
        GraphCyan -> Color(0xFF287C8F)
        GraphOrange -> Color(0xFFAF622D)
        GraphLime -> Color(0xFF637C29)
        else -> Color(0xFF8995A5)
    }
}

private fun buildTopology(
    llmConnected: Boolean,
    provider: String,
    model: String,
    dataConnected: Boolean,
    alertsCount: Int = 0,
    watchlistCount: Int = 4,
    activeSignalsCount: Int = 8,
    visionReady: Boolean = true
): Pair<List<LiveNode>, List<LiveEdge>> {
    val nodes = mutableListOf<LiveNode>()
    val edges = mutableListOf<LiveEdge>()

    fun hub(
        id: String,
        label: String,
        group: String,
        x: Float,
        y: Float,
        z: Float,
        color: Color,
        description: String,
        relations: String,
        lobe: String,
        region: String
    ) {
        nodes += LiveNode(id, label, group, x, y, z, 14f, color, description, relations, true, lobe, region)
    }

    fun child(
        parent: String,
        id: String,
        label: String,
        dx: Float,
        dy: Float,
        dz: Float,
        color: Color,
        description: String,
        region: String = ""
    ) {
        val p = nodes.first { it.id == parent }
        nodes += LiveNode(
            id, label, p.group,
            p.x + dx,
            p.y + dy,
            p.z + dz,
            4.6f, color, description, parent, false, p.lobe,
            if (region.isNotEmpty()) region else p.anatomicalRegion
        )
        edges += LiveEdge(parent, id, "CONTAINS")
    }

    // 1. MATRIX CORE (Midbrain Nucleus & Corpus Callosum Center)
    hub("maximus", "MAXIMUS", "MATRIX CORE", 0f, -8f, 5f, GraphAccent,
        "Central operating neural nucleus coordinating all AI systems, real-time intelligence feeds, vision analysis, and autonomous agents.",
        "Vision · News · Signals · Alerts · Chat · Agents · Gateway · Genome · Quant · Data · Mission",
        "MIDBRAIN CORE", "Thalamic Nucleus & Corpus Callosum")
    child("maximus", "core_kernel", "Matrix Kernel", -22f, -14f, 15f, GraphAccent, "High-performance reactive event bus and telemetry stream", "Left Internal Capsule")
    child("maximus", "quantum_state", "Session State", 22f, -14f, 15f, GraphAccent, "Reactive state store maintaining cross-module continuity", "Right Internal Capsule")

    // 2. RIGHT HEMISPHERE - SOMATOSENSORY & MOTOR (Live Trading Signals)
    hub("signals_hub", "Live Signals", "LIVE SIGNALS", 78f, -112f, 10f, GraphSignalsGreen,
        "Autonomous market signal matrix stored in Room database with $activeSignalsCount active setups across Crypto, Forex, Commodities, and Indices.",
        "Order Blocks · Scalping · Breakouts · Webhook Gateway · Push & Sound Alerts · Room DB",
        "PARIETAL LOBE", "Superior Somatosensory Strip")
    child("signals_hub", "signal_order_blocks", "Order Blocks", -18f, -24f, 22f, GraphSignalsGreen, "Institutional mitigation blocks and fair value gap reaction entries", "Brodmann Area 1")
    child("signals_hub", "signal_scalping", "Scalping Engine", 18f, -22f, 28f, GraphSignalsGreen, "M1/M5 momentum breakouts with tight invalidation and high win probability", "Brodmann Area 2")
    child("signals_hub", "signal_risk_matrix", "TP/SL Matrix", 32f, 8f, 8f, GraphSignalsGreen, "Automated take profit 1/2/3 scaling and dynamic stop loss guardrails", "Brodmann Area 3")
    child("signals_hub", "signal_webhook_gateway", "Webhook Gateway", 12f, -32f, -22f, GraphSignalsGreen, "Dedicated HTTP Webhook server on :8080 ingesting TradingView & Cloudflare signals", "Somatosensory Association")
    child("signals_hub", "signal_push_notifs", "Push Alerts & Sound", -14f, -18f, -32f, GraphSignalsGreen, "Instant Android push notifications & audible sound chimes for high-confidence signals", "Sensory Outflow Tract")
    child("signals_hub", "signal_room_store", "Room Persistence", -26f, 18f, 14f, GraphSignalsGreen, "Local zero-latency Room SQLite persistence for offline trade signal recall", "Parietal Memory Bridge")

    // 3. RIGHT HEMISPHERE - VISUAL CORTEX (AI Chart Vision)
    hub("vision_hub", "AI Chart Vision", "CHART VISION", 75f, -35f, -110f, GraphVisionCyan,
        "Computer vision technical analysis engine with candlestick OCR, pattern detection, market structure mapping, and trade setup generation.",
        "OCR · Patterns · Market Structure · Setups · Report · Maximus",
        "OCCIPITAL LOBE", "Primary Visual Cortex (V1-V4)")
    child("vision_hub", "vision_ocr", "Chart OCR", -18f, -22f, -18f, GraphVisionCyan, "High-resolution image ingestion, candlestick recognition and timeframe detection", "Striate Visual Area V1")
    child("vision_hub", "vision_patterns", "Pattern Detector", 20f, -16f, -12f, GraphVisionCyan, "Detects Bull/Bear Flags, Head & Shoulders, Order Blocks, and Fair Value Gaps", "Visual Area V2/V3")
    child("vision_hub", "vision_structure", "Market Structure", 32f, 10f, 16f, GraphVisionCyan, "Break of Structure (BOS), Change of Character (CHoCH), and Liquidity Sweeps", "Dorsal Stream Area MT")
    child("vision_hub", "vision_setups", "Setup Engine", -14f, 18f, -16f, GraphVisionCyan, "Confluence matrix, Entry/SL/TP calculations, and risk-adjusted reward sizing", "Ventral Form Area V4")
    child("vision_hub", "vision_report", "Report & Graph Sync", 12f, 32f, 8f, GraphVisionCyan, "Exports technical analysis reports and commits findings to Matrix Knowledge Graph", "Visual Association Cortex")

    // 4. RIGHT HEMISPHERE - TEMPORAL & SENTIMENT (News Intelligence)
    hub("news_hub", "News Intelligence", "NEWS INTELLIGENCE", 115f, -15f, 48f, GraphNewsAmber,
        "Live macroeconomic intelligence aggregating Forex Factory economic releases, multi-source RSS newsfeeds, and AI sentiment scoring.",
        "Forex Factory · Sentiment · Breaking News · Macro Calendar · Alerts · Maximus",
        "TEMPORAL LOBE", "Superior Temporal Gyrus")
    child("news_hub", "news_forex_factory", "Forex Factory Feed", 18f, -22f, -12f, GraphNewsAmber, "Live economic releases, actual vs consensus deviations, and volatility ratings", "Auditory-Temporal Cortex")
    child("news_hub", "news_sentiment", "AI Sentiment Engine", 22f, 18f, 10f, GraphNewsAmber, "Real-time bullish/bearish scoring across financial media and market commentaries", "Limbic Temporal Interface")
    child("news_hub", "news_breaking", "Breaking News", 6f, -16f, 32f, GraphNewsAmber, "Streaming fast-breaking global geopolitical and financial market catalysts", "Anterior Temporal Pole")
    child("news_hub", "news_macro_calendar", "Macro Calendar", -18f, 22f, 18f, GraphNewsAmber, "Central bank rate decisions, CPI inflation, Non-Farm Payrolls, and FOMC schedule", "Parahippocampal Belt")

    // 5. RIGHT HEMISPHERE - LIMBIC SYSTEM (Market Alerts Sentinel)
    hub("alerts_hub", "Alert Sentinel", "MARKET ALERTS", 50f, 6f, 22f, GraphAlertsCrimson,
        if (alertsCount > 0) "Real-time notification engine actively monitoring $watchlistCount pairs with $alertsCount recent alerts." else "Real-time notification sentinel monitoring $watchlistCount saved watchlist assets.",
        "Push Service · Watchlist · Urgency Filter · Ask AI · News Hub · Maximus",
        "LIMBIC SYSTEM", "Right Amygdaloid Complex")
    child("alerts_hub", "alert_service", "Notification Service", 16f, -18f, 14f, GraphAlertsCrimson, "Native Android notification channel with high priority, custom sound, and vibration alerts", "Autonomic Outflow Tract")
    child("alerts_hub", "alert_watchlist", "Watchlist Sentinel", 18f, 22f, 8f, GraphAlertsCrimson, "Active real-time pair monitoring (EUR/USD, XAU/USD, BTC/USD, GBP/USD, etc.)", "Hippocampal Indexer")
    child("alerts_hub", "alert_urgency", "Urgency Classifier", -12f, 16f, 22f, GraphAlertsCrimson, "Triages alerts into Critical Breaking, High Impact, and Elevated volatility", "Cingulate Cortex Node")
    child("alerts_hub", "alert_ask_ai", "Ask AI Dispatch", -16f, -18f, 6f, GraphAlertsCrimson, "One-tap dispatch sending alert context directly to Maximus AI Chat", "Thalamocortical Loop")

    // 6. RIGHT HEMISPHERE - EXECUTIVE FRONTAL CORTEX (Agent Factory & Mission Control)
    hub("agents", "Agent Factory", "AGENT RUNTIME", 65f, -65f, 92f, GraphAccent,
        "Registry of autonomous capability-gated trading, vision, news, and research agents.",
        "Executive · Vision · News · Trading · Pine · Quant · Mission · Maximus",
        "FRONTAL LOBE", "Dorsolateral Prefrontal Cortex")
    child("agents", "executive", "Executive Agent", -16f, -22f, 15f, GraphAccent, "Tool-aware mission coordinator orchestrating complex multi-step pipelines", "Prefrontal Area 9")
    child("agents", "vision_agent", "Vision Analyst", 20f, -16f, 18f, GraphAccent, "Autonomous technical chart price-action and geometry analyst", "Frontal Eye Field Area 8")
    child("agents", "news_agent", "News Sentinel", 30f, 10f, -4f, GraphAccent, "Real-time news reader synthesizing catalysts and sentiment", "Orbitofrontal Loop")
    child("agents", "trading_agent", "Trading Agent", -22f, 8f, 26f, GraphAccent, "Generates risk-adjusted execution setups and trade entry checklists", "Premotor Cortex Area 6")
    child("agents", "pine_agent", "Pine Agent", 8f, 24f, 22f, GraphAccent, "Translates natural language strategies into TradingView Pine Script v5", "Frontal Operculum")

    hub("mission", "Mission Control", "EXECUTION CONTROL", 32f, -95f, 78f, GraphPurple,
        "Mission planning, tool invocation, safety policy enforcement, and telemetry streaming.",
        "Planner · Policy · Telemetry Stream · Validation · Maximus",
        "FRONTAL LOBE", "Frontal Polar Area 10")
    child("mission", "planner", "Mission Planner", -14f, -20f, 14f, GraphPurple, "Decomposes complex trading prompts into structured task execution steps", "Rostrolateral Prefrontal")
    child("mission", "policy_engine", "Safety Policy", 24f, -16f, -10f, GraphPurple, "Enforces account risk limits and guardrails before strategy generation", "Ventromedial Prefrontal")
    child("mission", "event_stream", "Telemetry Stream", -16f, 15f, 18f, GraphPurple, "Real-time audit log of agent steps and execution milestones", "Anterior Cingulate Axis")

    // 7. LEFT HEMISPHERE - LANGUAGE & DIALOGUE (Conversational AI)
    hub("chat_hub", "Conversational AI", "CONVERSATIONAL AI", -115f, 10f, 30f, GraphChatViolet,
        "Interactive multimodal conversational engine for deep market debriefs, position sizing questions, and trade scenario planning.",
        "Multimodal · Context Memory · Dispatcher · Maximus · Models",
        "TEMPORAL LOBE", "Broca & Wernicke Centers")
    child("chat_hub", "chat_multimodal", "Multimodal Input", -18f, -24f, 12f, GraphChatViolet, "Hands-free voice recognition input and technical chart image attachments", "Superior Temporal Sulcus")
    child("chat_hub", "chat_context", "Session Memory", -16f, 22f, -12f, GraphChatViolet, "Preserves conversation history, trader preferences, and strategy assumptions", "Middle Temporal Gyrus")
    child("chat_hub", "chat_dispatcher", "Agent Dispatcher", 22f, 14f, 16f, GraphChatViolet, "Routes inquiries dynamically to specialized trading, quant, or pine agents", "Arcuate Fasciculus")

    // 8. LEFT HEMISPHERE - FRONTAL LOGIC & RISK (Trading Genome)
    hub("genome", "Trading Genome", "TRADING GENOME", -65f, -70f, 88f, GraphGold,
        "Indicator DNA, Strategy DNA, and reusable trading primitives registry.",
        "Indicators · Strategies · Risk Engine · Quant Lab · Maximus",
        "FRONTAL LOBE", "Left Prefrontal Logic Network")
    child("genome", "indicator_dna", "Indicator DNA", 16f, -20f, 18f, GraphGold, "Algorithmic lineage for RSI, MACD, Bollinger, Moving Averages, and ATR", "Dorsolateral Left Area 46")
    child("genome", "strategy_dna", "Strategy DNA", -24f, -14f, 16f, GraphGold, "Rulesets for Trend Following, Mean Reversion, Breakout, and SMC", "Frontal Area 9L")
    child("genome", "risk_engine", "Risk Engine", -14f, 16f, 26f, GraphGold, "Position sizing algorithms, Kelly Criterion, and drawdown circuit breakers", "Orbitofrontal Guardrail")

    // 9. LEFT HEMISPHERE - PARIETAL LOGIC & CODE (Pine Library)
    hub("pine", "Pine Library", "PINE ASSETS", -85f, -112f, -5f, GraphBlue,
        "TradingView Pine Script v5 repository, automated sanitizer, and code generation engine.",
        "Sanitizer · Classifier · Provenance · Genome · Maximus",
        "PARIETAL LOBE", "Left Superior Parietal Lobule")
    child("pine", "sanitizer", "Sanitizer", 18f, -22f, 20f, GraphBlue, "Code normalization, promo stripping, and syntax verification", "Angular Gyrus Area 39")
    child("pine", "classifier", "Classifier", -22f, -16f, -14f, GraphBlue, "Taxonomy of indicators, overlays, strategies, and libraries", "Supramarginal Gyrus Area 40")
    child("pine", "provenance", "Provenance", -24f, 16f, 16f, GraphBlue, "Immutable origin hash and copyright metadata", "Intraparietal Sulcus")

    // 10. LEFT HEMISPHERE - OCCIPITAL & STATISTICAL (Quant Lab)
    hub("quant", "Quant Lab", "QUANT RESEARCH", -78f, -35f, -108f, GraphCyan,
        "Backtest and experimental architecture for OOS, walk-forward, stress and sensitivity analysis.",
        "OOS · Walk Forward · Monte Carlo · Validation · Maximus",
        "OCCIPITAL LOBE", "Left Lateral Occipital Network")
    child("quant", "oos", "Out-of-Sample", 18f, -20f, -16f, GraphCyan, "Strict unseen data testing to eliminate curve-fitting and lookahead bias", "Occipitotemporal Junction")
    child("quant", "walk_forward", "Walk Forward", -24f, -12f, -6f, GraphCyan, "Rolling window optimization and parameter stability index", "Inferior Temporal Gyrus")
    child("quant", "monte_carlo", "Monte Carlo", -30f, 18f, 18f, GraphCyan, "Permutation testing and worst-case drawdown probability curves", "Fusiform Gyrus Area 37")

    // 11. LEFT HEMISPHERE - CEREBELLUM & PROOF (Validation Lab & Research Engine)
    hub("validation", "Validation Lab", "VALIDATION LAB", -62f, 65f, -72f, GraphOrange,
        "Mathematical evidence verification, risk-reward audit, and metric certification.",
        "Evidence Checker · Benchmark Suite · Quant Lab · Maximus",
        "CEREBELLAR", "Left Cerebellar Cortex")
    child("validation", "evidence", "Evidence Checker", 20f, 18f, -16f, GraphOrange, "Historical tick verification ensuring valid price action rules", "Dentate Nucleus")
    child("validation", "benchmark", "Benchmark Suite", -22f, 16f, -6f, GraphOrange, "Sharpe, Sortino, Calmar, and Maximum Adverse Excursion certification", "Cerebellar Vermis Interface")

    hub("research", "Research Engine", "RESEARCH MEMORY", -70f, -5f, -32f, GraphPurple,
        "Systematic hypothesis testing, research notebooks, and failure memory bank.",
        "Experiments · Failure Memory · Knowledge · Maximus",
        "TEMPORAL LOBE", "Hippocampal Memory Complex")
    child("research", "experiments", "Experiments Registry", -22f, -14f, -14f, GraphPurple, "Active research tracks, parameter matrices, and empirical findings", "Entorhinal Cortex")
    child("research", "failure_memory", "Failure Memory", 16f, 18f, -12f, GraphPurple, "Documented losing paradigms to prevent repeating past strategy errors", "Subiculum Formation")

    // 12. RIGHT HEMISPHERE - SUBCORTICAL & TEMPORAL (Data Center & AI Gateway & FinOps)
    hub("company", "Data Center", "DATA & KNOWLEDGE", 108f, 32f, -15f, GraphLime,
        if (dataConnected) "Local Room DB & vector knowledge base connected." else "Local storage online; cloud sync ready.",
        "Documents · Graph DB · Local Cache · Maximus · Research",
        "TEMPORAL LOBE", "Inferior Temporal Knowledge Hub")
    child("company", "documents", "Market Documents", 22f, -14f, -14f, GraphLime, "Trading playbooks, research PDFs, and institutional notes", "Parahippocampal Gyrus")
    child("company", "graph_db", "Matrix Graph DB", 16f, 22f, 18f, GraphLime, "Room database persisting knowledge nodes and saved trader notes", "Temporal Pole Network")
    child("company", "local_cache", "Offline Cache", -18f, 16f, -16f, GraphLime, "Local cache for historical charts, news articles, and offline setups", "Collateral Sulcus")

    hub("models", "AI Gateway", "MODEL ROUTING", 60f, 68f, -60f, GraphCyan,
        if (llmConnected) "Connected to $provider using $model." else "LLM API disconnected.",
        "Provider · Model · Embeddings · FinOps · Maximus",
        "CEREBELLAR", "Right Cerebellar Hemisphere")
    child("models", "provider", "Provider", -20f, 18f, -14f, GraphCyan, if (llmConnected) provider else "Offline", "Cerebellar Peduncle")
    child("models", "model", "Active Model", 20f, 16f, -6f, GraphCyan, if (llmConnected) model else "None selected", "Interposed Nucleus")
    child("models", "embedding_engine", "Embedding Engine", 4f, -18f, -22f, GraphCyan, "Semantic vector representations for neural retrieval", "Fastigial Relay")

    hub("api", "FinOps & Control", "FINOPS & CONTROL", 24f, 82f, -38f, GraphRed,
        "API credentials management, subscription billing, token analytics, and UI preferences.",
        "Token Usage · Secret Vault · Preferences · Models · Maximus",
        "BRAINSTEM", "Brainstem & Reticular Formation")
    child("api", "usage", "Token Usage", -15f, 18f, -12f, GraphRed, "Measured input and output token consumption per model", "Pontine Nuclei")
    child("api", "key_vault", "Secret Vault", 18f, 16f, 4f, GraphRed, "Secure encrypted storage for Gemini, OpenAI, and custom API keys", "Medulla Sentinel")
    child("api", "theme_engine", "Preferences", -6f, -12f, 18f, GraphRed, "Dark/Light mode, haptic feedback, and notification channel controls", "Reticular Activating Loop")

    // Inter-hemispheric and intra-cortical axon projections
    val allHubs = listOf(
        "signals_hub", "vision_hub", "news_hub", "alerts_hub", "chat_hub",
        "agents", "models", "genome", "pine", "quant",
        "company", "mission", "api", "validation", "research"
    )
    allHubs.forEach { edges += LiveEdge("maximus", it, "CORPUS_CALLOSUM_TRACT") }

    // Inter-hub structural and data-flow nerve tracts
    edges += listOf(
        // Sensory & Trading Signals pathways
        LiveEdge("signals_hub", "chat_hub", "INTERHEMISPHERIC_COMMISSURE"),
        LiveEdge("signals_hub", "vision_hub", "PARIETO_OCCIPITAL_TRACT"),
        LiveEdge("signals_hub", "company", "TEMPORO_PARIETAL_FASCICULUS"),
        LiveEdge("agents", "signals_hub", "FRONTAL_MOTOR_PROJECTION"),
        LiveEdge("alerts_hub", "signals_hub", "LIMBIC_SENSORY_RELAY"),

        // Visual Cortex pathways
        LiveEdge("vision_hub", "news_hub", "VENTRAL_STREAM_ASSOCIATION"),
        LiveEdge("vision_hub", "chat_hub", "INTERHEMISPHERIC_VISUAL_TRACT"),
        LiveEdge("vision_hub", "genome", "OCCIPITO_FRONTAL_FASCICULUS"),
        LiveEdge("vision_hub", "company", "INFERIOR_LONGITUDINAL_TRACT"),
        LiveEdge("agents", "vision_hub", "FRONTAL_VISUAL_FEEDBACK"),

        // News & Sentinel pathways
        LiveEdge("news_hub", "alerts_hub", "AMYGDALAR_TRIGGER_TRACT"),
        LiveEdge("news_hub", "chat_hub", "INTERHEMISPHERIC_COMMISSURE"),
        LiveEdge("news_hub", "company", "TEMPORAL_ASSOCIATION_BUNDLE"),
        LiveEdge("alerts_hub", "chat_hub", "LIMBIC_LANGUAGE_PROJECTION"),
        LiveEdge("agents", "news_hub", "UNCINATE_FASCICULUS"),

        // AI Gateway & Executive pathways
        LiveEdge("models", "chat_hub", "CEREBELLO_THALAMIC_TRACT"),
        LiveEdge("models", "agents", "PONTO_CEREBELLAR_LOOP"),
        LiveEdge("models", "api", "BRAINSTEM_METABOLIC_LINK"),
        LiveEdge("chat_hub", "agents", "SUPERIOR_LONGITUDINAL_FASCICULUS"),

        // Algorithmic & Verification pathways
        LiveEdge("pine", "genome", "FRONTOPARIETAL_CIRCUIT"),
        LiveEdge("genome", "quant", "FRONTAL_OCCIPITAL_TRACT"),
        LiveEdge("quant", "validation", "OCCIPITO_CEREBELLAR_TRACT"),
        LiveEdge("agents", "mission", "PREFRONTAL_AXIS"),
        LiveEdge("mission", "validation", "INTERNAL_CAPSULE_LOOP"),
        LiveEdge("company", "research", "HIPPOCAMPAL_FORNIX"),
        LiveEdge("company", "genome", "ANTERIOR_COMMISSURE")
    )

    return nodes to edges
}

