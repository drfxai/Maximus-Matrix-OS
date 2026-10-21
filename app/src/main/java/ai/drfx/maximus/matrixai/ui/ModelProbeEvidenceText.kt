package ai.drfx.maximus.matrixai.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Historical result, not a claim of permanent entitlement or remaining provider credit. */
internal fun modelProbeOutcomeLabel(evidence: ModelProbeEvidence?): String = when (evidence?.outcome) {
    ModelProbeOutcome.SUCCEEDED -> "Last test passed"
    ModelProbeOutcome.FAILED -> "Last test unsuccessful"
    ModelProbeOutcome.UNCERTAIN -> "Last test inconclusive"
    null -> "Catalog candidate · not tested"
}

internal fun modelProbeChoiceDetail(evidence: ModelProbeEvidence?): String =
    modelProbeOutcomeLabel(evidence) + (evidence?.let {
        " · " + DateFormat.getDateTimeInstance().format(Date(it.observedAtEpochMs))
    } ?: "")

@Composable
internal fun ModelProbeEvidenceText(evidence: ModelProbeEvidence?, modifier: Modifier = Modifier) {
    // One expiration wakeup per observation; do not recompose the whole chat on a frame timer.
    val fresh by produceState(evidence?.isFresh(System.currentTimeMillis()) == true, evidence) {
        value = evidence?.isFresh(System.currentTimeMillis()) == true
        if (value && evidence != null) {
            delay((ApiCatalogSession.CATALOG_TTL_MS - (System.currentTimeMillis() - evidence.observedAtEpochMs) + 1).coerceAtLeast(1))
            value = false
        }
    }
    val reason = when (evidence?.failure) {
        ProviderFailure.AUTHENTICATION -> " · check API credentials"
        ProviderFailure.FORBIDDEN -> " · account access denied"
        ProviderFailure.RATE_LIMIT_OR_QUOTA -> " · rate limit or quota"
        ProviderFailure.MODEL_OR_ENDPOINT -> " · check model and endpoint"
        ProviderFailure.SERVICE_OUTAGE -> " · provider unavailable"
        ProviderFailure.REDIRECT -> " · redirect rejected"
        ProviderFailure.REQUEST -> " · request compatibility"
        null -> ""
    }
    Text(modelProbeChoiceDetail(evidence) + reason +
        (if (evidence != null && !fresh) " · stale; test again" else ""),
        modifier = modifier, color = MaximusReference.muted, fontSize = 10.sp)
}
