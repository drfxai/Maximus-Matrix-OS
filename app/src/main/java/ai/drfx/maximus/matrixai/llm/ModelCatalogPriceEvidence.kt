package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Verified provider-catalog evidence, NOT provider quota. Only free routes are currently
 * eligible for automatic dispatch: no user-paid or promotional/unknown pricing is inferred.
 * Pricing schemas vary; this adapter accepts explicit zero-valued evidence for text.
 */
internal object ModelCatalogPriceEvidence {
    fun verifiedFreeTextPricing(model: JSONObject): SmartComboPlanner.Price? {
        val pricing = model.optJSONObject("pricing") ?: return null
        val prompt = parseNonNegative(pricing.opt("prompt")) ?: return null
        val completion = parseNonNegative(pricing.opt("completion")) ?: return null
        if (prompt.signum() != 0 || completion.signum() != 0) return null
        // Explicit nonzero auxiliary fees disqualify a free claim. Missing auxiliary
        // fields are not evidence of chargeable modalities; automatic routes are text-only.
        for (fee in listOf("request", "input_cache_read", "input_cache_write")) {
            if (pricing.has(fee) && parseNonNegative(pricing.opt(fee))?.signum() != 0) return null
        }
        return SmartComboPlanner.Price(0.0, 0.0, verified = true)
    }

    fun verifiedContext(model: JSONObject): Int? {
        val value = model.opt("context_length") ?: return null
        val tokens = when (value) {
            is Number -> runCatching { java.math.BigDecimal(value.toString()).longValueExact() }.getOrNull()
            is String -> value.toLongOrNull()
            else -> null
        } ?: return null
        return tokens.takeIf { it in 512..10_000_000 }?.toInt()
    }

    private fun parseNonNegative(value: Any?): BigDecimal? {
        // A positive fee smaller than Double.MIN_VALUE must never underflow to
        // 0.0 and incorrectly authorize an automatic "free" route.
        val literal = when (value) {
            is Number, is String -> value.toString()
            else -> return null
        }
        return runCatching { BigDecimal(literal) }.getOrNull()
            ?.takeIf { it.signum() >= 0 }
    }
}
