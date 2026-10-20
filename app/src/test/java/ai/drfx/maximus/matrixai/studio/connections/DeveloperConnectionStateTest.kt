package ai.drfx.maximus.matrixai.studio.connections

import org.junit.Assert.*
import org.junit.Test

class DeveloperConnectionStateTest {
    @Test fun saveAndServiceSwitchNeedFreshVerification() {
        val verified = DeveloperConnectionState().reset(true).let { it.finishVerification(it.revision) }
        assertTrue(verified.verified)
        val saved = verified.reset(true)
        assertTrue(saved.credentialSaved)
        assertFalse(saved.verified)
        assertFalse(saved.finishVerification(verified.revision).verified)
    }

    @Test fun revokeRejectsLateVerificationAndWorkers() {
        val active = DeveloperConnectionState().reset(true).selectAccount("account-a")
        val revoked = active.reset(false)
        assertEquals(revoked, revoked.finishVerification(active.revision))
        assertEquals(revoked, revoked.receiveWorkers(active.revision, "account-a", listOf("private-worker")))
    }

    @Test fun accountSelectionClearsOldNamesAndRejectsOldAccountResponse() {
        val first = DeveloperConnectionState(credentialSaved = true).selectAccount("account-a")
        val listed = first.receiveWorkers(first.revision, "account-a", listOf("a-worker"))
        assertEquals(listOf("a-worker"), listed.workerNames)
        val second = listed.selectAccount("account-b")
        assertTrue(second.workerNames.isEmpty())
        assertEquals(second, second.receiveWorkers(first.revision, "account-a", listOf("a-worker")))
        assertEquals(listOf("b-worker"), second.receiveWorkers(second.revision, "account-b", listOf("b-worker")).workerNames)
        val returned = second.selectAccount("account-a")
        assertEquals(returned, returned.receiveWorkers(first.revision, "account-a", listOf("stale-a-worker")))
    }

    @Test fun autoSelectionAndManualReselectionClearNames() {
        val listed = DeveloperConnectionState(credentialSaved = true, accountId = "account-a", workerNames = listOf("old"))
        assertTrue(listed.selectAccount("account-a").workerNames.isEmpty())
        assertTrue(listed.selectAccount("account-b").workerNames.isEmpty())
    }

    @Test fun verificationAndDiscoveryHaveIndependentObservationTimes() {
        val state = DeveloperConnectionState(credentialSaved = true).selectAccount("account-a")
        val verified = state.finishVerification(state.revision, 100L, "active")
        val discovered = verified.receiveWorkers(verified.revision, "account-a", emptyList(), 200L)
        assertTrue(discovered.verified)
        assertEquals(100L, discovered.verifiedAtEpochMs)
        assertEquals("active", discovered.verifiedIdentity)
        assertEquals(200L, discovered.workersObservedAtEpochMs)
        assertTrue(discovered.workerNames.isEmpty())
        val switched = discovered.selectAccount("account-b")
        assertEquals(0L, switched.workersObservedAtEpochMs)
        assertEquals(100L, switched.verifiedAtEpochMs)
        val revoked = discovered.reset(false)
        assertEquals(0L, revoked.verifiedAtEpochMs)
        assertEquals("", revoked.verifiedIdentity)
        assertEquals(0L, revoked.workersObservedAtEpochMs)
    }

    @Test fun invalidOrStaleEvidenceCannotReplaceCurrentObservation() {
        val state = DeveloperConnectionState(credentialSaved = true).selectAccount("account-a")
        assertEquals(state, state.finishVerification(state.revision, 0L, "active"))
        assertEquals(state, state.receiveWorkers(state.revision, "account-a", listOf("worker"), -1L))
        assertEquals(state, state.finishVerification(state.revision - 1, 100L, "stale"))
    }
}
