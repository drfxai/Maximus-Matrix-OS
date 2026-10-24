package ai.drfx.maximus.matrixai.llm

import kotlin.math.max

/**
 * Deterministic, side-effect-free routing policy. This does not contact providers, reserve
 * distributed quota, read credentials, execute tools, or claim a live balance.
 * Inputs must come from authenticated provider adapters and a trusted execution ledger.
 */
object SmartComboPlanner {
    enum class Confidence { PROVIDER_VERIFIED, LEDGER_RECONCILED, ESTIMATED, UNKNOWN }
    enum class LimitKind { REQUESTS_PER_MINUTE, REQUESTS_PER_DAY, INPUT_TOKENS_PER_MINUTE, TOTAL_TOKENS_PER_MINUTE, TOTAL_TOKENS_PER_DAY, TOKEN_BALANCE, DOLLAR_BALANCE_MICROS }

    data class QuotaWindow(
        val kind: LimitKind,
        val remaining: Long?,
        val limit: Long? = null,
        val resetAtEpochMs: Long? = null,
        val observedAtEpochMs: Long? = null,
        val confidence: Confidence = Confidence.UNKNOWN
    ) {
        init {
            require(remaining == null || remaining >= 0L)
            require(limit == null || limit >= 0L)
        }
    }

    data class Price(
        val inputUsdPerMillion: Double,
        val outputUsdPerMillion: Double,
        val verified: Boolean
    ) {
        init {
            require(inputUsdPerMillion.isFinite() && inputUsdPerMillion >= 0.0)
            require(outputUsdPerMillion.isFinite() && outputUsdPerMillion >= 0.0)
        }

        val isFree: Boolean get() = verified && inputUsdPerMillion == 0.0 && outputUsdPerMillion == 0.0
        fun estimateUsd(inputTokens: Int, outputTokens: Int): Double =
            (inputTokens.toDouble() * inputUsdPerMillion + outputTokens.toDouble() * outputUsdPerMillion) / 1_000_000.0
    }

    data class Route(
        val id: String,
        val provider: LlmProvider,
        val modelId: String,
        val verified: Boolean,
        val healthy: Boolean,
        val capabilities: Set<ModelCapability>,
        val contextWindowTokens: Int?,
        val price: Price?,
        val p95TimeToFirstTokenMs: Long?,
        val quality: Int,
        val quota: List<QuotaWindow> = emptyList(),
        val cooldownUntilEpochMs: Long? = null,
        val approvedForUserData: Boolean = true
    ) {
        init {
            require(id.isNotBlank() && modelId.isNotBlank())
            require(quality in 0..100)
            require(contextWindowTokens == null || contextWindowTokens > 0)
            require(p95TimeToFirstTokenMs == null || p95TimeToFirstTokenMs >= 0L)
        }
    }

    data class Task(
        val requiredCapabilities: Set<ModelCapability>,
        val estimatedInputTokens: Int,
        val reservedOutputTokens: Int,
        val minimumQuality: Int = 60,
        val paidFallbackApproved: Boolean = false,
        val maxRequestCostUsd: Double? = null,
        val nowEpochMs: Long,
        val interactive: Boolean = true,
        /** Ordering hint only; never makes an ineligible route eligible. */
        val intent: SmartComboModelScorer.Intent = SmartComboModelScorer.Intent.GENERAL
    ) {
        init {
            require(estimatedInputTokens >= 0 && reservedOutputTokens > 0)
            require(minimumQuality in 0..100)
            require(maxRequestCostUsd == null || (maxRequestCostUsd.isFinite() && maxRequestCostUsd >= 0.0))
        }
    }

    data class Selection(
        val primary: Route?,
        val standby: Route?,
        val eligible: List<Route>,
        val rejectedReasons: Map<String, String>,
        val requiresLiveQuotaConfirmation: Boolean
    )

    fun choose(task: Task, routes: List<Route>): Selection {
        val rejected = linkedMapOf<String, String>()
        // A route ID is an execution identity. Never offer a duplicate as its own fallback,
        // or select conflicting records that disagree about privacy, price or quota.
        val uniqueRoutes = routes.groupBy { it.id }.mapNotNull { (id, records) ->
            if (records.any { it != records.first() }) {
                rejected[id] = "Conflicting route identity"
                null
            } else records.first()
        }
        val candidates = uniqueRoutes.mapNotNull { route ->
            val rejectedFor = rejectionReason(task, route)
            if (rejectedFor != null) {
                rejected[route.id] = rejectedFor
                null
            } else route
        }
        val sorted = candidates.sortedWith(
            compareByDescending<Route> { it.price?.isFree == true }
                .thenByDescending { score(it, task) }
                .thenBy { it.id }
        )
        return Selection(
            primary = sorted.firstOrNull(),
            standby = sorted.drop(1).firstOrNull(),
            eligible = sorted,
            rejectedReasons = rejected,
            requiresLiveQuotaConfirmation = sorted.firstOrNull()?.quota
                ?.let { windows -> windows.isEmpty() || windows.any { !isFreshConfirmed(it, task.nowEpochMs) } }
                ?: false
        )
    }

    private fun rejectionReason(task: Task, route: Route): String? {
        if (!route.verified) return "Unverified model catalog"
        if (!route.healthy) return "Route unhealthy"
        if (!route.approvedForUserData) return "Privacy policy prohibits this route"
        if (route.cooldownUntilEpochMs?.let { it > task.nowEpochMs } == true) return "Rate-limit cooldown"
        if (!route.capabilities.containsAll(task.requiredCapabilities)) return "Missing model capability"
        if (route.quality < task.minimumQuality) return "Quality below task threshold"
        val requiredContext = task.estimatedInputTokens.toLong() + task.reservedOutputTokens
        if (route.contextWindowTokens == null || requiredContext > route.contextWindowTokens) return "Context window insufficient or unverified"
        val price = route.price ?: return "Price not verified"
        if (!price.verified) return "Price not verified"
        val cost = price.estimateUsd(task.estimatedInputTokens, task.reservedOutputTokens)
        if (!price.isFree && (!task.paidFallbackApproved || task.maxRequestCostUsd == null || cost > task.maxRequestCostUsd)) {
            return "Paid use requires explicit per-request budget"
        }
        for (window in route.quota) {
            // Unknown quota stays unknown; never report it as an exact positive balance.
            val balance = window.remaining ?: continue
            if (!isFreshConfirmed(window, task.nowEpochMs)) continue
            val needed = when (window.kind) {
                LimitKind.REQUESTS_PER_MINUTE, LimitKind.REQUESTS_PER_DAY -> 1L
                LimitKind.INPUT_TOKENS_PER_MINUTE -> task.estimatedInputTokens.toLong()
                LimitKind.TOTAL_TOKENS_PER_MINUTE, LimitKind.TOTAL_TOKENS_PER_DAY, LimitKind.TOKEN_BALANCE ->
                    requiredContext
                LimitKind.DOLLAR_BALANCE_MICROS -> (cost * 1_000_000.0).toLong().coerceAtLeast(if (cost > 0.0) 1L else 0L)
            }
            // A verified zero-price route consumes no dollar credit. Free request/token
            // windows are still enforced independently below and by the admission ledger.
            if (window.kind == LimitKind.DOLLAR_BALANCE_MICROS && needed == 0L) continue
            // Reserve at least the current task's demand plus a 15% headroom of the limit
            // where published; do not allocate the provider's quota here.
            val guard = max(needed, window.limit?.let { (it.toDouble() * 0.15).toLong() } ?: 0L)
            if (balance < needed || balance - needed < guard) return "Quota nearing exhaustion: ${window.kind}"
        }
        return null
    }

    private fun isFreshConfirmed(window: QuotaWindow, nowEpochMs: Long): Boolean {
        if (window.remaining == null) return false
        if (window.confidence != Confidence.PROVIDER_VERIFIED &&
            window.confidence != Confidence.LEDGER_RECONCILED) return false
        val observed = window.observedAtEpochMs ?: return false
        if (observed > nowEpochMs || (window.resetAtEpochMs != null && nowEpochMs >= window.resetAtEpochMs)) return false
        val maxAgeMs = when (window.kind) {
            LimitKind.REQUESTS_PER_MINUTE, LimitKind.INPUT_TOKENS_PER_MINUTE, LimitKind.TOTAL_TOKENS_PER_MINUTE -> 60_000L
            else -> 300_000L
        }
        // Signed timestamp subtraction can overflow for corrupt or extreme evidence.
        // Such evidence must never be treated as a fresh provider quota snapshot.
        val ageMs = nowEpochMs - observed
        return ageMs >= 0L && ageMs <= maxAgeMs
    }

    private fun score(route: Route, task: Task): Double {
        val latencyPenalty = if (task.interactive) ((route.p95TimeToFirstTokenMs ?: 5000L).coerceAtMost(20000L) / 80.0) else 0.0
        val unknownQuotaPenalty = if (route.quota.isEmpty()) 8.0 else
            route.quota.count { it.remaining == null || it.confidence == Confidence.UNKNOWN || it.confidence == Confidence.ESTIMATED }.toDouble() * 4.0
        val fit = SmartComboModelScorer.intentFit(route.modelId, task.intent, route.contextWindowTokens)
        return route.quality.toDouble() * 3 + fit - latencyPenalty - unknownQuotaPenalty
    }
}
