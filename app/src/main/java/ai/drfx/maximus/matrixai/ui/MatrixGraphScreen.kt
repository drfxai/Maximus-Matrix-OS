package ai.drfx.maximus.matrixai.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.speech.RecognizerIntent
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
private val GraphBlue = Color(0xFF578CDB)
private val GraphPurple = Color(0xFF9B72DA)
private val GraphGold = Color(0xFFE4B739)
private val GraphRed = Color(0xFFD664A2)
private val GraphCyan = Color(0xFF6ABCC9)
private val GraphOrange = Color(0xFFE99148)
private val GraphLime = Color(0xFFAAC56A)

private data class LiveNode(val id: String, val label: String, val group: String,
    val x: Float, val y: Float, val radius: Float, val color: Color,
    val description: String, val relations: String, val hub: Boolean = false)
private data class LiveEdge(val from: String, val to: String, val relation: String)
private data class MeshPoint(val point: SpacePoint, val parent: String, val color: Color)
private enum class GraphPanel { INSPECTOR, FILTERS }

@Composable
fun MatrixGraphScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
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
    val topology = remember(llm.status, llm.provider, llm.selectedModel, dataCenter.status.connected) {
        buildTopology(llm.status == ConnectionStatus.CONNECTED, llm.provider.name.replace('_', ' '),
            llm.selectedModel, dataCenter.status.connected)
    }
    val nodes = topology.first
    val edges = topology.second
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = nodes.find { it.id == selectedId }
    var search by remember { mutableStateOf("") }
    var hiddenGroups by remember { mutableStateOf(emptySet<String>()) }
    var openPanel by remember { mutableStateOf<GraphPanel?>(null) }
    var rotation by remember { mutableStateOf(GraphRotation().orbit(pitch = -.12f, yaw = .20f)) }
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
    fun reset() { rotation = GraphRotation().orbit(pitch = -.12f, yaw = .20f); zoom = 1f; selectedId = null; lastTouch = SystemClock.uptimeMillis() }
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

    val inspector: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MAXIMUS MATRIX OS", color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("${nodes.size} modules · ${edges.size} connections", color = muted, fontSize = 10.sp)
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search the matrix…", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) })
            Text("INSPECTOR", color = muted, fontSize = 9.sp, letterSpacing = 1.sp)
            Surface(color = bg, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, border)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(selected?.label ?: "Explore the matrix", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(selected?.description ?: "Tap a node to focus its connections. Drag to orbit in 3D. Twist with two fingers to roll.",
                        color = muted, fontSize = 11.sp, lineHeight = 16.sp)
                    if (selected != null) {
                        Text(selected.group, color = graphTone(selected.color, light), fontSize = 9.sp)
                        Text(selected.relations, color = muted, fontSize = 10.sp)
                        TextButton(onClick = { selectedId = null }) { Text("Clear focus") }
                    }
                }
            }
            Text(if (search.isBlank()) "TOP HUBS" else "SEARCH RESULTS", color = muted, fontSize = 9.sp, letterSpacing = 1.sp)
            nodes.filter { if (search.isBlank()) it.hub else it.label.contains(search, true) }.forEach { node ->
                Row(Modifier.fillMaxWidth().clickable { selectedId = node.id; hiddenGroups = hiddenGroups - node.group; openPanel = null }
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(graphTone(node.color, light), CircleShape))
                    Text(node.label, Modifier.weight(1f).padding(start = 7.dp), color = ink, fontSize = 11.sp)
                    Text(edges.count { it.from == node.id || it.to == node.id }.toString(), color = muted, fontSize = 10.sp)
                }
            }
            if (search.isNotBlank() && nodes.none { it.label.contains(search, true) }) Text("No matching modules", color = muted, fontSize = 11.sp)
            HorizontalDivider(color = border)
            Text("DISPLAY", color = muted, fontSize = 9.sp, letterSpacing = 1.sp)
            Text("Cluster spread", color = muted, fontSize = 11.sp)
            Slider(spread, { spread = it }, valueRange = .65f..1.5f)
            Text("Link visibility", color = muted, fontSize = 11.sp)
            Slider(linkOpacity, { linkOpacity = it }, valueRange = .12f.. .7f)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(labels, { labels = it }); Text("Node labels", color = ink, fontSize = 11.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(mesh, { mesh = it }); Text("Ambient neural mesh", color = ink, fontSize = 11.sp) }
            Text("Mesh particles are visual detail; module counts represent the application topology.", color = muted, fontSize = 10.sp)
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

    Column(modifier.background(bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MAXIMUS AI", color = ink, fontSize = 17.sp, letterSpacing = 1.sp, fontWeight = FontWeight.ExtraBold)
                Text("NEURAL WORKSPACE / 1.0.1", color = muted, fontSize = 9.sp, letterSpacing = .8.sp)
            }
            Box(Modifier.size(6.dp).background(if (status == "READY") GraphAccent else GraphGold, CircleShape))
            Text(status, Modifier.padding(start = 6.dp), color = graphTone(GraphAccent, light), fontSize = 10.sp)
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
                    NeuralScene(nodes, edges, rotation, zoom, spread, linkOpacity, labels, mesh, hiddenGroups, search, selectedId, light,
                        onGesture = { dx, dy, magnification, roll ->
                            rotation = rotation.orbit(pitch = dy * .006f, yaw = dx * .006f, roll = roll * PI.toFloat() / 180f)
                            zoom = (zoom * magnification).coerceIn(.55f, 3.5f); lastTouch = SystemClock.uptimeMillis()
                        }, onTouch = { touching = it; lastTouch = SystemClock.uptimeMillis() },
                        onSelect = { selectedId = it }, onReset = { reset() })
                    Surface(Modifier.align(Alignment.TopCenter).padding(top = 5.dp), color = panelColor,
                        shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, border)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!wide) IconButton(onClick = { openPanel = GraphPanel.INSPECTOR }) { Icon(Icons.Default.Search, "Search and inspect nodes", tint = ink, modifier = Modifier.size(18.dp)) }
                            TextButton(onClick = { reset() }) { Text("Fit", color = ink, fontSize = 12.sp) }
                            IconButton(onClick = { autoRotate = !autoRotate }) { Icon(if (autoRotate) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (autoRotate) "Pause automatic rotation" else "Start automatic rotation", tint = graphTone(GraphPurple, light), modifier = Modifier.size(18.dp)) }
                            if (!wide) IconButton(onClick = { openPanel = GraphPanel.FILTERS }) { Icon(Icons.Default.FilterList, "Filter node groups", tint = ink, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    if (!isCompact) {
                        Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${nodes.count { it.group !in hiddenGroups }} MODULES · ${edges.count { edge -> nodes.none { it.group in hiddenGroups && (it.id == edge.from || it.id == edge.to) } }} CONNECTIONS", color = muted, fontSize = 8.sp, letterSpacing = .6.sp)
                            Text("Drag X/Y · Twist Z · Pinch zoom", color = muted, fontSize = 10.sp)
                            Text(if (selected != null) "FOCUS LOCKED" else if (autoRotate) "AUTO ORBIT" else "MANUAL ORBIT",
                                color = graphTone(GraphAccent, light), fontSize = 8.sp, letterSpacing = 1.sp)
                        }
                    }
                    if (!wide && !isCompact) IntelligenceRing(status, light, Modifier.align(Alignment.BottomEnd).padding(10.dp).size(78.dp))
                    if (!wide && selected != null) Surface(Modifier.align(Alignment.TopStart).padding(top = 62.dp, start = 10.dp, end = 10.dp).fillMaxWidth(),
                        color = panelColor, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, graphTone(selected.color, light))) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { openPanel = GraphPanel.INSPECTOR }.padding(vertical = 10.dp)) {
                                Text(selected.label, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${edges.count { it.from == selected.id || it.to == selected.id }} connections · Tap for details", color = muted, fontSize = 10.sp)
                            }
                            IconButton(onClick = { selectedId = null }) { Icon(Icons.Default.Close, "Clear node focus", tint = ink, modifier = Modifier.size(18.dp)) }
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
        Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), color = panelColor,
            shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (mission.isBlank()) {
                    Text(events.firstOrNull()?.message ?: "Your matrix is ready. Explore a node or describe a mission.",
                        color = muted, fontSize = 11.sp, maxLines = 1, lineHeight = 15.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(mission, { mission = it }, Modifier.weight(1f), singleLine = true,
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
            }
        }
    }
}

@Composable
private fun IntelligenceRing(status: String, light: Boolean, modifier: Modifier) {
    val purple = graphTone(GraphPurple, light)
    Box(modifier.semantics { contentDescription = "MAXIMUS engine $status" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * .40f
            drawCircle(Brush.radialGradient(listOf(purple.copy(alpha = .13f), Color.Transparent), radius = r*1.2f), r*1.2f)
            repeat(64) { i ->
                val a = i * PI.toFloat() / 32f
                val unit = Offset(cos(a), sin(a))
                drawLine(purple.copy(alpha = if (i % 4 == 0) .8f else .4f), center + unit * r, center + unit * (r + if (i % 4 == 0) 6.dp.toPx() else 3.dp.toPx()), 1.dp.toPx())
            }
            drawCircle(purple.copy(alpha = .4f), r * .91f, style = Stroke(1.dp.toPx()))
            drawArc(purple, 200f, 210f, false, Offset(center.x-r*.85f,center.y-r*.85f), Size(r*1.7f,r*1.7f), style = Stroke(2.dp.toPx()))
            drawCircle(graphTone(GraphAccent, light).copy(alpha = .4f), r * .54f, style = Stroke(.7.dp.toPx()))
        }
        Text("M.A.X.", color = if (light) Color(0xFF40365F) else Color(0xFFD9CBF7), fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
    }
}

private fun world(node: LiveNode, spread: Float): SpacePoint = SpacePoint(node.x * spread, node.y * spread,
    if (node.id == "maximus") 0f else (((node.id.hashCode() ushr 3) and 511) - 255f) * .85f)

@Composable
private fun NeuralScene(nodes: List<LiveNode>, edges: List<LiveEdge>, rotation: GraphRotation, zoom: Float,
    spread: Float, linkOpacity: Float, labels: Boolean, mesh: Boolean, hidden: Set<String>, search: String,
    selected: String?, light: Boolean, onGesture: (Float, Float, Float, Float) -> Unit,
    onTouch: (Boolean) -> Unit, onSelect: (String?) -> Unit, onReset: () -> Unit) {
    val points = remember(nodes, spread) {
        nodes.associate { node ->
            val p = world(node, spread)
            val parent = nodes.find { it.id == node.relations }
            node.id to if (parent != null) p.copy(z = world(parent, spread).z + p.z * .28f) else p
        }
    }
    val lookup = remember(nodes) { nodes.associateBy { it.id } }
    // Decorative scaffolding gives depth and density without inventing application modules.
    val scaffold = remember(nodes, spread) {
        nodes.filter { it.hub }.flatMapIndexed { h, node ->
            val p = world(node, spread)
            List(20) { i ->
                val a = (i * 2.39996 + h).toFloat()
                val y = 1f - 2f * (i + .5f) / 20f
                val r = sqrt(1f - y*y)
                val distance = (60f + (i * 17 % 70)) * spread
                MeshPoint(SpacePoint(p.x + cos(a)*r*distance, p.y+y*distance, p.z+sin(a)*r*distance), node.id,
                    if (i % 4 == 0) Color(0xFF565B6B) else node.color)
            }
        }
    }
    val radius = remember(points, scaffold) { max(points.values.maxOf { it.length }, scaffold.maxOf { it.point.length }) }
    val visible = remember(nodes, hidden) { nodes.filter { it.group !in hidden } }
    val neighbors = remember(selected, edges) { edges.filter { it.from == selected || it.to == selected }.flatMap { listOf(it.from, it.to) }.toSet() }
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
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Interactive 3D node graph. Drag to rotate X and Y, twist two fingers for Z, pinch to zoom, double tap to fit. Use the inspector to select modules by name." }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) { val event = awaitPointerEvent(PointerEventPass.Initial); touch(event.changes.any { it.pressed }) }
            }
        }
        .pointerInput(Unit) { detectTransformGestures { _, pan, scale, twist -> gesture(pan.x / density, pan.y / density, scale, twist) } }
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { reset() }, onTap = { tap ->
                val hits = currentVisible.map { node ->
                    node to projectGraph(currentPoints.getValue(node.id), currentRotation, size.width.toFloat(), size.height.toFloat(), currentRadius, currentZoom)
                }.filter { (_, p) -> (tap - Offset(p.x, p.y)).getDistance() <= 22.dp.toPx() }
                // Distance chooses the intended sphere; depth breaks ties for overlapping spheres.
                val hit = hits.minWithOrNull(compareBy<Pair<LiveNode, GraphProjection>> { (_, p) -> (tap-Offset(p.x,p.y)).getDistance() }.thenByDescending { it.second.z })
                select(hit?.first?.id)
            })
        }) {
        val projected = points.mapValues { projectGraph(it.value, rotation, size.width, size.height, radius, zoom) }
        fun at(id: String) = projected.getValue(id).let { Offset(it.x, it.y) }
        fun emphasized(node: LiveNode) = (selected == null || node.id in neighbors) && (search.isBlank() || node.label.contains(search, true))
        if (mesh) {
            val projectedMesh = scaffold.map { it to projectGraph(it.point, rotation, size.width, size.height, radius, zoom) }.sortedBy { it.second.z }
            projectedMesh.forEach { (particle, p) ->
                val parent = lookup.getValue(particle.parent)
                if (parent.group in hidden) return@forEach
                val alpha = if (emphasized(parent)) 1f else .12f
                val center = Offset(p.x,p.y)
                val tone = graphTone(particle.color, light)
                drawLine(tone.copy(alpha = linkOpacity * .3f * alpha), at(particle.parent), center, .45.dp.toPx())
                drawCircle(tone.copy(alpha = (if (light) .5f else .46f) * alpha), (1.4f + (particle.point.x.toInt() and 3)*.35f).dp.toPx()*p.perspective*sqrt(zoom), center)
            }
        }
        edges.forEach { edge ->
            val from = lookup.getValue(edge.from); val to = lookup.getValue(edge.to)
            if (from.group in hidden || to.group in hidden) return@forEach
            val focused = selected != null && (edge.from == selected || edge.to == selected)
            val alpha = if (focused) .8f else if (selected != null) .04f else linkOpacity
            drawLine(graphTone(from.color, light).copy(alpha = alpha), at(edge.from), at(edge.to), (if (focused) .9f else .55f).dp.toPx())
        }
        val labelRects = mutableListOf<android.graphics.RectF>()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT }
        visible.sortedBy { projected.getValue(it.id).z }.forEach { node ->
            val p = projected.getValue(node.id)
            val center = Offset(p.x,p.y)
            val alpha = if (emphasized(node)) 1f else .15f
            val r = (if (node.hub) 5.8f else 3.1f).dp.toPx()*p.perspective*sqrt(zoom)
            val tone = graphTone(node.color, light)
            if (node.id == selected) {
                drawCircle(tone.copy(alpha = .10f), r*3f, center)
                drawCircle(tone.copy(alpha = .8f), r+4.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            }
            drawCircle(Brush.radialGradient(listOf(lerp(tone, Color.White, .50f).copy(alpha = alpha), tone.copy(alpha = alpha),
                lerp(tone, if (light) Color.White else Color.Black, .25f).copy(alpha = alpha)),
                center-Offset(r*.3f,r*.35f), r*1.6f), r, center)
            drawCircle(tone.copy(alpha = .4f*alpha), r, center, style = Stroke(.5.dp.toPx()))
            if ((labels && node.hub || node.id == selected || search.isNotBlank() && node.label.contains(search,true)) && alpha > .2f) {
                paint.textSize = (if (node.id == selected) 11.sp else 9.sp).toPx()
                paint.color = (if (light) Color(0xFF253446) else Color(0xFFCED5DF)).copy(alpha = alpha).toArgb()
                paint.setShadowLayer(2.dp.toPx(), 0f, 1f, if (light) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                val width = paint.measureText(node.label)
                val rect = android.graphics.RectF(center.x-width/2-3,center.y+r+2,center.x+width/2+3,center.y+r+paint.textSize+6)
                if (node.id == selected || labelRects.none { android.graphics.RectF.intersects(it,rect) }) {
                    drawContext.canvas.nativeCanvas.drawText(node.label, center.x, center.y+r+paint.textSize+4,paint)
                    labelRects += rect
                }
            }
        }
    }
}

private fun graphTone(color: Color, light: Boolean): Color {
    if (!light) return color
    return when(color) {
        GraphAccent -> Color(0xFF087F68); GraphBlue -> Color(0xFF3469B7)
        GraphPurple -> Color(0xFF794AB3); GraphGold -> Color(0xFF9C7208)
        GraphRed -> Color(0xFFAD427F); GraphCyan -> Color(0xFF287C8F)
        GraphOrange -> Color(0xFFAF622D); GraphLime -> Color(0xFF637C29)
        else -> Color(0xFF8995A5)
    }
}
private fun buildTopology(
    llmConnected: Boolean,
    provider: String,
    model: String,
    dataConnected: Boolean
): Pair<List<LiveNode>, List<LiveEdge>> {
    val nodes = mutableListOf<LiveNode>()
    val edges = mutableListOf<LiveEdge>()

    fun hub(id: String, label: String, group: String, x: Float, y: Float, color: Color, description: String, relations: String) {
        nodes += LiveNode(id, label, group, x, y, 16f, color, description, relations, true)
    }
    fun child(parent: String, id: String, label: String, angle: Float, distance: Float, color: Color, description: String) {
        val p = nodes.first { it.id == parent }
        nodes += LiveNode(
            id, label, p.group,
            p.x + cos(angle.toDouble()).toFloat() * distance,
            p.y + sin(angle.toDouble()).toFloat() * distance,
            5f, color, description, parent, false
        )
        edges += LiveEdge(parent, id, "CONTAINS")
    }

    hub("maximus", "MAXIMUS", "MATRIX CORE", 0f, 0f, GraphAccent, "Central operating graph for MAXIMUS AI and internal Matrix OS engines.", "Mission Control · Agent Factory · Models · Knowledge · Research · Validation")
    hub("models", "AI Gateway", "MODEL ROUTING", 210f, -40f, GraphCyan, if (llmConnected) "Connected to $provider using $model." else "No LLM API is connected.", "Models · Compatible Agents · Usage")
    hub("agents", "Agent Factory", "AGENT RUNTIME", 260f, 150f, GraphAccent, "Registry of capability-gated MAXIMUS agents.", "Executive · Research · Trading · Pine · Code · Validation · Automation · Quant")
    hub("mission", "Mission Control", "EXECUTION", 70f, 230f, GraphPurple, "Plans missions, applies policy, invokes tools and records execution events.", "Planner · Policy · Tools · Validation · Artifacts")
    hub("pine", "Pine Library", "PINE ASSETS", -250f, -135f, GraphBlue, "Gateway for the company Pine Script source library. Counts are shown only when a real data center reports them.", "Sources · Sanitization · Classification · Provenance")
    hub("genome", "Trading Genome", "TRADING INTELLIGENCE", -75f, -225f, GraphGold, "Indicator DNA, Strategy DNA and reusable trading primitives.", "Indicators · Strategies · Primitive Registry · Quant Lab")
    hub("research", "Research Engine", "RESEARCH MEMORY", 80f, -225f, GraphPurple, "Research missions, evidence, hypotheses, failures and reproducibility.", "Knowledge · Experiments · Failure Memory · Artifacts")
    hub("quant", "Quant Lab", "QUANT RESEARCH", -255f, 95f, GraphCyan, "Backtest and experimental architecture for OOS, walk-forward, stress and sensitivity analysis.", "Datasets · Experiments · Metrics · Strategy DNA")
    hub("company", "Company Data", "COMPANY KNOWLEDGE", -130f, 245f, GraphCyan, if (dataConnected) "Company data backend connected." else "Company data backend is not connected.", "Documents · Projects · Pine Sources · Research")
    hub("primitive", "Primitive Registry", "REUSABLE LOGIC", -300f, -10f, GraphLime, "Registry for reusable signal, filter, risk and exit components.", "Trading Genome · Pine Library · Validation")
    hub("validation", "Validation Lab", "VALIDATION", 295f, -180f, GraphOrange, "Evidence checks and validation states for tools, models and research artifacts.", "Policy · Tools · Models · Artifacts")
    hub("api", "API Control", "FINOPS", 300f, 15f, GraphRed, "Provider, subscription metadata and measured token usage.", "AI Gateway · Models · Usage · Budget")

    listOf("models","agents","mission","pine","genome","research","quant","company","primitive","validation","api").forEach { edges += LiveEdge("maximus", it, "ORCHESTRATES") }
    edges += listOf(
        LiveEdge("models","agents","SUPPORTS"),
        LiveEdge("models","api","MEASURED_BY"),
        LiveEdge("agents","mission","EXECUTES"),
        LiveEdge("pine","primitive","EXTRACTS"),
        LiveEdge("primitive","genome","FEEDS"),
        LiveEdge("genome","quant","TESTED_BY"),
        LiveEdge("research","company","RETRIEVES_FROM"),
        LiveEdge("research","validation","VALIDATED_BY"),
        LiveEdge("mission","validation","VALIDATED_BY"),
        LiveEdge("company","pine","CONTAINS"),
        LiveEdge("company","genome","REFERENCES")
    )

    child("models","provider","Provider", .2f, 60f, GraphCyan, if (llmConnected) provider else "Disconnected")
    child("models","model","Selected Model", 1.6f, 62f, GraphCyan, if (llmConnected) model else "None")
    child("agents","executive","Executive", .2f, 62f, GraphAccent, "Tool-aware mission coordinator")
    child("agents","research_agent","Research Agent", 1.2f, 65f, GraphAccent, "Evidence synthesis and research")
    child("agents","trading_agent","Trading Agent", 2.2f, 60f, GraphAccent, "Trading intelligence")
    child("agents","pine_agent","Pine Agent", 3.2f, 60f, GraphAccent, "Pine source intelligence")
    child("pine","sanitizer","Sanitizer", .4f, 65f, GraphBlue, "Promo, identity and normalization pipeline")
    child("pine","classifier","Classifier", 1.6f, 70f, GraphBlue, "Semantic feature taxonomy")
    child("pine","provenance","Provenance", 2.8f, 62f, GraphBlue, "Immutable origin and license metadata")
    child("genome","indicator_dna","Indicator DNA", .2f, 65f, GraphGold, "Indicator component lineage")
    child("genome","strategy_dna","Strategy DNA", 1.4f, 68f, GraphGold, "Strategy composition lineage")
    child("genome","risk_engine","Risk Engine", 2.7f, 63f, GraphGold, "Reusable risk components")
    child("research","failure_memory","Failure Memory", .2f, 65f, GraphPurple, "Rejected experiments and failure knowledge")
    child("research","experiments","Experiments", 1.5f, 68f, GraphPurple, "Research experiment registry")
    child("quant","oos","Out-of-Sample", .5f, 64f, GraphCyan, "OOS validation architecture")
    child("quant","walk_forward","Walk Forward", 1.8f, 68f, GraphCyan, "Walk-forward architecture")
    child("company","documents","Documents", .4f, 62f, GraphCyan, "Company documents and specifications")
    child("company","projects","Projects", 1.6f, 64f, GraphCyan, "Company project artifacts")
    child("validation","evidence","Evidence", .3f, 64f, GraphOrange, "Validation evidence state")
    child("api","usage","Usage", .7f, 58f, GraphRed, "Measured API token usage")
    child("api","subscription","Subscription", 2.2f, 60f, GraphRed, "Local subscription and budget metadata")

    return nodes to edges
}
