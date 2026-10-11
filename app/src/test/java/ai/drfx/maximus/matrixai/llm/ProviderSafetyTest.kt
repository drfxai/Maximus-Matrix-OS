package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class ProviderSafetyTest {
    private fun rejected(block: () -> Unit) { try { block(); fail("Must reject") } catch (_: IllegalArgumentException) {} }
    @Test fun rejectCleartextUserInfoQueryAndUnexpectedHost() {
        rejected { EndpointPolicy.validate("http://example.com") }
        rejected { EndpointPolicy.validate("https://key@example.com") }
        rejected { EndpointPolicy.validate("https://example.com?key=secret") }
        rejected { EndpointPolicy.validate("https://api.openai.com.attacker.test", LlmProvider.OPENAI) }
        rejected { EndpointPolicy.validate("https://api.openai.com", LlmProvider.GEMINI) }
        EndpointPolicy.validate("https://router.example.com/v1", LlmProvider.ROUTER_9_SMART)
    }
    @Test fun classifyErrorsWithoutReflectingProviderSecrets() {
        assertEquals(ProviderFailure.AUTHENTICATION, ProviderRequestException.fromHttp(401).failure)
        assertEquals(ProviderFailure.FORBIDDEN, ProviderRequestException.fromHttp(403).failure)
        assertEquals(ProviderFailure.RATE_LIMIT_OR_QUOTA, ProviderRequestException.fromHttp(429).failure)
        assertEquals(ProviderFailure.SERVICE_OUTAGE, ProviderRequestException.fromHttp(503).failure)
        assertEquals(ProviderFailure.MODEL_OR_ENDPOINT, ProviderRequestException.fromHttp(404).failure)
        assertEquals(ProviderFailure.REDIRECT, ProviderRequestException.fromHttp(302).failure)
    }
    @Test fun rejectMissingMalformedOversizedAndUnsupportedAttachments() {
        rejected { AttachmentPolicy.validate(LlmProvider.GEMINI, ChatAttachment("x", "image/png", "", false)) }
        rejected { AttachmentPolicy.validate(LlmProvider.GEMINI, ChatAttachment("x", "image/png", "not base64", false)) }
        rejected { AttachmentPolicy.validate(LlmProvider.GEMINI, ChatAttachment("x", "text/plain", "x".repeat(AttachmentPolicy.MAX_BYTES + 1), true)) }
        rejected { AttachmentPolicy.validate(LlmProvider.NVIDIA, ChatAttachment("x", "audio/m4a", "YWJjZA==", false)) }
        rejected { AttachmentPolicy.validate(LlmProvider.OPENAI, ChatAttachment("x", "application/pdf", "YWJjZA==", false)) }
    }
}
