package ai.drfx.maximus.matrixai.llm

enum class LlmProvider { OPENAI, ANTHROPIC, GEMINI, OPENAI_COMPATIBLE, UNKNOWN }

data class ModelDescriptor(
    val id: String,
    val displayName: String = id,
    val capabilities: Set<String> = emptySet()
)

data class ApiConnectionConfig(
    val baseUrl: String,
    val apiKey: String,
    val provider: LlmProvider,
    val selectedModel: String
)

data class ApiDiscoveryResult(
    val provider: LlmProvider,
    val baseUrl: String,
    val models: List<ModelDescriptor>,
    val message: String
)

data class ChatMessage(
    val role: String,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

enum class ConnectionStatus { DISCONNECTED, DETECTING, CONNECTED, ERROR }

data class LlmUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val provider: LlmProvider = LlmProvider.UNKNOWN,
    val baseUrl: String = "",
    val models: List<ModelDescriptor> = emptyList(),
    val selectedModel: String = "",
    val hasSavedKey: Boolean = false,
    val statusMessage: String = "Configure an API endpoint to begin.",
    val isGenerating: Boolean = false
)
