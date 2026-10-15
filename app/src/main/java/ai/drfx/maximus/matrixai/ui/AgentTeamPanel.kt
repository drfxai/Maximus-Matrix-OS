package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.agent.teams.AgentTeamJob
import ai.drfx.maximus.matrixai.agent.teams.AgentTeamScheduler
import ai.drfx.maximus.matrixai.agent.teams.AgentTeamSupervisor
import ai.drfx.maximus.matrixai.agent.teams.AgentTeamSpec
import ai.drfx.maximus.matrixai.agent.teams.AgentTeamStore
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import java.text.DateFormat
import java.util.Date

/**
 * REAL local best-effort scheduled tasks. Does not present agent personas as
 * inference workers, or imply the phone can run continuously 24/7 in Doze.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentTeamPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { AgentTeamStore(context) }
    var teams by remember { mutableStateOf(store.list()) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AgentTeamJob.VERIFIED_NEWS) }
    var period by remember { mutableLongStateOf(180L) }
    var feedback by remember { mutableStateOf("") }
    val createFailure = stringResource(R.string.dash_create_failed)
    val scheduleFailure = stringResource(R.string.dash_schedule_failed)
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4500)
            teams = store.list()
            refreshTick++
        }
    }

    var showCreate by remember { mutableStateOf(teams.isEmpty()) }
    // RUNNING is observed from WorkManager; an enabled periodic schedule is never counted as live.
    val workInfos by produceState<List<WorkInfo>?>(null, context) {
        try {
            WorkManager.getInstance(context).getWorkInfosByTagFlow("maximus.team").collect { value = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { value = null }
    }
    val supervisor = remember(teams, refreshTick) {
        AgentTeamSupervisor.observe(teams, System.currentTimeMillis(), store::report)
    }
    Column(modifier.fillMaxWidth().testTag("agent_team_dashboard"),
        verticalArrangement = Arrangement.spacedBy(11.dp)) {
        CommandDashboardCard(Modifier.fillMaxWidth().testTag("agent_team_supervisor")) {
            Text(stringResource(R.string.dash_team_title),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            CommandStatus(stringResource(R.string.dash_android_scheduler), workInfos != null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandMetric(stringResource(R.string.dash_running_jobs),
                    workInfos?.count { it.state == WorkInfo.State.RUNNING }?.toString()
                        ?: stringResource(R.string.api_unknown),
                    stringResource(R.string.dash_observed_workmanager), Modifier.weight(1f))
                CommandMetric(stringResource(R.string.dash_enabled_schedules),
                    supervisor.active.toString(), stringResource(R.string.dash_of_teams, teams.size),
                    Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandMetric(stringResource(R.string.dash_verified_reports), supervisor.healthy.toString(),
                    stringResource(R.string.dash_last_report_evidence), Modifier.weight(1f))
                CommandMetric(stringResource(R.string.dash_attention), supervisor.attention.toString(),
                    stringResource(R.string.dash_attention_detail), Modifier.weight(1f),
                    attention = supervisor.attention > 0)
            }
            Text(stringResource(R.string.team_supervisor_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { showCreate = !showCreate }, modifier = Modifier.fillMaxWidth()
                .testTag("agent_team_create_toggle")) {
                Text(stringResource(if (showCreate) R.string.dash_hide_setup else R.string.dash_new_team))
            }
        }
        AnimatedVisibility(visible = showCreate) {
            ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.team_title),
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(stringResource(R.string.team_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(60) },
                        label = { Text(stringResource(R.string.team_name)) },
                        modifier = Modifier.fillMaxWidth().testTag("agent_team_name"),
                        singleLine = true
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = kind == AgentTeamJob.VERIFIED_NEWS,
                            onClick = { kind = AgentTeamJob.VERIFIED_NEWS },
                            label = { Text(stringResource(R.string.team_news)) }
                        )
                        FilterChip(
                            selected = kind == AgentTeamJob.DEVICE_HEALTH,
                            onClick = { kind = AgentTeamJob.DEVICE_HEALTH },
                            label = { Text(stringResource(R.string.team_health)) }
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        for ((minutes, label) in listOf(
                            15L to stringResource(R.string.team_15m),
                            180L to stringResource(R.string.team_3h),
                            1440L to stringResource(R.string.team_day)
                        )) {
                            FilterChip(selected = period == minutes, onClick = { period = minutes },
                                label = { Text(label) })
                        }
                    }
                    Button(
                        onClick = {
                            try {
                                val team = store.create(name.trim(), kind, period)
                                AgentTeamScheduler.schedule(context, team)
                                teams = store.list()
                                feedback = ""
                                name = ""
                                showCreate = false
                            } catch (e: Exception) {
                                feedback = e.message ?: createFailure
                            }
                        },
                        enabled = name.trim().length in 2..60 && teams.size < 8,
                        modifier = Modifier.fillMaxWidth().testTag("agent_team_create")
                    ) {
                        Icon(Icons.Default.Autorenew, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.team_create))
                    }
                    Text(stringResource(R.string.team_android_limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (feedback.isNotBlank()) {
                        Text(feedback, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

        }
        if (teams.isEmpty()) {
            Text(stringResource(R.string.team_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        teams.forEach { team ->
            key(team.id) {
                val report = remember(team.id, refreshTick) { store.report(team.id) }
                val observedHealth = supervisor.items.firstOrNull { it.teamId == team.id }?.health
                Surface(shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(team.name, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f))
                            Text(if (team.enabled) stringResource(R.string.dash_scheduled)
                                else stringResource(R.string.team_paused),
                                color = if (team.enabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            (if (team.job == AgentTeamJob.VERIFIED_NEWS)
                                stringResource(R.string.team_news)
                            else stringResource(R.string.team_health)) + " · " +
                                when (team.periodMinutes) {
                                    15L -> stringResource(R.string.team_15m)
                                    180L -> stringResource(R.string.team_3h)
                                    else -> stringResource(R.string.team_day)
                                },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            when (observedHealth) {
                                AgentTeamSupervisor.Health.HEALTHY ->
                                    stringResource(R.string.team_supervisor_healthy)
                                AgentTeamSupervisor.Health.STALE ->
                                    stringResource(R.string.team_supervisor_stale)
                                AgentTeamSupervisor.Health.UNAVAILABLE ->
                                    stringResource(R.string.team_supervisor_unavailable)
                                AgentTeamSupervisor.Health.CLOCK_ANOMALY ->
                                    stringResource(R.string.team_supervisor_clock)
                                AgentTeamSupervisor.Health.PAUSED ->
                                    stringResource(R.string.team_paused)
                                else -> stringResource(R.string.team_supervisor_waiting)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (observedHealth == AgentTeamSupervisor.Health.STALE ||
                                observedHealth == AgentTeamSupervisor.Health.UNAVAILABLE ||
                                observedHealth == AgentTeamSupervisor.Health.CLOCK_ANOMALY)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(stringResource(R.string.team_roles),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.team_last) + ": " +
                            if (report.atEpochMs > 0L)
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(report.atEpochMs))
                            else stringResource(R.string.team_no_report),
                            style = MaterialTheme.typography.bodySmall)
                        if (report.summary.isNotBlank()) {
                            Text(report.status + " · " + report.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 5)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                enabled = team.enabled,
                                onClick = {
                                    runCatching { AgentTeamScheduler.runNow(context, team) }
                                        .onFailure { feedback = it.message ?: scheduleFailure }
                                }, modifier = Modifier.testTag("agent_team_run_now")
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(15.dp))
                                Text(stringResource(R.string.team_run))
                            }
                            OutlinedButton(onClick = {
                                runCatching {
                                    val changed = store.setEnabled(team.id, !team.enabled)
                                    AgentTeamScheduler.schedule(context, changed)
                                    teams = store.list()
                                }.onFailure { feedback = it.message.orEmpty() }
                            }) {
                                Icon(Icons.Default.StopCircle, null, Modifier.size(15.dp))
                                Text(if (team.enabled) stringResource(R.string.team_pause)
                                    else stringResource(R.string.team_resume))
                            }
                            TextButton(onClick = { pendingDelete = team.id }) {
                                Text(stringResource(R.string.team_delete))
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.team_capability_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.team_connections_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
    val deleting = pendingDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.team_delete)) },
            text = { Text(teams.firstOrNull { it.id == deleting }?.name.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    AgentTeamScheduler.cancel(context, deleting)
                    store.remove(deleting)
                    teams = store.list()
                    pendingDelete = null
                }) { Text(stringResource(R.string.team_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.graph_edit_cancel))
                }
            }
        )
    }
}
