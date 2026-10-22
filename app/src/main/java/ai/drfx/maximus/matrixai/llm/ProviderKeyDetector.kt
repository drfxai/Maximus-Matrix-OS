package ai.drfx.maximus.matrixai.llm

/**
 * Recognizes a pasted API key by its documented prefix and suggests the matching provider and
 * endpoint. A prefix is only a hint: the authenticated catalog request is still the proof.
 * The key itself is never stored or sent anywhere by this object.
 */
object ProviderKeyDetector {
    data class Suggestion(
        val provider: LlmProvider,
        val baseUrl: String,
        /** Service name shown to the user, e.g. "Groq" for an OpenAI-compatible endpoint. */
        val service: String,
        /** True when several services share the prefix, so the user should confirm the endpoint. */
        val ambiguous: Boolean = false
    )

    private data class Rule(val prefix: String, val suggestion: Suggestion)

    // Longest prefixes first so "sk-ant-" and "sk-or-" win over the generic "sk-".
    private val rules = listOf(
        Rule("sk-ant-", Suggestion(LlmProvider.ANTHROPIC, LlmProvider.ANTHROPIC.defaultBaseUrl, "Anthropic")),
        Rule("sk-or-", Suggestion(LlmProvider.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "OpenRouter")),
        Rule("sk-proj-", Suggestion(LlmProvider.OPENAI, LlmProvider.OPENAI.defaultBaseUrl, "OpenAI")),
        Rule("sk-svcacct-", Suggestion(LlmProvider.OPENAI, LlmProvider.OPENAI.defaultBaseUrl, "OpenAI")),
        Rule("sk-admin-", Suggestion(LlmProvider.OPENAI, LlmProvider.OPENAI.defaultBaseUrl, "OpenAI")),
        Rule("AIza", Suggestion(LlmProvider.GEMINI, LlmProvider.GEMINI.defaultBaseUrl, "Google Gemini")),
        Rule("nvapi-", Suggestion(LlmProvider.NVIDIA, LlmProvider.NVIDIA.defaultBaseUrl, "NVIDIA NIM")),
        Rule("gsk_", Suggestion(LlmProvider.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1", "Groq")),
        Rule("xai-", Suggestion(LlmProvider.OPENAI_COMPATIBLE, "https://api.x.ai/v1", "xAI Grok")),
        Rule("pplx-", Suggestion(LlmProvider.OPENAI_COMPATIBLE, "https://api.perplexity.ai", "Perplexity")),
        Rule("csk-", Suggestion(LlmProvider.OPENAI_COMPATIBLE, "https://api.cerebras.ai/v1", "Cerebras")),
        Rule("fw_", Suggestion(LlmProvider.OPENAI_COMPATIBLE, "https://api.fireworks.ai/inference/v1", "Fireworks AI")),
        Rule("hf_", Suggestion(LlmProvider.OPENAI_COMPATIBLE, "https://router.huggingface.co/v1", "Hugging Face")),
        // Plain "sk-" is used by OpenAI legacy keys and by several compatible services (e.g. DeepSeek).
        Rule("sk-", Suggestion(LlmProvider.OPENAI, LlmProvider.OPENAI.defaultBaseUrl, "OpenAI", ambiguous = true))
    )

    fun detect(rawKey: String): Suggestion? {
        val key = rawKey.trim()
        if (key.length < 8 || key.any { it.isWhitespace() || it.isISOControl() }) return null
        return rules.firstOrNull { key.startsWith(it.prefix) }?.suggestion
    }

    /** Whether a key typed for [selected] clearly belongs to another provider's preset. */
    fun mismatch(selected: LlmProvider, rawKey: String): Suggestion? {
        val suggestion = detect(rawKey) ?: return null
        if (suggestion.ambiguous) return null
        if (suggestion.provider == selected) return null
        // Compatible endpoints accept any key format; only flag first-party presets.
        if (selected == LlmProvider.OPENAI_COMPATIBLE && suggestion.provider == LlmProvider.OPENAI_COMPATIBLE) return null
        if (selected.isNineRouter) return null
        return suggestion
    }
}
