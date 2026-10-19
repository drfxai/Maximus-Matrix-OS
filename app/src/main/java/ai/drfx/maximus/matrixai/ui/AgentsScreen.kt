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
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            WorkspaceBrand()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.design_agents), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Button(onClick = { newName = ""; creating = true }) { Text("+ New") }
            }
            if (section == 0) Text(
                stringResource(R.string.agents_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        item {
            ScrollableTabRow(selectedTabIndex = section, edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.background) {
                listOf("My agents", "Teams", "Connections")
                    .forEachIndexed { index, label ->
                        Tab(selected = section == index, onClick = { section = index },
                            text = { Text(label) })
                    }
            }
        }
        if (section == 0) {
            item { OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search agents") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            items(personal.filter { it.name.contains(search, true) }) { agent ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .5f))) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(agent.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editing = agent }) { Text("Edit") }
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
            item { Text("Available personas", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        }
        if (section == 2) { item { DeveloperConnectionsPanel() } }
        if (section == 1) { item { AgentTeamPanel() }; item { CloudTeamStatusPanel() } }
        if (section == 0) {
        items(AgentRegistry.agents.filter { it.name.contains(search, true) }) { agent ->
            val supported = agent.id in supportedIds
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (supported) MaterialTheme.colorScheme.primary.copy(alpha = .55f)
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(Modifier.padding(13.dp)) {
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
                                fontSize = 11.sp,
                                lineHeight = 15.sp
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Requires: " + agent.requiredCapabilities.joinToString(" · ") { it.name.replace('_', ' ') },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    if (supported) {
                        Spacer(Modifier.height(9.dp))
                        OutlinedButton(onClick = { viewModel.selectAgent(agent.id) }) {
                            Text(if (state.selectedAgentId == agent.id) "Selected" else "Use Persona")
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
    editing?.let { agent ->
        var name by remember(agent.id) { mutableStateOf(agent.name) }
        var instructions by remember(agent.id) { mutableStateOf(agent.instructions) }
        var tasks by remember(agent.id) { mutableStateOf(agent.tasks.joinToString("\n")) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Edit agent") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(instructions, { instructions = it }, label = { Text("Instructions") }, minLines = 3)
                OutlinedTextField(tasks, { tasks = it }, label = { Text("Tasks · one per line") }, minLines = 2)
                Text("Tools: configured through provider and workspace connections", fontSize = 11.sp)
            } },
            confirmButton = { TextButton(onClick = {
                if (name.isNotBlank()) {
                    store.save(agent.copy(name = name.trim().take(60), instructions = instructions.take(8000),
                        tasks = tasks.lines().map { it.trim().take(1000) }.filter { it.isNotBlank() }.take(20)))
                    personal = store.list(); editing = null
                }
            }) { Text("Save changes") } },
            dismissButton = { Row {
                TextButton(onClick = { store.delete(agent.id); personal = store.list(); editing = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { editing = null }) { Text("Cancel") }
            } })
    }
}
