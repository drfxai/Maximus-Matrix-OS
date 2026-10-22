package ai.drfx.maximus.matrixai.llm

import kotlin.math.ln

/**
 * Smart Combo routing hints derived from the request text and the catalog model id.
 * These are ESTIMATES used only to order already-verified free routes; they never make a
 * route eligible, never claim a benchmark score and never replace provider evidence.
 */
object SmartComboModelScorer {
    enum class Intent(val label: String) {
        GENERAL("general"),
        CODE("code"),
        REASONING("reasoning"),
        LONG_DOCUMENT("long document"),
        QUICK("quick answer")
    }

    /** Lowest estimate this scorer returns, kept at the planner's default threshold so no route is newly excluded. */
    const val FLOOR = 60
    private const val CEILING = 96

    private val codeSignals = Regex(
        "```|\\b(code|function|class|bug|stack ?trace|exception|compile|refactor|kotlin|java|python|typescript|javascript|" +
            "rust|golang|sql|regex|pine ?script|pinescript|indicator|strategy\\(|api endpoint|unit test)\\b",
        RegexOption.IGNORE_CASE
    )
    private val reasoningSignals = Regex(
        "\\b(why|prove|derive|step[- ]by[- ]step|calculate|probability|optimi[sz]e|backtest|expected value|" +
            "compare|trade-?offs?|analy[sz]e|reason|plan|evaluate)\\b",
        RegexOption.IGNORE_CASE
    )

    fun classify(prompt: String, estimatedInputTokens: Int = 0): Intent {
        val text = prompt.trim()
        if (estimatedInputTokens >= 24_000 || text.length >= 60_000) return Intent.LONG_DOCUMENT
        val code = codeSignals.findAll(text.take(8_000)).count()
        if (code >= 1 && (text.contains("```") || code >= 2)) return Intent.CODE
        val reasoning = reasoningSignals.findAll(text.take(8_000)).count()
        if (reasoning >= 2 || (reasoning >= 1 && text.length > 280)) return Intent.REASONING
        if (code >= 1) return Intent.CODE
        if (text.length <= 80 && !text.contains('\n')) return Intent.QUICK
        return Intent.GENERAL
    }

    private val sizeRegex = Regex("(?<![a-z0-9])(\\d+(?:\\.\\d+)?)\\s*([bm])(?![a-z])", RegexOption.IGNORE_CASE)
    private val expertsRegex = Regex("(\\d+)x(\\d+(?:\\.\\d+)?)b", RegexOption.IGNORE_CASE)

    /** Parameter count in billions when the id states it, e.g. "llama-3.3-70b" or "8x7b". */
    fun parameterBillions(modelId: String): Double? {
        val id = modelId.substringAfterLast('/').lowercase()
        expertsRegex.find(id)?.let { match ->
            return match.groupValues[1].toDouble() * match.groupValues[2].toDouble()
        }
        return sizeRegex.findAll(id).mapNotNull { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            if (match.groupValues[2].equals("m", true)) value / 1000.0 else value
        }.filter { it in 0.05..5_000.0 }.maxOrNull()
    }

    /** Estimated general quality from the model id and verified context length, clamped to [FLOOR]..96. */
    fun quality(modelId: String, contextTokens: Int?): Int {
        val id = modelId.lowercase()
        var score = 70.0
        parameterBillions(id)?.let { billions ->
            // ~+4 per doubling around 8B; 1B ≈ 58, 8B ≈ 70, 70B ≈ 82, 405B ≈ 92.
            score = 70.0 + 5.8 * (ln(billions / 8.0) / ln(2.0))
        }
        when {
            Regex("\\b(opus|ultra|gpt-?5|o3|o4|r1|reasoner|thinking|qwq|k2)\\b").containsMatchIn(id) -> score += 8
            Regex("\\b(pro|sonnet|large|maverick|v3|deepseek-chat|gpt-?4\\.?1|gpt-?4o)\\b").containsMatchIn(id) -> score += 5
        }
        if (Regex("\\b(mini|flash|haiku|small|lite|nano|tiny|instant)\\b").containsMatchIn(id)) score -= 5
        if (Regex("\\b(preview|exp|experimental|beta)\\b").containsMatchIn(id)) score -= 2
        if (Regex("\\b(guard|moderation|safety|classifier|reward)\\b").containsMatchIn(id)) score -= 20
        contextTokens?.let { context ->
            if (context >= 128_000) score += 2
            if (context < 16_000) score -= 4
        }
        return score.toInt().coerceIn(FLOOR, CEILING)
    }

    /** Capabilities a verified catalog context length proves on its own. */
    fun contextCapabilities(contextTokens: Int?): Set<ModelCapability> =
        if (contextTokens != null && contextTokens >= LONG_CONTEXT_TOKENS) setOf(ModelCapability.LONG_CONTEXT) else emptySet()

    const val LONG_CONTEXT_TOKENS = 100_000

    /** Extra ordering weight for how well a model id fits the request intent; 0 when unknown. */
    fun intentFit(modelId: String, intent: Intent, contextTokens: Int?): Double {
        val id = modelId.lowercase()
        return when (intent) {
            Intent.CODE -> when {
                Regex("coder|codestral|code|devstral|starcoder").containsMatchIn(id) -> 30.0
                Regex("\\b(r1|reasoner|qwq|thinking)\\b").containsMatchIn(id) -> 10.0
                else -> 0.0
            }
            Intent.REASONING -> when {
                Regex("\\b(r1|reasoner|qwq|thinking|o3|o4|k2)\\b").containsMatchIn(id) -> 30.0
                Regex("\\b(pro|opus|large)\\b").containsMatchIn(id) -> 10.0
                else -> 0.0
            }
            Intent.LONG_DOCUMENT -> ((contextTokens ?: 0) / 32_000).coerceAtMost(8) * 4.0
            // Short questions should not wait for a slow reasoning model.
            Intent.QUICK -> when {
                Regex("\\b(r1|reasoner|qwq|thinking)\\b").containsMatchIn(id) -> -25.0
                Regex("\\b(flash|mini|instant|lite|haiku|small)\\b").containsMatchIn(id) -> 12.0
                else -> 0.0
            }
            Intent.GENERAL -> 0.0
        }
    }
}
