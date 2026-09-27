package ai.drfx.maximus.matrixai.ui

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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

private val Bg = Color(0xFF030707)
private val Panel = Color(0xFF071110)
private val Border = Color(0xFF15342D)
private val Accent = Color(0xFF5CF0BC)
private val Gold = Color(0xFFE4B34D)
private val Muted = Color(0xFF87A29B)
private val Soft = Color(0xFFB8CBC5)
private val Cyan = Color(0xFF58C1D7)
private val Blue = Color(0xFF5798FF)
private val Purple = Color(0xFF9C79FF)
private val Orange = Color(0xFFF09A58)
private val Red = Color(0xFFE96E91)
private val Lime = Color(0xFF9BC566)

private enum class Destination(val title: String) {
    MATRIX("Matrix"), CHAT("Chat"), MISSIONS("Missions"), AGENTS("Agents"),
    RESEARCH("Research"), TRADING("Trading"), KNOWLEDGE("Knowledge"), MEMORY("Memory"),
    TOOLS("Tools"), ACTIVITY("Activity"), SETTINGS("Settings")
}

private data class GraphNode(
    val name: String,
    val color: Color,
    val x: Float,
    val y: Float,
    val kind: String = "Runtime Node",
    val status: String = "READY",
    val description: String = "MAXIMUS MATRIX OS runtime entity.",
    val relationships: String = "Connected to MAXIMUS"
)
private data class QuickAction(val label: String, val command: String)
private data class Capability(val name: String, val detail: String, val state: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixScreen(viewModel: MatrixViewModel = viewModel()) {
    val events by viewModel.events.collectAsState()
    val status by viewModel.status.collectAsState()
    var mission by remember { mutableStateOf("device info") }
    var destination by remember { mutableStateOf(Destination.MATRIX) }
    var showMore by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { AppHeader(status) },
        bottomBar = {
            Column(modifier = Modifier.background(Bg).navigationBarsPadding()) {
                CommandBar(mission, { mission = it }, { viewModel.runMission(mission) }, status !in setOf("EXECUTING", "PLANNING"))
                PrimaryNavigation(destination) {
                    if (it == null) showMore = !showMore else {
                        destination = it
                        showMore = false
                    }
                }
            }
        }
    ) { innerPadding ->
        if (showMore) {
            MoreScreen(destination, { destination = it; showMore = false }, Modifier.padding(innerPadding))
        } else {
            val modifier = Modifier.padding(innerPadding)
            when (destination) {
                Destination.MATRIX -> MatrixHome(events, status, { mission = it }, modifier)
                Destination.CHAT -> ProviderChatScreen(viewModel, modifier)
                Destination.MISSIONS -> MissionsScreen(events, modifier)
                Destination.AGENTS -> AgentsScreen(modifier)
                Destination.RESEARCH -> DomainScreen("Research", "Research missions, evidence and reproducibility", listOf(
                    Capability("Research Engine", "Mission-backed research records", "FOUNDATION", Cyan),
                    Capability("Evidence tracking", "Provider citations arrive with the AI runtime", "PHASE 3", Gold),
                    Capability("Failure knowledge", "Searchable failure records", "PLANNED", Purple)
                ), modifier)
                Destination.TRADING -> DomainScreen("Trading Intelligence", "Analysis lives inside MAXIMUS AI, never as a separate product", listOf(
                    Capability("Strategy analysis", "Static request registration is available", "LIMITED", Gold),
                    Capability("Trading Genome", "Reusable strategy primitives", "PLANNED", Purple),
                    Capability("Quant Lab", "No performance results are fabricated", "PLANNED", Cyan)
                ), modifier)
                Destination.KNOWLEDGE -> DomainScreen("Knowledge", "Retrieval-first company intelligence", listOf(
                    Capability("Local lookup", "Routes through the current tool registry", "AVAILABLE", Accent),
                    Capability("Document index", "No index is connected in this build", "UNAVAILABLE", Orange),
                    Capability("Code index", "Repository ingestion is planned", "PLANNED", Purple)
                ), modifier)
                Destination.MEMORY -> DomainScreen("Memory", "Transparent storage with provenance and retention policy", listOf(
                    Capability("Session events", "Kept in memory for the current app process", "AVAILABLE", Accent),
                    Capability("Mission notes", "Current runtime only; cleared on restart", "LIMITED", Gold),
                    Capability("Persistent memory", "Encrypted storage is not yet connected", "UNAVAILABLE", Orange)
                ), modifier)
                Destination.TOOLS -> ToolsScreen(modifier)
                Destination.ACTIVITY -> ActivityScreen(events, modifier)
                Destination.SETTINGS -> SettingsScreen(modifier)
            }
        }
    }
}

@Composable
private fun AppHeader(status: String) {
    Surface(color = Bg) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (status == "ATTENTION") Red else Accent, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("MATRIX ${if (status == "EXECUTING") "ACTIVE" else "ONLINE"}", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text("MAXIMUS AI", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("MAXIMUS MATRIX OS · V1.1.0", color = Muted, fontSize = 10.sp)
            }
            StatusBadge(status)
        }
    }
}

@Composable
private fun PrimaryNavigation(selected: Destination, onSelect: (Destination?) -> Unit) {
    val primary = listOf(Destination.MATRIX, Destination.CHAT, Destination.MISSIONS, Destination.AGENTS)
    NavigationBar(containerColor = Color(0xFF050B0A), tonalElevation = 0.dp) {
        primary.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = {
                    Icon(
                        when (item) {
                            Destination.MATRIX -> Icons.Default.Hub
                            Destination.CHAT -> Icons.Default.QuestionAnswer
                            Destination.MISSIONS -> Icons.Default.Route
                            else -> Icons.Default.Person
                        }, item.title
                    )
                },
                label = { Text(item.title, fontSize = 10.sp) },
                colors = navColors()
            )
        }
        NavigationBarItem(
            selected = selected !in primary,
            onClick = { onSelect(null) },
            icon = { Icon(Icons.Default.MoreHoriz, "More destinations") },
            label = { Text("More", fontSize = 10.sp) },
            colors = navColors()
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Accent,
    selectedTextColor = Accent,
    indicatorColor = Color(0xFF0E2A22),
    unselectedIconColor = Muted,
    unselectedTextColor = Muted
)

@Composable
private fun MatrixHome(events: List<MatrixEvent>, status: String, onCommand: (String) -> Unit, modifier: Modifier) {
    val quickActions = remember {
        listOf(
            QuickAction("Device", "device info"), QuickAction("Camera", "open camera"),
            QuickAction("Settings", "settings"), QuickAction("Web Search", "search gold price"),
            QuickAction("Alarm", "alarm 09:00"), QuickAction("Calendar", "calendar Research session"),
            QuickAction("Copy", "copy MAXIMUS AI note")
        )
    }
    Page(modifier) {
        item { GraphCard(events, status == "EXECUTING" || status == "PLANNING") }
        item { SectionTitle("Quick Actions", "Commands are staged in the dock before execution") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(quickActions) { action ->
                    AssistChip(
                        onClick = { onCommand(action.command) },
                        label = { Text(action.label, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Hub, null, tint = Accent, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Panel, labelColor = Soft),
                        border = BorderStroke(1.dp, Border),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }
        }
        item { SectionTitle("Mission Feed", "Real events from this app session") }
        item { EventFeedCard(events.take(6)) }
        item { SectionTitle("System Domains", "Internal MAXIMUS MATRIX OS capabilities") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DomainRow("Control Plane", "Policy, tools and execution", Blue, "ACTIVE")
                DomainRow("Knowledge Core", "Local retrieval foundation", Purple, "LIMITED")
                DomainRow("Trading Intelligence", "Static analysis foundation", Gold, "LIMITED")
                DomainRow("Validation", "Outcome checks and evidence", Red, "ACTIVE")
            }
        }
    }
}

@Composable
private fun ChatScreen(modifier: Modifier) = Page(modifier) {
    item { PageIntro("Chat", "Provider-aware AI conversation") }
    item { AvailabilityCard("AI provider not configured", "Streaming chat will activate after the Phase 3 provider registry and encrypted credentials are connected. This build does not simulate model responses.", Orange) }
    item { SectionTitle("What is ready", "The product surface is separated from runtime implementation") }
    item { DomainRow("Mission dock", "Send deterministic device commands from any screen", Accent, "AVAILABLE") }
    item { DomainRow("Matrix events", "Chat tool events can join the same graph contract", Cyan, "READY") }
    item { DomainRow("Streaming model response", "Requires a configured provider", Orange, "UNAVAILABLE") }
}

@Composable
private fun MissionsScreen(events: List<MatrixEvent>, modifier: Modifier) = Page(modifier) {
    item { PageIntro("Missions", "Plan, policy, execution, validation and artifacts") }
    if (events.isEmpty()) item { AvailabilityCard("No session missions", "Enter a deterministic command in the dock to create the first mission trace.", Muted) }
    else item { EventFeedCard(events) }
}

@Composable
private fun AgentsScreen(modifier: Modifier) = Page(modifier) {
    item { PageIntro("Agents", "Governed identities inside the shared runtime") }
    item { DomainRow("Executive Agent", "Planner, policy, tools and validation", Accent, "ACTIVE") }
    item { DomainRow("Device Agent", "Android intents behind visible permission boundaries", Blue, "ACTIVE") }
    item { DomainRow("Research Agent", "Template awaiting provider and retrieval runtime", Purple, "PLANNED") }
    item { DomainRow("Trading Agent", "Template awaiting evidence-backed tools", Gold, "PLANNED") }
    item { AvailabilityCard("Agent Factory foundation", "Agent templates, versioning, budgets and permission policies are architecture targets; they are not presented as working controls yet.", Cyan) }
}

@Composable
private fun DomainScreen(title: String, subtitle: String, capabilities: List<Capability>, modifier: Modifier) = Page(modifier) {
    item { PageIntro(title, subtitle) }
    items(capabilities) { capability -> DomainRow(capability.name, capability.detail, capability.color, capability.state) }
}

@Composable
private fun ToolsScreen(modifier: Modifier) = Page(modifier) {
    item { PageIntro("Tools", "Android actions use system UI and device permission boundaries") }
    item { SectionTitle("Available now", "Commands accepted by the current deterministic planner") }
    items(listOf(
        "Device status" to "device info", "Web search" to "search <query>",
        "Open URL" to "open url <address>", "Settings" to "settings [wifi|bluetooth|display|battery|apps]",
        "Camera" to "open camera", "Alarm" to "alarm HH:MM", "Calendar" to "calendar <title>",
        "Dialer" to "dial <number>", "SMS composer" to "sms <number> <message>",
        "Clipboard" to "copy <text>", "Share sheet" to "share <text>", "App launcher" to "app <package>"
    )) { (name, syntax) -> DomainRow(name, syntax, Accent, "AVAILABLE") }
}

@Composable
private fun ActivityScreen(events: List<MatrixEvent>, modifier: Modifier) = Page(modifier) {
    item { PageIntro("Activity", "Auditable runtime events for the current session") }
    item { AvailabilityCard("Session-only history", "Events are not persistent after app restart until encrypted storage is implemented.", Gold) }
    item { EventFeedCard(events) }
}

@Composable
private fun SettingsScreen(modifier: Modifier) = Page(modifier) {
    item { PageIntro("Settings", "Security and runtime configuration") }
    item { DomainRow("Security policy", "Critical actions denied; sensitive actions use visible Android flows", Accent, "ENFORCED") }
    item { DomainRow("Active provider", "No cloud provider configured", Orange, "NONE") }
    item { DomainRow("Persistent memory", "Encrypted store not connected", Orange, "OFF") }
    item { DomainRow("Notification access", "No listener service is registered", Muted, "OFF") }
    item { DomainRow("Accessibility automation", "No accessibility service is registered", Muted, "OFF") }
    item { DomainRow("Build", "MAXIMUS AI V1.1.0 · arm64-v8a", Blue, "PREVIEW") }
}

@Composable
private fun MoreScreen(selected: Destination, onSelect: (Destination) -> Unit, modifier: Modifier) = Page(modifier) {
    item { PageIntro("MAXIMUS AI", "Capability domains") }
    items(Destination.entries.drop(4)) { destination ->
        Surface(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable { onSelect(destination) },
            color = if (selected == destination) Color(0xFF0D241D) else Panel,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, if (selected == destination) Accent else Border)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(if (selected == destination) Accent else Gold, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(destination.title, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Page(modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun PageIntro(title: String, subtitle: String) {
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun StatusBadge(status: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Panel, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status, color = if (status == "ATTENTION") Red else Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("SECURE", color = Muted, fontSize = 8.sp)
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
private fun AvailabilityCard(title: String, text: String, color: Color) {
    Card(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, color.copy(alpha = .45f)), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(text, color = Soft, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun DomainRow(title: String, detail: String, color: Color, state: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Panel, border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(state, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GraphCard(events: List<MatrixEvent>, isExecuting: Boolean) {
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    Card(shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, Border), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Live Matrix", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (isExecuting) "Runtime event path active" else "Tap any node to inspect its runtime details", color = Muted, fontSize = 12.sp)
                }
                Text(if (isExecuting) "LIVE" else "INTERACTIVE", color = if (isExecuting) Accent else Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(1.02f).background(Color(0xFF020605), RoundedCornerShape(20.dp))) {
                LiveMatrixGraph(
                    activeType = if (isExecuting) events.firstOrNull()?.type else null,
                    isExecuting = isExecuting,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    onNodeSelected = { selectedNode = it }
                )
            }
            selectedNode?.let { node ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Color(0xFF091512),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, node.color.copy(alpha = .5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(node.color, CircleShape))
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(node.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(node.kind, color = Muted, fontSize = 10.sp)
                            }
                            Text(node.status, color = node.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(node.description, color = Soft, fontSize = 12.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("RELATIONSHIPS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(node.relationships, color = Soft, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventFeedCard(events: List<MatrixEvent>) {
    Surface(shape = RoundedCornerShape(20.dp), color = Panel, border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (events.isEmpty()) Text("No runtime events yet.", color = Muted, fontSize = 13.sp)
            events.forEachIndexed { index, event ->
                Text(event.type.name.replace('_', ' '), color = eventColor(event.type), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(event.message, color = Soft, fontSize = 13.sp, lineHeight = 18.sp)
                Text("${event.sourceNode}${event.targetNode?.let { " -> $it" }.orEmpty()}", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (index != events.lastIndex) {
                    Spacer(Modifier.height(9.dp)); HorizontalDivider(color = Border); Spacer(Modifier.height(9.dp))
                }
            }
        }
    }
}

@Composable
private fun CommandBar(mission: String, onMissionChange: (String) -> Unit, onRun: () -> Unit, enabled: Boolean) {
    Surface(color = Bg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = mission,
                onValueChange = onMissionChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                placeholder = { Text("Enter mission command", color = Muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled) onRun() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedContainerColor = Panel, unfocusedContainerColor = Panel, cursorColor = Accent
                )
            )
            Button(
                onClick = onRun,
                enabled = enabled,
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E3A2E), contentColor = Accent)
            ) { Icon(Icons.Default.ArrowUpward, "Run mission") }
        }
    }
}

@Composable
private fun LiveMatrixGraph(
    activeType: MatrixEventType?,
    isExecuting: Boolean,
    modifier: Modifier = Modifier,
    onNodeSelected: (GraphNode) -> Unit = {}
) {
    val transition = rememberInfiniteTransition(label = "matrix-runtime")
    val animatedPhase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "event-packet")
    val animatedPulse by transition.animateFloat(.92f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "event-pulse")
    val phase = if (isExecuting) animatedPhase else 0f
    val pulse = if (isExecuting) animatedPulse else 1f
    val nodes = remember {
        listOf(
            GraphNode("MAXIMUS", Accent, .50f, .48f, "Executive Agent", "ACTIVE", "Central mission coordinator for planning, policy, tools, models, validation and artifacts.", "TOOLS · MEMORY · POLICY · MODEL · RESEARCH · VALIDATION · ARTIFACT"),
            GraphNode("TOOLS", Blue, .13f, .28f, "Tool Gateway", "ACTIVE", "Android capability gateway for device actions and deterministic tools.", "MAXIMUS · POLICY · VALIDATION"),
            GraphNode("MEMORY", Purple, .82f, .23f, "Memory Core", "SESSION", "Session and mission context. Persistent encrypted memory remains a controlled expansion.", "MAXIMUS · KNOWLEDGE · RESEARCH"),
            GraphNode("POLICY", Orange, .88f, .58f, "Policy Engine", "ENFORCED", "Evaluates action risk and blocks critical operations or requires confirmation.", "MAXIMUS · TOOLS · HUMAN APPROVAL"),
            GraphNode("RESEARCH", Cyan, .13f, .72f, "Research Engine", "FOUNDATION", "Unifies Research OS, Intelligence Foundry and evidence-oriented research missions.", "KNOWLEDGE · GENOME · VALIDATION"),
            GraphNode("GENOME", Gold, .37f, .89f, "Trading Genome", "FOUNDATION", "Represents strategy, indicator and reusable trading primitive DNA.", "RESEARCH · QUANT LAB · ARTIFACT"),
            GraphNode("VALIDATION", Red, .47f, .09f, "Validation Lab", "ACTIVE", "Verifies tool outcomes and tracks evidence without fabricating runtime claims.", "TOOLS · MODEL · ARTIFACT"),
            GraphNode("ARTIFACT", Lime, .72f, .89f, "Artifact Registry", "ACTIVE", "Records mission evidence and future lineage for generated outputs.", "VALIDATION · RESEARCH · GENOME"),
            GraphNode("MODEL", Color(0xFF6FE3FF), .70f, .48f, "LLM Router", "API-AWARE", "Detects API providers, discovers supported models and executes native chat requests.", "MAXIMUS · POLICY · KNOWLEDGE · VALIDATION"),
            GraphNode("KNOWLEDGE", Color(0xFFB78CFF), .31f, .27f, "Knowledge Core", "FOUNDATION", "Retrieval-first layer for documents, code, research memory and company intelligence.", "MEMORY · RESEARCH · MODEL · GENOME")
        )
    }
    val activeNode = when (activeType) {
        MatrixEventType.MEMORY_RECALLED -> 2
        MatrixEventType.POLICY_CHECKED, MatrixEventType.CONFIRMATION_REQUIRED -> 3
        MatrixEventType.TOOL_STARTED, MatrixEventType.TOOL_COMPLETED -> 1
        MatrixEventType.VALIDATION_STARTED, MatrixEventType.VALIDATION_PASSED, MatrixEventType.VALIDATION_FAILED -> 6
        MatrixEventType.ARTIFACT_CREATED -> 7
        MatrixEventType.MODEL_DISCOVERY, MatrixEventType.MODEL_STARTED, MatrixEventType.MODEL_COMPLETED, MatrixEventType.MODEL_FAILED -> 8
        else -> 0
    }
    val interactiveModifier = modifier.pointerInput(nodes) {
        detectTapGestures { tap ->
            val hit = nodes.minByOrNull { node ->
                val px = size.width * node.x
                val py = size.height * node.y
                val dx = tap.x - px
                val dy = tap.y - py
                dx * dx + dy * dy
            }
            if (hit != null) {
                val dx = tap.x - size.width * hit.x
                val dy = tap.y - size.height * hit.y
                val threshold = 42.dp.toPx()
                if (dx * dx + dy * dy <= threshold * threshold) onNodeSelected(hit)
            }
        }
    }
    Canvas(interactiveModifier) {
        val centers = nodes.map { Offset(size.width * it.x, size.height * it.y) }
        val edges = listOf(
            0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 0 to 6, 0 to 7, 0 to 8, 0 to 9,
            4 to 9, 5 to 6, 6 to 7, 8 to 9, 8 to 6, 2 to 9
        )
        val grid = 32.dp.toPx()
        var gx = 0f
        while (gx < size.width) { drawLine(Color(0x112A4942), Offset(gx, 0f), Offset(gx, size.height)); gx += grid }
        var gy = 0f
        while (gy < size.height) { drawLine(Color(0x112A4942), Offset(0f, gy), Offset(size.width, gy)); gy += grid }
        edges.forEach { (from, to) ->
            val active = isExecuting && (from == activeNode || to == activeNode)
            drawLine(if (active) Accent.copy(alpha = .75f) else Border.copy(alpha = .72f), centers[from], centers[to], if (active) 2.dp.toPx() else 1.dp.toPx())
            if (active) {
                val p = Offset(
                    centers[from].x + (centers[to].x - centers[from].x) * phase,
                    centers[from].y + (centers[to].y - centers[from].y) * phase
                )
                drawCircle(Accent, 2.5.dp.toPx(), p)
            }
        }
        nodes.forEachIndexed { index, node ->
            val active = isExecuting && index == activeNode
            val radius = (if (index == 0) 17.dp else 10.dp).toPx() * if (active) pulse else 1f
            if (active) drawCircle(node.color.copy(alpha = .18f), radius * 2.2f, centers[index], style = Stroke(2.dp.toPx()))
            drawCircle(node.color.copy(alpha = if (active) 1f else .82f), radius, centers[index])
            drawContext.canvas.nativeCanvas.drawText(
                node.name,
                centers[index].x,
                centers[index].y + radius + 13.dp.toPx(),
                Paint().apply {
                    color = Soft.toArgb()
                    textSize = 8.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }
    }
}

private fun eventColor(type: MatrixEventType): Color = when (type) {
    MatrixEventType.MEMORY_RECALLED -> Purple
    MatrixEventType.POLICY_CHECKED, MatrixEventType.CONFIRMATION_REQUIRED -> Orange
    MatrixEventType.TOOL_STARTED, MatrixEventType.TOOL_COMPLETED -> Blue
    MatrixEventType.VALIDATION_STARTED, MatrixEventType.VALIDATION_PASSED -> Cyan
    MatrixEventType.VALIDATION_FAILED, MatrixEventType.MISSION_FAILED -> Red
    MatrixEventType.ARTIFACT_CREATED -> Lime
    MatrixEventType.PLAN_CREATED -> Gold
    MatrixEventType.MODEL_DISCOVERY -> Gold
    MatrixEventType.MODEL_STARTED -> Cyan
    MatrixEventType.MODEL_COMPLETED -> Accent
    MatrixEventType.MODEL_FAILED -> Red
    else -> Accent
}
