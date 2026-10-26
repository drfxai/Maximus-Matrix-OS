package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class SmartComboFailoverTest {
    private fun decide(error: Throwable?, timedOut: Boolean = false, text: Boolean = false, tried: Int = 1,
        cancelled: Boolean = false) = SmartComboFailover.decide(error, timedOut, text, tried, cancelled)

    @Test fun quotaOutageAndNetworkFailuresBeforeAnyTextSwitchRoutes() {
        assertEquals(SmartComboFailover.Decision.Switch(SmartComboFailover.Reason.QUOTA),
            decide(ProviderRequestException.fromHttp(429)))
        assertEquals(SmartComboFailover.Decision.Switch(SmartComboFailover.Reason.OUTAGE),
            decide(ProviderRequestException.fromHttp(503)))
        assertEquals(SmartComboFailover.Decision.Switch(SmartComboFailover.Reason.REJECTED),
            decide(ProviderRequestException.fromHttp(404)))
        assertEquals(SmartComboFailover.Decision.Switch(SmartComboFailover.Reason.NETWORK),
            decide(IOException("reset")))
        assertEquals(SmartComboFailover.Decision.Switch(SmartComboFailover.Reason.EMPTY),
            decide(IllegalStateException("Provider stream returned no assistant text.")))
    }

    @Test fun slowFirstTokenWatchdogSwitchesEvenThoughItCancelledTheCall() {
        assertEquals(SmartComboFailover.Decision.Switch(SmartComboFailover.Reason.SLOW),
            decide(CancellationException("watchdog"), timedOut = true))
    }

    @Test fun userStopIsNeverTreatedAsARouteFailure() {
        assertEquals(SmartComboFailover.Decision.Stop(null, false), decide(CancellationException("user")))
        assertEquals(SmartComboFailover.Decision.Stop(null, false),
            decide(CancellationException("user"), timedOut = true, cancelled = true))
    }

    @Test fun textAlreadyShownIsNeverReplayedAutomatically() {
        val decision = decide(IOException("dropped"), text = true)
        assertTrue(decision is SmartComboFailover.Decision.Stop)
        assertTrue((decision as SmartComboFailover.Decision.Stop).partialReply)
    }

    @Test fun failoverStopsAfterTheRouteBudget() {
        assertTrue(decide(ProviderRequestException.fromHttp(429), tried = SmartComboFailover.MAX_ROUTES - 1)
            is SmartComboFailover.Decision.Switch)
        assertEquals(SmartComboFailover.Decision.Stop(SmartComboFailover.Reason.QUOTA, false),
            decide(ProviderRequestException.fromHttp(429), tried = SmartComboFailover.MAX_ROUTES))
    }

    @Test fun onlyStreamingProvidersGetTheFirstTokenWatchdog() {
        assertTrue(SmartComboFailover.watchesFirstToken(LlmProvider.NVIDIA))
        assertTrue(SmartComboFailover.watchesFirstToken(LlmProvider.OPENAI_COMPATIBLE))
        assertFalse(SmartComboFailover.watchesFirstToken(LlmProvider.GEMINI))
    }

    @Test fun noticeNamesBothModelsAndTheReason() {
        assertEquals("Switched to llama-3.3-70b: gemma-2 hit its rate limit or quota.",
            SmartComboFailover.notice("google/gemma-2", "meta/llama-3.3-70b", SmartComboFailover.Reason.QUOTA))
    }
}
