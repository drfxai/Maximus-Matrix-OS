package ai.drfx.maximus.matrixai.llm

object ModelLimitsResolver {
    /**
     * Returns the context window capacity (maximum tokens) for the given provider and model.
     */
    fun contextWindow(provider: LlmProvider, modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            // Gemini 3.x / 2.5
            id.contains("gemini-3.1-pro") || id.contains("gemini-2.5-pro") || id.contains("gemini-1.5-pro") -> 2_097_152
            id.contains("gemini-3.8") || id.contains("gemini-3.5") || id.contains("gemini-3.1") ||
                id.contains("gemini-2.5") || id.contains("gemini") -> 1_048_576

            // NVIDIA NIM / Open Source Models
            id.contains("llama-3.3") || id.contains("llama-3.1") -> 131_072
            id.contains("deepseek-r1") || id.contains("deepseek") -> 131_072
            id.contains("nemotron") -> 131_072
            id.contains("qwen2.5") || id.contains("qwen") -> 131_072
            id.contains("mistral-large") || id.contains("mixtral") -> 131_072

            // 9Router
            provider == LlmProvider.ROUTER_9_SMART || id.contains("9router-smart") -> 1_048_576
            provider == LlmProvider.ROUTER_9_COMBO || id.contains("9router-combo") -> 262_144

            // Claude
            id.contains("claude") -> 200_000

            // OpenAI
            id.contains("gpt-4o") || id.contains("o1") || id.contains("o3") -> 128_000
            id.contains("gpt-4-turbo") -> 128_000
            id.contains("gpt-4") -> 8_192
            id.contains("gpt-3.5") -> 16_385

            else -> 128_000
        }
    }
}
