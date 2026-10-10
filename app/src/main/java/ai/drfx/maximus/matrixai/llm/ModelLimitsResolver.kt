package ai.drfx.maximus.matrixai.llm

/** Conservative local budgets, always labeled estimates; routing deployments have no universal limit. */
object ModelLimitsResolver {
    fun contextWindow(provider: LlmProvider, modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            provider.isNineRouter -> 8192
            provider == LlmProvider.GEMINI && id.startsWith("gemini-2.5-") -> 1_048_576
            provider == LlmProvider.OPENAI && (id.startsWith("gpt-4o") || id.startsWith("gpt-4.1")) -> 128_000
            provider == LlmProvider.ANTHROPIC && id.startsWith("claude-") -> 200_000
            else -> 8192
        }
    }
}
