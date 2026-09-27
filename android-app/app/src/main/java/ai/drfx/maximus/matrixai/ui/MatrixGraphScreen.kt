package ai.drfx.maximus.matrixai.ui

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
                    Text("MAXIMUS MATRIX", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Live operating graph · real entities and runtime events", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                    Text("Pinch to zoom · drag to pan · tap a node to inspect", color = GraphMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(7.dp))
                    LiveMatrixCanvas(
                        nodes = topology.first,
                        edges = topology.second,
                        events = events,
                        onNodeSelected = { selected = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 430.dp, max = 620.dp)
                            .background(GraphBg, RoundedCornerShape(18.dp))
                    )
                }
            }
        }

        selected?.let { node ->
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = GraphPanel, border = BorderStroke(1.dp, node.color.copy(alpha = .6f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(11.dp).background(node.color, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(node.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Text(node.group, color = node.color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text(node.description, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, lineHeight = 18.sp)
                        Text("RELATIONSHIPS", color = GraphMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(node.relations, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, lineHeight = 16.sp)
                    }
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
    nodes: List<LiveNode>,
    edges: List<LiveEdge>,
    events: List<MatrixEvent>,
    onNodeSelected: (LiveNode) -> Unit,
    modifier: Modifier
) {
    var zoom by remember { mutableFloatStateOf(0.82f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transition = rememberInfiniteTransition(label = "matrix")
    val packet by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "packet")
    val pulse by transition.animateFloat(.9f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val nodeMap = remember(nodes) { nodes.associateBy { it.id } }
    val activeText = events.firstOrNull()?.let { (it.sourceNode + " " + (it.targetNode ?: "")).lowercase() }.orEmpty()

    Box(modifier) {
        val gestureModifier = Modifier
            .fillMaxSize()
            .pointerInput(nodes) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(.32f, 3.2f)
                    pan += panChange
                }
            }
            .pointerInput(nodes, zoom, pan) {
                detectTapGestures(
                    onDoubleTap = {
                        zoom = 0.82f
                        pan = Offset.Zero
                    },
                    onTap = { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val hit = nodes.minByOrNull { node ->
                            val screen = Offset(center.x + node.x * zoom + pan.x, center.y + node.y * zoom + pan.y)
                            (tap - screen).getDistance()
                        }
                        if (hit != null) {
                            val screen = Offset(center.x + hit.x * zoom + pan.x, center.y + hit.y * zoom + pan.y)
                            if ((tap - screen).getDistance() <= 48.dp.toPx()) onNodeSelected(hit)
                        }
                    }
                )
            }

        Canvas(gestureModifier) {
            val center = Offset(size.width / 2f, size.height / 2f)
            fun screen(node: LiveNode) = Offset(center.x + node.x * zoom + pan.x, center.y + node.y * zoom + pan.y)

            val grid = 36.dp.toPx()
            var gx = ((pan.x % grid) + grid) % grid
            while (gx < size.width) {
                drawLine(Color(0x112A4942), Offset(gx, 0f), Offset(gx, size.height))
                gx += grid
            }
            var gy = ((pan.y % grid) + grid) % grid
            while (gy < size.height) {
                drawLine(Color(0x112A4942), Offset(0f, gy), Offset(size.width, gy))
                gy += grid
            }

            edges.forEachIndexed { index, edge ->
                val from = nodeMap[edge.from] ?: return@forEachIndexed
                val to = nodeMap[edge.to] ?: return@forEachIndexed
                val a = screen(from)
                val b = screen(to)
                val active = activeText.contains(from.id.lowercase()) || activeText.contains(to.id.lowercase()) ||
                    activeText.contains(from.label.lowercase()) || activeText.contains(to.label.lowercase())
                drawLine(
                    color = if (active) GraphAccent.copy(alpha = .8f) else from.color.copy(alpha = .20f),
                    start = a,
                    end = b,
                    strokeWidth = if (active) 2.dp.toPx() else 1.dp.toPx()
                )
                if (active || index % 6 == 0) {
                    val t = (packet + index * .07f) % 1f
                    drawCircle(
                        color = if (active) GraphAccent else GraphAccent.copy(alpha = .45f),
                        radius = 2.dp.toPx(),
                        center = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                    )
                }
            }

            nodes.forEach { node ->
                val p = screen(node)
                val active = activeText.contains(node.id.lowercase()) || activeText.contains(node.label.lowercase())
                val baseRadius = node.radius.dp.toPx() * zoom.coerceAtLeast(.65f)
                if (node.hub) {
                    drawCircle(node.color.copy(alpha = .10f), baseRadius * (if (active) 2.5f * pulse else 2.1f), p)
                    drawCircle(node.color.copy(alpha = .24f), baseRadius * 1.45f, p, style = Stroke(1.dp.toPx()))
                }
                drawCircle(node.color.copy(alpha = if (active) 1f else .86f), baseRadius, p)
                if (active) drawCircle(Color.White.copy(alpha = .45f), baseRadius + 5.dp.toPx(), p, style = Stroke(1.dp.toPx()))

                if (node.hub || zoom > .95f) {
                    drawContext.canvas.nativeCanvas.drawText(
                        node.label,
                        p.x,
                        p.y + baseRadius + 14.dp.toPx(),
                        Paint().apply {
                            color = GraphText.toArgb()
                            textSize = (if (node.hub) 10.sp else 8.sp).toPx()
                            textAlign = Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallGraphButton("+") { zoom = (zoom * 1.2f).coerceAtMost(3.2f) }
            SmallGraphButton("−") { zoom = (zoom / 1.2f).coerceAtLeast(.32f) }
            SmallGraphButton("⌂") {
                zoom = .82f
                pan = Offset.Zero
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            color = Color(0xCC071110),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, GraphBorder)
        ) {
            Text(
                "Pinch with two fingers · drag to pan · tap to inspect · double-tap to reset",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                color = GraphMuted,
                fontSize = 8.sp
            )
        }
    }
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
