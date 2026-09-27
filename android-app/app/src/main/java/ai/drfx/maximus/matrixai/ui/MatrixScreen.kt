package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.drfx.maximus.matrixai.agent.MatrixEventType
import kotlin.math.cos
import kotlin.math.sin

private val MatrixBg = Color(0xFF020405)
private val MatrixPanel = Color(0xE8070D0D)
private val MatrixGreen = Color(0xFF59F0B7)
private val MatrixCyan = Color(0xFF5FCBD6)
private val MatrixBlue = Color(0xFF4D98FF)
private val MatrixPurple = Color(0xFF9A75FF)
private val MatrixYellow = Color(0xFFF0B83E)
private val MatrixRed = Color(0xFFE76283)
private val MatrixOrange = Color(0xFFF09259)

private data class GraphNode(val name: String, val color: Color, val nx: Float, val ny: Float)

@Composable
fun MatrixScreen(viewModel: MatrixViewModel = viewModel()) {
    val events by viewModel.events.collectAsState()
    val status by viewModel.status.collectAsState()
    var mission by remember { mutableStateOf("Research and validate a new trading strategy") }

    Box(modifier = Modifier.fillMaxSize().background(MatrixBg)) {
        LiveMatrixGraph(
            activeType = events.firstOrNull()?.type,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(MatrixGreen, CircleShape)
                )
                Spacer(Modifier.size(8.dp))
                Text("MATRIX ONLINE · EVENT STREAM ACTIVE", color = MatrixGreen, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("MAXIMUS MATRIX AI", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("Graph-native Android agent runtime · V1.0.1", color = Color(0xFF718783), fontSize = 11.sp)
        }

        Surface(
            color = MatrixPanel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp)
                .fillMaxWidth(0.43f)
                .border(1.dp, Color(0xFF16362F), RoundedCornerShape(14.dp))
        ) {
            Column(Modifier.padding(13.dp)) {
                Text("EXECUTIVE AGENT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("POLICY · MEMORY · TOOLS · VALIDATION", color = Color(0xFF6D817E), fontSize = 8.sp)
                Spacer(Modifier.height(9.dp))
                Metric("RUNTIME", status)
                Metric("POLICY", "ENFORCED")
                Metric("ABI", "ARM64-V8A")
                Metric("TELEMETRY", if (events.isEmpty()) "STANDBY" else "LIVE")
            }
        }

        if (events.isNotEmpty()) {
            Surface(
                color = MatrixPanel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .fillMaxWidth(0.43f)
                    .height(190.dp)
                    .border(1.dp, Color(0xFF16362F), RoundedCornerShape(12.dp))
            ) {
                LazyColumn(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(events.take(7), key = { it.id }) { event ->
                        Column {
                            Text(event.type.name, color = eventColor(event.type), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(event.message, color = Color(0xFF9DB0AC), fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        Surface(
            color = MatrixPanel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(18.dp)
                .fillMaxWidth()
                .border(1.dp, Color(0xFF16362F), RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Hub, contentDescription = null, tint = MatrixGreen)
                OutlinedTextField(
                    value = mission,
                    onValueChange = { mission = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Mission command") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MatrixGreen,
                        unfocusedBorderColor = Color(0xFF24453F),
                        focusedLabelColor = MatrixGreen,
                        unfocusedLabelColor = Color(0xFF718783)
                    )
                )
                Button(
                    enabled = status != "EXECUTING",
                    onClick = { viewModel.runMission(mission) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10392D), contentColor = MatrixGreen)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Run mission")
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF667B77), fontSize = 8.sp)
        Text(value, color = MatrixGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LiveMatrixGraph(activeType: MatrixEventType?, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "matrix")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600), RepeatMode.Restart),
        label = "phase"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse"
    )

    val nodes = remember {
        listOf(
            GraphNode("MAXIMUS", MatrixGreen, 0.46f, 0.42f),
            GraphNode("TOOLS", MatrixBlue, 0.20f, 0.29f),
            GraphNode("MEMORY", MatrixPurple, 0.69f, 0.24f),
            GraphNode("POLICY", MatrixOrange, 0.75f, 0.50f),
            GraphNode("RESEARCH", MatrixCyan, 0.20f, 0.58f),
            GraphNode("GENOME", MatrixYellow, 0.43f, 0.68f),
            GraphNode("VALIDATION", MatrixRed, 0.48f, 0.18f),
            GraphNode("ARTIFACTS", Color(0xFFA5D46B), 0.64f, 0.68f)
        )
    }

    Canvas(modifier = modifier) {
        val graphHeight = size.height * 0.78f
        val centers = nodes.map { Offset(size.width * it.nx, graphHeight * it.ny) }
        val edges = listOf(0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 0 to 6, 0 to 7, 4 to 2, 5 to 6, 6 to 7)

        val grid = 44.dp.toPx()
        var gx = 0f
        while (gx < size.width) {
            drawLine(Color(0x0F2B4A45), Offset(gx, 0f), Offset(gx, graphHeight), 1f)
            gx += grid
        }
        var gy = 0f
        while (gy < graphHeight) {
            drawLine(Color(0x0F2B4A45), Offset(0f, gy), Offset(size.width, gy), 1f)
            gy += grid
        }

        edges.forEachIndexed { index, pair ->
            val a = centers[pair.first]
            val b = centers[pair.second]
            drawLine(Color(0x443C756A), a, b, if (index < 7) 1.8f else 1f)
            val q = (phase + index * 0.093f) % 1f
            val packet = Offset(a.x + (b.x - a.x) * q, a.y + (b.y - a.y) * q)
            drawCircle(MatrixGreen.copy(alpha = 0.7f), radius = 2.4.dp.toPx(), center = packet)
        }

        nodes.forEachIndexed { index, node ->
            val center = centers[index]
            val base = if (index == 0) 17.dp.toPx() else 11.dp.toPx()
            val active = when (activeType) {
                MatrixEventType.MEMORY_RECALLED -> index == 2
                MatrixEventType.POLICY_CHECKED, MatrixEventType.CONFIRMATION_REQUIRED -> index == 3
                MatrixEventType.TOOL_STARTED, MatrixEventType.TOOL_COMPLETED -> index == 1
                MatrixEventType.VALIDATION_STARTED, MatrixEventType.VALIDATION_PASSED, MatrixEventType.VALIDATION_FAILED -> index == 6
                MatrixEventType.ARTIFACT_CREATED -> index == 7
                MatrixEventType.PLAN_CREATED, MatrixEventType.MISSION_ACCEPTED, MatrixEventType.MISSION_COMPLETED -> index == 0
                else -> false
            }
            drawCircle(node.color.copy(alpha = 0.12f), radius = base * (if (active) 2.4f * pulse else 1.9f), center = center)
            drawCircle(node.color.copy(alpha = if (active) 1f else 0.85f), radius = base, center = center)
            drawCircle(Color.White.copy(alpha = 0.3f), radius = base + 4.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))

            for (satellite in 0 until if (index == 0) 18 else 10) {
                val angle = satellite * 0.61f + index * 0.8f
                val radius = base * (2.4f + (satellite % 4) * 0.55f)
                val sat = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                drawLine(node.color.copy(alpha = 0.15f), center, sat, 0.7f)
                drawCircle(node.color.copy(alpha = 0.55f), 1.7.dp.toPx(), sat)
            }
        }

        drawRect(
            color = Color(0x2200FFAA),
            topLeft = Offset(0f, graphHeight - 1.dp.toPx()),
            size = Size(size.width, 1.dp.toPx())
        )
    }
}

private fun eventColor(type: MatrixEventType): Color = when (type) {
    MatrixEventType.MISSION_FAILED, MatrixEventType.VALIDATION_FAILED -> MatrixRed
    MatrixEventType.CONFIRMATION_REQUIRED -> MatrixOrange
    MatrixEventType.VALIDATION_PASSED, MatrixEventType.MISSION_COMPLETED -> MatrixGreen
    MatrixEventType.MEMORY_RECALLED -> MatrixPurple
    else -> MatrixCyan
}
