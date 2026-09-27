package ai.drfx.maximus.matrixai.ui

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.drfx.maximus.matrixai.agent.MatrixEvent
import ai.drfx.maximus.matrixai.agent.MatrixEventType
import kotlin.math.cos
import kotlin.math.sin

private val Bg = Color(0xFF030707)
private val Panel = Color(0xFF071110)
private val Border = Color(0xFF15342D)
private val Accent = Color(0xFF5CF0BC)
private val Muted = Color(0xFF87A29B)
private val Soft = Color(0xFFB8CBC5)
private val Cyan = Color(0xFF58C1D7)
private val Blue = Color(0xFF5798FF)
private val Purple = Color(0xFF9C79FF)
private val Orange = Color(0xFFF09A58)
private val Yellow = Color(0xFFE4B34D)
private val Red = Color(0xFFE96E91)
private val Lime = Color(0xFF9BC566)

private data class GraphNode(val name: String, val color: Color, val x: Float, val y: Float)
private data class QuickAction(val label: String, val command: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixScreen(viewModel: MatrixViewModel = viewModel()) {
    val events by viewModel.events.collectAsState()
    val status by viewModel.status.collectAsState()
    var mission by remember { mutableStateOf("device info") }

    val quickActions = remember {
        listOf(
            QuickAction("Device", "device info"),
            QuickAction("Camera", "open camera"),
            QuickAction("Settings", "settings"),
            QuickAction("Web Search", "search gold price"),
            QuickAction("Alarm", "alarm 09:00"),
            QuickAction("Calendar", "calendar London session research"),
            QuickAction("Copy", "copy EURUSD notes"),
            QuickAction("Share", "share Matrix test")
        )
    }

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(color = Bg) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(Accent, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("MATRIX ONLINE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("MAXIMUS MATRIX AI", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Graph-native Android executive agent · V1.0.3", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(10.dp))
                    StatusBadge(status)
                }
            }
        },
        bottomBar = {
            CommandBar(mission, { mission = it }, { viewModel.runMission(mission) }, status != "EXECUTING")
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { GraphCard(events) }
            item { SectionTitle("Quick Actions", "Touch-friendly shortcuts") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(quickActions) { action ->
                        AssistChip(
                            onClick = { mission = action.command },
                            label = { Text(action.label, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Hub, null, tint = Accent, modifier = Modifier.size(18.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Panel, labelColor = Soft),
                            border = BorderStroke(1.dp, Border),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }
            item { SectionTitle("Mission Feed", "Latest runtime events") }
            item { EventFeedCard(events.take(8)) }
            item { SectionTitle("Operations", "Core runtime modules") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModuleCard("Tools", "Device actions, web, camera and alarms", Blue, Modifier.weight(1f))
                        ModuleCard("Memory", "Mission memory and local notes", Purple, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModuleCard("Policy", "Risk checks and confirmation gates", Orange, Modifier.weight(1f))
                        ModuleCard("Validation", "Outcome verification and evidence", Red, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModuleCard("Research", "Indicator and strategy lookup pipeline", Cyan, Modifier.weight(1f))
                        ModuleCard("Artifacts", "Evidence bundles and execution traces", Lime, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Panel, border = BorderStroke(1.dp, Border)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text("ARM64-V8A", color = Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun GraphCard(events: List<MatrixEvent>) {
    Card(shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, Border), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Mission Graph", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Live event topology", color = Muted, fontSize = 12.sp)
                }
                Text("POLICY ENFORCED", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.88f).background(Color(0xFF020605), RoundedCornerShape(20.dp))) {
                LiveMatrixGraph(events.firstOrNull()?.type, Modifier.fillMaxSize().padding(10.dp))
            }
        }
    }
}

@Composable
private fun EventFeedCard(events: List<MatrixEvent>) {
    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Panel),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (events.isEmpty()) {
                Text("No events yet. Run a mission to populate the Matrix.", color = Muted, fontSize = 13.sp)
            } else {
                events.forEachIndexed { index, event ->
                    Text(event.type.name, color = eventColor(event.type), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(event.message, color = Soft, fontSize = 14.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        buildString {
                            append(event.sourceNode)
                            if (!event.targetNode.isNullOrBlank()) append(" → ${event.targetNode}")
                        },
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (index != events.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Border)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(title: String, text: String, color: Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Panel), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(text, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun CommandBar(mission: String, onMissionChange: (String) -> Unit, onRun: () -> Unit, enabled: Boolean) {
    Surface(color = Bg, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(24.dp),
            color = Panel,
            border = BorderStroke(1.dp, Border)
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = Color(0xFF0C201B)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Hub, null, tint = Accent, modifier = Modifier.size(24.dp))
                    }
                }
                OutlinedTextField(
                    value = mission,
                    onValueChange = onMissionChange,
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    shape = RoundedCornerShape(18.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    placeholder = { Text("Enter mission command", color = Muted) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (enabled) onRun() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedContainerColor = Color(0xFF040A09),
                        unfocusedContainerColor = Color(0xFF040A09),
                        cursorColor = Accent
                    )
                )
                Button(
                    onClick = onRun,
                    enabled = enabled,
                    modifier = Modifier.size(width = 60.dp, height = 56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E3A2E), contentColor = Accent)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Run mission")
                }
            }
        }
    }
}

@Composable
private fun LiveMatrixGraph(activeType: MatrixEventType?, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "matrix")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Restart), label = "phase")
    val pulse by transition.animateFloat(0.88f, 1.15f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    val nodes = remember {
        listOf(
            GraphNode("MAXIMUS", Accent, 0.50f, 0.50f),
            GraphNode("TOOLS", Blue, 0.18f, 0.30f),
            GraphNode("MEMORY", Purple, 0.82f, 0.30f),
            GraphNode("POLICY", Orange, 0.86f, 0.66f),
            GraphNode("RESEARCH", Cyan, 0.18f, 0.72f),
            GraphNode("GENOME", Yellow, 0.40f, 0.88f),
            GraphNode("VALIDATION", Red, 0.48f, 0.11f),
            GraphNode("ARTIFACTS", Lime, 0.70f, 0.88f)
        )
    }

    Canvas(modifier = modifier) {
        val centers = nodes.map { Offset(size.width * it.x, size.height * it.y) }
        val edges = listOf(0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 0 to 6, 0 to 7, 4 to 2, 5 to 6, 6 to 7)

        val grid = 34.dp.toPx()
        var gx = 0f
        while (gx < size.width) { drawLine(Color(0x112A4942), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += grid }
        var gy = 0f
        while (gy < size.height) { drawLine(Color(0x112A4942), Offset(0f, gy), Offset(size.width, gy), 1f); gy += grid }

        edges.forEachIndexed { index, pair ->
            val a = centers[pair.first]
            val b = centers[pair.second]
            drawLine(Color(0x404A7A6E), a, b, if (pair.first == 0) 1.8f else 1f)
            val q = (phase + index * 0.09f) % 1f
            drawCircle(Accent.copy(alpha = 0.7f), 2.2.dp.toPx(), Offset(a.x + (b.x - a.x) * q, a.y + (b.y - a.y) * q))
        }

        nodes.forEachIndexed { index, node ->
            val center = centers[index]
            val radius = if (index == 0) 24.dp.toPx() else 16.dp.toPx()
            val active = when (activeType) {
                MatrixEventType.MEMORY_RECALLED -> index == 2
                MatrixEventType.POLICY_CHECKED, MatrixEventType.CONFIRMATION_REQUIRED -> index == 3
                MatrixEventType.TOOL_STARTED, MatrixEventType.TOOL_COMPLETED -> index == 1
                MatrixEventType.VALIDATION_STARTED, MatrixEventType.VALIDATION_PASSED, MatrixEventType.VALIDATION_FAILED -> index == 6
                MatrixEventType.ARTIFACT_CREATED -> index == 7
                MatrixEventType.MISSION_ACCEPTED, MatrixEventType.PLAN_CREATED, MatrixEventType.MISSION_COMPLETED -> index == 0
                else -> false
            }
            drawCircle(node.color.copy(alpha = 0.12f), radius * if (active) 2.7f * pulse else 2f, center)
            drawCircle(node.color, radius, center)
            drawCircle(Color.White.copy(alpha = 0.18f), radius + 5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            repeat(if (index == 0) 14 else 8) { orbit ->
                val angle = orbit * 0.78f + index
                val ring = radius * (2f + (orbit % 3) * 0.62f)
                val dot = Offset(center.x + cos(angle) * ring, center.y + sin(angle) * ring)
                drawLine(node.color.copy(alpha = 0.14f), center, dot, 0.8f)
                drawCircle(node.color.copy(alpha = 0.65f), 2.dp.toPx(), dot)
            }
            drawContext.canvas.nativeCanvas.drawText(
                node.name,
                center.x,
                center.y + radius + 22.dp.toPx(),
                Paint().apply {
                    color = Color(0xFFBFCFCB).toArgb()
                    textSize = 11.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }
    }
}

private fun eventColor(type: MatrixEventType): Color = when (type) {
    MatrixEventType.MISSION_FAILED, MatrixEventType.VALIDATION_FAILED -> Red
    MatrixEventType.CONFIRMATION_REQUIRED -> Orange
    MatrixEventType.VALIDATION_PASSED, MatrixEventType.MISSION_COMPLETED -> Accent
    MatrixEventType.MEMORY_RECALLED -> Purple
    MatrixEventType.ARTIFACT_CREATED -> Lime
    else -> Cyan
}
