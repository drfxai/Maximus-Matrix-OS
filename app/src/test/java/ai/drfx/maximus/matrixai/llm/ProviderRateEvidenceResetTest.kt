package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class ProviderRateEvidenceResetTest {
    @Test fun sameProviderAndModelCannotCarryAnotherAccountsRateSnapshot() {
        val original = TokenMetrics(
            verifiedRateRequestsRemaining = 12L,
            verifiedRateTokensRemaining = 900L,
            verifiedRequestRateKind = "RPM",
            rateEvidenceAtEpochMs = 1_000L,
            rateProvider = LlmProvider.OPENAI,
            rateModelId = "same-model")
        val replaced = original.withoutProviderRateEvidence()
        val evidence = SmartComboEvidenceProjection.from(LlmUiState(
            provider = LlmProvider.OPENAI, selectedModel = "same-model",
            tokenMetrics = replaced), 2_000L)
        assertNull(evidence.tokenRate.value)
        assertNull(evidence.requestTokens.value)
        assertEquals(SmartComboEvidenceProjection.Status.UNKNOWN, evidence.tokenRate.status)
        assertNull(replaced.rateEvidenceAtEpochMs)
        assertEquals(LlmProvider.UNKNOWN, replaced.rateProvider)
    }

    @Test fun resettingRemoteEvidencePreservesLocalAccountingAndIsIdempotent() {
        val original = TokenMetrics(contextCapacity = 8192, remainingContextTokens = 6000,
            consumedLifetimeTokens = 12345L, remainingBudgetTokens = 100L,
            verifiedRateTokensRemaining = 800L, rateEvidenceAtEpochMs = 2000L)
        val reset = original.withoutProviderRateEvidence()
        assertEquals(original.contextCapacity, reset.contextCapacity)
        assertEquals(original.remainingContextTokens, reset.remainingContextTokens)
        assertEquals(original.consumedLifetimeTokens, reset.consumedLifetimeTokens)
        assertEquals(original.remainingBudgetTokens, reset.remainingBudgetTokens)
        assertEquals(reset, reset.withoutProviderRateEvidence())
    }
}
