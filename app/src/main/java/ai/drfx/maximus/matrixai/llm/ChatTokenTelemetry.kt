package ai.drfx.maximus.matrixai.llm

/** Finish a request without discarding newer provider-rate evidence or budget edits. */
internal object ChatTokenTelemetry {
    fun dispatched(current: TokenMetrics, capacity: Int, promptEstimate: Int): TokenMetrics {
        val boundedCapacity = capacity.coerceAtLeast(0)
        val context = promptEstimate.coerceIn(0, boundedCapacity)
        return current.copy(contextCapacity = boundedCapacity,
            remainingContextTokens = boundedCapacity - context,
            contextUsagePercent = if (boundedCapacity > 0) context.toFloat() / boundedCapacity else 0f)
    }

    fun completed(
        current: TokenMetrics,
        usage: ChatUsage,
        lifetimeTokens: Long,
        contextCapacity: Int,
        contextTokensEstimate: Int,
        monthlyTokenBudget: Long,
        // The bounded conversation estimate excludes the captured system/memory budget.
        contextOverheadEstimate: Int = 0
    ): TokenMetrics {
        val capacity = contextCapacity.coerceAtLeast(0)
        val context = (contextTokensEstimate.coerceAtLeast(0).toLong() +
            contextOverheadEstimate.coerceAtLeast(0).toLong()).coerceAtMost(capacity.toLong()).toInt()
        return current.copy(
            consumedTurnInputTokens = usage.inputTokens,
            consumedTurnOutputTokens = usage.outputTokens,
            consumedTurnTotalTokens = usage.totalTokens,
            consumedSessionTokens = current.consumedSessionTokens + usage.totalTokens,
            consumedLifetimeTokens = lifetimeTokens,
            contextCapacity = capacity,
            remainingContextTokens = capacity - context,
            monthlyTokenBudget = monthlyTokenBudget,
            remainingBudgetTokens = if (monthlyTokenBudget > 0)
                (monthlyTokenBudget - lifetimeTokens).coerceAtLeast(0L) else null,
            contextUsagePercent = if (capacity > 0) context.toFloat() / capacity else 0f,
            streamingEstimateActive = false,
            streamingOutputTokensEstimate = 0,
            turnUsageEstimated = usage.estimated
        )
    }
}
