package ai.drfx.maximus.matrixai.llm

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class SmartComboSessionBrokerTest {
    private fun withOwner(team: Boolean = true, action: (String) -> Unit) {
        val id = UUID.randomUUID().toString()
        assertTrue(SmartComboSessionBroker.acquire(id, team))
        try { action(id) } finally { SmartComboSessionBroker.release(id) }
    }

    @Test fun cancellationBeforeRegistrationIsDeliveredOnce() = withOwner { id ->
        var calls = 0
        SmartComboSessionBroker.cancelTeam()
        SmartComboSessionBroker.registerCancellation(id) { calls++ }
        SmartComboSessionBroker.cancelTeam()
        SmartComboSessionBroker.registerCancellation(id) { calls++ }
        assertEquals(1, calls)
    }

    @Test fun cancellationAfterRegistrationIsDeliveredOnceOutsideLock() = withOwner { id ->
        var calls = 0
        SmartComboSessionBroker.registerCancellation(id) {
            assertFalse(Thread.holdsLock(SmartComboSessionBroker))
            calls++
        }
        SmartComboSessionBroker.cancelTeam()
        SmartComboSessionBroker.cancelTeam()
        assertEquals(1, calls)
    }

    @Test fun pendingCancellationCallbackAlsoRunsOutsideLock() = withOwner { id ->
        SmartComboSessionBroker.cancelTeam()
        var called = false
        SmartComboSessionBroker.registerCancellation(id) {
            assertFalse(Thread.holdsLock(SmartComboSessionBroker))
            called = true
        }
        assertTrue(called)
    }

    @Test fun teamCancellationDoesNotCancelChat() = withOwner(team = false) { id ->
        var calls = 0
        SmartComboSessionBroker.cancelTeam()
        SmartComboSessionBroker.registerCancellation(id) { calls++ }
        SmartComboSessionBroker.cancelTeam()
        assertEquals(0, calls)
    }

    @Test fun staleOwnerCannotRegisterReleaseOrLeakCancellationToNextOwner() {
        var staleCalls = 0
        var oldId = ""
        withOwner { id ->
            oldId = id
            SmartComboSessionBroker.cancelTeam()
        }
        withOwner { id ->
            var currentCalls = 0
            SmartComboSessionBroker.registerCancellation(oldId) { staleCalls++ }
            SmartComboSessionBroker.release(oldId)
            assertTrue(SmartComboSessionBroker.busy())
            SmartComboSessionBroker.registerCancellation(id) { currentCalls++ }
            assertEquals(0, currentCalls)
            SmartComboSessionBroker.cancelTeam()
            assertEquals(1, currentCalls)
        }
        assertEquals(0, staleCalls)
    }

    @Test fun callbackCanReleaseAndAcquireWithoutCancellingReplacement() {
        val replacement = UUID.randomUUID().toString()
        try {
            withOwner { id ->
                SmartComboSessionBroker.registerCancellation(id) {
                    SmartComboSessionBroker.release(id)
                    assertTrue(SmartComboSessionBroker.acquire(replacement, team = true))
                }
                SmartComboSessionBroker.cancelTeam()
                var calls = 0
                SmartComboSessionBroker.registerCancellation(replacement) { calls++ }
                assertEquals(0, calls)
                SmartComboSessionBroker.cancelTeam()
                assertEquals(1, calls)
            }
        } finally { SmartComboSessionBroker.release(replacement) }
    }
}
