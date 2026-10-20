package ai.drfx.maximus.matrixai.llm

/**
 * Read-only projection of existing inference state. It never reserves quota, sends a request,
 * changes a model, or turns catalog price into live account credit. A screen refresh cannot
 * extend provider evidence lifetime.
 */
internal object SmartComboEvidenceProjection {
    const val MINUTE_EVIDENCE_TTL_MS = 60_000L
    const val DAY_EVIDENCE_TTL_MS = 300_000L
    const val CATALOG_EVIDENCE_TTL_MS = 600_000L

    enum class Status { OBSERVED, DERIVED, STALE, UNKNOWN, UNAVAILABLE, ERROR }
    enum class Phase { IDLE, PREPARING, DISPATCHED, STREAMING }

    data class Reading(
        val value: Long?,
        val status: Status,
        val observedAtEpochMs: Long?,
        val note: String
    )

    data class Snapshot(
        val phase: Phase,
        val active: ChatRequestTelemetry?,
        val lastDispatch: ChatRequestTelemetry?,
        val configuredProvider: LlmProvider,
        val configuredModel: String,
        val requestTokens: Reading,
        val tokenRate: Reading,
        val requestRateKind: String,
        val catalog: Reading,
        val candidateLabels: List<String>,
        val elapsedSinceDispatchMs: Long?
    )

    fun from(state: LlmUiState, nowMs: Long): Snapshot {
        require(nowMs >= 0)
        val active = state.activeRequest.takeIf { state.isGenerating }
        val metrics = state.tokenMetrics
        val provider = active?.provider ?: state.provider
        val model = active?.modelId ?: state.selectedModel
        val matchesRoute = provider != LlmProvider.UNKNOWN && model.isNotBlank() &&
            metrics.rateProvider == provider && metrics.rateModelId == model
        fun rate(value: Long?, ttlMs: Long, supported: Boolean = true): Reading {
            val at = metrics.rateEvidenceAtEpochMs
            return when {
                !matchesRoute -> Reading(null, Status.UNKNOWN, at,
                    "No rate evidence for the displayed route")
                !supported -> Reading(null, Status.UNKNOWN, at,
                    "The response did not identify a supported rate window")
                at == null -> Reading(null, Status.UNKNOWN, null, "No provider snapshot received")
                at < 0 || at > nowMs -> Reading(null, Status.ERROR, at, "Snapshot clock is inconsistent")
                nowMs - at >= ttlMs -> Reading(null, Status.STALE, at,
                    "Snapshot expired; refresh requires a new provider response")
                value == null -> Reading(null, Status.UNAVAILABLE, at,
                    "This counter was not present in the provider response")
                value < 0 -> Reading(null, Status.ERROR, at, "Invalid provider counter")
                else -> Reading(value, Status.OBSERVED, at,
                    "Provider response headers; a dated rate snapshot, not account credit")
            }
        }
        val requestKind = metrics.verifiedRequestRateKind
        val requestTtl = if (requestKind == "RPD") DAY_EVIDENCE_TTL_MS else MINUTE_EVIDENCE_TTL_MS
        val checked = state.smartComboCheckedAtEpochMs
        val catalog = when {
            !state.smartComboEnabled -> Reading(null, Status.UNKNOWN, checked, "Free-only routing is off")
            state.smartComboUiPhase == FreeCatalogUiPhase.UNAVAILABLE ->
                Reading(null, Status.UNAVAILABLE, checked, "Catalog refresh failed; no current candidate count is asserted")
            state.smartComboUiPhase == FreeCatalogUiPhase.NO_ACCOUNTS ->
                Reading(null, Status.UNAVAILABLE, checked, "No configured provider account")
            checked == null -> Reading(null, Status.UNKNOWN, null, "No catalog check recorded")
            checked < 0 || checked > nowMs -> Reading(null, Status.ERROR, checked,
                "Catalog clock is inconsistent")
            nowMs - checked >= CATALOG_EVIDENCE_TTL_MS -> Reading(null, Status.STALE, checked,
                "Catalog evidence expired; no current candidate count is asserted")
            state.smartComboOffers < 0 -> Reading(null, Status.ERROR, checked, "Invalid candidate count")
            else -> Reading(state.smartComboOffers.toLong(), Status.OBSERVED, checked,
                "Zero-price catalog candidates; authentication, quota and admission still apply")
        }
        val phase = when {
            !state.isGenerating -> Phase.IDLE
            active == null -> Phase.PREPARING
            metrics.streamingEstimateActive && metrics.streamingOutputTokensEstimate > 0 -> Phase.STREAMING
            else -> Phase.DISPATCHED
        }
        return Snapshot(phase, active, state.lastRequest, state.provider, state.selectedModel,
            rate(metrics.verifiedRateRequestsRemaining, requestTtl, requestKind in setOf("RPM", "RPD")),
            rate(metrics.verifiedRateTokensRemaining, MINUTE_EVIDENCE_TTL_MS), requestKind,
            catalog, if (catalog.status == Status.OBSERVED)
                state.smartComboRouteLabels.filter { it.isNotBlank() }.distinct().take(8) else emptyList(),
            active?.startedAtEpochMs?.takeIf { it in 0L..nowMs }?.let { nowMs - it })
    }
}
