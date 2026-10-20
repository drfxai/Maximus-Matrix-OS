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
    onRefreshCatalog: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
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
                Text("Recovery scope: process-local reservations; no automatic replay after ambiguity",
                    style = MaterialTheme.typography.labelSmall, color = MaximusReference.muted)
                if (onRefreshCatalog != null) OutlinedButton(onClick = onRefreshCatalog,
                    enabled = state.smartComboEnabled && !state.smartComboRefreshing && !state.isGenerating,
                    modifier = Modifier.fillMaxWidth().testTag("smart_combo_catalog_refresh")) {
                    Text(if (state.smartComboRefreshing) "Checking catalog…" else "Refresh catalog evidence")
                }
            }
        }
    }
}
