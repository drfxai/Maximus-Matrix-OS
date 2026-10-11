package ai.drfx.maximus.matrixai.llm

/** Reject recognizable foreign-provider keys before transmission; unknown formats are verified by the provider. */
internal object CredentialPolicy {
    fun compatible(provider: LlmProvider, key: String): Boolean {
        val owner = when {
            key.startsWith("AIza") -> LlmProvider.GEMINI
            key.startsWith("nvapi-") -> LlmProvider.NVIDIA
            key.startsWith("sk-ant-") -> LlmProvider.ANTHROPIC
            key.startsWith("sk-proj-") || key.startsWith("sk-svcacct-") -> LlmProvider.OPENAI
            else -> return true
        }
        return provider == owner || provider == LlmProvider.OPENAI_COMPATIBLE
    }
    fun validate(provider: LlmProvider, key: String) {
        require(compatible(provider, key)) { "Credential format belongs to another provider. Enter this provider's own key." }
    }
}
