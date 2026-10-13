package ai.drfx.maximus.matrixai.llm

import kotlin.math.ceil

/**
 * Low-latency select-and-reserve boundary. Pure routing decisions are followed by an atomic
 * process-local admission. No network calls, implicit paid fallback, or duplicate dispatch.
 *
 * Scope IDs must represent the provider's real shared quota bucket, not individual aliases.
 * A backend transaction is required before enabling cross-device/cloud concurrency.
 */
internal class SmartComboAdmissionController(private val ledger: SmartComboQuotaLedger) {
    data class Prepared(
        val route: SmartComboPlanner.Route?,
        val standby: SmartComboPlanner.Route?,
        val reasons: Map<String, String>,
        val needsQuotaRefresh: Boolean
    )

    fun prepare(
        requestId: String,
        task: SmartComboPlanner.Task,
        routes: List<SmartComboPlanner.Route>,
        quotaScopes: Map<String, String>
    ): Prepared {
        require(requestId.isNotBlank())
        // An already dispatched or settled request must never silently run on a second route.
        if (ledger.entry(requestId) != null) return Prepared(
            null, null, mapOf(requestId to "Duplicate request ID"), false
        )
        val choice = SmartComboPlanner.choose(task, routes)
        val rejected = choice.rejectedReasons.toMutableMap()
        for (route in choice.eligible) {
            val scope = quotaScopes[route.id]?.takeIf { it.isNotBlank() }
            if (scope == null) {
                rejected[route.id] = "No authenticated quota scope"
                continue
            }
            val verifiedPrice = requireNotNull(route.price)
            val rawMicros = ceil(verifiedPrice.estimateUsd(
                task.estimatedInputTokens, task.reservedOutputTokens
            ) * 1_000_000.0)
            if (!rawMicros.isFinite() || rawMicros > Long.MAX_VALUE.toDouble()) {
                rejected[route.id] = "Unrepresentable cost"
                continue
            }
            val plan = SmartComboQuotaLedger.Plan(
                requestId = requestId,
                scope = scope,
                routeId = route.id,
                input = task.estimatedInputTokens.toLong(),
                reservedOutput = task.reservedOutputTokens.toLong(),
                reservedMicros = rawMicros.toLong()
            )
            val reservation = ledger.reserve(plan)
            if (!reservation.accepted) {
                rejected[route.id] = reservation.reason
                // Duplicate request ID is not a retryable route failure.
                if (reservation.reason == "Duplicate request ID") break
                continue
            }
            // A useful standby should not share the selected route's quota bucket.
            val standby = choice.eligible.firstOrNull {
                it.id != route.id && quotaScopes[it.id]?.let { candidateScope ->
                    candidateScope.isNotBlank() && candidateScope != scope
                } == true
            }
            return Prepared(route, standby, rejected, reservation.needsQuotaRefresh)
        }
        return Prepared(null, null, rejected, choice.requiresLiveQuotaConfirmation)
    }
}
