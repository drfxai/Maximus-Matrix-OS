package ai.drfx.maximus.matrixai.ui

import android.graphics.Paint
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
    var mission by remember { mutableStateOf("device info") }
    val quickActions = listOf(
        "Device" to "device info",
        "Camera" to "open camera",
        "Settings" to "settings",
        "Web Search" to "search gold price",
        "Alarm" to "alarm 09:00",
        "Calendar" to "calendar Trading research"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MatrixBg)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(MatrixGreen, CircleShape))
                    Spacer(Modifier.size(7.dp))
                    Text("MATRIX ONLINE", color = MatrixGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text("MAXIMUS MATRIX AI", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("Graph-native Android executive agent · V1.0.2", color = Color(0xFF718783), fontSize = 10.sp)
            }
            Surface(color = Color(0xFF0A1714), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A4037))) {
                Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), horizontalAlignment = Alignment.End) {
                    Text(status, color = MatrixGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("ARM64-V8A", color = Color(0xFF718783), fontSize = 7.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color(0xFF102A25), RoundedCornerShape(18.dp))
                .background(Color(0xFF030706), RoundedCornerShape(18.dp))
        ) {
            LiveMatrixGraph(events.firstOrNull()?.type, Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text("POLICY ENFORCED", color = MatrixGreen, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                Text("TOOLS · MEMORY · VALIDATION", color = Color(0xFF637B76), fontSize = 7.sp)
            }
            if (events.isNotEmpty()) {
                Surface(
                    color = Color(0xC9060C0B),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth(0.72f)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        events.take(3).forEach { event ->
                            Text(event.type.name, color = eventColor(event.type), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            Text(event.message, color = Color(0xFF93A7A3), fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(quickActions) { (label, command) ->
                AssistChip(
                    onClick = { mission = command },
                    label = { Text(label, fontSize = 8.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF08130F),
                        labelColor = Color(0xFFA8B9B5)
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = Color(0xFF17362F)
                    )
                )
            }
        }

        Surface(
            color = MatrixPanel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF17362F), RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier.padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
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
private fun LiveMatrixGraph(activeType: MatrixEventType?, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "matrix")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3500), RepeatMode.Restart), label = "phase")
    val pulse by transition.animateFloat(0.8f, 1.22f, infiniteRepeatable(tween(1050), RepeatMode.Reverse), label = "pulse")
    val nodes = remember {
        listOf(
            GraphNode("MAXIMUS", MatrixGreen, 0.50f, 0.48f),
            GraphNode("TOOLS", MatrixBlue, 0.20f, 0.30f),
            GraphNode("MEMORY", MatrixPurple, 0.76f, 0.28f),
            GraphNode("POLICY", MatrixOrange, 0.80f, 0.56f),
            GraphNode("RESEARCH", MatrixCyan, 0.20f, 0.62f),
            GraphNode("GENOME", MatrixYellow, 0.42f, 0.78f),
            GraphNode("VALIDATION", MatrixRed, 0.49f, 0.17f),
            GraphNode("ARTIFACTS", Color(0xFFA5D46B), 0.68f, 0.78f)
        )
    }

    Canvas(modifier = modifier) {
        val centers = nodes.map { Offset(size.width * it.nx, size.height * it.ny) }
        val edges = listOf(0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 0 to 6, 0 to 7, 4 to 2, 5 to 6, 6 to 7)
        val grid = 42.dp.toPx()
        var gx = 0f
        while (gx < size.width) { drawLine(Color(0x0D2B4A45), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += grid }
        var gy = 0f
        while (gy < size.height) { drawLine(Color(0x0D2B4A45), Offset(0f, gy), Offset(size.width, gy), 1f); gy += grid }

        edges.forEachIndexed { index, pair ->
            val a = centers[pair.first]
            val b = centers[pair.second]
            drawLine(Color(0x453C756A), a, b, if (pair.first == 0) 1.6f else 0.8f)
            val q = (phase + index * 0.087f) % 1f
            drawCircle(MatrixGreen.copy(alpha = 0.7f), 2.1.dp.toPx(), Offset(a.x + (b.x - a.x) * q, a.y + (b.y - a.y) * q))
        }

        nodes.forEachIndexed { index, node ->
            val center = centers[index]
            val base = if (index == 0) 18.dp.toPx() else 11.dp.toPx()
            val active = when (activeType) {
                MatrixEventType.MEMORY_RECALLED -> index == 2
                MatrixEventType.POLICY_CHECKED, MatrixEventType.CONFIRMATION_REQUIRED -> index == 3
                MatrixEventType.TOOL_STARTED, MatrixEventType.TOOL_COMPLETED -> index == 1
                MatrixEventType.VALIDATION_STARTED, MatrixEventType.VALIDATION_PASSED, MatrixEventType.VALIDATION_FAILED -> index == 6
                MatrixEventType.ARTIFACT_CREATED -> index == 7
                MatrixEventType.MISSION_ACCEPTED, MatrixEventType.PLAN_CREATED, MatrixEventType.MISSION_COMPLETED -> index == 0
                else -> false
            }
            drawCircle(node.color.copy(alpha = 0.10f), base * if (active) 2.6f * pulse else 2.0f, center)
            drawCircle(node.color.copy(alpha = if (active) 1f else 0.88f), base, center)
            drawCircle(Color.White.copy(alpha = 0.25f), base + 4.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            repeat(if (index == 0) 16 else 9) { satellite ->
                val angle = satellite * 0.67f + index
                val radius = base * (2.4f + (satellite % 3) * 0.62f)
                val sat = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                drawLine(node.color.copy(alpha = 0.14f), center, sat, 0.7f)
                drawCircle(node.color.copy(alpha = 0.6f), 1.6.dp.toPx(), sat)
            }
            drawContext.canvas.nativeCanvas.drawText(
                node.name,
                center.x,
                center.y + base + 15.dp.toPx(),
                Paint().apply {
                    color = Color(0xFFB9CAC6).toArgb()
                    textSize = 8.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }
    }
}

private fun eventColor(type: MatrixEventType): Color = when (type) {
    MatrixEventType.MISSION_FAILED, MatrixEventType.VALIDATION_FAILED -> MatrixRed
    MatrixEventType.CONFIRMATION_REQUIRED -> MatrixOrange
    MatrixEventType.VALIDATION_PASSED, MatrixEventType.MISSION_COMPLETED -> MatrixGreen
    MatrixEventType.MEMORY_RECALLED -> MatrixPurple
    else -> MatrixCyan
}
