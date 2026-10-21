package ai.drfx.maximus.matrixai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.drfx.maximus.matrixai.operations.*
import ai.drfx.maximus.matrixai.studio.ProjectContinuityStore
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** Native read-only dashboard projections; actions go through the existing command authorities. */
@Composable
internal fun OperationsScreen(viewModel: MatrixViewModel, onBack: () -> Unit,
    onWork: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val repository = remember(context) { OperationsRepository(context) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(OperationsSnapshot()) }
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var commandBusy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var checkpoint by remember { mutableStateOf("") }
    var checkpointBusy by remember { mutableStateOf(false) }
    val run by viewModel.projectTeamRun.collectAsStateWithLifecycle()
    val invalidRun by viewModel.projectTeamInvalid.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LaunchedEffect(repository, owner, refresh) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refreshing = true
                try {
                    snapshot = withContext(Dispatchers.IO) { repository.snapshot() }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { feedback = "Local observations unavailable. Refresh to retry." }
                finally { refreshing = false }
                now = System.currentTimeMillis()
                delay(10_000L)
            }
        }
    }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(1_000L) }
        }
    }
    fun command(routine: RoutineProjection, action: String) {
        if (commandBusy || refreshing) return
        commandBusy = true
        scope.launch {
            feedback = try {
                withContext(Dispatchers.IO) { repository.command(routine, action) }
                "Command submitted. Actual job state is read from WorkManager."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { "Command outcome requires review. Refresh state before retry." }
            finally { commandBusy = false; refresh++ }
        }
    }
    val tabs = listOf("Agents", "Pulse", "Memory", "Runtime", "Security")
    LazyColumn(modifier.fillMaxSize().testTag("operations_screen"),
        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            WorkspaceBrand()
            Row(Modifier.fillMaxWidth()) {
                Text("Operations", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                    color = MaximusReference.gold, fontWeight = FontWeight.Bold)
                TextButton(onClick = onBack) { Text("Back to Hub") }
            }
            Text("Authoritative state, observed locally · no background remote probes",
                style = MaterialTheme.typography.bodySmall, color = MaximusReference.muted)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                tabs.forEachIndexed { index, title ->
                    FilterChip(selected = tab == index, onClick = { tab = index },
                        label = { Text(title) }, modifier = Modifier.padding(end = 6.dp).testTag("operations_tab_$index"))
                }
            }
            Text("Last local observation: ${time(snapshot.observedAt)}", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { refresh++ }, enabled = !refreshing && !commandBusy) {
                Text(if (refreshing) "Reading local state…" else "Refresh observations")
            }
            if (feedback.isNotBlank()) Text(feedback, color = MaximusReference.gold)
        }
        if (tab == 0) {
            item { MaximusFramedCard {
                Text("Agent Command Center", color = MaximusReference.gold, fontWeight = FontWeight.Bold)
                Text("${snapshot.personalAgents?.toString() ?: "UNKNOWN"} saved personal definitions · ${if (snapshot.routinesState == EvidenceState.OBSERVED) snapshot.routines.size.toString() else snapshot.routinesState.name} local routines")
                Text("Definitions are not online agents. Project team state comes from its durable execution checkpoint.",
                    style = MaterialTheme.typography.bodySmall)
                if (invalidRun) Text("ERROR · Project team checkpoint is invalid. Open Work for explicit recovery.", color = MaterialTheme.colorScheme.error)
                else if (run == null) Text("UNKNOWN · No project team run recorded")
                else run?.let { value ->
                    Text("${value.state.name} · project ${value.projectId.take(8)}")
                    Text("Run ${value.id.take(8)} · created ${time(value.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    Text("Backend: Android-local orchestration · inference provider ${value.activeProvider.ifBlank { "none dispatched" }}")
                    Text("Model: ${value.activeModel.ifBlank { "none dispatched" }}")
                    Text("Planner evidence: ${if (value.planner != null) "recorded" else "absent"} · reviewer: ${if (value.reviewer != null) "recorded" else "absent"}")
                    Text("${value.planner?.inputTokens?.toString() ?: "UNKNOWN"} planner input tokens · ${value.planner?.outputTokens?.toString() ?: "UNKNOWN"} output tokens · ${if (value.planner?.estimated != false) "estimate / no confirmed evidence" else "provider-reported"}")
                    if (value.state.name == "AWAITING_APPROVAL") Text("Approval pending · review exact destinations and task in Work")
                }
                TextButton(onClick = { onWork(0) }) { Text("Open Work & approvals") }
            } }
            item { Text("Local monitoring teams", fontWeight = FontWeight.Bold) }
        }
        if (tab == 0 || tab == 1) {
            if (tab == 1) item { MaximusFramedCard {
                Text("Pulse Control Center", color = MaximusReference.gold, fontWeight = FontWeight.Bold)
                Text("Android WorkManager · best effort under Doze, network and battery restrictions.")
                Text("Next eligible time is not a guaranteed start. Pause and Cancel both disable scheduling and request cancellation of current jobs.", style = MaterialTheme.typography.bodySmall)
            } }
            if (snapshot.routines.isEmpty()) item { Text(if (snapshot.routinesState == EvidenceState.OBSERVED) "No local routines configured. Configure a team in Agents → Teams." else "${snapshot.routinesState} · Routine index has no usable observation") }
            snapshot.routines.forEach { routine -> item(key = routine.id) { MaximusFramedCard {
                Text(routine.name, color = MaximusReference.gold, fontWeight = FontWeight.Bold)
                Text("${routine.job} · ${if (routine.enabled) "scheduling enabled" else "paused"} · every ${routine.periodMinutes} min")
                EvidenceRow(routine.evidence, now)
                EvidenceRow(routine.lastResult, now)
                Text("Next eligible: ${routine.nextEligibleAt?.let(::time) ?: "UNKNOWN"}", style = MaterialTheme.typography.bodySmall)
                if (tab == 1) Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { command(routine, "RUN") }, enabled = routine.enabled && !commandBusy && !refreshing && now - snapshot.observedAt in 0L..30_000L) { Text("Run now") }
                    TextButton(onClick = { command(routine, if (routine.enabled) "PAUSE" else "RESUME") }, enabled = !commandBusy && !refreshing && now - snapshot.observedAt in 0L..30_000L) { Text(if (routine.enabled) "Pause" else "Resume") }
                    TextButton(onClick = { command(routine, "CANCEL") }, enabled = !commandBusy && !refreshing && now - snapshot.observedAt in 0L..30_000L) { Text("Cancel all") }
                }
            } } }
        }
        if (tab == 2) {
            item { MaximusFramedCard {
                Text("Memory Explorer", color = MaximusReference.gold, fontWeight = FontWeight.Bold)
                snapshot.evidence.filter { it.source.startsWith("Hindsight") }.forEach { EvidenceRow(it, now) }
                Text("Project-isolated banks remain in Hindsight. Recalled text is untrusted evidence, never tool authorization.")
                TextButton(onClick = { onWork(5) }) { Text("Manage shared memory & recall in Work") }
            } }
            if (snapshot.projects.isEmpty()) item { Text(if (snapshot.projectsState == EvidenceState.OBSERVED) "No indexed local projects" else "${snapshot.projectsState} · Project index has no usable observation") }
            snapshot.projects.forEach { project -> item(key = project.id) { MaximusFramedCard {
                Text(project.name, color = MaximusReference.gold)
                Text("${project.id} · ${project.fileCount} workspace files", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !checkpointBusy, onClick = {
                    checkpointBusy = true
                    scope.launch {
                        checkpoint = try {
                            withContext(Dispatchers.IO) {
                                val store = ProjectWorkspaceStore(context)
                                val result = ProjectContinuityStore(context, store).latest(project.id)
                                if (result == null) "${project.name}: UNKNOWN · no checkpoint"
                                else "${project.name}: ${if (result.unchanged) "VALIDATED" else "STALE"} · ${result.revision.take(12)} · modified ${result.modifiedPaths.size}, missing ${result.missingPaths.size} · checked ${time(System.currentTimeMillis())}"
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { "${project.name}: ERROR · checkpoint validation failed" }
                        finally { checkpointBusy = false }
                    }
                }) { Text("Validate latest checkpoint") }
            } } }
            if (checkpoint.isNotBlank()) item { Text(checkpoint) }
        }
        if (tab == 3) item { MaximusFramedCard {
            Text("Runtime & Infrastructure", color = MaximusReference.gold, fontWeight = FontWeight.Bold)
            snapshot.evidence.filter { !it.source.startsWith("Hindsight") }.forEach { EvidenceRow(it, now) }
            Text("PRoot shares Android's kernel and is not hostile-code isolation. Cloudflare is not a persistent Linux host.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onWork(3) }) { Text("Open phone runtime controls") }
            TextButton(onClick = { onWork(4) }) { Text("Open authenticated developer connections") }
        } }
        if (tab == 4) item { MaximusFramedCard {
            Text("Security & Audit", color = MaximusReference.gold, fontWeight = FontWeight.Bold)
            Text("Local command history · bounded to 30 audit entries; timestamps are epoch milliseconds.")
            Text("Cloud write approvals, credential checks and receipts remain in Work. No global security-pass status is inferred.", style = MaterialTheme.typography.bodySmall)
            if (snapshot.commandHistory.isEmpty()) Text("UNKNOWN · No operational commands recorded")
            snapshot.commandHistory.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        } }
        snapshot.evidence.filter { it.state == EvidenceState.ERROR }.forEach { item { EvidenceRow(it, now) } }
    }
}

@Composable
private fun EvidenceRow(evidence: OperationalEvidence, now: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("${evidence.freshness(now)} · ${evidence.source}", color = MaximusReference.gold,
            style = MaterialTheme.typography.labelMedium)
        Text(evidence.value, style = MaterialTheme.typography.bodyMedium)
        Text("Observed ${time(evidence.observedAt)}" + (evidence.eventAt?.let { " · source event ${time(it)}" } ?: ""),
            style = MaterialTheme.typography.labelSmall, color = MaximusReference.muted)
    }
}
private fun time(at: Long): String = if (at <= 0L) "UNKNOWN" else Instant.ofEpochMilli(at).toString()
