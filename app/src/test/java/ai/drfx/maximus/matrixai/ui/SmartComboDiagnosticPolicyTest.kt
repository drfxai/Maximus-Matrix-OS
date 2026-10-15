package ai.drfx.maximus.matrixai.ui

import org.junit.Assert.*
import org.junit.Test

class SmartComboDiagnosticPolicyTest {
    private val policy = SmartComboDiagnosticPolicy

    @Test fun zeroCostOfferAndQuotaRemainDistinct() {
        val parsed = policy.parse("3 verified zero-price catalog routes. Remaining account quota is unknown.")
        assertEquals(SmartComboDiagnosticPolicy.Kind.VERIFIED_ROUTES, parsed.kind)
        assertEquals(3, parsed.count)
    }

    @Test fun nvidiaMissingPriceEvidenceHasDedicatedTranslationCode() {
        val parsed = policy.parse(
            "NVIDIA NIM: No published zero-price text pricing; free tier not proven.")
        assertEquals(SmartComboDiagnosticPolicy.Kind.NO_FREE_PRICE, parsed.kind)
        assertEquals("NVIDIA NIM", parsed.provider)
    }

    @Test fun failedAuthenticationNeverLooksLikeAFreeOffer() {
        assertEquals(
            SmartComboDiagnosticPolicy.Kind.PROVIDER_UNAVAILABLE,
            policy.parse("9Router: Catalog unavailable or authentication failed; no free-pricing claim.").kind
        )
    }

    @Test fun noVerifiedContextAndProviderReadyHaveAccurateCount() {
        val missing = policy.parse("Gemini: 2 free-price model(s), but context length is not verified.")
        assertEquals(SmartComboDiagnosticPolicy.Kind.NO_CONTEXT, missing.kind)
        assertEquals(2, missing.count)
        val ready = policy.parse("OpenAI: 4 verified free text route(s); live quota unknown.")
        assertEquals(SmartComboDiagnosticPolicy.Kind.ELIGIBLE_ROUTES, ready.kind)
        assertEquals(4, ready.count)
    }

    @Test fun unfamiliarServerTextIsNotTranslatedOrTreatedAsVerified() {
        val unknown = "Provider X: user-supplied model name foo/bar"
        val result = policy.parse(unknown)
        assertEquals(SmartComboDiagnosticPolicy.Kind.UNRECOGNIZED, result.kind)
        assertEquals(unknown, result.original)
    }

    @Test fun routingDisabledAndNoAccountAreDifferentSituations() {
        assertEquals(SmartComboDiagnosticPolicy.Kind.DISABLED,
            policy.parse("Free-only routing is off.").kind)
        assertEquals(SmartComboDiagnosticPolicy.Kind.NO_ACCOUNT,
            policy.parse("No configured account found. Add a provider key, or switch off free-only routing.").kind)
        assertEquals(SmartComboDiagnosticPolicy.Kind.NO_FREE_ROUTE,
            policy.parse("No catalog-confirmed zero-price text route. An API key or promotional credits do not prove a model is free.").kind)
    }
}
