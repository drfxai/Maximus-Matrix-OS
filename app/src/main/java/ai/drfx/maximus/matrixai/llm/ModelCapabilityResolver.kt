package ai.drfx.maximus.matrixai.llm

object ModelCapabilityResolver {
    fun resolve(provider: LlmProvider, modelId: String): Set<ModelCapability> {
        val id = modelId.lowercase()
        if (id.contains("embed")) return setOf(ModelCapability.EMBEDDINGS)

        val capabilities = linkedSetOf(ModelCapability.CHAT)

        when (provider) {
            LlmProvider.GEMINI -> {
                if (id.startsWith("gemini-2.5-") || id.startsWith("gemini-2.0-")) {
                    capabilities += ModelCapability.VISION
                    capabilities += ModelCapability.LONG_CONTEXT
                    capabilities += ModelCapability.STRUCTURED_OUTPUT
                }
            }
            LlmProvider.ROUTER_9_SMART, LlmProvider.ROUTER_9_COMBO -> {
                // A routing service does not establish any individual model capability.
                if (id.contains("vision") || id.contains("-vl")) capabilities += ModelCapability.VISION
            }
            LlmProvider.NVIDIA -> {
                if (id.contains("llama") || id.contains("qwen") || id.contains("mistral") || id.contains("nemotron") || id.contains("gpt-oss")) {
                    capabilities += ModelCapability.LONG_CONTEXT
                }
                if (id.contains("gpt-oss") || id.contains("reason") || id.contains("r1") || id.contains("deepseek")) {
                    capabilities += ModelCapability.REASONING
                }
                if (id.contains("vision") || id.contains("vl") || id.contains("vila") || id.contains("neva")) {
                    capabilities += ModelCapability.VISION
                }
                if (id.contains("instruct") || id.contains("nemotron") || id.contains("llama-3.1") || id.contains("llama-3.3") || id.contains("qwen")) {
                    capabilities += ModelCapability.TOOLS
                }
                if (ModelCapability.TOOLS in capabilities) capabilities += ModelCapability.STRUCTURED_OUTPUT
            }
            LlmProvider.OPENAI -> {
                if (id.contains("gpt-4o") || id.contains("gpt-4.1") || id.contains("gpt-5") || id.startsWith("o3") || id.startsWith("o4")) {
                    capabilities += ModelCapability.TOOLS
                    capabilities += ModelCapability.STRUCTURED_OUTPUT
                    capabilities += ModelCapability.LONG_CONTEXT
                }
                if (id.contains("gpt-4o") || id.contains("gpt-4.1") || id.contains("gpt-5")) capabilities += ModelCapability.VISION
                if (id.contains("gpt-5") || id.startsWith("o3") || id.startsWith("o4") || id.startsWith("o1")) capabilities += ModelCapability.REASONING
            }
            LlmProvider.ANTHROPIC -> {
                capabilities += ModelCapability.TOOLS
                capabilities += ModelCapability.VISION
                capabilities += ModelCapability.LONG_CONTEXT
                capabilities += ModelCapability.STRUCTURED_OUTPUT
                if (id.contains("opus") || id.contains("sonnet") || id.contains("claude-4") || id.contains("3-7")) capabilities += ModelCapability.REASONING
            }
            LlmProvider.OPENAI_COMPATIBLE, LlmProvider.UNKNOWN -> {
                when {
                    id.contains("grok") -> {
                        capabilities += ModelCapability.TOOLS
                        capabilities += ModelCapability.LONG_CONTEXT
                        if (id.contains("vision")) capabilities += ModelCapability.VISION
                        if (id.contains("reason")) capabilities += ModelCapability.REASONING
                    }
                    id.contains("qwen") -> {
                        capabilities += ModelCapability.LONG_CONTEXT
                        if (id.contains("vl")) capabilities += ModelCapability.VISION
                    }
                    id.contains("llama") || id.contains("mistral") || id.contains("mixtral") -> capabilities += ModelCapability.LONG_CONTEXT
                    id.contains("deepseek-reasoner") || id.contains("r1") -> {
                        capabilities += ModelCapability.REASONING
                        capabilities += ModelCapability.LONG_CONTEXT
                    }
                }
            }
        }
        return capabilities
    }
}
