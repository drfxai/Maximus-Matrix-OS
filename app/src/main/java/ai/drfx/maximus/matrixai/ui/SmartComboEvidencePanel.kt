package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ai.drfx.maximus.matrixai.llm.LlmUiState
import ai.drfx.maximus.matrixai.llm.SmartComboEvidenceProjection
import ai.drfx.maximus.matrixai.llm.SmartComboQuotaLedger
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/** Clock advances only while this screen is started; expiry never needs a network request. */
@Composable
internal fun rememberSmartComboEvidence(state: LlmUiState): SmartComboEvidenceProjection.Snapshot {
    val owner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }
    return remember(state, now) { SmartComboEvidenceProjection.from(state, now) }
}

internal fun comboReadingLabel(reading: SmartComboEvidenceProjection.Reading): String =
    reading.value?.let { NumberFormat.getIntegerInstance().format(it) }
        ?: when (reading.status) {
            SmartComboEvidenceProjection.Status.STALE -> "Stale"
            SmartComboEvidenceProjection.Status.ERROR -> "Invalid evidence"
            SmartComboEvidenceProjection.Status.UNAVAILABLE -> "Unavailable"
            else -> "Unknown"
        }

/** Existing authoritative StateFlow is projected here; rendering writes no application facts. */
@Composable
internal fun SmartComboEvidencePanel(state: LlmUiState,
    evidence: SmartComboEvidenceProjection.Snapshot,
    onRefreshCatalog: (() -> Unit)? = null,
    durability: SmartComboQuotaLedger.Durability? = null,
    onArchiveSettled: (() -> Unit)? = null,
    archiveMessage: String = "") {
    var expanded by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    if (confirmArchive) AlertDialog(onDismissRequest = { confirmArchive = false },
        title = { Text("Archive settled Smart Combo evidence?") },
        text = { Text("Move settled records older than five minutes into immutable app-private archives. Request IDs, provider usage and checkpoint evidence remain recoverable. No request is replayed. Uncertain and in-flight requests are never archived. Nothing is deleted.") },
        confirmButton = { TextButton(onClick = {
            confirmArchive = false; onArchiveSettled?.invoke()
        }) { Text("Archive settled records") } },
        dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel") } })
    val active = evidence.active
    fun date(at: Long?): String = at?.takeIf { it >= 0 }?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it))
    } ?: "Unknown"
    val phase = when (evidence.phase) {
        SmartComboEvidenceProjection.Phase.IDLE -> "Idle"
        SmartComboEvidenceProjection.Phase.PREPARING -> "Preparing · no provider dispatched"
        SmartComboEvidenceProjection.Phase.DISPATCHED -> "Dispatched · waiting for output"
        SmartComboEvidenceProjection.Phase.STREAMING -> "Streaming · estimated output"
    }
    MaximusFramedCard {
        Column(Modifier.fillMaxWidth().testTag("smart_combo_runtime_evidence"),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Runtime evidence", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Text(phase, style = MaterialTheme.typography.labelMedium,
                color = if (active != null) MaximusReference.gold else MaximusReference.muted)
            Text("Actual dispatch: " + (active?.let { it.provider.displayName + " · " + it.modelId }
                ?: "None active"), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("smart_combo_actual_dispatch"))
            Text("Configured: " + evidence.configuredProvider.displayName + " · " +
                evidence.configuredModel.ifBlank { "No model selected" },
                style = MaterialTheme.typography.labelSmall, color = MaximusReference.muted)
            Text("TPM snapshot: " + comboReadingLabel(evidence.tokenRate) + " · " +
                evidence.requestRateKind.takeIf { it in setOf("RPM", "RPD") }.orEmpty()
                    .ifBlank { "Request window" } + ": " + comboReadingLabel(evidence.requestTokens),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("smart_combo_rate_evidence"))
            durability?.let { journal ->
                val status = when (journal.status) {
                    "DURABLE" -> "Ready · device-local durable journal"
                    "ERROR" -> "Unavailable · automatic routing paused"
                    "FULL" -> "Full · new reservations paused; evidence preserved"
                    "NOT_OPENED" -> "Opening request journal…"
                    else -> "Process-local only"
                }
                Text("Request recovery: " + status, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("smart_combo_journal_status"))
                if (journal.uncertainRequests > 0) Text(
                    "Uncertain requests: " + journal.uncertainRequests + " · never automatically replayed",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth().testTag("smart_combo_evidence_expand")) {
                Text(if (expanded) "Hide evidence details" else "Inspect evidence and candidates")
            }
            if (expanded) {
                active?.let {
                    Text("Active agent: " + it.agentName.ifBlank { it.agentId },
                        style = MaterialTheme.typography.bodySmall)
                    Text("Dispatch observed: " + date(it.startedAtEpochMs),
                        style = MaterialTheme.typography.labelSmall)
                    Text("Elapsed since dispatch: " +
                        (evidence.elapsedSinceDispatchMs?.let { ms -> "${ms / 1000}s" } ?: "Unknown") +
                        " · local elapsed time, not provider latency",
                        style = MaterialTheme.typography.labelSmall)
                }
                evidence.lastDispatch?.let {
                    Text("Last dispatched: " + it.provider.displayName + " · " + it.modelId +
                        " · " + date(it.startedAtEpochMs),
                        style = MaterialTheme.typography.labelSmall)
                    Text("A dispatch record is not proof of successful completion.",
                        style = MaterialTheme.typography.labelSmall, color = MaximusReference.muted)
                }
                Text("Rate source: provider response headers · " +
                    date(state.tokenMetrics.rateEvidenceAtEpochMs),
                    style = MaterialTheme.typography.labelSmall)
                Text("TPM: " + evidence.tokenRate.status.name + " · " + evidence.tokenRate.note,
                    style = MaterialTheme.typography.labelSmall, color = MaximusReference.muted)
                Text("Requests: " + evidence.requestTokens.status.name + " · " + evidence.requestTokens.note,
                    style = MaterialTheme.typography.labelSmall, color = MaximusReference.muted)
                HorizontalDivider(color = MaximusReference.stroke)
                Text("Zero-price catalog candidates: " + comboReadingLabel(evidence.catalog) +
                    " · " + date(evidence.catalog.observedAtEpochMs),
                    style = MaterialTheme.typography.labelMedium)
                Text(evidence.catalog.note, style = MaterialTheme.typography.labelSmall,
                    color = MaximusReference.muted)
                evidence.candidateLabels.forEach {
                    Text("• " + it, style = MaterialTheme.typography.labelSmall)
                }
                Text("Provider credit: Unknown · no account balance API evidence",
                    style = MaterialTheme.typography.labelSmall)
                Text("Automatic paid fallback: Disabled",
                    style = MaterialTheme.typography.labelSmall)
                if (durability == null) Text("Request journal state is not inspected by this view.",
                    style = MaterialTheme.typography.labelSmall, color = MaximusReference.muted)
                durability?.let { journal ->
                    Text("Retained request IDs: " + journal.retainedRequests +
                        " · Active journal: " + journal.activeJournalRequests +
                        " · Archive segments: " + journal.archiveSegments +
                        " · Checkpoint references: " + journal.checkpointReferences,
                        style = MaterialTheme.typography.labelSmall)
                    journal.confirmedTokensByProvider.forEach { (provider, tokens) ->
                        Text(provider.displayName + " · confirmed journal tokens: " +
                            NumberFormat.getIntegerInstance().format(tokens),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Journal totals cover automatic free-route requests only; estimates and manual usage remain separate.",
                        style = MaterialTheme.typography.labelSmall, color = MaximusReference.muted)
                    Text("Recovery never restores credentials, replays a request or grants tool permission. Uncertainty needs provider evidence.",
                        style = MaterialTheme.typography.labelSmall, color = MaximusReference.muted)
                }
                if (onArchiveSettled != null) {
                    OutlinedButton(onClick = { confirmArchive = true },
                        enabled = !state.isGenerating && durability?.status in setOf("DURABLE", "FULL") &&
                            (durability?.activeJournalRequests ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth().testTag("smart_combo_archive_review")) {
                        Text("Review settled-evidence archival")
                    }
                    if (archiveMessage.isNotBlank()) Text(archiveMessage,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("smart_combo_archive_result"))
                }
                if (onRefreshCatalog != null) OutlinedButton(onClick = onRefreshCatalog,
                    enabled = state.smartComboEnabled && !state.smartComboRefreshing && !state.isGenerating,
                    modifier = Modifier.fillMaxWidth().testTag("smart_combo_catalog_refresh")) {
                    Text(if (state.smartComboRefreshing) "Checking catalog…" else "Refresh catalog evidence")
                }
            }
        }
    }
}
