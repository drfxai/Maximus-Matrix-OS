package ai.drfx.maximus.matrixai.llm

/**
 * Process-local circuit breaker for verified free providers. A failed dispatched
 * attempt is never replayed automatically, but a later NEW user request may
 * safely choose a different route while the failing one cools down.
 *
 * Failures from user cancellation must not be counted as provider failures.
 * This is health telemetry, NOT evidence of account credit or live quota.
 */
internal class SmartComboRouteHealth(
    private val baseCooldownMs: Long = 15_000,
    private val maxCooldownMs: Long = 5 * 60_000
) {
    private data class Record(
        val failures: Int,
        val cooldownUntil: Long,
        val lastHealthyAt: Long?,
        val firstTokenSamplesMs: List<Long> = emptyList()
    )

    private val routes = linkedMapOf<String, Record>()

    init {
        require(baseCooldownMs > 0L && maxCooldownMs >= baseCooldownMs)
    }

    @Synchronized fun failed(routeId: String, nowMs: Long) {
        require(routeId.isNotBlank())
        val old = routes[routeId]
        val failures = (old?.failures ?: 0).coerceAtMost(19) + 1
        val shift = (failures - 1).coerceAtMost(10)
        // Clamp before multiplying: overflowing Long arithmetic must never clear a cooldown.
        val multiplier = 1L shl shift
        val backoff = if (baseCooldownMs > maxCooldownMs / multiplier) maxCooldownMs
            else baseCooldownMs * multiplier
        val until = if (nowMs > Long.MAX_VALUE - backoff) Long.MAX_VALUE else nowMs + backoff
        routes[routeId] = Record(failures, until, old?.lastHealthyAt,
            old?.firstTokenSamplesMs.orEmpty())
        trim()
    }

    @Synchronized fun succeeded(routeId: String, nowMs: Long) {
        require(routeId.isNotBlank())
        routes[routeId] = Record(0, 0L, nowMs,
            routes[routeId]?.firstTokenSamplesMs.orEmpty())
        trim()
    }

    @Synchronized fun decorate(
        offers: List<SmartComboFreeCatalog.Offer>, nowMs: Long
    ): List<SmartComboFreeCatalog.Offer> = offers.map { offer ->
        val state = routes[offer.route.id]
        if (state == null) offer else {
            val current = offer.route
            offer.copy(route = current.copy(
                healthy = current.healthy && state.cooldownUntil <= nowMs,
                p95TimeToFirstTokenMs = if (state.firstTokenSamplesMs.size >= 3) {
                    val sorted = state.firstTokenSamplesMs.sorted()
                    sorted[((sorted.size * 95 + 99) / 100 - 1).coerceIn(0, sorted.lastIndex)]
                } else current.p95TimeToFirstTokenMs,
                cooldownUntilEpochMs = maxOf(
                    current.cooldownUntilEpochMs ?: 0L,
                    state.cooldownUntil
                )
            ))
        }
    }

    /**
     * Records monotonic elapsed time from dispatch to the first NONEMPTY provider token.
     * At least three observations are needed before using local measurements to rank.
     * No message, user data, account balance or credentials are stored here.
     */
    @Synchronized fun observeFirstToken(routeId: String, durationMs: Long) {
        if (routeId.isBlank() || durationMs !in 1L..120_000L) return
        val previous = routes[routeId] ?: Record(0, 0L, null)
        routes[routeId] = previous.copy(
            firstTokenSamplesMs = (previous.firstTokenSamplesMs + durationMs).takeLast(20)
        )
        trim()
    }

    @Synchronized fun remainingCooldownMs(routeId: String, nowMs: Long): Long =
        (routes[routeId]?.cooldownUntil ?: nowMs).let { until ->
            if (until <= nowMs) 0L else until - nowMs
        }

    private fun trim() {
        // Never retain an unbounded number of arbitrary upstream route identifiers.
        while (routes.size > 128) routes.remove(routes.keys.first())
    }
}
