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
        // Ambiguous route IDs could bind a credential to a different endpoint.
        // Treat all duplicates as untrusted instead of allowing last-write wins.
        val ambiguousIds = offers.groupingBy { it.route.id }.eachCount()
            .filterValues { it > 1 }.keys
        val task = SmartComboPlanner.Task(
            requiredCapabilities = requiredCapabilities,
            estimatedInputTokens = estimatedInput,
            reservedOutputTokens = reservedOutput,
            minimumQuality = 60,
            paidFallbackApproved = false,
            maxRequestCostUsd = null,
            nowEpochMs = nowMs
        )
        // Never reserve a route that cannot actually authenticate. A failed lookup may
        // otherwise consume the request ID and prevent a safe, pre-dispatch alternate.
        // A local credential lookup does not send a model request or spend provider quota.
        val usableCredentials = linkedMapOf<String, String>()
        val filtered = offers.filter { offer ->
            if (offer.route.id in ambiguousIds || offer.route.price?.isFree != true ||
                nowMs < offer.checkedAtEpochMs ||
                nowMs - offer.checkedAtEpochMs > 10 * 60_000L) return@filter false
            val credential = runCatching {
                resolveCredential(offer.route.provider, offer.endpoint)
            }.getOrDefault("")
            if (credential.isBlank()) return@filter false
            usableCredentials[offer.route.id] = credential
            true
        }
        val map = filtered.associateBy { it.route.id }
        val admission = SmartComboAdmissionController(ledger).prepare(
            requestId, task, filtered.map { it.route },
            filtered.associate { it.route.id to it.quotaScope }
        )
        val route = admission.route ?: return null
        val offer = map[route.id] ?: return null
        val key = usableCredentials[route.id] ?: return null
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

    /** Read-only state for safe post-dispatch provider health accounting. */
    fun requestState(requestId: String): SmartComboQuotaLedger.State? =
        ledger.entry(requestId)?.state

    /** Provider-confirmed usage only. Estimated usage keeps the request uncertain. */
    fun completed(prepared: Prepared, usage: ChatUsage) {
        if (!usage.estimated) {
            ledger.reconcile(prepared.requestId, SmartComboQuotaLedger.Usage(
                usage.inputTokens.toLong(), usage.outputTokens.toLong(),
                maxOf(usage.totalTokens.toLong(), usage.inputTokens.toLong() + usage.outputTokens.toLong())
            ))
        } else {
            ledger.completedWithoutUsage(prepared.requestId)
        }
    }

    /** Release only if dispatch never happened; otherwise preserve uncertain usage. */
    fun failed(prepared: Prepared) {
        if (!ledger.releaseUndispatched(prepared.requestId)) {
            ledger.markUncertain(prepared.requestId)
        }
    }

}
