package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class SmartComboEvidenceProjectionTest {
    private val now = 1_800_000_000_000L
    private val configured = LlmProvider.OPENAI
    private val actual = LlmProvider.NVIDIA
    private fun dispatch(provider: LlmProvider = actual, model: String = "actual-model") =
        ChatRequestTelemetry(provider, model, "engineer", "Engineer", now - 1_000L, true)
    private fun state(at: Long = now - 10_000L) = LlmUiState(
        provider = configured, selectedModel = "configured-model", smartComboEnabled = true,
        smartComboUiPhase = FreeCatalogUiPhase.READY, smartComboOffers = 2,
        smartComboCheckedAtEpochMs = at, smartComboRouteLabels = listOf("NVIDIA / actual-model"),
        tokenMetrics = TokenMetrics(verifiedRateTokensRemaining = 999L,
            verifiedRateRequestsRemaining = 12L, verifiedRequestRateKind = "RPM",
            rateEvidenceAtEpochMs = at, rateProvider = configured, rateModelId = "configured-model"))

    @Test fun configuredRouteIsNeverPresentedAsActualDispatch() {
        val result = SmartComboEvidenceProjection.from(state().copy(
            isGenerating = true, activeRequest = dispatch()), now)
        assertEquals(actual, result.active?.provider)
        assertEquals(configured, result.configuredProvider)
        assertNull(result.tokenRate.value)
        assertEquals(SmartComboEvidenceProjection.Status.UNKNOWN, result.tokenRate.status)
        assertEquals(SmartComboEvidenceProjection.Phase.DISPATCHED, result.phase)
    }

    @Test fun minuteSnapshotExpiresWithoutAnyStateOrNetworkMutation() {
        val original = state(now - 59_999L)
        assertEquals(999L, SmartComboEvidenceProjection.from(original, now).tokenRate.value)
        val expired = SmartComboEvidenceProjection.from(original, now + 1L)
        assertNull(expired.tokenRate.value)
        assertEquals(SmartComboEvidenceProjection.Status.STALE, expired.tokenRate.status)
        assertEquals(999L, original.tokenMetrics.verifiedRateTokensRemaining)
    }

    @Test fun rpdAndTpmKeepDifferentFreshnessWindows() {
        val result = SmartComboEvidenceProjection.from(state(now - 120_000L).let {
            it.copy(tokenMetrics = it.tokenMetrics.copy(verifiedRequestRateKind = "RPD"))
        }, now)
        assertEquals(12L, result.requestTokens.value)
        assertNull(result.tokenRate.value)
        assertEquals(SmartComboEvidenceProjection.Status.STALE, result.tokenRate.status)
    }

    @Test fun unknownRequestWindowDoesNotBecomeRpm() {
        val original = state()
        val result = SmartComboEvidenceProjection.from(original.copy(
            tokenMetrics = original.tokenMetrics.copy(verifiedRequestRateKind = "Unknown")), now)
        assertNull(result.requestTokens.value)
        assertEquals(SmartComboEvidenceProjection.Status.UNKNOWN, result.requestTokens.status)
    }

    @Test fun futureSnapshotsAndNegativeCountersFailClosed() {
        assertEquals(SmartComboEvidenceProjection.Status.ERROR,
            SmartComboEvidenceProjection.from(state(now + 1), now).tokenRate.status)
        val original = state()
        val result = SmartComboEvidenceProjection.from(original.copy(
            tokenMetrics = original.tokenMetrics.copy(verifiedRateTokensRemaining = -1L)), now)
        assertNull(result.tokenRate.value)
        assertEquals(SmartComboEvidenceProjection.Status.ERROR, result.tokenRate.status)
    }

    @Test fun missingCounterIsUnavailableAndVerifiedZeroRemainsZero() {
        val original = state()
        val missing = SmartComboEvidenceProjection.from(original.copy(
            tokenMetrics = original.tokenMetrics.copy(verifiedRateTokensRemaining = null)), now)
        assertEquals(SmartComboEvidenceProjection.Status.UNAVAILABLE, missing.tokenRate.status)
        val zero = SmartComboEvidenceProjection.from(original.copy(
            tokenMetrics = original.tokenMetrics.copy(verifiedRateTokensRemaining = 0L)), now)
        assertEquals(0L, zero.tokenRate.value)
        assertEquals(SmartComboEvidenceProjection.Status.OBSERVED, zero.tokenRate.status)
    }

    @Test fun staleCatalogHidesCandidateCountAndLabels() {
        val result = SmartComboEvidenceProjection.from(state(
            now - SmartComboEvidenceProjection.CATALOG_EVIDENCE_TTL_MS), now)
        assertNull(result.catalog.value)
        assertEquals(SmartComboEvidenceProjection.Status.STALE, result.catalog.status)
        assertTrue(result.candidateLabels.isEmpty())
    }

    @Test fun failedCatalogDoesNotBecomeVerifiedZeroCandidates() {
        val result = SmartComboEvidenceProjection.from(state().copy(
            smartComboUiPhase = FreeCatalogUiPhase.UNAVAILABLE, smartComboOffers = 0), now)
        assertNull(result.catalog.value)
        assertEquals(SmartComboEvidenceProjection.Status.UNAVAILABLE, result.catalog.status)
        assertTrue(result.candidateLabels.isEmpty())
    }

    @Test fun idleCannotReuseAnOldActiveRequestAsCurrentActivity() {
        val result = SmartComboEvidenceProjection.from(state().copy(isGenerating = false,
            activeRequest = dispatch(), lastRequest = dispatch()), now)
        assertNull(result.active)
        assertNotNull(result.lastDispatch)
        assertNull(result.elapsedSinceDispatchMs)
        assertEquals(SmartComboEvidenceProjection.Phase.IDLE, result.phase)
    }

    @Test fun preparingAndStreamingAreDerivedFromRealDispatchAndOutputEvidence() {
        val original = state().copy(isGenerating = true)
        assertEquals(SmartComboEvidenceProjection.Phase.PREPARING,
            SmartComboEvidenceProjection.from(original, now).phase)
        val streaming = original.copy(activeRequest = dispatch(),
            tokenMetrics = original.tokenMetrics.copy(
                streamingEstimateActive = true, streamingOutputTokensEstimate = 4))
        val result = SmartComboEvidenceProjection.from(streaming, now)
        assertEquals(SmartComboEvidenceProjection.Phase.STREAMING, result.phase)
        assertEquals(1_000L, result.elapsedSinceDispatchMs)
    }
}
