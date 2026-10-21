package ai.drfx.maximus.matrixai.operations

import org.junit.Assert.*
import org.junit.Test

class OperationalEvidenceTest {
    @Test fun absentAndFailedSourcesCannotBecomeObserved() {
        for (state in listOf(EvidenceState.UNKNOWN, EvidenceState.UNAVAILABLE, EvidenceState.ERROR)) {
            assertEquals(state, OperationalEvidence("source", "none", 100L, state = state).freshness(101L))
        }
    }
    @Test fun dashboardRefreshDoesNotRewriteSourceEventTime() {
        val result = OperationalEvidence("report", "VERIFIED", 100_000L, 10L)
        assertEquals(10L, result.eventAt)
        assertEquals(EvidenceState.OBSERVED, result.freshness(100_010L))
        assertEquals(EvidenceState.STALE, result.freshness(130_001L))
    }
    @Test fun clockAnomaliesAndFutureEventsAreErrors() {
        assertEquals(EvidenceState.ERROR, OperationalEvidence("source", "x", 100L).freshness(99L))
        assertEquals(EvidenceState.ERROR, OperationalEvidence("source", "x", 100L, 101L).freshness(101L))
        assertEquals(EvidenceState.UNKNOWN, OperationalEvidence("source", "x", 0L).freshness(100L))
    }
    @Test fun derivedStateDoesNotAcquireUpstreamAuthority() {
        assertEquals(EvidenceState.DERIVED, OperationalEvidence("count", "2", 100L,
            state = EvidenceState.DERIVED).freshness(110L))
    }
}
