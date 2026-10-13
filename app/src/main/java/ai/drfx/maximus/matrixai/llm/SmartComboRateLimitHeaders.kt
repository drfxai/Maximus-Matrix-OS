package ai.drfx.maximus.matrixai.llm

import java.net.URI

/**
 * Only documented, provider-specific HTTP rate-limit headers may become verified quota
 * evidence. Never infer account credit or lifetime token balances from these headers.
 */
internal object SmartComboRateLimitHeaders {
    fun parse(
        provider: LlmProvider,
        endpoint: String,
        header: (String) -> String?,
        observedAt: Long = System.currentTimeMillis()
    ): List<SmartComboPlanner.QuotaWindow> {
        val host = runCatching { URI(endpoint).host?.lowercase() }.getOrNull() ?: return emptyList()
        val groq = host == "api.groq.com" && provider == LlmProvider.OPENAI_COMPATIBLE
        val openAi = host == "api.openai.com" && provider == LlmProvider.OPENAI
        if (!groq && !openAi) return emptyList()
        val results = mutableListOf<SmartComboPlanner.QuotaWindow>()
        val requestKind = if (groq) SmartComboPlanner.LimitKind.REQUESTS_PER_DAY else
            SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE
        fun collect(kind: SmartComboPlanner.LimitKind, limitKey: String, remainingKey: String) {
            val limit = header(limitKey)?.trim()?.toLongOrNull()
            val left = header(remainingKey)?.trim()?.toLongOrNull()
            if (limit == null || limit <= 0L || left == null || left < 0L || left > limit) return
            results += SmartComboPlanner.QuotaWindow(
                kind = kind,
                remaining = left,
                limit = limit,
                observedAtEpochMs = observedAt,
                confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
            )
        }
        collect(requestKind, "x-ratelimit-limit-requests", "x-ratelimit-remaining-requests")
        collect(SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_MINUTE,
            "x-ratelimit-limit-tokens", "x-ratelimit-remaining-tokens")
        return results
    }
}
