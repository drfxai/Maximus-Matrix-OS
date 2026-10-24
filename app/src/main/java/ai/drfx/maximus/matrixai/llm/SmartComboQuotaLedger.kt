package ai.drfx.maximus.matrixai.llm

import kotlin.math.ceil
import kotlin.math.max

/**
 * Device-local atomic quota reservation and reconciled usage. Cloud/multi-device deployments
 * MUST replace this with a transactional backend. No credentials or assumed account balances.
 */
internal class SmartComboQuotaLedger(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val headroom: Double = 0.15,
    private val maxUnknownInFlightPerScope: Int = 2,
    private val journal: SmartComboRequestJournal? = null
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
        val input: Long, val reservedOutput: Long, val reservedMicros: Long,
        val provider: LlmProvider = LlmProvider.UNKNOWN,
        val checkpoint: SmartComboCheckpointReference? = null
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

    data class Durability(
        val status: String, val retainedRequests: Int, val uncertainRequests: Int,
        val checkpointReferences: Int, val confirmedTokensByProvider: Map<LlmProvider, Long>,
        val activeJournalRequests: Int, val archiveSegments: Int
    )
    private var loaded = journal == null
    private var journalRevision = 0L
    private var journalFailed = false
    private var archiveReferences = emptyList<SmartComboRequestJournal.Archive>()
    private var archived = SmartComboRequestJournal.ArchivedMetadata(emptySet(), 0, 0, emptyMap())

    @Volatile private var cachedDurability = Durability(
        if (journal == null) "PROCESS_LOCAL" else "NOT_OPENED", 0, 0, 0, emptyMap(), 0, 0)

    /** Never acquires the journal lock, which may be held by a background fsync/recovery. */
    fun durability(): Durability = cachedDurability

    /** Called only while holding the ledger mutation lock. */
    private fun computeDurability(): Durability {
        val totals = archived.confirmedTokensByProvider.toMutableMap()
        entries.values.filter { it.state == State.RECONCILED }.forEach { e ->
            totals[e.plan.provider] = saturatingAdd(totals[e.plan.provider] ?: 0L, requireNotNull(e.confirmed).total)
        }
        return Durability(when { journal == null -> "PROCESS_LOCAL"; journalFailed -> "ERROR";
            !loaded -> "NOT_OPENED"; entries.size >= SmartComboRequestJournal.MAX_ENTRIES -> "FULL";
            else -> "DURABLE" }, entries.size + archived.count,
            entries.values.count { it.state == State.UNCERTAIN },
            entries.values.count { it.plan.checkpoint != null } + archived.checkpointReferences,
            totals, entries.size, archiveReferences.size)
    }

    /** Explicit archival never removes idempotency history or unsettled quota reservations. */
    @Synchronized fun archiveSettled(): Int {
        check(ensureLoaded() && journal != null) { "Request journal unavailable." }
        check(archiveReferences.size < SmartComboRequestJournal.MAX_ARCHIVES) { "Archive capacity reached; evidence was preserved." }
        val cutoff = now() - 300_000L
        val eligible = entries.values.filter {
            it.state in setOf(State.RELEASED, State.RECONCILED, State.FINISHED_UNVERIFIED) &&
                (it.settledAt ?: it.createdAt) < cutoff
        }
        if (eligible.isEmpty()) return 0
        try {
            val reference = journal.archive(eligible)
            // Archive bytes exist first. A crash before the following commit retains active evidence.
            archiveReferences = archiveReferences + reference
            val metadata = journal.archivedMetadata(archiveReferences)
            eligible.forEach { entries.remove(it.plan.requestId) }
            // Publish active+archived evidence together; lock-free readers must never
            // observe a temporary drop in confirmed totals during archival.
            archived = metadata
            check(persist()) { "Could not commit archival; automatic routing paused." }
            return eligible.size
        } catch (failure: Exception) { journalFailed = true; throw failure }
        finally { cachedDurability = computeDurability() }
    }

    @Synchronized fun initialize(): Durability { ensureLoaded(); return durability() }

    private fun scopeKey(scope: String): String =
        if (journal == null) scope else SmartComboRequestJournal.identity(scope)

    private fun ensureLoaded(): Boolean {
        if (journalFailed) return false
        if (loaded) return true
        try {
            val saved = requireNotNull(journal).load()
            journalRevision = saved.revision
            archiveReferences = saved.archives
            archived = requireNotNull(journal).archivedMetadata(archiveReferences)
            check(saved.entries.none { SmartComboRequestJournal.identity(it.plan.requestId) in archived.requestHashes })
            saved.entries.forEach { original ->
                val recovered = when (original.state) {
                    State.RESERVED -> original.copy(state = State.RELEASED)
                    State.DISPATCHED -> original.copy(state = State.UNCERTAIN)
                    else -> original
                }
                entries[recovered.plan.requestId] = recovered
            }
            saved.quotas.forEach { (scope, windows) ->
                snapshots[scope] = windows.associate { w ->
                    w.kind to Evidence(w, w.observedAtEpochMs ?: 0L)
                }.toMutableMap()
            }
            loaded = true
            // Commit recovery before accepting new work. No saved request is replayed.
            if (saved.entries.any { it.state == State.RESERVED || it.state == State.DISPATCHED })
                return persist()
            return true
        } catch (_: Exception) { journalFailed = true; return false }
        finally { cachedDurability = computeDurability() }
    }
    private fun persist(): Boolean {
        if (journal == null) {
            cachedDurability = computeDurability()
            return true
        }
        if (journalFailed) return false
        return try {
            journalRevision = journal.save(journalRevision, SmartComboRequestJournal.Snapshot(
                entries = entries.values.toList(),
                quotas = snapshots.mapValues { (_, values) -> values.values.map { it.value } },
                archives = archiveReferences
            ))
            true
        } catch (_: Exception) { journalFailed = true; false }
        finally { cachedDurability = computeDurability() }
    }

    /** Inputs must be normalized and authenticated by a provider-specific quota adapter. */
    @Synchronized fun observe(scope: String, windows: List<SmartComboPlanner.QuotaWindow>) {
        require(scope.isNotBlank())
        if (!ensureLoaded()) return
        val received = now()
        val byKind = snapshots.getOrPut(scopeKey(scope)) { linkedMapOf() }
        for (window in windows) {
            val observed = window.observedAtEpochMs ?: continue
            // A reset that precedes (or coincides with) its own observation cannot
            // describe a live quota window. Do not let malformed evidence replace
            // the last consistent provider snapshot.
            if (observed > received ||
                (window.resetAtEpochMs != null && window.resetAtEpochMs <= observed) ||
                window.remaining == null ||
                (window.limit != null && window.remaining > window.limit) ||
                window.confidence != SmartComboPlanner.Confidence.PROVIDER_VERIFIED) continue
            val previous = byKind[window.kind]
            val previousAt = previous?.value?.observedAtEpochMs ?: Long.MIN_VALUE
            // Identical observation timestamps are not new provider evidence. Never
            // raise advertised headroom from a duplicate or delayed response. A stricter
            // (lower remaining) observation is safe to incorporate; a higher one must
            // carry a strictly newer trusted observation time.
            if (observed > previousAt ||
                (observed == previousAt && previous != null &&
                    window.remaining < requireNotNull(previous.value.remaining))) {
                byKind[window.kind] = Evidence(window, received)
            }
        }
        persist()
    }

    /** Duplicate request IDs are rejected so retries cannot cause duplicate dispatch. */
    @Synchronized fun reserve(requested: Plan): Admission {
        if (!ensureLoaded()) return Admission(false, "Request journal unavailable; automatic routing paused", true)
        val plan = if (journal == null) requested else requested.copy(
            scope = scopeKey(requested.scope), routeId = SmartComboRequestJournal.identity(requested.routeId))
        if (entries.containsKey(plan.requestId) ||
            SmartComboRequestJournal.identity(plan.requestId) in archived.requestHashes) return Admission(false, "Duplicate request ID", false)
        if (journal != null && entries.size >= SmartComboRequestJournal.MAX_ENTRIES)
            return Admission(false, "Request journal full; no evidence was deleted", true)
        val at = now()
        var refresh = snapshots[plan.scope].isNullOrEmpty()
        for (capacity in capacitiesForKey(plan.scope, at)) {
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
        if (!persist()) return Admission(false, "Request journal unavailable; automatic routing paused", true)
        return Admission(true, "Reserved locally", refresh)
    }

    @Synchronized fun dispatch(requestId: String): Boolean {
        if (!ensureLoaded()) return false
        val current = entries[requestId] ?: return false
        if (current.state != State.RESERVED) return false
        entries[requestId] = current.copy(state = State.DISPATCHED)
        return persist()
    }

    /** Releasing dispatched/uncertain usage without provider evidence is forbidden. */
    @Synchronized fun releaseUndispatched(requestId: String): Boolean {
        if (!ensureLoaded()) return false
        val current = entries[requestId] ?: return false
        if (current.state != State.RESERVED) return false
        entries[requestId] = current.copy(state = State.RELEASED)
        return persist()
    }

    @Synchronized fun markUncertain(requestId: String): Boolean {
        if (!ensureLoaded()) return false
        val current = entries[requestId] ?: return false
        if (current.state != State.DISPATCHED && current.state != State.UNCERTAIN) return false
        entries[requestId] = current.copy(state = State.UNCERTAIN)
        return persist()
    }

    /**
     * Completed text stream without provider usage: keep conservative token reservation in
     * known windows, but do not permanently count this as an in-flight request. Provider
     * totals remain unknown; do not label local heuristic counts as verified.
     */
    @Synchronized fun completedWithoutUsage(requestId: String): Boolean {
        if (!ensureLoaded()) return false
        val current = entries[requestId] ?: return false
        if (current.state != State.DISPATCHED) return false
        entries[requestId] = current.copy(state = State.FINISHED_UNVERIFIED, settledAt = now())
        return persist()
    }

    /** Accept only final provider/billing-confirmed usage, never a tokenization heuristic. */
    @Synchronized fun reconcile(requestId: String, usage: Usage): Boolean {
        if (!ensureLoaded()) return false
        val current = entries[requestId] ?: return false
        if (current.state == State.RECONCILED) return current.confirmed == usage
        if (current.state !in setOf(State.DISPATCHED, State.UNCERTAIN, State.FINISHED_UNVERIFIED)) return false
        // A delayed final usage callback may follow an estimated stream completion.
        // Preserve the original completion boundary: newer quota snapshots already
        // cover that request and must not be charged for it a second time.
        entries[requestId] = current.copy(
            state = State.RECONCILED, confirmed = usage,
            settledAt = current.settledAt ?: now()
        )
        return persist()
    }

    @Synchronized fun entry(requestId: String): Entry? {
        if (!ensureLoaded()) return null
        return entries[requestId]
    }

    @Synchronized fun capacities(scope: String, at: Long = now()): List<Capacity> {
        if (!ensureLoaded()) return emptyList()
        return capacitiesForKey(scopeKey(scope), at)
    }
    private fun capacitiesForKey(scope: String, at: Long): List<Capacity> =
        snapshots[scope].orEmpty().values.map { evidence ->
            val w = evidence.value
            if (!fresh(w, at)) {
                Capacity(w.kind, Source.UNKNOWN, null, null, 0L, w.resetAtEpochMs)
            } else {
                val seen = requireNotNull(w.observedAtEpochMs)
                val usageSince = entries.values.asSequence()
                    .filter { it.plan.scope == scope && notYetCoveredBySnapshot(it, seen) }
                    .fold(0L) { sum, entry -> saturatingAdd(sum, accounted(entry, w.kind)) }
                // Reject inflated quota reports conservatively: a provider's
                // declared window limit is an upper bound even when a delayed
                // remaining counter advertises a larger number.
                val reported = requireNotNull(w.remaining)
                val advertised = minOf(reported, w.limit ?: reported)
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
            w.remaining == null || (w.limit != null && w.remaining > w.limit) ||
            observed > at || (w.resetAtEpochMs != null && at >= w.resetAtEpochMs)) return false
        val ttl = when (w.kind) {
            SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE,
            SmartComboPlanner.LimitKind.INPUT_TOKENS_PER_MINUTE,
            SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_MINUTE -> 60_000L
            else -> 300_000L
        }
        // A negative age also catches signed subtraction overflow from extreme timestamps.
        // Do not advertise untrustworthy provider quota as confirmed remaining capacity.
        val ageMs = at - observed
        return ageMs >= 0L && ageMs <= ttl
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
