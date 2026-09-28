package ai.drfx.maximus.matrixai.ui

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.agent.MatrixEvent
import ai.drfx.maximus.matrixai.llm.ConnectionStatus
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min

private val GraphBg = Color(0xFF020405)
private val GraphPanel = Color(0xFF071110)
private val GraphBorder = Color(0xFF15342D)
private val GraphText = Color(0xFFDCE8E7)
private val GraphMuted = Color(0xFF87A29B)
private val GraphAccent = Color(0xFF5CF0BC)
private val GraphBlue = Color(0xFF4C9EFF)
private val GraphPurple = Color(0xFF9B72FF)
private val GraphGold = Color(0xFFF3B735)
private val GraphRed = Color(0xFFE65D83)
private val GraphCyan = Color(0xFF6AC7D8)
private val GraphOrange = Color(0xFFEF8E54)
private val GraphLime = Color(0xFFB2D15B)

private data class LiveNode(
    val id: String,
    val label: String,
    val group: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val description: String,
    val relations: String,
    val hub: Boolean = false
)

private data class LiveEdge(val from: String, val to: String, val relation: String)

@Composable
fun MatrixGraphScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val events by viewModel.events.collectAsState()
    val status by viewModel.status.collectAsState()
    val llm by viewModel.llmState.collectAsState()
    val dataCenter by viewModel.dataCenter.collectAsState()

    var selected by remember { mutableStateOf<LiveNode?>(null) }
    var mission by remember { mutableStateOf("") }

    val topology = remember(llm.status, llm.provider, llm.selectedModel, dataCenter.status.connected) {
        buildTopology(
            llmConnected = llm.status == ConnectionStatus.CONNECTED,
            provider = llm.provider.name.replace('_', ' '),
            model = llm.selectedModel,
            dataConnected = dataCenter.status.connected
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NEURAL MATRIX", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text("LIVE OPERATING GRAPH", color = GraphMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                }
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(status, color = if (status == "READY") GraphAccent else GraphGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${topology.first.size} NODES", color = GraphMuted, fontSize = 8.sp)
                    }
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(10.dp)) {
                    Text("NEURAL MATRIX   •   Pinch to zoom   •   Tap a node", color = GraphMuted, fontSize = 10.sp, letterSpacing = .4.sp)
                    Spacer(Modifier.height(7.dp))
                    LiveMatrixCanvas(
                        nodes = topology.first,
                        edges = topology.second,
                        events = events,
                        selected = selected,
                        onNodeSelected = { selected = it },
                        onDismiss = { selected = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(630.dp)
                            .background(GraphBg, RoundedCornerShape(18.dp))
                    )
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Mission Control", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = mission,
                            onValueChange = { mission = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Describe a mission") }
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.runMission(mission); mission = "" },
                            enabled = mission.isNotBlank() && status != "EXECUTING",
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E3A2E), contentColor = GraphAccent)
                        ) { Text("Run") }
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Live Matrix Events", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(7.dp))
                        events.take(6).forEachIndexed { index, event ->
                            Text(event.type.name.replace('_', ' '), color = eventColor(event), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(event.message, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, lineHeight = 16.sp)
                            if (index != events.take(6).lastIndex) {
                                Spacer(Modifier.height(6.dp))
                                HorizontalDivider(color = GraphBorder)
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMatrixCanvas(
    nodes: List<LiveNode>, edges: List<LiveEdge>, events: List<MatrixEvent>,
    selected: LiveNode?, onNodeSelected: (LiveNode) -> Unit, onDismiss: () -> Unit,
    modifier: Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val animation = rememberInfiniteTransition(label = "neural matrix")
    val packet by animation.animateFloat(0f, 1f, infiniteRepeatable(tween(3500), RepeatMode.Restart), label = "signal")
    val pulse by animation.animateFloat(.95f, 1.08f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow")
    val lookup = remember(nodes) { nodes.associateBy { it.id } }
    val active = events.firstOrNull()?.let { (it.sourceNode + " " + (it.targetNode ?: "")).lowercase() }.orEmpty()

    Box(modifier.background(Brush.radialGradient(listOf(Color(0xFF12322F), Color(0xFF091725), GraphBg), radius = 1100f))) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(nodes) {
                detectTransformGestures { _, translation, magnification, _ ->
                    zoom = (zoom * magnification).coerceIn(.55f, 2.6f)
                    pan += translation
                }
            }
            .pointerInput(nodes, zoom, pan, selected) {
                detectTapGestures(
                    onDoubleTap = { zoom = 1f; pan = Offset.Zero; onDismiss() },
                    onTap = { tap ->
                        if (selected != null && tap.y > size.height - 170.dp.toPx()) return@detectTapGestures
                        val hit = nodes.filter { it.hub }.minByOrNull {
                            (tap - project(it, size.width.toFloat(), size.height.toFloat(), zoom, pan)).getDistance()
                        }
                        if (hit != null && (tap - project(hit, size.width.toFloat(), size.height.toFloat(), zoom, pan)).getDistance() < 42.dp.toPx()) {
                            onNodeSelected(hit)
                        }
                    }
                )
            }
        ) {
            val w = size.width
            val h = size.height
            fun position(node: LiveNode) = project(node, w, h, zoom, pan)
            // Deterministic ambient stars, independent from application status.
            repeat(145) { i ->
                val p = Offset(((i * 97 + 41) % 157) / 157f * w, ((i * 131 + 23) % 163) / 163f * h)
                drawCircle(listOf(GraphCyan, GraphPurple, GraphGold, GraphAccent)[i % 4].copy(alpha = if (i % 13 == 0) .52f else .19f),
                    (if (i % 13 == 0) 1.6f else .7f).dp.toPx(), p)
            }
            edges.forEachIndexed { i, edge ->
                val from = lookup[edge.from] ?: return@forEachIndexed
                val to = lookup[edge.to] ?: return@forEachIndexed
                val a = position(from)
                val b = position(to)
                val control = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f - (if (from.hub && to.hub) 16f else 5f).dp.toPx())
                val path = Path().apply { moveTo(a.x, a.y); quadraticTo(control.x, control.y, b.x, b.y) }
                val lit = active.contains(from.id.lowercase()) || active.contains(to.id.lowercase())
                drawPath(path, from.color.copy(alpha = if (lit) .16f else .05f), style = Stroke(6.dp.toPx()))
                drawPath(path, from.color.copy(alpha = if (lit) .8f else if (from.hub && to.hub) .48f else .2f),
                    style = Stroke(if (lit) 1.6.dp.toPx() else .8.dp.toPx()))
                if (i % 3 == 0) {
                    val t = (packet + i * .113f) % 1f
                    val inv = 1f - t
                    drawCircle(from.color.copy(alpha = .8f), 1.7.dp.toPx(),
                        Offset(inv * inv * a.x + 2 * inv * t * control.x + t * t * b.x,
                            inv * inv * a.y + 2 * inv * t * control.y + t * t * b.y))
                }
            }
            nodes.sortedBy { depth(it) }.forEach { node ->
                val p = position(node)
                if (p.x < -80 || p.x > w + 80 || p.y < -80 || p.y > h + 80) return@forEach
                val radius = node.radius.dp.toPx() * zoom *
                    (1.2f - depth(node) * .002f).coerceIn(.7f, 1.4f) * (if (node.hub) 1.35f else .68f)
                if (node.hub) {
                    drawCircle(brush = Brush.radialGradient(listOf(node.color.copy(alpha = .26f), node.color.copy(alpha = 0f)),
                        center = p, radius = radius * 2.8f * pulse), radius = radius * 2.8f * pulse, center = p)
                    drawOval(node.color.copy(alpha = .43f), topLeft = Offset(p.x - radius * 1.65f, p.y + radius * .4f),
                        size = Size(radius * 3.3f, radius * .9f), style = Stroke(1.dp.toPx()))
                    drawOval(node.color.copy(alpha = .23f), topLeft = Offset(p.x - radius * 2f, p.y + radius * .2f),
                        size = Size(radius * 4f, radius * 1.4f), style = Stroke(.7.dp.toPx()))
                }
                drawCircle(brush = Brush.radialGradient(
                    listOf(Color.White, node.color, node.color.copy(alpha = .65f), Color(0xFF08131C)),
                    center = Offset(p.x - radius * .28f, p.y - radius * .31f), radius = radius * 1.65f),
                    radius = radius, center = p)
                drawCircle(node.color.copy(alpha = if (selected?.id == node.id) .95f else .47f),
                    radius * (if (selected?.id == node.id) 1.25f else 1.05f), p, style = Stroke(1.dp.toPx()))
                if (node.hub) {
                    val centerLabel = node.id == "maximus"
                    drawContext.canvas.nativeCanvas.drawText(node.label, p.x,
                        if (centerLabel) p.y + 4.dp.toPx() else p.y + radius + 14.dp.toPx(),
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = GraphText.toArgb()
                            textSize = (if (centerLabel) 13.sp else 10.sp).toPx()
                            typeface = android.graphics.Typeface.create(null, android.graphics.Typeface.BOLD)
                            textAlign = Paint.Align.CENTER
                            setShadowLayer(5.dp.toPx(), 0f, 1f, android.graphics.Color.BLACK)
                        })
                }
            }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallGraphButton("+") { zoom = (zoom * 1.2f).coerceAtMost(2.6f) }
            SmallGraphButton("−") { zoom = (zoom / 1.2f).coerceAtLeast(.55f) }
            SmallGraphButton("⌂") { zoom = 1f; pan = Offset.Zero; onDismiss() }
        }
        if (selected != null) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
                color = Color(0xF2091320), shape = RoundedCornerShape(18.dp),
                shadowElevation = 15.dp, border = BorderStroke(1.dp, selected.color.copy(alpha = .85f))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).background(selected.color.copy(alpha = .22f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(11.dp).background(selected.color, CircleShape))
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selected.label, color = GraphText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(selected.group, color = selected.color, fontSize = 10.sp)
                        }
                        Text("✕", Modifier.clickable(onClick = onDismiss).padding(8.dp), color = GraphText, fontSize = 16.sp)
                    }
                    Text(selected.description, color = GraphText.copy(alpha = .83f), fontSize = 11.sp,
                        lineHeight = 15.sp, maxLines = 3)
                    HorizontalDivider(color = selected.color.copy(alpha = .3f))
                    Text("CONNECTED  •  " + selected.relations, color = GraphMuted, fontSize = 10.sp,
                        maxLines = 2, lineHeight = 14.sp)
                }
            }
        } else {
            Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp),
                color = Color(0xD0071110), shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GraphBorder)) {
                Text("Pinch · drag · tap a node · double-tap to reset",
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = GraphMuted, fontSize = 9.sp)
            }
        }
    }
}

private fun depth(node: LiveNode): Float =
    if (node.id == "maximus") 70f else (node.id.hashCode() and 255) * .65f - 85f

/** Perspective projection shared by drawing and hit testing. */
private fun project(node: LiveNode, width: Float, height: Float, zoom: Float, pan: Offset): Offset {
    val z = depth(node)
    val yaw = .16f
    val pitch = -.10f
    val rotatedX = node.x * cos(yaw) + z * sin(yaw)
    val rotatedZ = z * cos(yaw) - node.x * sin(yaw)
    val rotatedY = node.y * cos(pitch) - rotatedZ * sin(pitch)
    val perspective = (1.2f - rotatedZ * .002f).coerceIn(.7f, 1.4f)
    val scale = min(width / 690f, height / 720f) * zoom * perspective
    return Offset(width / 2f + rotatedX * scale + pan.x, height * .46f + rotatedY * scale + pan.y)
}

@Composable
private fun SmallGraphButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

private fun eventColor(event: MatrixEvent): Color = when {
    event.type.name.contains("FAILED") -> GraphRed
    event.type.name.contains("MODEL") -> GraphCyan
    event.type.name.contains("VALIDATION") -> GraphOrange
    event.type.name.contains("POLICY") -> GraphGold
    event.type.name.contains("MEMORY") -> GraphPurple
    else -> GraphAccent
}
