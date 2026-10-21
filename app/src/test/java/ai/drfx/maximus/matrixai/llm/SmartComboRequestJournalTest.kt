package ai.drfx.maximus.matrixai.llm

import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SmartComboRequestJournalTest {
    @get:Rule val directory = TemporaryFolder()
    private val now = 1_800_000_000_000L
    private fun file() = File(directory.root, "private/requests.json")
    private fun ledger(file: File, at: Long = now) = SmartComboQuotaLedger(
        now = { at }, journal = SmartComboRequestJournal(file))
    private fun plan(id: String = "request-1") = SmartComboQuotaLedger.Plan(
        id, "NVIDIA|https://private.example/v1", "NVIDIA::model", 10, 20, 0, LlmProvider.NVIDIA)
    private fun quota(at: Long = now) = SmartComboPlanner.QuotaWindow(
        SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_MINUTE, 1_000, 1_000,
        observedAtEpochMs = at, confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED)

    @Test fun restartReleasesUndispatchedButNeverReusesItsIdentity() {
        val file = file()
        assertTrue(ledger(file).reserve(plan()).accepted)
        val recovered = ledger(file)
        assertEquals(SmartComboQuotaLedger.State.RELEASED, recovered.entry("request-1")?.state)
        assertFalse(recovered.reserve(plan()).accepted)
        assertFalse(recovered.dispatch("request-1"))
        assertEquals(1, recovered.durability().retainedRequests)
    }

    @Test fun dispatchedRequestRecoversAsUncertainWithoutReplayOrRelease() {
        val file = file()
        val first = ledger(file)
        assertTrue(first.reserve(plan()).accepted)
        assertTrue(first.dispatch("request-1"))
        val recovered = ledger(file)
        assertEquals(SmartComboQuotaLedger.State.UNCERTAIN, recovered.entry("request-1")?.state)
        assertFalse(recovered.dispatch("request-1"))
        assertFalse(recovered.releaseUndispatched("request-1"))
        assertFalse(recovered.reserve(plan()).accepted)
        assertEquals(1, recovered.durability().uncertainRequests)
    }

    @Test fun restoredUncertainRequestsStillLimitUnknownQuotaConcurrency() {
        val file = file()
        val original = ledger(file)
        for (id in listOf("a", "b")) {
            assertTrue(original.reserve(plan(id)).accepted)
            assertTrue(original.dispatch(id))
        }
        val recovered = ledger(file)
        assertFalse(recovered.reserve(plan("c")).accepted)
        assertEquals(2, recovered.durability().uncertainRequests)
    }

    @Test fun confirmedUsageAndCheckpointReferenceSurviveRestartWithoutDoubleCounting() {
        val file = file()
        val cp = SmartComboCheckpointReference(UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), "a".repeat(64))
        val original = ledger(file)
        assertTrue(original.reserve(plan().copy(checkpoint = cp)).accepted)
        assertTrue(original.dispatch("request-1"))
        val usage = SmartComboQuotaLedger.Usage(10, 15, 25)
        assertTrue(original.reconcile("request-1", usage))
        assertTrue(original.reconcile("request-1", usage))
        val recovered = ledger(file)
        assertEquals(usage, recovered.entry("request-1")?.confirmed)
        assertEquals(cp, recovered.entry("request-1")?.plan?.checkpoint)
        assertEquals(25L, recovered.durability().confirmedTokensByProvider[LlmProvider.NVIDIA])
        assertTrue(recovered.reconcile("request-1", usage))
        assertEquals(25L, recovered.durability().confirmedTokensByProvider[LlmProvider.NVIDIA])
    }

    @Test fun estimatedCompletionNeverBecomesConfirmedProviderUsage() {
        val file = file()
        val original = ledger(file)
        assertTrue(original.reserve(plan()).accepted)
        assertTrue(original.dispatch("request-1"))
        assertTrue(original.completedWithoutUsage("request-1"))
        val recovered = ledger(file)
        assertEquals(SmartComboQuotaLedger.State.FINISHED_UNVERIFIED, recovered.entry("request-1")?.state)
        assertTrue(recovered.durability().confirmedTokensByProvider.isEmpty())
        assertFalse(recovered.dispatch("request-1"))
    }

    @Test fun quotaEvidenceKeepsOriginalAgeAndConservativeUncertaintyAcrossRestart() {
        val file = file()
        val original = ledger(file)
        original.observe(plan().scope, listOf(quota()))
        assertTrue(original.reserve(plan()).accepted)
        assertTrue(original.dispatch("request-1"))
        val recovered = ledger(file, now + 100)
        assertEquals(970L, recovered.capacities(plan().scope).single().safeAvailable)
        val stale = ledger(file, now + 61_000)
        assertNull(stale.capacities(plan().scope).single().safeAvailable)
        assertEquals(SmartComboQuotaLedger.Source.UNKNOWN, stale.capacities(plan().scope).single().source)
    }

    @Test fun corruptJournalIsPreservedAndAutomaticRoutingFailsClosed() {
        val file = file()
        val original = ledger(file)
        assertTrue(original.reserve(plan()).accepted)
        file.writeText("corrupt journal")
        val recovered = ledger(file)
        assertFalse(recovered.reserve(plan("other")).accepted)
        assertFalse(recovered.dispatch("request-1"))
        assertEquals("ERROR", recovered.durability().status)
        assertEquals("corrupt journal", file.readText())
    }

    @Test fun storageContainsNoEndpointPromptCredentialOrRawRouteIdentity() {
        val file = file()
        assertTrue(ledger(file).reserve(plan()).accepted)
        val text = file.readText()
        assertFalse(text.contains("private.example"))
        assertFalse(text.contains("https://"))
        assertFalse(text.contains("NVIDIA::model"))
        assertFalse(text.contains("apiKey"))
        assertFalse(text.contains("prompt"))
    }

    @Test fun staleWriterCannotOverwriteAnotherWritersClaim() {
        val file = file()
        val first = ledger(file)
        val second = ledger(file)
        first.initialize()
        second.initialize()
        assertTrue(first.reserve(plan("first")).accepted)
        assertFalse(second.reserve(plan("second")).accepted)
        assertEquals("ERROR", second.durability().status)
        val recovered = ledger(file)
        assertNotNull(recovered.entry("first"))
        assertNull(recovered.entry("second"))
    }

    @Test fun failedDispatchCommitNeverAuthorizesNetworkSendAndLeavesRecoverableReservation() {
        val file = file()
        var fail = false
        val original = SmartComboQuotaLedger(now = { now },
            journal = SmartComboRequestJournal(file) { if (fail) error("Simulated disk failure") })
        assertTrue(original.reserve(plan()).accepted)
        fail = true
        assertFalse(original.dispatch("request-1"))
        assertEquals("ERROR", original.durability().status)
        val recovered = ledger(file)
        assertEquals(SmartComboQuotaLedger.State.RELEASED, recovered.entry("request-1")?.state)
        assertFalse(recovered.dispatch("request-1"))
    }

    @Test fun invalidCheckpointIdentityIsNotAccepted() {
        assertThrows(IllegalArgumentException::class.java) {
            SmartComboCheckpointReference("../project", UUID.randomUUID().toString(), "a".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SmartComboCheckpointReference(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "not-a-revision")
        }
    }
    @Test fun fullJournalCanBeArchivedWithoutLosingDuplicatePreventionOrEvidence() {
        val file = file()
        val journal = SmartComboRequestJournal(file)
        val old = now - 600_000L
        val rows = (0 until SmartComboRequestJournal.MAX_ENTRIES).map { i ->
            val p = plan("old-$i").let { it.copy(
                scope = SmartComboRequestJournal.identity(it.scope),
                routeId = SmartComboRequestJournal.identity(it.routeId)) }
            SmartComboQuotaLedger.Entry(p, old, SmartComboQuotaLedger.State.RELEASED)
        }
        journal.save(0, SmartComboRequestJournal.Snapshot(entries = rows))
        val ledger = ledger(file)
        assertFalse(ledger.reserve(plan("new")).accepted)
        assertEquals("FULL", ledger.durability().status)
        assertEquals(SmartComboRequestJournal.MAX_ENTRIES, ledger.archiveSettled())
        assertEquals("DURABLE", ledger.durability().status)
        assertEquals(1, ledger.durability().archiveSegments)
        assertTrue(ledger.reserve(plan("new")).accepted)
        assertFalse(ledger.reserve(plan("old-0")).accepted)
        val restarted = ledger(file)
        assertFalse(restarted.reserve(plan("old-0")).accepted)
        assertEquals(SmartComboRequestJournal.MAX_ENTRIES + 1, restarted.durability().retainedRequests)
        assertTrue(File(file.parentFile, "archives").listFiles().orEmpty().any { it.extension == "json" })
    }

    @Test fun archivalKeepsConfirmedProviderTotalsAndNeverMovesUncertainOrRecentEvidence() {
        val file = file()
        var at = now
        val ledger = SmartComboQuotaLedger(now = { at }, journal = SmartComboRequestJournal(file))
        assertTrue(ledger.reserve(plan("confirmed")).accepted)
        assertTrue(ledger.dispatch("confirmed"))
        assertTrue(ledger.reconcile("confirmed", SmartComboQuotaLedger.Usage(10, 15, 25)))
        assertTrue(ledger.reserve(plan("uncertain")).accepted)
        assertTrue(ledger.dispatch("uncertain"))
        assertTrue(ledger.markUncertain("uncertain"))
        assertEquals(0, ledger.archiveSettled())
        at += 600_000L
        assertEquals(1, ledger.archiveSettled())
        assertEquals(SmartComboQuotaLedger.State.UNCERTAIN, ledger.entry("uncertain")?.state)
        assertEquals(25L, ledger.durability().confirmedTokensByProvider[LlmProvider.NVIDIA])
        val recovered = ledger(file, at)
        recovered.initialize()
        assertEquals(25L, recovered.durability().confirmedTokensByProvider[LlmProvider.NVIDIA])
        assertFalse(recovered.reserve(plan("confirmed")).accepted)
        assertEquals(1, recovered.durability().uncertainRequests)
    }

    @Test fun brokenArchiveReferenceFailsClosedInsteadOfForgettingIdempotencyHistory() {
        val file = file()
        var at = now
        val ledger = SmartComboQuotaLedger(now = { at }, journal = SmartComboRequestJournal(file))
        assertTrue(ledger.reserve(plan()).accepted)
        assertTrue(ledger.releaseUndispatched("request-1"))
        at += 600_000L
        assertEquals(1, ledger.archiveSettled())
        val archive = File(file.parentFile, "archives").listFiles().orEmpty().single { it.extension == "json" }
        archive.writeText("corrupt archive")
        val recovered = ledger(file, at)
        assertFalse(recovered.reserve(plan("new")).accepted)
        assertEquals("ERROR", recovered.durability().status)
        assertEquals("corrupt archive", archive.readText())
    }

    @Test fun statusReadDoesNotWaitForBackgroundJournalCommit() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val proceed = java.util.concurrent.CountDownLatch(1)
        val journal = SmartComboRequestJournal(file(), beforeCommit = {
            entered.countDown()
            check(proceed.await(10, java.util.concurrent.TimeUnit.SECONDS))
        })
        val ledger = SmartComboQuotaLedger(now = { now }, journal = journal)
        ledger.initialize()
        val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
        val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val write = writer.submit<Boolean> { ledger.reserve(plan()).accepted }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val reading = reader.submit<SmartComboQuotaLedger.Durability> { ledger.durability() }
            assertEquals("DURABLE", reading.get(2, java.util.concurrent.TimeUnit.SECONDS).status)
            proceed.countDown()
            assertTrue(write.get(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(1, ledger.durability().activeJournalRequests)
        } finally {
            proceed.countDown(); writer.shutdownNow(); reader.shutdownNow()
        }
    }
}
