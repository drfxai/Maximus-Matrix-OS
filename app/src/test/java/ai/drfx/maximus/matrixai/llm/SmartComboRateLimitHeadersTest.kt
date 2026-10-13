package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class SmartComboRateLimitHeadersTest {
    private val pairs = mapOf(
        "x-ratelimit-limit-requests" to "14400",
        "x-ratelimit-remaining-requests" to "14370",
        "x-ratelimit-limit-tokens" to "18000",
        "x-ratelimit-remaining-tokens" to "17997"
    )

    @Test fun groqRequestLimitIsDailyNotMinuteAndTokensAreMinute() {
        val windows = SmartComboRateLimitHeaders.parse(
            LlmProvider.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1", pairs::get, 1000L)
        assertEquals(2, windows.size)
        assertEquals(SmartComboPlanner.LimitKind.REQUESTS_PER_DAY, windows[0].kind)
        assertEquals(14370L, windows[0].remaining)
        assertEquals(SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_MINUTE, windows[1].kind)
        assertEquals(17997L, windows[1].remaining)
    }

    @Test fun openAiRequestRateIsMinuteAndOtherHostsAreUnknown() {
        val direct = SmartComboRateLimitHeaders.parse(
            LlmProvider.OPENAI, "https://api.openai.com/v1", pairs::get, 1000L)
        assertEquals(SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE, direct[0].kind)
        assertTrue(SmartComboRateLimitHeaders.parse(LlmProvider.ROUTER_9_COMBO,
            "https://9router.com/v1", pairs::get).isEmpty())
        assertTrue(SmartComboRateLimitHeaders.parse(LlmProvider.OPENAI_COMPATIBLE,
            "https://example.com/v1", pairs::get).isEmpty())
    }

    @Test fun malformedOrInconsistentQuotaIsNeverTrusted() {
        val modified = pairs + ("x-ratelimit-remaining-requests" to "999999")
        val windows = SmartComboRateLimitHeaders.parse(LlmProvider.OPENAI,
            "https://api.openai.com/v1", modified::get)
        assertEquals(1, windows.size)
        assertEquals(SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_MINUTE, windows.single().kind)
    }
}
