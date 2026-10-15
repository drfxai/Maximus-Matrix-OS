package ai.drfx.maximus.matrixai.llm

import ai.drfx.maximus.matrixai.ui.ChatContextPolicy
import org.junit.Assert.*
import org.junit.Test

class ChatTokenTelemetryTest {
    @Test fun completionRetainsTheActualProvidersNewRateSnapshot() {
        val live = TokenMetrics(consumedSessionTokens = 80,
            verifiedRateRequestsRemaining = 7, verifiedRateTokensRemaining = 900,
            rateProvider = LlmProvider.GEMINI, rateModelId = "route-actually-used",
            rateEvidenceAtEpochMs = 123, streamingEstimateActive = true,
            streamingOutputTokensEstimate = 18)
        val done = ChatTokenTelemetry.completed(live, ChatUsage(20, 30, 50, false),
            250, 4096, 1024, 1000)
        assertEquals(LlmProvider.GEMINI, done.rateProvider)
        assertEquals("route-actually-used", done.rateModelId)
        assertEquals(7L, done.verifiedRateRequestsRemaining)
        assertEquals(900L, done.verifiedRateTokensRemaining)
        assertEquals(123L, done.rateEvidenceAtEpochMs)
        assertEquals(130L, done.consumedSessionTokens)
        assertFalse(done.turnUsageEstimated)
        assertFalse(done.streamingEstimateActive)
        assertEquals(0, done.streamingOutputTokensEstimate)
    }

    @Test fun completionUsesTheLatestLocalBudgetAndKeepsEstimatedUsageExplicit() {
        val done = ChatTokenTelemetry.completed(TokenMetrics(monthlyTokenBudget = 5000),
            ChatUsage(30, 10, 40, true), 300, 1024, 2000, 200)
        assertEquals(200L, done.monthlyTokenBudget)
        assertEquals(0L, done.remainingBudgetTokens)
        assertTrue(done.turnUsageEstimated)
        assertEquals(0, done.remainingContextTokens)
        assertNull(done.verifiedRateTokensRemaining)
    }

    @Test fun dispatchShowsActualRouteContextWithoutInventingAccountQuota() {
        val prepared = ChatTokenTelemetry.dispatched(TokenMetrics(contextCapacity = 1_000_000), 4096, 1024)
        assertEquals(4096, prepared.contextCapacity)
        assertEquals(3072, prepared.remainingContextTokens)
        assertEquals(.25f, prepared.contextUsagePercent, .0001f)
        assertNull(prepared.verifiedRateRequestsRemaining)
        assertNull(prepared.verifiedRateTokensRemaining)
        val unknown = ChatTokenTelemetry.dispatched(prepared, 0, 1024)
        assertEquals(0, unknown.remainingContextTokens)
        assertEquals(0f, unknown.contextUsagePercent, .0001f)
    }
    @Test fun completedContextIncludesCapturedMemoryAndSystemBudgetExactlyOnce() {
        val capacity = 4096
        val overhead = 512
        val messages = listOf(ChatMessage("user", "old".repeat(2000)),
            ChatMessage("assistant", "old reply"), ChatMessage("user", "latest request"),
            ChatMessage("assistant", "latest reply"))
        val latest = ChatContextPolicy.bounded(messages, capacity - overhead)
        assertEquals(2, latest.omittedMessages)
        val expected = latest.estimatedTokens + overhead
        val dispatched = ChatTokenTelemetry.dispatched(TokenMetrics(), capacity, expected)
        val done = ChatTokenTelemetry.completed(dispatched, ChatUsage(0, 0, 0, true),
            0, capacity, latest.estimatedTokens, 0, contextOverheadEstimate = overhead)
        assertEquals(capacity - expected, done.remainingContextTokens)
        assertEquals(dispatched.remainingContextTokens, done.remainingContextTokens)
        assertEquals(expected.toFloat() / capacity, done.contextUsagePercent, .0001f)
    }

    @Test fun completedContextClampsCombinedEstimatesWithoutOverflowOrNegativeCredits() {
        val full = ChatTokenTelemetry.completed(TokenMetrics(), ChatUsage(0, 0, 0, true),
            0, 4096, Int.MAX_VALUE, 0, contextOverheadEstimate = Int.MAX_VALUE)
        assertEquals(0, full.remainingContextTokens)
        assertEquals(1f, full.contextUsagePercent, .0001f)
        val empty = ChatTokenTelemetry.completed(TokenMetrics(), ChatUsage(0, 0, 0, true),
            0, 4096, -1, 0, contextOverheadEstimate = -10)
        assertEquals(4096, empty.remainingContextTokens)
    }

}
