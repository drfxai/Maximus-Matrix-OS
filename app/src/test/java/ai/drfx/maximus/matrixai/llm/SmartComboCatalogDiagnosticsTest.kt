package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class SmartComboCatalogDiagnosticsTest {
    @Test fun noConnectedAccountsDoesNotPretendModelsAreFree() {
        val result = SmartComboFreeCatalog.ScanResult(emptyList(), emptyList(), 0)
        assertTrue(result.diagnosis.contains("No configured account"))
    }

    @Test fun configuredNvidiaWithoutPublishedPricingExplainsUnavailableFreeRoute() {
        val result = SmartComboFreeCatalog.ScanResult(
            emptyList(),
            listOf(SmartComboFreeCatalog.ProviderStatus(
                LlmProvider.NVIDIA,
                "No published zero-price text pricing; free tier not proven."
            )),
            1
        )
        assertTrue(result.diagnosis.contains("No catalog-confirmed zero-price"))
        assertTrue(result.checks.single().message.contains("not proven"))
    }

    @Test fun manualModeCanBeChosenButUnknownPricingIsNeverConvertedToFree() {
        val noEvidence = org.json.JSONObject()
            .put("id", "nvidia/nemotron-test")
            .put("context_length", 8192)
        assertNull(ModelCatalogPriceEvidence.verifiedFreeTextPricing(noEvidence))
        val implicitFree = org.json.JSONObject()
            .put("id", "nvidia/nemotron-test")
            .put("pricing", org.json.JSONObject().put("prompt", "0"))
        assertNull(ModelCatalogPriceEvidence.verifiedFreeTextPricing(implicitFree))
    }

    @Test fun eligibleRouteDiagnosisDoesNotClaimConfirmedRemainingQuota() {
        val route = SmartComboPlanner.Route(
            id = "nine-free",
            provider = LlmProvider.ROUTER_9_SMART,
            modelId = "free-model",
            verified = true, healthy = true,
            capabilities = setOf(ModelCapability.CHAT),
            contextWindowTokens = 8192,
            price = SmartComboPlanner.Price(0.0, 0.0, verified = true),
            p95TimeToFirstTokenMs = null,
            quality = 75
        )
        val result = SmartComboFreeCatalog.ScanResult(
            listOf(SmartComboFreeCatalog.Offer(route, "https://9router.com/v1", "nine-router|test")),
            emptyList(), 1
        )
        assertTrue(result.diagnosis.contains("Remaining account quota is unknown"))
    }
}
