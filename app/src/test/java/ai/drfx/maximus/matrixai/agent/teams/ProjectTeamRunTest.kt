package ai.drfx.maximus.matrixai.agent.teams

import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectTeamRunTest {
    @get:Rule val temporary = TemporaryFolder()
    private val project = UUID.randomUUID().toString()
    private val revision = "a".repeat(64)
    private var now = 1000L
    private fun store(owner: String = "process1") = ProjectTeamRunStore(File(temporary.root, "run.json"), owner) { now }
    private fun ready(store: ProjectTeamRunStore): ProjectTeamRun {
        val run = store.prepare(project, revision, "Project metadata only")
        return store.change(run.id, setOf(run.state)) { it.copy(state = ProjectTeamRun.State.PLANNER_READY) }
    }
    private fun evidence(text: String = "proposal") = ProjectTeamRun.Evidence("OPENAI", "test-model", text, 8, 4, false)

    @Test fun secondStoreCannotClaimLiveLeaseOrAlterCapturedInputs() {
        val first = store(); val run = ready(first)
        assertThrows(IllegalStateException::class.java) { store().prepare(project, revision, "other") }
        assertThrows(IllegalStateException::class.java) {
            first.change(run.id, setOf(run.state)) { it.copy(prompt = "replacement") }
        }
        assertEquals(run.prompt, first.read()!!.prompt)
    }
    @Test fun dispatchedProcessDeathIsUncertainAndNeverReady() {
        val first = store(); val run = ready(first)
        first.change(run.id, setOf(run.state)) { it.copy(state = ProjectTeamRun.State.PLANNER_DISPATCHED) }
        val recovered = store("process2").read()!!
        assertEquals(ProjectTeamRun.State.UNCERTAIN, recovered.state)
        assertThrows(IllegalStateException::class.java) {
            store("process2").change(run.id, setOf(ProjectTeamRun.State.PLANNER_READY)) { it }
        }
    }
    @Test fun undispatchedRestartOrExpiredLeaseBlocksInsteadOfDispatching() {
        val first = store(); val run = ready(first)
        now = run.expiresAt + 1
        assertEquals(ProjectTeamRun.State.BLOCKED, first.read()!!.state)
        now = 1000
        val pending = store().prepare(project, revision, "captured")
        assertEquals(ProjectTeamRun.State.BLOCKED, store("process2").read()!!.state)
        assertEquals(pending.prompt, store().read()!!.prompt)
    }
    @Test fun corruptedJournalFailsClosedWithoutOverwriting() {
        val file = File(temporary.root, "run.json").apply { writeText("invalid") }
        assertThrows(Exception::class.java) { store().prepare(project, revision, "new") }
        assertEquals("invalid", file.readText())
    }
    @Test fun actualTwoStageRequestsFollowPersistedBarriersAndExactCheckpoint() = runTest {
        val store = store(); val run = ready(store); val roles = mutableListOf<String>()
        ProjectTeamExecutor(store).execute(run.id, {}, { role, prompt ->
            roles += role
            if (role == "reviewer") assertTrue(prompt.contains("original planner"))
            ProjectTeamExecutor.Dispatch("OPENAI", "test-model", { _, cap ->
                assertEquals(1024, cap)
                assertTrue(store.read()!!.dispatched)
                evidence(if (role == "planner") "original planner" else "review")
            })
        }, {})
        assertEquals(listOf("planner", "reviewer"), roles)
        val restored = store("process2").read()!!
        assertEquals(ProjectTeamRun.State.COMPLETE, restored.state)
        assertEquals("original planner", restored.planner!!.text)
        assertEquals(ProjectTeamRun.digest("review"), restored.reviewer!!.sha256)
    }
    @Test fun ambiguousFailureMakesOneCallAndPreservesUncertainDispatch() = runTest {
        val store = store(); val run = ready(store); var calls = 0
        ProjectTeamExecutor(store).execute(run.id, {}, { _, _ ->
            ProjectTeamExecutor.Dispatch("OPENAI", "test-model", { _, _ -> calls++; error("socket timeout") })
        }, {})
        assertEquals(1, calls)
        assertEquals(ProjectTeamRun.State.UNCERTAIN, store.read()!!.state)
    }
    @Test fun revokedApprovalBeforeReviewerPreservesPlannerAndPreventsSecondSend() = runTest {
        val store = store(); val run = ready(store); var allowed = true; var calls = 0
        ProjectTeamExecutor(store).execute(run.id, { check(allowed) }, { _, _ ->
            ProjectTeamExecutor.Dispatch("OPENAI", "test-model", { _, _ -> calls++; evidence() })
        }, { if (it.state == ProjectTeamRun.State.REVIEWER_READY) allowed = false })
        assertEquals(1, calls)
        assertNotNull(store.read()!!.planner)
        assertEquals(ProjectTeamRun.State.BLOCKED, store.read()!!.state)
    }
    @Test fun cancellationNeverStartsReviewerAndRethrows() = runTest {
        val store = store(); val run = ready(store); var calls = 0
        try {
            ProjectTeamExecutor(store).execute(run.id, {}, { _, _ ->
                ProjectTeamExecutor.Dispatch("OPENAI", "test-model", { _, _ -> calls++; throw CancellationException() })
            }, {})
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
        assertEquals(ProjectTeamRun.State.UNCERTAIN, store.read()!!.state)
    }
    @Test fun validationFailureAbandonsReservedRouteWithoutNetworkSend() = runTest {
        val store = store(); val run = ready(store); var valid = true; var released = false
        ProjectTeamExecutor(store).execute(run.id, { check(valid) }, { _, _ ->
            valid = false
            ProjectTeamExecutor.Dispatch("OPENAI", "test-model", { _, _ -> error("must not send") }, { released = true })
        }, {})
        assertTrue(released)
        assertEquals(ProjectTeamRun.State.BLOCKED, store.read()!!.state)
    }
    @Test fun overlongResponseCannotEnterCheckpointOrReviewer() = runTest {
        val store = store(); val run = ready(store); var calls = 0
        ProjectTeamExecutor(store).execute(run.id, {}, { _, _ ->
            ProjectTeamExecutor.Dispatch("OPENAI", "test-model", { _, _ -> calls++; evidence("x".repeat(16_385)) })
        }, {})
        assertEquals(1, calls)
        assertNull(store.read()!!.planner)
        assertEquals(ProjectTeamRun.State.UNCERTAIN, store.read()!!.state)
    }
    @Test fun explicitInvalidCheckpointRecoveryQuarantinesAndAllowsFreshApproval() {
        val file = File(temporary.root, "run.json").apply { writeText("invalid") }
        store().quarantineInvalid()
        assertFalse(file.exists())
        assertEquals("invalid", File(temporary.root, "run.json.invalid").readText())
        assertEquals(ProjectTeamRun.State.AWAITING_APPROVAL,
            store().prepare(project, revision, "fresh approval required").state)
    }
}
