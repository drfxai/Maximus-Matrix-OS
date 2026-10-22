package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject

/** Protocol serialization shared by streaming and nonstreaming compatible requests. */
internal object OutputTokenLimit {
    fun apply(body: JSONObject, provider: LlmProvider, limit: Int?) {
        if (limit == null) return
        require(limit in 1..SmartComboModelScorer.MAX_OUTPUT_TOKENS)
        body.put(if (provider == LlmProvider.OPENAI) "max_completion_tokens" else "max_tokens", limit)
    }

    private val legacyAnthropic = Regex("claude-(instant|2|3-(haiku|opus|sonnet))", RegexOption.IGNORE_CASE)

    /**
     * Explicit cap for a directly selected model. Only Anthropic needs one (its API requires
     * max_tokens; the default stays 4096): code gets 8192 on models that accept it. Other
     * providers keep their own default, which is already unbounded by this app.
     */
    fun directCap(provider: LlmProvider, modelId: String, intent: SmartComboModelScorer.Intent): Int? =
        if (provider == LlmProvider.ANTHROPIC && intent in setOf(SmartComboModelScorer.Intent.CODE,
                SmartComboModelScorer.Intent.LONG_DOCUMENT) && !legacyAnthropic.containsMatchIn(modelId)) 8_192
        else null
}
