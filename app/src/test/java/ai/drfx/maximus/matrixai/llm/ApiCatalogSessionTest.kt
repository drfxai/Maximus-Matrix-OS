package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class ApiCatalogSessionTest {
    private val model = ModelDescriptor("chat", verified = true, capabilities = setOf(ModelCapability.CHAT))
    @Test fun rememberedIdentityCannotDispatchWithoutAuthenticatedCatalog() {
        val session = ApiCatalogSession { 1000 }
        assertFalse(session.permits(session.currentRevision(), LlmProvider.GEMINI,
            LlmProvider.GEMINI.defaultBaseUrl, "fixture-fingerprint", "remembered-gemini"))
    }
    @Test fun catalogRequiresExactCredentialEndpointProviderAndModel() {
        val session = ApiCatalogSession { 1000 }
        val revision = session.invalidate()
        assertTrue(session.bind(revision, LlmProvider.OPENAI_COMPATIBLE, "https://example.test/v1/", "fingerprint", listOf(model)))
        assertTrue(session.permits(revision, LlmProvider.OPENAI_COMPATIBLE, "https://example.test/v1", "fingerprint", "chat"))
        assertFalse(session.permits(revision, LlmProvider.OPENAI_COMPATIBLE, "https://other.test/v1", "fingerprint", "chat"))
        assertFalse(session.permits(revision, LlmProvider.OPENAI_COMPATIBLE, "https://example.test/v1", "rotated", "chat"))
        assertFalse(session.permits(revision, LlmProvider.OPENAI, "https://example.test/v1", "fingerprint", "chat"))
        assertFalse(session.permits(revision, LlmProvider.OPENAI_COMPATIBLE, "https://example.test/v1", "fingerprint", "guessed"))
    }
    @Test fun staleDiscoveryCannotBindAfterInputChange() {
        val session = ApiCatalogSession { 1000 }
        val old = session.invalidate()
        session.invalidate()
        assertFalse(session.bind(old, LlmProvider.OPENAI, "https://api.openai.com/v1", "key", listOf(model)))
        assertFalse(session.isCurrent(old))
    }
    @Test fun modelSelectionRevokesProbeApprovalButKeepsCatalog() {
        val session = ApiCatalogSession { 1000 }
        val old = session.invalidate()
        session.bind(old, LlmProvider.OPENAI, "https://api.openai.com/v1", "key", listOf(model))
        val fresh = session.changeSelection()
        assertFalse(session.permits(old, LlmProvider.OPENAI, "https://api.openai.com/v1", "key", "chat"))
        assertTrue(session.permits(fresh, LlmProvider.OPENAI, "https://api.openai.com/v1", "key", "chat"))
    }
    @Test fun expiredOrFutureCatalogAndUnverifiedSuggestionsAreNotAuthorized() {
        var now = 1000L
        val session = ApiCatalogSession { now }
        val revision = session.invalidate()
        session.bind(revision, LlmProvider.OPENAI, "https://api.openai.com/v1", "key", listOf(model, model.copy(id = "suggestion", verified = false)))
        assertFalse(session.permits(revision, LlmProvider.OPENAI, "https://api.openai.com/v1", "key", "suggestion"))
        now = 999
        assertFalse(session.permits(revision, LlmProvider.OPENAI, "https://api.openai.com/v1", "key", "chat"))
        now = 1001 + ApiCatalogSession.CATALOG_TTL_MS
        assertFalse(session.permits(revision, LlmProvider.OPENAI, "https://api.openai.com/v1", "key", "chat"))
    }
    @Test fun perModelEvidenceSurvivesSelectionButNeverAccountOrCatalogChanges() {
        val session = ApiCatalogSession { 1000L }
        val revision = session.invalidate()
        session.bind(revision, LlmProvider.OPENAI, "https://api.openai.com/v1", "fingerprint", listOf(model, model.copy(id = "other")))
        assertTrue(session.recordProbe(revision, LlmProvider.OPENAI, "https://api.openai.com/v1", "fingerprint", "chat", ModelProbeOutcome.SUCCEEDED))
        val next = session.changeSelection()
        assertTrue(session.recordProbe(next, LlmProvider.OPENAI, "https://api.openai.com/v1", "fingerprint", "other", ModelProbeOutcome.FAILED, ProviderFailure.FORBIDDEN))
        val snapshot = session.probeEvidence()
        assertEquals(ModelProbeOutcome.SUCCEEDED, snapshot["chat"]?.outcome)
        assertEquals(ProviderFailure.FORBIDDEN, snapshot["other"]?.failure)
        session.invalidate()
        assertTrue(session.probeEvidence().isEmpty())
        assertEquals(2, snapshot.size) // callers hold an immutable copy, not the mutable authority
        session.bind(session.currentRevision(), LlmProvider.OPENAI, "https://api.openai.com/v1", "rotated", listOf(model))
        assertTrue(session.probeEvidence().isEmpty())
    }
    @Test fun staleModelAndRotatedCredentialResponsesCannotPublishEvidence() {
        val session = ApiCatalogSession { 1000L }
        val revision = session.invalidate()
        session.bind(revision, LlmProvider.OPENAI, "https://api.openai.com/v1", "fingerprint", listOf(model))
        session.changeSelection()
        assertFalse(session.recordProbe(revision, LlmProvider.OPENAI, "https://api.openai.com/v1", "fingerprint", "chat", ModelProbeOutcome.SUCCEEDED))
        assertFalse(session.recordProbe(session.currentRevision(), LlmProvider.OPENAI, "https://api.openai.com/v1", "rotated", "chat", ModelProbeOutcome.SUCCEEDED))
        assertFalse(session.recordProbe(session.currentRevision(), LlmProvider.OPENAI, "https://api.openai.com/v1", "fingerprint", "guessed", ModelProbeOutcome.SUCCEEDED))
        assertTrue(session.probeEvidence().isEmpty())
    }
    @Test fun expiredCatalogRejectsLateSuccessAndTimeoutEvidence() {
        var now = 1000L
        val session = ApiCatalogSession { now }
        val revision = session.invalidate()
        session.bind(revision, LlmProvider.OPENAI, "https://api.openai.com/v1", "fingerprint", listOf(model))
        now += ApiCatalogSession.CATALOG_TTL_MS + 1
        for (outcome in listOf(ModelProbeOutcome.SUCCEEDED, ModelProbeOutcome.UNCERTAIN)) {
            assertFalse(session.recordProbe(revision, LlmProvider.OPENAI, "https://api.openai.com/v1", "fingerprint", "chat", outcome))
        }
        assertTrue(session.probeEvidence().isEmpty())
    }
    @Test fun probeFreshnessRejectsFutureStaleAndInvalidTimes() {
        val observation = ModelProbeEvidence(ModelProbeOutcome.SUCCEEDED, 1000L)
        assertTrue(observation.isFresh(1000L))
        assertTrue(observation.isFresh(1000L + ApiCatalogSession.CATALOG_TTL_MS))
        assertFalse(observation.isFresh(999L))
        assertFalse(observation.isFresh(1001L + ApiCatalogSession.CATALOG_TTL_MS))
        assertFalse(ModelProbeEvidence(ModelProbeOutcome.SUCCEEDED, Long.MIN_VALUE).isFresh(Long.MAX_VALUE))
    }
}
