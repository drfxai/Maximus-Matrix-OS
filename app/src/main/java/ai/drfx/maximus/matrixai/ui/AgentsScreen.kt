package ai.drfx.maximus.matrixai.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext

import ai.drfx.maximus.matrixai.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.AgentRegistry

@Composable
fun AgentsScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier, onChat: () -> Unit = {}) {
    var section by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val store = remember(context) { PersonalAgentStore(context) }
    var personal by remember { mutableStateOf(store.list()) }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<PersonalAgent?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val state by viewModel.llmState.collectAsState()
    val supportedIds = state.supportedAgents.map { it.id }.toSet()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            WorkspaceBrand()
            Row(verticalAlignment = Alignment.CenterVertically) {
                MaximusSectionHeader(stringResource(R.string.design_agents), null, Modifier.weight(1f))
                OutlinedButton(onClick = { newName = ""; creating = true },
                    border = BorderStroke(1.dp, MaximusReference.gold)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New", color = MaximusReference.gold)
                }
            }
        }
        item {
            if (section == 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tools & Connections", modifier = Modifier.weight(1f),
                        color = MaximusReference.white, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { section = 0 }) { Text("Back to agents") }
                }
            } else {
                MaximusTabs(listOf("My agents", "Teams"), section, { section = it })
            }
        }
        if (section == 0) {
            item { OutlinedTextField(value = search, onValueChange = { search = it },
                placeholder = { Text("Search agents...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)) }
            if (personal.isEmpty()) item {
                MaximusFramedCard(padding = PaddingValues(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("My agents · 0", color = MaximusReference.white,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Create an agent with real instructions and tasks.",
                                color = MaximusReference.muted, fontSize = 10.sp,
                                maxLines = 2)
                        }
                        OutlinedButton(onClick = { newName = ""; creating = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                            Text("Create", fontSize = 11.sp)
                        }
                    }
                }
            }
            items(personal.filter { it.name.contains(search, true) }) { agent ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaximusReference.navy,
                    border = BorderStroke(1.dp, MaximusReference.stroke)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, null, tint = MaximusReference.muted, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(agent.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { editing = agent }) {
                                Icon(Icons.Default.Edit, "Edit agent", tint = MaximusReference.muted, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(agent.instructions.ifBlank { "Add instructions to configure this agent." },
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        Text("${agent.tasks.size} tasks", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        if (agent.tasks.isNotEmpty()) TextButton(onClick = {
                            val prompt = "Act as ${agent.name}. ${agent.instructions}\nTask: ${agent.tasks.first()}"
                            if (viewModel.sendChat(prompt)) onChat()
                        }) { Text("Run first task") }
                    }
                }
            }
            editing?.let { current ->
                item {
                    var editedName by remember(current.id) { mutableStateOf(current.name) }
                    var editedInstructions by remember(current.id) { mutableStateOf(current.instructions) }
                    var editedTasks by remember(current.id) { mutableStateOf(current.tasks.joinToString("\\n")) }
                    var editorTab by remember(current.id) { mutableIntStateOf(0) }
                    MaximusFramedCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Edit agent", Modifier.weight(1f), fontWeight = FontWeight.Bold,
                                color = MaximusReference.white)
                            IconButton(onClick = { editing = null }) {
                                Icon(Icons.Default.Close, "Close agent editor")
                            }
                        }
                        MaximusTabs(listOf("Instructions", "Tasks", "Tools"),
                            editorTab, { editorTab = it })
                        if (editorTab == 0) {
                            OutlinedTextField(editedName, { editedName = it.take(60) },
                                label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(editedInstructions,
                                { editedInstructions = it.take(8000) },
                                label = { Text("Instructions") }, minLines = 4,
                                modifier = Modifier.fillMaxWidth())
                        } else if (editorTab == 1) {
                            OutlinedTextField(editedTasks, { editedTasks = it.take(20_000) },
                                label = { Text("Tasks · one per line") }, minLines = 4,
                                modifier = Modifier.fillMaxWidth())
                        } else {
                            Text("Tool permissions depend on configured providers and workspace approvals.",
                                color = MaximusReference.muted, fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                store.delete(current.id); personal = store.list(); editing = null
                            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = {
                                if (editedName.isNotBlank()) {
                                    store.save(current.copy(
                                        name = editedName.trim().take(60),
                                        instructions = editedInstructions.take(8000),
                                        tasks = editedTasks.lines().map { it.trim().take(1000) }
                                            .filter { it.isNotBlank() }.take(20)))
                                    personal = store.list()
                                    editing = null
                                }
                            }, colors = ButtonDefaults.buttonColors(
                                containerColor = MaximusReference.gold, contentColor = Color.Black)) {
                                Text("Save changes")
                            }
                        }
                    }
                }
            }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Available personas", Modifier.weight(1f), color = MaximusReference.gold,
                    fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { section = 2 }) { Text("Tools", fontSize = 11.sp) }
            } }
        }
        if (section == 2) { item { DeveloperConnectionsPanel() } }
        if (section == 1) { item { AgentTeamPanel() }; item { CloudTeamStatusPanel() } }
        if (section == 0) {
        items(AgentRegistry.agents.filter { it.name.contains(search, true) }) { agent ->
            val supported = agent.id in supportedIds
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaximusReference.navy,
                border = BorderStroke(
                    1.dp,
                    if (supported) MaterialTheme.colorScheme.primary.copy(alpha = .55f)
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = if (supported) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    agent.name.take(1),
                                    color = if (supported) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(agent.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                agent.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp, lineHeight = 14.sp,
                                maxLines = 2
                            )
                        }
                        Text(
                            if (supported) "SUPPORTED" else "UNAVAILABLE",
                            color = if (supported) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Requires: " + agent.requiredCapabilities.joinToString(" · ") { it.name.replace('_', ' ') },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    if (supported) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { viewModel.selectAgent(agent.id) },
                            modifier = Modifier.heightIn(min = 34.dp)) {
                            Text(if (state.selectedAgentId == agent.id) "Selected" else "Use Persona",
                                fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        }
    }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("New agent") },
        text = { OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (newName.isNotBlank()) { editing = store.create(newName); personal = store.list(); creating = false } }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } })
}
