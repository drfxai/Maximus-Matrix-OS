package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class ApiCatalogSessionTest {
    private val model = ModelDescriptor("chat", verified = true, capabilities = setOf(ModelCapability.CHAT))
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
}
