package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.agent.teams.ProjectTeamRun
import ai.drfx.maximus.matrixai.agent.teams.ProjectTeamHandoff
import ai.drfx.maximus.matrixai.memory.SharedProjectMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Actual project run evidence, with a separate approval of the captured metadata. */
@Composable
fun ProjectTeamRunPanel(projectId: String?, run: ProjectTeamRun?, message: String,
    prepare: (String) -> Unit, approve: (String) -> Unit, cancel: () -> Unit,
    invalid: Boolean = false, discardInvalid: () -> Unit = {},
    modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val memory = remember(context) { SharedProjectMemory(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var pendingHandoff by remember(run?.id) { mutableStateOf<String?>(null) }
    var sendingHandoff by remember(run?.id) { mutableStateOf(false) }
    var handoffStatus by remember(run?.id) { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }
    var showInputs by remember(run?.id) { mutableStateOf(false) }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.project_team_invalid)) },
        text = { Text(stringResource(R.string.project_team_discard_help)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; discardInvalid() }) {
            Text(stringResource(R.string.project_team_discard))
        } }, dismissButton = { TextButton(onClick = { confirmDiscard = false }) {
            Text(stringResource(R.string.project_team_keep))
        } })
    pendingHandoff?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!sendingHandoff) pendingHandoff = null },
            title = { Text(stringResource(R.string.team_hindsight_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.team_hindsight_disclosure),
                        style = MaterialTheme.typography.bodySmall)
                    SelectionContainer {
                        Text(preview, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !sendingHandoff,
                    modifier = Modifier.testTag("project-team-hindsight-confirm"),
                    onClick = {
                        val selected = run?.takeIf {
                            it.projectId == projectId &&
                                it.state == ProjectTeamRun.State.COMPLETE
                        }
                        if (selected == null ||
                            ProjectTeamHandoff.approvedCheckpoint(selected, true) != preview) {
                            handoffStatus = context.getString(R.string.team_hindsight_failed)
                            pendingHandoff = null
                        } else {
                            sendingHandoff = true
                            scope.launch {
                                try {
                                    memory.storeApprovedTeamHandoff(selected, approved = true)
                                    handoffStatus = context.getString(R.string.team_hindsight_success)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    handoffStatus = context.getString(R.string.team_hindsight_failed)
                                } finally {
                                    sendingHandoff = false
                                    pendingHandoff = null
                                }
                            }
                        }
                    }
                ) { Text(stringResource(R.string.team_hindsight_send)) }
            },
            dismissButton = {
                TextButton(enabled = !sendingHandoff,
                    onClick = { pendingHandoff = null }) {
                    Text(stringResource(R.string.studio_cancel))
                }
            }
        )
    }
    ElevatedCard(modifier.fillMaxWidth().animateContentSize().testTag("project-team-panel")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.project_team_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.project_team_boundary), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamMetric(stringResource(R.string.project_team_roles), "2", Modifier.weight(1f))
                TeamMetric(stringResource(R.string.project_team_output_limit), "2 × 1,024", Modifier.weight(1f))
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            if (invalid) {
                Text(stringResource(R.string.project_team_invalid), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { confirmDiscard = true }) { Text(stringResource(R.string.project_team_discard)) }
            }
            val selected = run?.takeIf { it.projectId == projectId }
            if (selected == null) {
                if (run?.busy == true) Text(stringResource(R.string.project_team_other_project))
                Button(onClick = { projectId?.let(prepare) }, enabled = projectId != null && run?.busy != true && !invalid,
                    modifier = Modifier.testTag("project-team-prepare")) {
                    Text(stringResource(R.string.project_team_prepare))
                }
            } else {
                Text(stringResource(when (selected.state) {
                    ProjectTeamRun.State.AWAITING_APPROVAL -> R.string.project_team_awaiting
                    ProjectTeamRun.State.PLANNER_READY -> R.string.project_team_planner_ready
                    ProjectTeamRun.State.PLANNER_DISPATCHED -> R.string.project_team_planner_running
                    ProjectTeamRun.State.REVIEWER_READY -> R.string.project_team_reviewer_ready
                    ProjectTeamRun.State.REVIEWER_DISPATCHED -> R.string.project_team_reviewer_running
                    ProjectTeamRun.State.COMPLETE -> R.string.project_team_complete
                    ProjectTeamRun.State.CANCELLED -> R.string.project_team_cancelled
                    ProjectTeamRun.State.UNCERTAIN -> R.string.project_team_uncertain
                    ProjectTeamRun.State.BLOCKED -> R.string.project_team_blocked
                }), style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("project-team-state"))
                if (selected.dispatched) {
                    Text(selected.activeProvider + " · " + selected.activeModel,
                        style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { showInputs = !showInputs }) {
                    Text(stringResource(R.string.project_team_inputs))
                }
                if (showInputs) SelectionContainer {
                    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        Text(selected.prompt, style = MaterialTheme.typography.bodySmall)
                        Text("SHA-256 · " + selected.revision, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (selected.state == ProjectTeamRun.State.AWAITING_APPROVAL) {
                    SelectionContainer { Text(selected.approvedDestinations, style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.project_team_consent), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { approve(selected.id) }, modifier = Modifier.testTag("project-team-approve")) {
                        Text(stringResource(R.string.project_team_approve))
                    }
                }
                if (selected.busy || selected.state == ProjectTeamRun.State.AWAITING_APPROVAL) {
                    OutlinedButton(onClick = cancel, modifier = Modifier.testTag("project-team-cancel")) {
                        Text(stringResource(R.string.project_team_stop))
                    }
                } else Button(onClick = { projectId?.let(prepare) }, modifier = Modifier.testTag("project-team-prepare")) {
                    Text(stringResource(R.string.project_team_prepare))
                }
                selected.planner?.let { TeamEvidence(stringResource(R.string.project_team_planner), it) }
                selected.reviewer?.let { TeamEvidence(stringResource(R.string.project_team_reviewer), it) }
                if (selected.state == ProjectTeamRun.State.COMPLETE) {
                    val shareable = remember(selected.id, selected.planner?.sha256,
                        selected.reviewer?.sha256) {
                        ProjectTeamHandoff.approvedCheckpoint(selected, approved = true)
                    }
                    if (shareable != null) {
                        OutlinedButton(
                            onClick = { pendingHandoff = shareable },
                            enabled = !sendingHandoff && memory.enabled(),
                            modifier = Modifier.testTag("project-team-hindsight-preview")
                        ) { Text(stringResource(R.string.team_hindsight_review)) }
                        if (!memory.enabled()) {
                            Text(stringResource(R.string.team_hindsight_disabled),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (sendingHandoff) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (handoffStatus.isNotBlank()) {
                            Text(handoffStatus, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun TeamMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun TeamEvidence(label: String, evidence: ProjectTeamRun.Evidence) {
    var expanded by remember(evidence.sha256) { mutableStateOf(false) }
    HorizontalDivider()
    TextButton(onClick = { expanded = !expanded }) { Text(label + " · " + evidence.provider + " · " + evidence.model) }
    Text(stringResource(if (evidence.estimated) R.string.project_team_estimated else R.string.project_team_reported,
        evidence.inputTokens, evidence.outputTokens), style = MaterialTheme.typography.labelSmall)
    if (expanded) SelectionContainer {
        Text(evidence.text, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
    }
}
