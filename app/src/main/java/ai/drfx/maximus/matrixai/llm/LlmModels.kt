package ai.drfx.maximus.matrixai.llm

enum class LlmProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com", "gemini-2.5-flash"),
    NVIDIA("NVIDIA NIM", "https://integrate.api.nvidia.com/v1", "meta/llama-3.3-70b-instruct"),
    ROUTER_9_SMART("9Router", "https://9router.com/v1", ""),
    ROUTER_9_COMBO("9Router (custom combo)", "https://9router.com/v1", ""),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o"),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1", "claude-3-5-sonnet-20241022"),
    OPENAI_COMPATIBLE("OpenAI Compatible", "https://api.openai.com/v1", "gpt-4o"),
    UNKNOWN("Unknown Provider", "", "");

    val isNineRouter: Boolean get() = this == ROUTER_9_SMART || this == ROUTER_9_COMBO
}

enum class ImageInputSupport { SUPPORTED, UNSUPPORTED, UNKNOWN }

data class ModelDescriptor(
    val id: String,
    val displayName: String = id,
    val capabilities: Set<ModelCapability> = emptySet(),
    val contextWindowTokens: Int = 128_000,
    val verified: Boolean = false,
    val imageInputSupport: ImageInputSupport = ImageInputSupport.UNKNOWN,
    /** Explicit provider-catalog pricing, never a claim about remaining credit or tier quota. */
    val catalogFreeTextPrice: SmartComboPlanner.Price? = null,
    /** Context length is null if the upstream catalog does not declare it. */
    val catalogContextTokens: Int? = null
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
    val contextUsagePercent: Float = 0f,
    /** UI-only approximate output tokens during SSE, never a billed/provider count. */
    val streamingOutputTokensEstimate: Int = 0,
    val streamingEstimateActive: Boolean = false,
    val verifiedRateRequestsRemaining: Long? = null,
    val verifiedRateTokensRemaining: Long? = null,
    val verifiedRequestRateKind: String = "Unknown",
    val rateEvidenceAtEpochMs: Long? = null,
    /** Identity of the provider response carrying this rate-limit snapshot. */
    val rateProvider: LlmProvider? = null,
    val rateModelId: String = "",
    /** True until a provider supplies final usage; context is always a local estimate. */
    val turnUsageEstimated: Boolean = true
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

/** Stable, non-secret identifiers for translating Smart Combo catalog outcomes. */
enum class FreeCatalogCheckKind {
    VERIFIED_FREE, CATALOG_UNAVAILABLE, NO_CHAT_MODEL, NO_ZERO_PRICE,
    MISSING_CONTEXT, NO_ELIGIBLE_MODEL
}

data class FreeCatalogCheckUi(
    val provider: LlmProvider,
    val kind: FreeCatalogCheckKind,
    val count: Int = 0
)

enum class FreeCatalogUiPhase {
    OFF, CHECKING, NO_ACCOUNTS, NONE_VERIFIED, READY, UNAVAILABLE
}

/** Public dispatch metadata only. Never stores endpoint, key, prompt or response. */
data class ChatRequestTelemetry(
    val provider: LlmProvider,
    val modelId: String,
    val agentId: String,
    val agentName: String,
    val startedAtEpochMs: Long,
    val automatic: Boolean
)

data class LlmUiState(
    val apiInputRevision: Long = 0L,
    val catalogCheckedAtEpochMs: Long? = null,
    val inferenceVerifiedModel: String = "",
    val inferenceVerifiedAtEpochMs: Long? = null,
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
    val activeRequest: ChatRequestTelemetry? = null,
    val lastRequest: ChatRequestTelemetry? = null,
    val subscriptionLabel: String = "",
    val monthlyBudgetUsd: Double = 0.0,
    val monthlyTokenBudget: Long = 0L,
    val tokenMetrics: TokenMetrics = TokenMetrics(),
    val smartComboEnabled: Boolean = false,
    val smartComboRefreshing: Boolean = false,
    val smartComboOffers: Int = 0,
    /** Public route labels only; never contains credentials or model response content. */
    val smartComboRouteLabels: List<String> = emptyList(),
    /** Non-secret provider diagnostics: pricing evidence, missing context or failed catalog. */
    val smartComboProviderChecks: List<String> = emptyList(),
    /** Structured evidence for genuinely localized UI; legacy strings remain diagnostics only. */
    val smartComboCatalogChecks: List<FreeCatalogCheckUi> = emptyList(),
    val smartComboUiPhase: FreeCatalogUiPhase = FreeCatalogUiPhase.OFF,
    val smartComboConfiguredProviders: Int = 0,
    val smartComboCheckedAtEpochMs: Long? = null,
    val smartComboMessage: String = "Free-only routing is off."
)
