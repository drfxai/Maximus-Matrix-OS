package ai.drfx.maximus.matrixai.llm

/**
 * Single-dispatch verified-free routing. A network send is never replayed automatically:
 * retries after a socket timeout can incur duplicate charges or external side-effects.
 * Multi-device hard quota enforcement requires a trusted transactional backend.
 */
internal class SmartComboFreeExecutor(
    private val ledger: SmartComboQuotaLedger = SmartComboQuotaLedger()
) {
    data class Prepared(
        val config: ApiConnectionConfig,
        val offer: SmartComboFreeCatalog.Offer,
        val requestId: String,
        val standby: String?
    )

    /** Select and reserve only a provider-advertised zero-price route. */
    fun prepare(
        requestId: String,
        estimatedInput: Int,
        reservedOutput: Int,
        requiredCapabilities: Set<ModelCapability>,
        offers: List<SmartComboFreeCatalog.Offer>,
        resolveCredential: (LlmProvider, String) -> String,
        agentId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Prepared? {
        val map = offers.associateBy { it.route.id }
        val task = SmartComboPlanner.Task(
            requiredCapabilities = requiredCapabilities,
            estimatedInputTokens = estimatedInput,
            reservedOutputTokens = reservedOutput,
            minimumQuality = 60,
            paidFallbackApproved = false,
            maxRequestCostUsd = null,
            nowEpochMs = nowMs
        )
        val filtered = offers.filter { it.route.price?.isFree == true &&
            nowMs >= it.checkedAtEpochMs && nowMs - it.checkedAtEpochMs <= 10 * 60_000L }
        val admission = SmartComboAdmissionController(ledger).prepare(
            requestId, task, filtered.map { it.route },
            filtered.associate { it.route.id to it.quotaScope }
        )
        val route = admission.route ?: return null
        val offer = map[route.id] ?: return null
        val key = runCatching { resolveCredential(route.provider, offer.endpoint) }.getOrDefault("")
        if (key.isBlank()) {
            ledger.releaseUndispatched(requestId)
            return null
        }
        return Prepared(
            ApiConnectionConfig(offer.endpoint, key, route.provider, route.modelId, agentId),
            offer, requestId, admission.standby?.id
        )
    }

    fun observeProviderQuota(
        prepared: Prepared, windows: List<SmartComboPlanner.QuotaWindow>
    ) {
        if (windows.isNotEmpty()) ledger.observe(prepared.offer.quotaScope, windows)
    }

    fun dispatched(prepared: Prepared): Boolean = ledger.dispatch(prepared.requestId)

    /** Provider-confirmed usage only. Estimated usage keeps the request uncertain. */
    fun completed(prepared: Prepared, usage: ChatUsage) {
        if (!usage.estimated) {
            ledger.reconcile(prepared.requestId, SmartComboQuotaLedger.Usage(
                usage.inputTokens.toLong(), usage.outputTokens.toLong(),\n                maxOf(usage.totalTokens.toLong(), usage.inputTokens.toLong() + usage.outputTokens.toLong())
            ))
        } else {
            ledger.markUncertain(prepared.requestId)
        }
    }

    /** Release only if dispatch never happened; otherwise preserve uncertain usage. */
    fun failed(prepared: Prepared) {
        if (!ledger.releaseUndispatched(prepared.requestId)) {
            ledger.markUncertain(prepared.requestId)
        }
    }

}
