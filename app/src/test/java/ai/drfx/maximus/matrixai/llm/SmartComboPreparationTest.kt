package ai.drfx.maximus.matrixai.llm

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SmartComboPreparationTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun cancellationAtDispatcherReturnStillCapturesAndReleasesPersistedReservation() = runTest {
        val file = File(directory.root, "request-journal.json")
        val ledger = SmartComboQuotaLedger(journal = SmartComboRequestJournal(file))
        val executor = SmartComboFreeExecutor(ledger)
        val offer = SmartComboFreeCatalog.Offer(SmartComboPlanner.Route(
            "OPENAI::test", LlmProvider.OPENAI, "test", true, true,
            setOf(ModelCapability.CHAT), 32_768, SmartComboPlanner.Price(0.0, 0.0, true),
            null, 75), "https://api.openai.com/v1", "shared-test-scope")
        var captured: SmartComboFreeExecutor.Prepared? = null
        var returnedNormally = false
        lateinit var job: Job
        val worker = StandardTestDispatcher(testScheduler, name = "reservation-worker")
        job = launch(start = CoroutineStart.LAZY) {
            try {
                SmartComboPreparation.captureBeforeReturn(prepare = {
                    val prepared = executor.prepare("cancelled-at-return", 100, 200,
                        setOf(ModelCapability.CHAT), listOf(offer), { _, _ -> "unit-test-token" }, "general")
                    assertNotNull(prepared)
                    job.cancel() // Provider work never starts; cancellation races the return boundary.
                    prepared
                }, capture = { captured = it }, context = worker)
                returnedNormally = true
            } catch (cancelled: CancellationException) {
                captured?.let { executor.failed(it) }
                throw cancelled
            }
        }
        job.start()
        advanceUntilIdle()
        job.join()
        assertNotNull(captured)
        assertFalse(returnedNormally)
        assertEquals(SmartComboQuotaLedger.State.RELEASED,
            executor.requestState("cancelled-at-return"))
        val recovered = SmartComboQuotaLedger(journal = SmartComboRequestJournal(file))
        assertEquals(SmartComboQuotaLedger.State.RELEASED,
            recovered.entry("cancelled-at-return")?.state)
        assertFalse(recovered.dispatch("cancelled-at-return"))
    }
}
