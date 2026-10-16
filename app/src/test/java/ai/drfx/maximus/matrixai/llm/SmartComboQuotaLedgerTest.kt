package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SmartComboQuotaLedgerTest {
    private val clock = 1_800_000_000_000L
    private fun plan(id: String, amount: Long = 2000L, micros: Long = 0L) =
        SmartComboQuotaLedger.Plan(id, "shared-account", "free-model", amount / 2, amount - amount / 2, micros)

    private fun quota(
        kind: SmartComboPlanner.LimitKind = SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_DAY,
        remaining: Long = 10_000,
        limit: Long = 10_000,
        observed: Long = clock
    ) = SmartComboPlanner.QuotaWindow(
        kind = kind,
        remaining = remaining,
        limit = limit,
        observedAtEpochMs = observed,
        confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
    )

    @Test fun reservesHeadroomAcrossIndependentConcurrentRequests() {
        val ledger = SmartComboQuotaLedger(now = { clock })
        ledger.observe("shared-account", listOf(quota()))
        assertTrue(ledger.reserve(plan("first")).accepted)
        assertTrue(ledger.reserve(plan("second")).accepted)
        assertTrue(ledger.reserve(plan("third")).accepted)
        assertTrue(ledger.reserve(plan("fourth")).accepted)
        val blocked = ledger.reserve(plan("fifth"))
        assertFalse(blocked.accepted)
        assertTrue(blocked.reason.contains("headroom"))
        assertEquals(2000L, ledger.capacities("shared-account").single().safeAvailable)
    }

    @Test fun inflatedRemainingCannotExceedVerifiedWindowLimit() {
        val ledger = SmartComboQuotaLedger(now = { clock })
        ledger.observe("shared-account", listOf(quota(remaining = 100_000L, limit = 3_000L)))
        val capacity = ledger.capacities("shared-account").single()
        assertEquals(3_000L, capacity.providerRemaining)
        assertEquals(3_000L, capacity.safeAvailable)
        assertFalse(ledger.reserve(plan("inflated", amount = 2_000L)).accepted)
        assertTrue(ledger.reserve(plan("within-limit", amount = 1_000L)).accepted)
        assertEquals(2_000L, ledger.capacities("shared-account").single().safeAvailable)
    }

    @Test fun duplicateIdCannotBeDispatchedTwice() {
        val ledger = SmartComboQuotaLedger(now = { clock })
        assertTrue(ledger.reserve(plan("r1")).accepted)
        assertFalse(ledger.reserve(plan("r1")).accepted)
        assertTrue(ledger.dispatch("r1"))
        assertFalse(ledger.dispatch("r1"))
        assertFalse(ledger.releaseUndispatched("r1"))
    }

    @Test fun disconnectedStreamRemainsReservedUntilFinalReconciliation() {
        val ledger = SmartComboQuotaLedger(now = { clock })
        ledger.observe("shared-account", listOf(quota()))
        assertTrue(ledger.reserve(plan("r1")).accepted)
        assertTrue(ledger.dispatch("r1"))
        assertTrue(ledger.markUncertain("r1"))
        assertFalse(ledger.releaseUndispatched("r1"))
        assertEquals(8000L, ledger.capacities("shared-account").single().safeAvailable)
        val usage = SmartComboQuotaLedger.Usage(1000, 400, 1600)
        assertTrue(ledger.reconcile("r1", usage))
        assertTrue(ledger.reconcile("r1", usage))
        assertFalse(ledger.reconcile("r1", usage.copy(total = 2000)))
        assertEquals(8400L, ledger.capacities("shared-account").single().safeAvailable)
        assertEquals(SmartComboQuotaLedger.State.RECONCILED, ledger.entry("r1")?.state)
    }

    @Test fun onlyDefinitelyUndispatchedRequestsReleaseCapacity() {
        val ledger = SmartComboQuotaLedger(now = { clock })
        ledger.observe("shared-account", listOf(quota()))
        assertTrue(ledger.reserve(plan("r1")).accepted)
        assertEquals(8000L, ledger.capacities("shared-account").single().safeAvailable)
        assertTrue(ledger.releaseUndispatched("r1"))
        assertEquals(10_000L, ledger.capacities("shared-account").single().safeAvailable)
    }

    @Test fun staleProviderQuotaNeverAppearsAsExactRemaining() {
        var time = clock
        val ledger = SmartComboQuotaLedger(now = { time })
        ledger.observe("shared-account", listOf(quota()))
        time += 6 * 60_000L
        val display = ledger.capacities("shared-account").single()
        assertEquals(SmartComboQuotaLedger.Source.UNKNOWN, display.source)
        assertNull(display.providerRemaining)
        assertNull(display.safeAvailable)
        assertTrue(ledger.reserve(plan("r1")).needsQuotaRefresh)
    }

    @Test fun ignoresOlderSnapshotsAndEstimatedQuotaEvidence() {
        val ledger = SmartComboQuotaLedger(now = { clock })
        ledger.observe("shared-account", listOf(quota(remaining = 9000)))
        ledger.observe("shared-account", listOf(quota(remaining = 1, observed = clock - 500)))
        ledger.observe("shared-account", listOf(
            quota(remaining = 1).copy(confidence = SmartComboPlanner.Confidence.ESTIMATED)
        ))
        assertEquals(9000L, ledger.capacities("shared-account").single().safeAvailable)
    }

    @Test fun moneyBalanceIsNotConflatedWithTokenBalance() {
        val ledger = SmartComboQuotaLedger(now = { clock })
        ledger.observe("shared-account", listOf(
            quota(kind = SmartComboPlanner.LimitKind.DOLLAR_BALANCE_MICROS, remaining = 10_000, limit = 10_000)
        ))
        assertTrue(ledger.reserve(plan("r1", amount = 2000, micros = 3000)).accepted)
        assertEquals(7000L, ledger.capacities("shared-account").single().safeAvailable)
        assertFalse(ledger.reserve(plan("r2", amount = 2000, micros = 5000)).accepted)
    }

    @Test fun zeroPaidCreditDoesNotBlockVerifiedFreeModel() {
        val ledger = SmartComboQuotaLedger(now = { clock })
        ledger.observe("shared-account", listOf(
            quota(kind = SmartComboPlanner.LimitKind.DOLLAR_BALANCE_MICROS, remaining = 0, limit = 100_000)
        ))
        assertTrue(ledger.reserve(plan("free-route", micros = 0)).accepted)
    }

    @Test fun unknownProviderQuotaHasBoundedConcurrency() {
        val ledger = SmartComboQuotaLedger(now = { clock }, maxUnknownInFlightPerScope = 2)
        assertTrue(ledger.reserve(plan("one")).accepted)
        assertTrue(ledger.reserve(plan("two")).accepted)
        val blocked = ledger.reserve(plan("three"))
        assertFalse(blocked.accepted)
        assertTrue(blocked.needsQuotaRefresh)
        assertTrue(ledger.releaseUndispatched("one"))
        assertTrue(ledger.reserve(plan("four")).accepted)
    }

    @Test fun completedWithoutProviderUsageDoesNotExhaustUnknownConcurrency() {
        val ledger = SmartComboQuotaLedger(now = { clock }, maxUnknownInFlightPerScope = 1)
        assertTrue(ledger.reserve(plan("first")).accepted)
        assertTrue(ledger.dispatch("first"))
        assertTrue(ledger.completedWithoutUsage("first"))
        assertEquals(SmartComboQuotaLedger.State.FINISHED_UNVERIFIED, ledger.entry("first")?.state)
        assertTrue(ledger.reserve(plan("second")).accepted)
    }

    @Test fun interruptedRequestIsStillHeldForReconciliation() {
        val ledger = SmartComboQuotaLedger(now = { clock }, maxUnknownInFlightPerScope = 1)
        assertTrue(ledger.reserve(plan("first")).accepted)
        assertTrue(ledger.dispatch("first"))
        assertTrue(ledger.markUncertain("first"))
        assertFalse(ledger.reserve(plan("second")).accepted)
        assertFalse(ledger.completedWithoutUsage("first"))
    }

    @Test fun reservationsAreAtomicWithinOneProcess() {
        val ledger = SmartComboQuotaLedger(now = { clock })
        ledger.observe("shared-account", listOf(
            quota(kind = SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE, remaining = 5, limit = 5)
        ))
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..32).map { n -> pool.submit(Callable { ledger.reserve(plan("parallel-" + n)).accepted }) }
            val successCount = futures.count { it.get(5, TimeUnit.SECONDS) }
            assertEquals(4, successCount)
            assertEquals(1L, ledger.capacities("shared-account").single().safeAvailable)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun newerSnapshotCannotEraseAnOlderUndispatchedReservation() {
        var time = clock
        val ledger = SmartComboQuotaLedger(now = { time })
        assertTrue(ledger.reserve(plan("reserved-before-snapshot")).accepted)
        time += 100
        ledger.observe("shared-account", listOf(quota(remaining = 4000, observed = time)))

        val capacity = ledger.capacities("shared-account").single()
        assertEquals(4000L, capacity.providerRemaining)
        assertEquals(2000L, capacity.locallyReservedOrSpent)
        assertEquals(2000L, capacity.safeAvailable)
        assertEquals(SmartComboQuotaLedger.Source.LOCALLY_ADJUSTED, capacity.source)
        assertFalse(ledger.reserve(plan("would-overbook")).accepted)
        assertTrue(ledger.releaseUndispatched("reserved-before-snapshot"))
        assertEquals(4000L, ledger.capacities("shared-account").single().safeAvailable)
        assertTrue(ledger.reserve(plan("after-release")).accepted)
    }

    @Test fun newerSnapshotsCannotEraseDispatchedOrUncertainDemand() {
        var time = clock
        val ledger = SmartComboQuotaLedger(now = { time })
        assertTrue(ledger.reserve(plan("in-flight")).accepted)
        assertTrue(ledger.dispatch("in-flight"))
        for (uncertain in listOf(false, true)) {
            if (uncertain) assertTrue(ledger.markUncertain("in-flight"))
            time += 100
            ledger.observe("shared-account", listOf(quota(remaining = 4000, observed = time)))
            assertEquals(2000L, ledger.capacities("shared-account").single().safeAvailable)
            assertFalse(ledger.reserve(plan("blocked-" + uncertain)).accepted)
        }
    }

    @Test fun completionAfterSnapshotRetainsConfirmedUsageUntilNewerEvidenceArrives() {
        var time = clock
        val ledger = SmartComboQuotaLedger(now = { time })
        assertTrue(ledger.reserve(plan("old-request")).accepted)
        assertTrue(ledger.dispatch("old-request"))
        time += 100
        ledger.observe("shared-account", listOf(quota(observed = time)))
        time += 100
        val usage = SmartComboQuotaLedger.Usage(1000, 500, 1500)
        assertTrue(ledger.reconcile("old-request", usage))
        val settledAt = time
        assertEquals(settledAt, ledger.entry("old-request")?.settledAt)
        assertEquals(8500L, ledger.capacities("shared-account").single().safeAvailable)

        // Even a delayed snapshot received after completion can predate settlement.
        time += 100
        ledger.observe("shared-account", listOf(quota(remaining = 9000, observed = settledAt - 1)))
        assertEquals(7500L, ledger.capacities("shared-account").single().safeAvailable)
        // A duplicate callback must not move the original settlement timestamp.
        assertTrue(ledger.reconcile("old-request", usage))
        assertEquals(settledAt, ledger.entry("old-request")?.settledAt)
        ledger.observe("shared-account", listOf(quota(remaining = 8500, observed = time)))
        val caughtUp = ledger.capacities("shared-account").single()
        assertEquals(8500L, caughtUp.safeAvailable)
        assertEquals(0L, caughtUp.locallyReservedOrSpent)
        assertEquals(SmartComboQuotaLedger.Source.PROVIDER_SNAPSHOT, caughtUp.source)
    }

    @Test fun completionWithoutUsageKeepsConservativeHoldAcrossEarlierSnapshots() {
        var time = clock
        val ledger = SmartComboQuotaLedger(now = { time })
        assertTrue(ledger.reserve(plan("streamed-without-usage")).accepted)
        assertTrue(ledger.dispatch("streamed-without-usage"))
        time += 100
        ledger.observe("shared-account", listOf(quota(observed = time)))
        time += 100
        assertTrue(ledger.completedWithoutUsage("streamed-without-usage"))
        assertEquals(8000L, ledger.capacities("shared-account").single().safeAvailable)
        time += 100
        ledger.observe("shared-account", listOf(quota(remaining = 8000, observed = time)))
        assertEquals(8000L, ledger.capacities("shared-account").single().safeAvailable)
        assertEquals(0L, ledger.capacities("shared-account").single().locallyReservedOrSpent)
    }

    @Test fun expiredWindowsStayUnknownWhileOutstandingReservationsRemainHeld() {
        var time = clock
        val ledger = SmartComboQuotaLedger(now = { time })
        assertTrue(ledger.reserve(plan("held")).accepted)
        time += 100
        ledger.observe("shared-account", listOf(quota(observed = time).copy(resetAtEpochMs = time + 100)))
        time += 100
        val expired = ledger.capacities("shared-account").single()
        assertEquals(SmartComboQuotaLedger.Source.UNKNOWN, expired.source)
        assertNull(expired.safeAvailable)
        assertNull(expired.providerRemaining)
        time += 100
        ledger.observe("shared-account", listOf(quota(remaining = 6000, observed = time)))
        assertEquals(4000L, ledger.capacities("shared-account").single().safeAvailable)
    }
    @Test fun identicalProviderObservationCannotIncreaseQuota() {
        var time = clock
        val ledger = SmartComboQuotaLedger(now = { time })
        ledger.observe("shared-account", listOf(quota(remaining = 6000)))
        time += 50
        // A delayed, duplicated provider timestamp must not mint more tokens.
        ledger.observe("shared-account", listOf(quota(remaining = 9000)))
        assertEquals(6000L, ledger.capacities("shared-account").single().safeAvailable)
        // A more conservative observation at that timestamp may restrict capacity.
        ledger.observe("shared-account", listOf(quota(remaining = 5000)))
        assertEquals(5000L, ledger.capacities("shared-account").single().safeAvailable)
        // Fresh authenticated evidence is required before increasing available quota.
        time += 50
        ledger.observe("shared-account", listOf(quota(remaining = 8000, observed = time)))
        assertEquals(8000L, ledger.capacities("shared-account").single().safeAvailable)
    }

    @Test fun delayedSnapshotCannotErasePendingTokenReservation() {
        var time = clock
        val ledger = SmartComboQuotaLedger(now = { time })
        ledger.observe("shared-account", listOf(quota(remaining = 8000)))
        assertTrue(ledger.reserve(plan("reserved")).accepted)
        time += 50
        ledger.observe("shared-account", listOf(quota(remaining = 10000)))
        assertEquals(6000L, ledger.capacities("shared-account").single().safeAvailable)
    }

}
