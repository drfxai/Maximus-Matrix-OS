package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelCatalogPriceEvidenceTest {
    private fun model(price: String, completion: String, request: String = "0") = JSONObject()
        .put("id", "test/free")
        .put("context_length", 32768)
        .put("pricing", JSONObject().put("prompt", price).put("completion", completion).put("request", request))

    @Test fun zeroPricingAndVerifiedContextPermitFreeTextRoute() {
        val candidate = model("0", "0")
        assertTrue(ModelCatalogPriceEvidence.verifiedFreeTextPricing(candidate)?.isFree == true)
        assertEquals(32768, ModelCatalogPriceEvidence.verifiedContext(candidate))
    }

    @Test fun paidOrPartialPriceMetadataNeverLooksFree() {
        assertNull(ModelCatalogPriceEvidence.verifiedFreeTextPricing(model("0", "0.00001")))
        assertNull(ModelCatalogPriceEvidence.verifiedFreeTextPricing(model("0", "0", "0.01")))
        assertNull(ModelCatalogPriceEvidence.verifiedFreeTextPricing(JSONObject().put("pricing", JSONObject().put("prompt", "0"))))
        assertNull(ModelCatalogPriceEvidence.verifiedFreeTextPricing(model("nan", "0")))
        assertNull(ModelCatalogPriceEvidence.verifiedFreeTextPricing(model("-1", "0")))
        assertNull(ModelCatalogPriceEvidence.verifiedFreeTextPricing(model("0", "Infinity")))
    }

    @Test fun noContextOrImplausibleContextIsUnknown() {
        assertNull(ModelCatalogPriceEvidence.verifiedContext(model("0", "0").put("context_length", 0)))
        assertNull(ModelCatalogPriceEvidence.verifiedContext(model("0", "0").put("context_length", 99999999)))
        assertNull(ModelCatalogPriceEvidence.verifiedContext(JSONObject()))
    }

    @Test fun numericPricingIsAllowedButUnknownExtraFeeIsRejected() {
        val candidate = model("0", "0").put("pricing",
            JSONObject().put("prompt", 0).put("completion", 0).put("input_cache_write", 0.01))
        assertNull(ModelCatalogPriceEvidence.verifiedFreeTextPricing(candidate))
        assertTrue(ModelCatalogPriceEvidence.verifiedFreeTextPricing(
            model("0", "0").put("pricing", JSONObject().put("prompt", 0).put("completion", 0))
        )?.isFree == true)
    }
}
