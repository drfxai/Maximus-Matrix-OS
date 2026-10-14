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
        val lastHealthyAt: Long?
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
        val backoff = (baseCooldownMs * (1L shl shift)).coerceAtMost(maxCooldownMs)
        val until = if (nowMs > Long.MAX_VALUE - backoff) Long.MAX_VALUE else nowMs + backoff
        routes[routeId] = Record(failures, until, old?.lastHealthyAt)
        trim()
    }

    @Synchronized fun succeeded(routeId: String, nowMs: Long) {
        require(routeId.isNotBlank())
        routes[routeId] = Record(0, 0L, nowMs)
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
                cooldownUntilEpochMs = maxOf(
                    current.cooldownUntilEpochMs ?: 0L,
                    state.cooldownUntil
                )
            ))
        }
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
