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
        assertFalse(ledger.reserve(plan("r2", amount = 2000, micros = 3000)).accepted)
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
}
