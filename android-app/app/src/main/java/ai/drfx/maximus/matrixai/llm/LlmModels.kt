package ai.drfx.maximus.matrixai.llm

enum class LlmProvider {
    OPENAI,
    NVIDIA,
    ANTHROPIC,
    GEMINI,
    OPENAI_COMPATIBLE,
    UNKNOWN
}

data class ModelDescriptor(
    val id: String,
    val displayName: String = id,
    val capabilities: Set<ModelCapability> = emptySet()
)

data class ApiConnectionConfig(
    val baseUrl: String,
    val apiKey: String,
    val provider: LlmProvider,
    val selectedModel: String,
    val selectedAgentId: String
)

data class ApiDiscoveryResult(
    val provider: LlmProvider,
    val baseUrl: String,
    val models: List<ModelDescriptor>,
    val message: String
)

data class ChatUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = inputTokens + outputTokens,
    val estimated: Boolean = false
)

data class ChatCompletionResult(
    val text: String,
    val usage: ChatUsage
)

data class ChatMessage(
    val role: String,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val attachment: ChatAttachment? = null,
    val fromVoice: Boolean = false
)

/** Local, ephemeral content. Never written to preferences, diagnostics, or persistent storage. */
data class ChatAttachment(
    val name: String,
    val mimeType: String,
    val data: String,
    val isText: Boolean
)

enum class ConnectionStatus {
    DISCONNECTED,
    DETECTING,
    CONNECTED,
    ERROR
}

data class LlmUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val provider: LlmProvider = LlmProvider.UNKNOWN,
    val baseUrl: String = "",
    val models: List<ModelDescriptor> = emptyList(),
    val selectedModel: String = "",
    val selectedAgentId: String = "general",
    val supportedAgents: List<AgentDescriptor> = emptyList(),
    val hasSavedKey: Boolean = false,
    val statusMessage: String = "Configure an API endpoint to begin.",
    val isGenerating: Boolean = false,
    val subscriptionLabel: String = "",
    val monthlyBudgetUsd: Double = 0.0
)
