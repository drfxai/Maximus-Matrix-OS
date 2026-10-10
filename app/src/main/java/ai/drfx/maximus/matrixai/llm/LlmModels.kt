package ai.drfx.maximus.matrixai.llm

enum class LlmProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com", "gemini-2.5-flash"),
    NVIDIA("NVIDIA NIM", "https://integrate.api.nvidia.com/v1", "meta/llama-3.3-70b-instruct"),
    ROUTER_9_SMART("9Router", "", ""),
    ROUTER_9_COMBO("9Router (custom combo)", "", ""),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o"),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1", "claude-3-5-sonnet-20241022"),
    OPENAI_COMPATIBLE("OpenAI Compatible", "https://api.openai.com/v1", "gpt-4o"),
    UNKNOWN("Unknown Provider", "", "");

    val isNineRouter: Boolean get() = this == ROUTER_9_SMART || this == ROUTER_9_COMBO
}

data class ModelDescriptor(
    val id: String,
    val displayName: String = id,
    val capabilities: Set<ModelCapability> = emptySet(),
    val contextWindowTokens: Int = 128_000,
    val verified: Boolean = false
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

data class TokenMetrics(
    val consumedTurnInputTokens: Int = 0,
    val consumedTurnOutputTokens: Int = 0,
    val consumedTurnTotalTokens: Int = 0,
    val consumedSessionTokens: Long = 0L,
    val consumedLifetimeTokens: Long = 0L,
    val contextCapacity: Int = 1_048_576,
    val remainingContextTokens: Int = 1_048_576,
    val monthlyTokenBudget: Long = 0L,
    val remainingBudgetTokens: Long? = null,
    val contextUsagePercent: Float = 0f
)

data class ChatCompletionResult(
    val text: String,
    val usage: ChatUsage,
    val transcript: String? = null
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
    val isText: Boolean,
    val durationMs: Long = 0
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONFIGURED,
    VALIDATING,
    DEGRADED,
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
    val monthlyBudgetUsd: Double = 0.0,
    val monthlyTokenBudget: Long = 0L,
    val tokenMetrics: TokenMetrics = TokenMetrics()
)
