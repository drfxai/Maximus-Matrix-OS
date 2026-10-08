package ai.drfx.maximus.matrixai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import android.graphics.Bitmap
import ai.drfx.maximus.matrixai.agent.MaximusMatrixAgent
import ai.drfx.maximus.matrixai.agent.MatrixEvent
import ai.drfx.maximus.matrixai.agent.MatrixEventType
import ai.drfx.maximus.matrixai.data.CompanyDataClient
import ai.drfx.maximus.matrixai.data.DataCenterUiState
import ai.drfx.maximus.matrixai.data.SecureDataCenterStore
import ai.drfx.maximus.matrixai.database.entities.ChatMessageEntity
import ai.drfx.maximus.matrixai.database.entities.MatrixNodeEntity
import ai.drfx.maximus.matrixai.llm.*
import ai.drfx.maximus.matrixai.logging.AppLogStore
import ai.drfx.maximus.matrixai.news.*
import ai.drfx.maximus.matrixai.vision.*

class MatrixViewModel(application: Application) : AndroidViewModel(application) {
    private val agentRuntime = MaximusMatrixAgent(application)
    private val discovery = ApiDiscoveryEngine()
    private val chatClient = LlmChatClient()
    private val apiStore = SecureApiConfigStore(application)
    private val usageStore = ApiUsageStore(application)
    private val dataClient = CompanyDataClient()
    private val dataStore = SecureDataCenterStore(application)
    private val chartVisionEngine = ChartVisionEngine(application, apiStore, usageStore)
    val newsIntelligenceAgent = AiNewsIntelligenceAgent(application, apiStore)

    private val _chartVisionState = MutableStateFlow<ChartVisionUiState>(ChartVisionUiState.Idle)
    val chartVisionState: StateFlow<ChartVisionUiState> = _chartVisionState.asStateFlow()

    private val _activeChartBitmap = MutableStateFlow<Bitmap?>(null)
    val activeChartBitmap: StateFlow<Bitmap?> = _activeChartBitmap.asStateFlow()

    // Room Database & Repositories via Dependency Injection
    private val dbModule = ai.drfx.maximus.matrixai.database.di.DatabaseModule.getInstance(application)
    val graphRepository = dbModule.matrixGraphRepository
    val chatRepository = dbModule.chatHistoryRepository
    val eventRepository = dbModule.matrixEventRepository
    val userSessionRepository = dbModule.userSessionRepository
    val signalRepository = dbModule.tradingSignalRepository

    // Dedicated Webhook Signal Notification Service for High-Confidence Alerts
    val signalNotificationService = ai.drfx.maximus.matrixai.signals.SignalNotificationService(
        context = application,
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    )

    // Real-Time Live Signals Service backed by Room Database
    val liveSignalsService = ai.drfx.maximus.matrixai.signals.LiveSignalsService(
        signalRepository = signalRepository,
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()),
        notificationService = signalNotificationService
    )

    // Real-Time Market Notification Service for Watchlist
    val notificationService = MarketNotificationService(application, userSessionRepository)
    private val _isScanningAlerts = MutableStateFlow(false)
    val isScanningAlerts: StateFlow<Boolean> = _isScanningAlerts.asStateFlow()

    private val _events = MutableStateFlow<List<MatrixEvent>>(emptyList())
    val events: StateFlow<List<MatrixEvent>> = _events.asStateFlow()

    private val _status = MutableStateFlow("READY")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _usage = MutableStateFlow(usageStore.summary())
    val usage: StateFlow<ApiUsageSummary> = _usage.asStateFlow()

    private val initialProvider = apiStore.loadProvider()
    private val initialModel = apiStore.loadModel().ifBlank { initialProvider.defaultModel }
    private val initialBaseUrl = apiStore.loadBaseUrl().ifBlank { initialProvider.defaultBaseUrl }
    private val initialTokenBudget = apiStore.loadMonthlyTokenBudget()
    private val initialCatalog = ApiDiscoveryEngine.defaultCatalog(initialProvider)
    private val initialCapabilities = ModelCapabilityResolver.resolve(initialProvider, initialModel)
    private val initialCapacity = ModelLimitsResolver.contextWindow(initialProvider, initialModel)
    private val initialSummary = usageStore.summary()

    private val _llmState = MutableStateFlow(
        LlmUiState(
            status = if (apiStore.hasApiKey()) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
            provider = initialProvider,
            baseUrl = initialBaseUrl,
            models = initialCatalog,
            selectedModel = initialModel,
            selectedAgentId = apiStore.loadAgentId(),
            supportedAgents = AgentRegistry.supportedAgents(initialCapabilities),
            hasSavedKey = apiStore.hasApiKey(),
            statusMessage = if (apiStore.hasApiKey()) {
                "Connected to ${initialProvider.displayName}. Ready to assist."
            } else {
                "Configure ${initialProvider.displayName} API to begin."
            },
            subscriptionLabel = apiStore.loadSubscriptionLabel(),
            monthlyBudgetUsd = apiStore.loadMonthlyBudgetUsd(),
            monthlyTokenBudget = initialTokenBudget,
            tokenMetrics = TokenMetrics(
                consumedLifetimeTokens = initialSummary.totalTokens,
                contextCapacity = initialCapacity,
                remainingContextTokens = initialCapacity,
                monthlyTokenBudget = initialTokenBudget,
                remainingBudgetTokens = if (initialTokenBudget > 0) (initialTokenBudget - initialSummary.totalTokens).coerceAtLeast(0L) else null
            )
        )
    )
    val llmState: StateFlow<LlmUiState> = _llmState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _dataCenter = MutableStateFlow(DataCenterUiState(baseUrl = dataStore.baseUrl()))
    val dataCenter: StateFlow<DataCenterUiState> = _dataCenter.asStateFlow()

    init {
        // Load persisted chat history from Room
        viewModelScope.launch {
            val savedMessages = chatRepository.getMessages("default_session")
            if (savedMessages.isNotEmpty()) {
                _chatMessages.value = savedMessages.map { entity ->
                    ChatMessage(
                        role = entity.role,
                        content = entity.content,
                        timestampMs = entity.timestampMs,
                        isError = entity.isError,
                        attachment = if (entity.attachmentName != null && entity.attachmentMimeType != null) {
                            ChatAttachment(
                                name = entity.attachmentName,
                                mimeType = entity.attachmentMimeType,
                                data = "",
                                isText = entity.attachmentMimeType.startsWith("text/") || entity.attachmentMimeType == "application/json"
                            )
                        } else null,
                        fromVoice = entity.fromVoice
                    )
                }
            }
        }

        // Load persisted Matrix events from Room
        viewModelScope.launch {
            val savedEvents = eventRepository.getRecentEvents(50)
            if (savedEvents.isNotEmpty() && _events.value.isEmpty()) {
                _events.value = savedEvents.map { e ->
                    MatrixEvent(
                        id = e.id,
                        missionId = e.missionId,
                        type = runCatching { MatrixEventType.valueOf(e.type) }.getOrDefault(MatrixEventType.MISSION_ACCEPTED),
                        sourceNode = e.sourceNode,
                        targetNode = e.targetNode,
                        message = e.message,
                        timestampMs = e.timestampMs
                    )
                }
            }
        }

        // Collect runtime events
        viewModelScope.launch {
            agentRuntime.eventStream.collect { event: MatrixEvent ->
                appendEvent(event)
                _status.value = when (event.type) {
                    MatrixEventType.MISSION_COMPLETED -> "READY"
                    MatrixEventType.MISSION_FAILED -> "ATTENTION"
                    MatrixEventType.CONFIRMATION_REQUIRED -> "CONFIRM"
                    else -> "EXECUTING"
                }
            }
        }

        // Initialize Market Notification Service state
        viewModelScope.launch {
            notificationService.loadSavedState()
        }
    }

    fun scanWatchlistAlertsNow() {
        if (_isScanningAlerts.value) return
        viewModelScope.launch {
            _isScanningAlerts.value = true
            try {
                val currentWatchlist = notificationService.activeWatchlist.value
                val newAlerts = newsIntelligenceAgent.evaluateWatchlistMarketMovingCatalysts(currentWatchlist)
                newAlerts.forEach { alert ->
                    notificationService.addAlert(alert)
                }
                appendEvent(
                    MatrixEvent(
                        missionId = "watchlist-scan-" + UUID.randomUUID().toString(),
                        type = MatrixEventType.MISSION_COMPLETED,
                        sourceNode = "NewsIntelligence",
                        targetNode = "WatchlistAlerts",
                        message = "AI scanned ${currentWatchlist.size} watchlist assets and generated ${newAlerts.size} real-time market-moving alerts.",
                        metadata = mapOf("symbols" to currentWatchlist.joinToString(","))
                    )
                )
            } catch (e: Exception) {
                AppLogStore.warn("WATCHLIST_SCAN", "Error during scan: ${e.message}")
            } finally {
                _isScanningAlerts.value = false
            }
        }
    }

    fun toggleWatchlistSymbol(symbol: String) {
        viewModelScope.launch {
            notificationService.toggleWatchlistAsset(symbol)
        }
    }

    fun setMarketAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationService.setNotificationsEnabled(enabled)
        }
    }

    fun runMission(objective: String) {
        if (_status.value == "EXECUTING" || _status.value == "PLANNING") return
        viewModelScope.launch {
            _status.value = "PLANNING"
            agentRuntime.execute(objective)
        }
    }

    fun applyProviderPreset(provider: LlmProvider) {
        val current = _llmState.value
        val catalog = ApiDiscoveryEngine.defaultCatalog(provider)
        val defaultModel = provider.defaultModel
        val capabilities = ModelCapabilityResolver.resolve(provider, defaultModel)
        val supportedAgents = AgentRegistry.supportedAgents(capabilities)
        val selectedAgent = supportedAgents.firstOrNull { it.id == current.selectedAgentId }?.id
            ?: supportedAgents.firstOrNull()?.id.orEmpty()
        val capacity = ModelLimitsResolver.contextWindow(provider, defaultModel)
        val summary = usageStore.summary()

        val updatedMetrics = current.tokenMetrics.copy(
            contextCapacity = capacity,
            remainingContextTokens = capacity,
            consumedLifetimeTokens = summary.totalTokens,
            remainingBudgetTokens = if (current.monthlyTokenBudget > 0) (current.monthlyTokenBudget - summary.totalTokens).coerceAtLeast(0L) else null
        )

        _llmState.value = current.copy(
            provider = provider,
            baseUrl = provider.defaultBaseUrl,
            models = catalog,
            selectedModel = defaultModel,
            selectedAgentId = selectedAgent,
            supportedAgents = supportedAgents,
            statusMessage = "Switched to ${provider.displayName}. Default model: $defaultModel",
            tokenMetrics = updatedMetrics
        )
        apiStore.save(
            provider.defaultBaseUrl,
            "",
            provider,
            defaultModel,
            selectedAgent,
            current.subscriptionLabel,
            current.monthlyBudgetUsd,
            current.monthlyTokenBudget
        )
    }

    fun detectApi(baseUrl: String, apiKeyInput: String) {
        if (_llmState.value.status == ConnectionStatus.DETECTING) return
        viewModelScope.launch {
            val key = apiKeyInput.ifBlank { apiStore.loadApiKey() }
            _llmState.value = _llmState.value.copy(
                status = ConnectionStatus.DETECTING,
                statusMessage = "Detecting API protocol, models and agent compatibility..."
            )
            AppLogStore.info("API", "API discovery started for " + (if (key.startsWith("nvapi-")) "NVIDIA NIM" else baseUrl))
            appendLlmEvent(MatrixEventType.MODEL_DISCOVERY, "model:discovery", "provider:api", "API discovery started")
            try {
                val result = discovery.discover(baseUrl, key)
                val remembered = apiStore.loadModel()
                val selectedModel = result.models.firstOrNull { it.id == remembered }?.id
                    ?: result.models.firstOrNull { ModelCapability.CHAT in it.capabilities }?.id
                    ?: result.models.firstOrNull()?.id.orEmpty()
                val model = result.models.firstOrNull { it.id == selectedModel }
                val capabilities = model?.capabilities ?: ModelCapabilityResolver.resolve(result.provider, selectedModel)
                val supported = AgentRegistry.supportedAgents(capabilities)
                val rememberedAgent = apiStore.loadAgentId()
                val selectedAgent = supported.firstOrNull { it.id == rememberedAgent }?.id
                    ?: supported.firstOrNull()?.id.orEmpty()

                if (key.isNotBlank()) {
                    apiStore.save(
                        result.baseUrl,
                        key,
                        result.provider,
                        selectedModel,
                        selectedAgent,
                        _llmState.value.subscriptionLabel,
                        _llmState.value.monthlyBudgetUsd,
                        _llmState.value.monthlyTokenBudget
                    )
                }

                val capacity = ModelLimitsResolver.contextWindow(result.provider, selectedModel)
                val summary = usageStore.summary()
                val budget = _llmState.value.monthlyTokenBudget

                val newMetrics = _llmState.value.tokenMetrics.copy(
                    contextCapacity = capacity,
                    remainingContextTokens = capacity,
                    consumedLifetimeTokens = summary.totalTokens,
                    remainingBudgetTokens = if (budget > 0) (budget - summary.totalTokens).coerceAtLeast(0L) else null
                )

                _llmState.value = _llmState.value.copy(
                    status = ConnectionStatus.CONNECTED,
                    provider = result.provider,
                    baseUrl = result.baseUrl,
                    models = result.models,
                    selectedModel = selectedModel,
                    selectedAgentId = selectedAgent,
                    supportedAgents = supported,
                    hasSavedKey = apiStore.hasApiKey(),
                    statusMessage = result.message,
                    tokenMetrics = newMetrics
                )
                AppLogStore.info("API", "API discovery successful: " + result.provider.displayName + " (" + result.models.size + " models)")
                appendLlmEvent(
                    MatrixEventType.MODEL_DISCOVERY,
                    "provider:" + result.provider.name.lowercase(),
                    "model:" + selectedModel,
                    "API detected and model catalog loaded"
                )
            } catch (error: Throwable) {
                // If network fails, keep curated models for the provider so app remains operational
                val fallbackProvider = _llmState.value.provider
                val catalog = ApiDiscoveryEngine.defaultCatalog(fallbackProvider)
                val defModel = fallbackProvider.defaultModel
                _llmState.value = _llmState.value.copy(
                    status = ConnectionStatus.ERROR,
                    models = catalog,
                    selectedModel = defModel,
                    statusMessage = error.message ?: "API detection failed."
                )
                AppLogStore.error("API", error.message ?: "API detection failed")
                appendLlmEvent(MatrixEventType.MODEL_FAILED, "model:discovery", "provider:api", error.message ?: "API detection failed")
            }
        }
    }

    fun selectModel(modelId: String) {
        val current = _llmState.value
        val model = current.models.firstOrNull { it.id == modelId }
        val capabilities = model?.capabilities ?: ModelCapabilityResolver.resolve(current.provider, modelId)
        val agents = AgentRegistry.supportedAgents(capabilities)
        val selectedAgent = agents.firstOrNull { it.id == current.selectedAgentId }?.id
            ?: agents.firstOrNull()?.id.orEmpty()

        val capacity = ModelLimitsResolver.contextWindow(current.provider, modelId)
        val remainingCtx = (capacity - current.tokenMetrics.consumedTurnTotalTokens).coerceAtLeast(0)
        val percent = if (capacity > 0) (current.tokenMetrics.consumedTurnTotalTokens.toFloat() / capacity.toFloat()).coerceIn(0f, 1f) else 0f

        val updatedMetrics = current.tokenMetrics.copy(
            contextCapacity = capacity,
            remainingContextTokens = remainingCtx,
            contextUsagePercent = percent
        )

        _llmState.value = current.copy(
            selectedModel = modelId,
            selectedAgentId = selectedAgent,
            supportedAgents = agents,
            tokenMetrics = updatedMetrics
        )
        apiStore.save(
            current.baseUrl,
            "",
            current.provider,
            modelId,
            selectedAgent,
            current.subscriptionLabel,
            current.monthlyBudgetUsd,
            current.monthlyTokenBudget
        )
    }

    fun selectAgent(agentId: String) {
        val current = _llmState.value
        if (current.supportedAgents.none { it.id == agentId }) return
        _llmState.value = current.copy(selectedAgentId = agentId)
        apiStore.save(
            current.baseUrl,
            "",
            current.provider,
            current.selectedModel,
            agentId,
            current.subscriptionLabel,
            current.monthlyBudgetUsd,
            current.monthlyTokenBudget
        )
    }

    fun saveSubscription(label: String, monthlyBudgetUsd: Double) {
        val current = _llmState.value
        _llmState.value = current.copy(subscriptionLabel = label, monthlyBudgetUsd = monthlyBudgetUsd.coerceAtLeast(0.0))
        apiStore.save(
            current.baseUrl,
            "",
            current.provider,
            current.selectedModel,
            current.selectedAgentId,
            label,
            monthlyBudgetUsd.coerceAtLeast(0.0),
            current.monthlyTokenBudget
        )
    }

    fun saveTokenBudget(budgetTokens: Long) {
        val current = _llmState.value
        val summary = usageStore.summary()
        val validBudget = budgetTokens.coerceAtLeast(0L)
        val remaining = if (validBudget > 0) (validBudget - summary.totalTokens).coerceAtLeast(0L) else null
        val updatedMetrics = current.tokenMetrics.copy(
            monthlyTokenBudget = validBudget,
            remainingBudgetTokens = remaining
        )
        _llmState.value = current.copy(
            monthlyTokenBudget = validBudget,
            tokenMetrics = updatedMetrics
        )
        apiStore.save(
            current.baseUrl,
            "",
            current.provider,
            current.selectedModel,
            current.selectedAgentId,
            current.subscriptionLabel,
            current.monthlyBudgetUsd,
            validBudget
        )
    }

    fun sendChat(text: String, attachment: ChatAttachment? = null, fromVoice: Boolean = false): Boolean {
        val prompt = text.trim().ifBlank {
            if (attachment == null) return false else when {
                attachment.mimeType.startsWith("audio/") -> "Transcribe and respond to this voice message."
                attachment.mimeType.startsWith("image/") -> "Please analyze the attached image."
                else -> "Please analyze the attached document."
            }
        }
        val current = _llmState.value
        if (current.status != ConnectionStatus.CONNECTED || current.selectedModel.isBlank()) {
            appendAssistantError("Connect an API and select a supported model before sending a message.")
            return false
        }
        val selectedAgent = current.supportedAgents.firstOrNull { it.id == current.selectedAgentId }
        if (selectedAgent == null) {
            appendAssistantError("Select an agent supported by the current model.")
            return false
        }
        if (current.isGenerating) return false
        if (attachment != null) {
            val audio = attachment.mimeType.startsWith("audio/")
            val vision = current.models.firstOrNull { it.id == current.selectedModel }
                ?.capabilities?.contains(ModelCapability.VISION) == true ||
                current.provider == LlmProvider.GEMINI || current.provider.isNineRouter
            if (audio && current.provider != LlmProvider.GEMINI && current.provider != LlmProvider.OPENAI) {
                appendAssistantError("Raw voice notes need a Gemini or OpenAI connection. Use Dictate to send speech as text with this provider.")
                return false
            }
            if (!attachment.isText && !audio && !vision) {
                appendAssistantError("The selected model does not advertise image or document vision. Choose a compatible model.")
                return false
            }
            if (attachment.mimeType == "application/pdf" &&
                current.provider != LlmProvider.ANTHROPIC && current.provider != LlmProvider.GEMINI) {
                appendAssistantError("PDF upload is supported with Anthropic or Gemini here. Text files work with all providers.")
                return false
            }
        }

        val userMessage = ChatMessage(role = "user", content = prompt, attachment = attachment, fromVoice = fromVoice)
        _chatMessages.value = _chatMessages.value + userMessage
        _llmState.value = current.copy(isGenerating = true)

        viewModelScope.launch {
            chatRepository.saveMessage(
                ChatMessageEntity(
                    sessionId = "default_session",
                    role = "user",
                    content = prompt,
                    fromVoice = fromVoice,
                    timestampMs = userMessage.timestampMs,
                    attachmentName = attachment?.name,
                    attachmentMimeType = attachment?.mimeType
                )
            )
        }

        appendLlmEvent(
            MatrixEventType.MODEL_STARTED,
            "agent:" + selectedAgent.id,
            "model:" + current.selectedModel,
            selectedAgent.name + " request started"
        )
        AppLogStore.info("CHAT", "Request started provider=" + current.provider.name + " model=" + current.selectedModel + " agent=" + selectedAgent.id)

        viewModelScope.launch {
            try {
                val config = ApiConnectionConfig(
                    baseUrl = current.baseUrl,
                    apiKey = apiStore.loadApiKey(),
                    provider = current.provider,
                    selectedModel = current.selectedModel,
                    selectedAgentId = selectedAgent.id
                )
                val result = chatClient.send(config, _chatMessages.value, selectedAgent)
                if (result.transcript != null) {
                    val index = _chatMessages.value.indexOfLast { it.role == "user" && it.attachment?.mimeType?.startsWith("audio/") == true }
                    if (index >= 0) {
                        _chatMessages.value = _chatMessages.value.toMutableList().also { list ->
                            list[index] = list[index].copy(content = result.transcript)
                        }
                    }
                }
                val assistantMessage = ChatMessage(role = "assistant", content = result.text)
                _chatMessages.value = _chatMessages.value + assistantMessage

                chatRepository.saveMessage(
                    ChatMessageEntity(
                        sessionId = "default_session",
                        role = "assistant",
                        content = result.text,
                        timestampMs = assistantMessage.timestampMs
                    )
                )

                usageStore.record(current.provider, current.selectedModel, result.usage)
                val summary = usageStore.summary()
                _usage.value = summary

                val capacity = ModelLimitsResolver.contextWindow(current.provider, current.selectedModel)
                val remainingCtx = (capacity - result.usage.totalTokens).coerceAtLeast(0)
                val budget = current.monthlyTokenBudget
                val remainingBudget = if (budget > 0) (budget - summary.totalTokens).coerceAtLeast(0L) else null
                val percent = if (capacity > 0) (result.usage.totalTokens.toFloat() / capacity.toFloat()).coerceIn(0f, 1f) else 0f

                val updatedMetrics = current.tokenMetrics.copy(
                    consumedTurnInputTokens = result.usage.inputTokens,
                    consumedTurnOutputTokens = result.usage.outputTokens,
                    consumedTurnTotalTokens = result.usage.totalTokens,
                    consumedSessionTokens = current.tokenMetrics.consumedSessionTokens + result.usage.totalTokens,
                    consumedLifetimeTokens = summary.totalTokens,
                    contextCapacity = capacity,
                    remainingContextTokens = remainingCtx,
                    monthlyTokenBudget = budget,
                    remainingBudgetTokens = remainingBudget,
                    contextUsagePercent = percent
                )

                _llmState.value = _llmState.value.copy(
                    isGenerating = false,
                    tokenMetrics = updatedMetrics
                )
                AppLogStore.info("CHAT", "Response completed model=" + current.selectedModel + " inputTokens=" + result.usage.inputTokens + " outputTokens=" + result.usage.outputTokens + " remainingCtx=" + remainingCtx)
                appendLlmEvent(
                    MatrixEventType.MODEL_COMPLETED,
                    "model:" + current.selectedModel,
                    "agent:" + selectedAgent.id,
                    "Model response completed"
                )
            } catch (error: Throwable) {
                AppLogStore.error("CHAT", error.message ?: "The model request failed.")
                appendAssistantError(error.message ?: "The model request failed.")
                _llmState.value = _llmState.value.copy(isGenerating = false)
                appendLlmEvent(
                    MatrixEventType.MODEL_FAILED,
                    "model:" + current.selectedModel,
                    "agent:" + selectedAgent.id,
                    error.message ?: "Model request failed"
                )
            }
        }
        return true
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
        val current = _llmState.value
        _llmState.value = current.copy(
            tokenMetrics = current.tokenMetrics.copy(
                consumedSessionTokens = 0L,
                consumedTurnTotalTokens = 0,
                consumedTurnInputTokens = 0,
                consumedTurnOutputTokens = 0,
                remainingContextTokens = current.tokenMetrics.contextCapacity,
                contextUsagePercent = 0f
            )
        )
        viewModelScope.launch {
            chatRepository.clearSession("default_session")
        }
    }

    fun disconnectApi() {
        apiStore.clear()
        _llmState.value = LlmUiState()
        appendLlmEvent(MatrixEventType.MODEL_DISCOVERY, "provider:api", "model:none", "API configuration cleared")
    }

    fun clearUsage() {
        usageStore.clear()
        val summary = usageStore.summary()
        _usage.value = summary
        val current = _llmState.value
        val budget = current.monthlyTokenBudget
        _llmState.value = current.copy(
            tokenMetrics = current.tokenMetrics.copy(
                consumedLifetimeTokens = 0L,
                remainingBudgetTokens = if (budget > 0) budget else null
            )
        )
    }

    fun connectDataCenter(baseUrl: String, tokenInput: String) {
        if (_dataCenter.value.busy) return
        viewModelScope.launch {
            val token = tokenInput.ifBlank { dataStore.token() }
            _dataCenter.value = _dataCenter.value.copy(baseUrl = baseUrl, busy = true)
            try {
                val status = dataClient.status(baseUrl, token)
                dataStore.save(baseUrl, token)
                _dataCenter.value = _dataCenter.value.copy(baseUrl = baseUrl, status = status, busy = false)
                AppLogStore.info("DATA", "Company data center connected: " + status.name)
                appendLlmEvent(MatrixEventType.MEMORY_RECALLED, "company:data-center", "knowledge:core", "Company data center connected")
            } catch (error: Throwable) {
                AppLogStore.error("DATA", error.message ?: "Company data center operation failed.")
                _dataCenter.value = _dataCenter.value.copy(
                    busy = false,
                    status = _dataCenter.value.status.copy(
                        connected = false,
                        message = error.message ?: "Company data center connection failed."
                    )
                )
            }
        }
    }

    fun searchDataCenter(query: String) {
        val current = _dataCenter.value
        if (!current.status.connected || current.busy || query.isBlank()) return
        viewModelScope.launch {
            _dataCenter.value = current.copy(busy = true, query = query)
            try {
                val results = dataClient.search(current.baseUrl, dataStore.token(), query)
                _dataCenter.value = _dataCenter.value.copy(searchResults = results, busy = false, query = query)
            } catch (error: Throwable) {
                _dataCenter.value = _dataCenter.value.copy(
                    busy = false,
                    status = _dataCenter.value.status.copy(message = error.message ?: "Search failed.")
                )
            }
        }
    }

    fun disconnectDataCenter() {
        dataStore.clear()
        _dataCenter.value = DataCenterUiState()
    }

    private fun appendAssistantError(message: String) {
        val errorMsg = ChatMessage(
            role = "assistant",
            content = message,
            isError = true
        )
        _chatMessages.value = _chatMessages.value + errorMsg
        viewModelScope.launch {
            chatRepository.saveMessage(
                ChatMessageEntity(
                    sessionId = "default_session",
                    role = "assistant",
                    content = message,
                    isError = true,
                    timestampMs = errorMsg.timestampMs
                )
            )
        }
    }

    fun setActiveChartBitmap(bitmap: Bitmap?) {
        _activeChartBitmap.value = bitmap
        _chartVisionState.value = ChartVisionUiState.Idle
    }

    fun selectPresetChart(preset: SampleChartPreset) {
        val bmp = SampleChartGenerator.generatePresetBitmap(preset)
        _activeChartBitmap.value = bmp
        _chartVisionState.value = ChartVisionUiState.Idle
    }

    fun analyzeActiveChart(notes: String = "") {
        val bitmap = _activeChartBitmap.value ?: return
        val currentModel = _llmState.value.selectedModel.ifBlank { "gemini-3.8-flash" }

        _chartVisionState.value = ChartVisionUiState.Analyzing(
            stage = "Scanning Price Action & Geometric Patterns with Gemini 3.8...",
            progressPercent = 0.35f
        )

        viewModelScope.launch {
            try {
                val result = chartVisionEngine.analyzeChart(
                    bitmap = bitmap,
                    userNotes = notes,
                    modelOverride = currentModel
                )
                _chartVisionState.value = ChartVisionUiState.Success(result)
                _usage.value = usageStore.summary()

                // Update token metrics with vision tokens
                val currentLlm = _llmState.value
                val updatedMetrics = currentLlm.tokenMetrics.copy(
                    consumedTurnInputTokens = result.inputTokens,
                    consumedTurnOutputTokens = result.outputTokens,
                    consumedTurnTotalTokens = result.totalTokens,
                    consumedSessionTokens = currentLlm.tokenMetrics.consumedSessionTokens + result.totalTokens,
                    consumedLifetimeTokens = currentLlm.tokenMetrics.consumedLifetimeTokens + result.totalTokens,
                    remainingContextTokens = (currentLlm.tokenMetrics.contextCapacity.toLong() - (currentLlm.tokenMetrics.consumedSessionTokens + result.totalTokens)).coerceAtLeast(0L).toInt(),
                    remainingBudgetTokens = currentLlm.tokenMetrics.monthlyTokenBudget?.let { budget ->
                        (budget - (currentLlm.tokenMetrics.consumedLifetimeTokens + result.totalTokens)).coerceAtLeast(0L)
                    }
                )
                _llmState.value = currentLlm.copy(tokenMetrics = updatedMetrics)

                appendLlmEvent(
                    MatrixEventType.TOOL_COMPLETED,
                    "vision:chart",
                    "ui:result",
                    "Chart Vision analysis ready: ${result.assetIdentifier} (${result.direction.displayName})"
                )
            } catch (e: Exception) {
                _chartVisionState.value = ChartVisionUiState.Error(
                    message = e.message ?: "Failed to process chart image.",
                    canRetry = true
                )
            }
        }
    }

    fun clearChartVision() {
        _chartVisionState.value = ChartVisionUiState.Idle
        _activeChartBitmap.value = null
    }

    fun saveAnalysisToMatrixGraph(analysis: ChartVisionAnalysis) {
        viewModelScope.launch {
            val nodeId = "chart-vision-" + analysis.id.take(8)
            val node = MatrixNodeEntity(
                id = nodeId,
                label = "Chart: ${analysis.assetIdentifier}",
                groupName = "Vision Intelligence",
                x = (150..650).random().toFloat(),
                y = (150..650).random().toFloat(),
                radius = 24f,
                colorHex = if (analysis.direction == TrendDirection.BULLISH) "#00E676" else "#FF5252",
                description = "${analysis.direction.displayName} | Pattern: ${analysis.patterns.firstOrNull()?.name ?: "Trend Channel"} | R:R ${analysis.tradePlan.riskRewardRatio}",
                relations = "llm:gemini,vision:engine",
                isCustom = true
            )
            graphRepository.saveNode(node)
            appendEvent(
                MatrixEvent(
                    missionId = "graph-" + UUID.randomUUID().toString(),
                    type = MatrixEventType.ARTIFACT_CREATED,
                    sourceNode = "vision:chart",
                    targetNode = nodeId,
                    message = "Created Knowledge Node for ${analysis.assetIdentifier} Chart Analysis"
                )
            )
        }
    }

    fun sendAnalysisToChat(analysis: ChartVisionAnalysis) {
        val attachment = analysis.imageBase64?.let { base64 ->
            ChatAttachment(
                name = "chart_${analysis.assetIdentifier.replace("/", "_")}.jpg",
                mimeType = "image/jpeg",
                data = base64,
                isText = false
            )
        }
        val prompt = buildString {
            append("Here is the Gemini 3.8 AI Chart Vision analysis for ${analysis.assetIdentifier} (${analysis.timeframeEstimate}):\n")
            append("• Trend: ${analysis.direction.displayName} (${analysis.trendStrength})\n")
            if (analysis.patterns.isNotEmpty()) {
                append("• Key Pattern: ${analysis.patterns.first().name} (${analysis.patterns.first().confidencePercent}% confidence)\n")
            }
            if (analysis.trendlines.isNotEmpty()) {
                append("• Trendline: ${analysis.trendlines.first().type} - ${analysis.trendlines.first().description}\n")
            }
            append("• Trade Setup: Entry: ${analysis.tradePlan.entryZone} | SL: ${analysis.tradePlan.stopLoss} | TP: ${analysis.tradePlan.target1} (R:R: ${analysis.tradePlan.riskRewardRatio})\n\n")
            append("Let's review this chart setup together. What additional risk or confirmation signals should I look for before execution?")
        }
        sendChat(text = prompt, attachment = attachment)
    }

    fun appendEvent(event: MatrixEvent) {
        _events.value = (listOf(event) + _events.value).take(150)
        AppLogStore.info("MATRIX", event.type.name + " | " + event.sourceNode + " -> " + (event.targetNode ?: "-") + " | " + event.message)
        viewModelScope.launch {
            eventRepository.recordEvent(event)
        }
    }

    private fun appendLlmEvent(type: MatrixEventType, source: String, target: String, message: String) {
        appendEvent(
            MatrixEvent(
                missionId = "system-" + UUID.randomUUID().toString(),
                type = type,
                sourceNode = source,
                targetNode = target,
                message = message,
                metadata = mapOf("domain" to "matrix")
            )
        )
    }
}
