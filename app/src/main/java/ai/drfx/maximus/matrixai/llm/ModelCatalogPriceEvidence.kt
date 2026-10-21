package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject

/**
 * Verified provider-catalog evidence, NOT provider quota. Only free routes are currently
 * eligible for automatic dispatch: no user-paid or promotional/unknown pricing is inferred.
 * Pricing schemas vary; this adapter accepts explicit zero-valued evidence for text.
 */
internal object ModelCatalogPriceEvidence {
    fun verifiedFreeTextPricing(model: JSONObject): SmartComboPlanner.Price? {
        val pricing = model.optJSONObject("pricing") ?: return null
        // Floating-point parsing can underflow a tiny positive fee (e.g. 1e-9999)
        // into 0.0. Require exact decimal zero before admitting an automatic route.
        if (!isExplicitZero(pricing.opt("prompt")) || !isExplicitZero(pricing.opt("completion"))) return null
        // Explicit nonzero auxiliary fees disqualify a free claim. Missing auxiliary
        // fields are not evidence of chargeable modalities; automatic routes are text-only.
        for (fee in listOf("request", "input_cache_read", "input_cache_write")) {
            if (pricing.has(fee) && !isExplicitZero(pricing.opt(fee))) return null
        }
        return SmartComboPlanner.Price(0.0, 0.0, verified = true)
    }

    fun verifiedContext(model: JSONObject): Int? {
        val value = model.opt("context_length") ?: return null
        val tokens = when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        } ?: return null
        return tokens.takeIf { it in 512..10_000_000 }?.toInt()
    }

    private fun isExplicitZero(value: Any?): Boolean {
        val decimal = when (value) {
            is Number -> value.toString()
            is String -> value.trim()
            else -> return false
        }.toBigDecimalOrNull() ?: return false
        return decimal.signum() == 0
    }
}
