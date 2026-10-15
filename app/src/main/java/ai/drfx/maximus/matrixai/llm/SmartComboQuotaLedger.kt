package ai.drfx.maximus.matrixai.llm

import kotlin.math.ceil
import kotlin.math.max

/**
 * Process-local atomic quota reservation and reconciled usage. Cloud/multi-device deployments
 * MUST replace this with a transactional backend. No credentials or assumed account balances.
 */
internal class SmartComboQuotaLedger(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val headroom: Double = 0.15,
    private val maxUnknownInFlightPerScope: Int = 2
) {
    init {
        require(headroom.isFinite() && headroom in 0.0..0.5)
        require(maxUnknownInFlightPerScope > 0)
    }

    enum class State { RESERVED, DISPATCHED, UNCERTAIN, RECONCILED, FINISHED_UNVERIFIED, RELEASED }
    enum class Source { PROVIDER_SNAPSHOT, LOCALLY_ADJUSTED, UNKNOWN }

    /** Scope is a nonsecret identifier for a shared provider quota bucket. */
    data class Plan(
        val requestId: String, val scope: String, val routeId: String,
        val input: Long, val reservedOutput: Long, val reservedMicros: Long
    ) {
        init {
            require(requestId.isNotBlank() && scope.isNotBlank() && routeId.isNotBlank())
            require(input >= 0L && reservedOutput > 0L && reservedMicros >= 0L)
            require(input <= Long.MAX_VALUE - reservedOutput)
        }
    }

    data class Usage(
        val input: Long, val output: Long, val total: Long, val billedMicros: Long? = null
    ) {
        init {
            require(input >= 0 && output >= 0 && total >= 0)
            require(input <= Long.MAX_VALUE - output && total >= input + output)
            require(billedMicros == null || billedMicros >= 0)
        }
    }

    data class Entry(
        val plan: Plan, val createdAt: Long, val state: State, val confirmed: Usage? = null,
        val settledAt: Long? = null
    )
    data class Admission(val accepted: Boolean, val reason: String, val needsQuotaRefresh: Boolean)
    data class Capacity(
        val kind: SmartComboPlanner.LimitKind,
        val source: Source,
        val providerRemaining: Long?,
        val safeAvailable: Long?,
        val locallyReservedOrSpent: Long,
        val resetAt: Long?
    )
    private data class Evidence(val value: SmartComboPlanner.QuotaWindow, val receivedAt: Long)
    private val entries = linkedMapOf<String, Entry>()
    private val snapshots = linkedMapOf<String, MutableMap<SmartComboPlanner.LimitKind, Evidence>>()

    /** Inputs must be normalized and authenticated by a provider-specific quota adapter. */
    @Synchronized fun observe(scope: String, windows: List<SmartComboPlanner.QuotaWindow>) {
        require(scope.isNotBlank())
        val received = now()
        val byKind = snapshots.getOrPut(scope) { linkedMapOf() }
        for (window in windows) {
            val observed = window.observedAtEpochMs ?: continue
            if (observed > received || window.remaining == null ||
                window.confidence != SmartComboPlanner.Confidence.PROVIDER_VERIFIED) continue
            val previous = byKind[window.kind]
            val previousAt = previous?.value?.observedAtEpochMs ?: Long.MIN_VALUE
            if (observed > previousAt ||
                (observed == previousAt && received > (previous?.receivedAt ?: Long.MIN_VALUE))) {
                byKind[window.kind] = Evidence(window, received)
            }
        }
    }

    /** Duplicate request IDs are rejected so retries cannot cause duplicate dispatch. */
    @Synchronized fun reserve(plan: Plan): Admission {
        if (entries.containsKey(plan.requestId)) return Admission(false, "Duplicate request ID", false)
        val at = now()
        var refresh = snapshots[plan.scope].isNullOrEmpty()
        for (capacity in capacities(plan.scope, at)) {
            val available = capacity.safeAvailable
            if (available == null) { refresh = true; continue }
            val quota = snapshots[plan.scope]?.get(capacity.kind)?.value ?: continue
            val needed = demand(plan, capacity.kind)
            // A genuinely free request requires no paid credit; do not confuse zero credit with zero free quota.
            if (capacity.kind == SmartComboPlanner.LimitKind.DOLLAR_BALANCE_MICROS && needed == 0L) continue
            val buffer = max(needed, quota.limit?.let { ceil(it.toDouble() * headroom).toLong() } ?: 0L)
            if (available < needed || available - needed < buffer) {
                return Admission(false, "Insufficient " + capacity.kind + " headroom", refresh)
            }
        }
        if (refresh) {
            val unknownInFlight = entries.values.count { entry ->
                entry.plan.scope == plan.scope && entry.state in setOf(State.RESERVED, State.DISPATCHED, State.UNCERTAIN)
            }
            if (unknownInFlight >= maxUnknownInFlightPerScope) {
                return Admission(false, "Unknown quota concurrency limit", true)
            }
        }
        entries[plan.requestId] = Entry(plan, at, State.RESERVED)
        return Admission(true, "Reserved locally", refresh)
    }

    @Synchronized fun dispatch(requestId: String): Boolean {
        val current = entries[requestId] ?: return false
        if (current.state != State.RESERVED) return false
        entries[requestId] = current.copy(state = State.DISPATCHED)
        return true
    }

    /** Releasing dispatched/uncertain usage without provider evidence is forbidden. */
    @Synchronized fun releaseUndispatched(requestId: String): Boolean {
        val current = entries[requestId] ?: return false
        if (current.state != State.RESERVED) return false
        entries[requestId] = current.copy(state = State.RELEASED)
        return true
    }

    @Synchronized fun markUncertain(requestId: String): Boolean {
        val current = entries[requestId] ?: return false
        if (current.state != State.DISPATCHED && current.state != State.UNCERTAIN) return false
        entries[requestId] = current.copy(state = State.UNCERTAIN)
        return true
    }

    /**
     * Completed text stream without provider usage: keep conservative token reservation in
     * known windows, but do not permanently count this as an in-flight request. Provider
     * totals remain unknown; do not label local heuristic counts as verified.
     */
    @Synchronized fun completedWithoutUsage(requestId: String): Boolean {
        val current = entries[requestId] ?: return false
        if (current.state != State.DISPATCHED) return false
        entries[requestId] = current.copy(state = State.FINISHED_UNVERIFIED, settledAt = now())
        return true
    }

    /** Accept only final provider/billing-confirmed usage, never a tokenization heuristic. */
    @Synchronized fun reconcile(requestId: String, usage: Usage): Boolean {
        val current = entries[requestId] ?: return false
        if (current.state == State.RECONCILED) return current.confirmed == usage
        if (current.state != State.DISPATCHED && current.state != State.UNCERTAIN) return false
        entries[requestId] = current.copy(state = State.RECONCILED, confirmed = usage, settledAt = now())
        return true
    }

    @Synchronized fun entry(requestId: String): Entry? = entries[requestId]

    @Synchronized fun capacities(scope: String, at: Long = now()): List<Capacity> =
        snapshots[scope].orEmpty().values.map { evidence ->
            val w = evidence.value
            if (!fresh(w, at)) {
                Capacity(w.kind, Source.UNKNOWN, null, null, 0L, w.resetAtEpochMs)
            } else {
                val seen = requireNotNull(w.observedAtEpochMs)
                val usageSince = entries.values.asSequence()
                    .filter { it.plan.scope == scope && notYetCoveredBySnapshot(it, seen) }
                    .fold(0L) { sum, entry -> saturatingAdd(sum, accounted(entry, w.kind)) }
                val advertised = requireNotNull(w.remaining)
                Capacity(
                    w.kind,
                    if (usageSince > 0L) Source.LOCALLY_ADJUSTED else Source.PROVIDER_SNAPSHOT,
                    advertised,
                    advertised - usageSince.coerceAtMost(advertised),
                    usageSince,
                    w.resetAtEpochMs
                )
            }
        }

    /**
     * An account snapshot cannot prove that an unfinished local request is included.
     * In particular an older RESERVED request has not even reached the provider yet.
     * Hold unresolved demand conservatively, then allow a strictly newer snapshot to
     * supersede settled usage. This can underestimate availability while work is in
     * flight; it must never advertise the reservation as newly available capacity.
     */
    private fun notYetCoveredBySnapshot(entry: Entry, observedAt: Long): Boolean =
        when (entry.state) {
            State.RELEASED -> false
            State.RESERVED, State.DISPATCHED, State.UNCERTAIN -> true
            State.RECONCILED, State.FINISHED_UNVERIFIED ->
                (entry.settledAt ?: entry.createdAt) >= observedAt
        }

    private fun fresh(w: SmartComboPlanner.QuotaWindow, at: Long): Boolean {
        val observed = w.observedAtEpochMs ?: return false
        if (w.confidence != SmartComboPlanner.Confidence.PROVIDER_VERIFIED ||
            w.remaining == null || observed > at ||
            (w.resetAtEpochMs != null && at >= w.resetAtEpochMs)) return false
        val ttl = when (w.kind) {
            SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE,
            SmartComboPlanner.LimitKind.INPUT_TOKENS_PER_MINUTE,
            SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_MINUTE -> 60_000L
            else -> 300_000L
        }
        return at - observed <= ttl
    }

    private fun accounted(entry: Entry, kind: SmartComboPlanner.LimitKind): Long {
        val confirmed = entry.confirmed.takeIf { entry.state == State.RECONCILED }
        return when (kind) {
            SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE,
            SmartComboPlanner.LimitKind.REQUESTS_PER_DAY -> 1L
            SmartComboPlanner.LimitKind.INPUT_TOKENS_PER_MINUTE -> confirmed?.input ?: entry.plan.input
            SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_MINUTE,
            SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_DAY,
            SmartComboPlanner.LimitKind.TOKEN_BALANCE ->
                confirmed?.total ?: entry.plan.input + entry.plan.reservedOutput
            SmartComboPlanner.LimitKind.DOLLAR_BALANCE_MICROS ->
                confirmed?.billedMicros ?: entry.plan.reservedMicros
        }
    }

    private fun demand(plan: Plan, kind: SmartComboPlanner.LimitKind): Long = when (kind) {
        SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE,
        SmartComboPlanner.LimitKind.REQUESTS_PER_DAY -> 1L
        SmartComboPlanner.LimitKind.INPUT_TOKENS_PER_MINUTE -> plan.input
        SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_MINUTE,
        SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_DAY,
        SmartComboPlanner.LimitKind.TOKEN_BALANCE -> plan.input + plan.reservedOutput
        SmartComboPlanner.LimitKind.DOLLAR_BALANCE_MICROS -> plan.reservedMicros
    }

    private fun saturatingAdd(a: Long, b: Long): Long =
        if (a >= Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}
