package ai.drfx.maximus.matrixai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import ai.drfx.maximus.matrixai.database.entities.ChatSessionEntity
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
import ai.drfx.maximus.matrixai.memory.SharedProjectMemory
import ai.drfx.maximus.matrixai.logging.AppLogStore
import ai.drfx.maximus.matrixai.news.*
import ai.drfx.maximus.matrixai.vision.*

class MatrixViewModel(application: Application) : AndroidViewModel(application) {
    private val agentRuntime = MaximusMatrixAgent(application)
    val pendingAgentConfirmation = agentRuntime.pendingConfirmation
    fun approveAgentAction(requestId: String, approved: Boolean) = agentRuntime.approveAction(requestId, approved)
    private val discovery = ApiDiscoveryEngine()
    private val chatClient = LlmChatClient()
    private val apiStore = SecureApiConfigStore(application)
    private val usageStore = ApiUsageStore(application)
    private val freeCatalog = SmartComboFreeCatalog(discovery, apiStore)
    private val freeExecutor = SmartComboFreeExecutor()
    private val freeRouteHealth = ai.drfx.maximus.matrixai.llm.SmartComboRouteHealth()
    private val sharedProjectMemory = SharedProjectMemory(application)
    private var freeOffers: List<SmartComboFreeCatalog.Offer> = emptyList()
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
            status = if (apiStore.hasKeyForProvider(initialProvider)) ConnectionStatus.CONFIGURED else ConnectionStatus.DISCONNECTED,
            provider = initialProvider,
            baseUrl = initialBaseUrl,
            models = initialCatalog,
            selectedModel = initialModel,
            selectedAgentId = apiStore.loadAgentId(),
            supportedAgents = AgentRegistry.supportedAgents(initialCapabilities),
            hasSavedKey = apiStore.hasKeyForProvider(initialProvider),
            statusMessage = if (apiStore.hasApiKey()) {
                "Credentials stored for ${initialProvider.displayName}; inference not verified."
            } else {
                "Configure ${initialProvider.displayName} API to begin."
            },
            subscriptionLabel = apiStore.loadSubscriptionLabel(),
            monthlyBudgetUsd = apiStore.loadMonthlyBudgetUsd(),
            monthlyTokenBudget = initialTokenBudget,
            smartComboEnabled = apiStore.smartComboEnabled(),
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

    private var freeRefreshLoop: Job? = null
    private var chatJob: Job? = null
    private var chatGeneration = 0L
    private var validationJob: Job? = null
    private var restoreJob: Job? = null
    private var historyReady = false
    private var streamingTimestamp: Long? = null
    private val sessionPreferences = application.getSharedPreferences("matrix_chat_sessions", android.content.Context.MODE_PRIVATE)
    private val _activeChatSessionId = MutableStateFlow(sessionPreferences.getString("active", "legacy_unassigned").orEmpty())
    val activeChatSessionId: StateFlow<String> = _activeChatSessionId.asStateFlow()
    private val _chatSessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
    val chatSessions: StateFlow<List<ChatSessionEntity>> = _chatSessions.asStateFlow()

    init {
        viewModelScope.launch { chatRepository.sessions.collect { sessions -> _chatSessions.value = sessions.map { if (it.id == "default_session") it.copy(title = "Legacy history (read only)", provider = "UNKNOWN") else it } } }
        restoreChatSession(_activeChatSessionId.value)
        if (apiStore.smartComboEnabled()) scheduleSmartComboRefresh()
        refreshProjectMemoriesInBackground()


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

    private fun scheduleSmartComboRefresh() {
        if (freeRefreshLoop?.isActive == true) return
        freeRefreshLoop = viewModelScope.launch {
            while (_llmState.value.smartComboEnabled) {
                refreshSmartCombo()
                kotlinx.coroutines.delay(4 * 60_000L)
            }
        }
    }

    /** Explicit opt-in. Only provider-declared, authenticated zero-price text models qualify. */
    fun setSmartComboEnabled(enabled: Boolean) {
        if (_llmState.value.isGenerating) return
        apiStore.setSmartComboEnabled(enabled)
        if (!enabled) {
            freeRefreshLoop?.cancel()
            freeRefreshLoop = null
            freeOffers = emptyList()
        }
        _llmState.value = _llmState.value.copy(
            smartComboEnabled = enabled,
            smartComboOffers = if (enabled) freeOffers.size else 0,
            smartComboRouteLabels = if (enabled) _llmState.value.smartComboRouteLabels else emptyList(),
            smartComboProviderChecks = if (enabled) _llmState.value.smartComboProviderChecks else emptyList(),
            smartComboCheckedAtEpochMs = if (enabled) _llmState.value.smartComboCheckedAtEpochMs else null,
            smartComboMessage = if (enabled) "Checking free model catalogues…" else "Free-only routing is off."
        )
        if (enabled) scheduleSmartComboRefresh()
    }

    fun refreshSmartCombo() {
        if (!_llmState.value.smartComboEnabled || _llmState.value.smartComboRefreshing) return
        viewModelScope.launch {
            _llmState.value = _llmState.value.copy(smartComboRefreshing = true, smartComboMessage = "Verifying free model offers…")
            try {
                val report = freeCatalog.refreshDetailed()
                if (!_llmState.value.smartComboEnabled) return@launch
                val refreshed = report.offers
                freeOffers = refreshed
                val checkedAt = System.currentTimeMillis()
                // Human-readable non-secret catalog evidence; free price does not prove live account quota.
                val routes = refreshed.take(8).map { offer ->
                    offer.route.provider.displayName.take(25) + " · " +
                        offer.route.modelId.take(55) + " · zero catalog price"
                }
                _llmState.value = _llmState.value.copy(
                    smartComboOffers = freeOffers.size,
                    smartComboRouteLabels = routes,
                    smartComboProviderChecks = report.checks.take(8).map { status ->
                        status.provider.displayName + ": " + status.message
                    },
                    smartComboCheckedAtEpochMs = checkedAt,
                    smartComboMessage = report.diagnosis
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!_llmState.value.smartComboEnabled) return@launch
                freeOffers = emptyList()
                _llmState.value = _llmState.value.copy(smartComboOffers = 0,
                    smartComboRouteLabels = emptyList(), smartComboProviderChecks = emptyList(),
                    smartComboCheckedAtEpochMs = null,
                    smartComboMessage = "Free catalogue unavailable. No automatic paid fallback.")
            } finally {
                _llmState.value = _llmState.value.copy(smartComboRefreshing = false)
            }
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
                        message = "Watchlist refresh: ${newAlerts.size} verified alerts. Automated market triggers require a configured data integration.",
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

    fun runMission(objective: String) = executeMission(objective, false)

    private fun executeMission(objective: String, reportToChat: Boolean) {
        if (_status.value in setOf("EXECUTING", "PLANNING", "CONFIRM")) return
        val sessionId = _activeChatSessionId.value
        viewModelScope.launch {
            _status.value = "PLANNING"
            val result = agentRuntime.execute(objective)
            if (reportToChat) {
                val message = ChatMessage("assistant", "Runtime execution evidence:\n" + result.summary, isError = !result.success)
                chatRepository.saveMessage(ChatMessageEntity(sessionId = sessionId, role = "assistant", content = message.content, isError = message.isError, timestampMs = message.timestampMs))
                if (_activeChatSessionId.value == sessionId) _chatMessages.value += message
            }
        }
    }

    fun applyProviderPreset(provider: LlmProvider) {
        cancelChat()
        validationJob?.cancel()
        val current = _llmState.value
        val catalog = ApiDiscoveryEngine.defaultCatalog(provider)
        val defaultModel = apiStore.loadModelForProvider(provider).ifBlank { provider.defaultModel }
        val providerEndpoint = apiStore.loadBaseUrlForProvider(provider).ifBlank { provider.defaultBaseUrl }
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
            status = if (apiStore.hasKeyForProvider(provider)) ConnectionStatus.CONFIGURED else ConnectionStatus.DISCONNECTED,
            hasSavedKey = apiStore.hasKeyForProvider(provider),
            provider = provider,
            baseUrl = providerEndpoint,
            models = catalog,
            selectedModel = defaultModel,
            selectedAgentId = selectedAgent,
            supportedAgents = supportedAgents,
            statusMessage = "Switched to ${provider.displayName}. Default model: $defaultModel",
            tokenMetrics = updatedMetrics
        )
        createChatSession()
        apiStore.save(
            providerEndpoint,
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
        if (_llmState.value.status in setOf(ConnectionStatus.DETECTING, ConnectionStatus.VALIDATING)) return
        cancelChat()
        val requestedState = _llmState.value
        validationJob = viewModelScope.launch {
            val requestedProvider = requestedState.provider
            var catalogVerified = false
            _llmState.value = _llmState.value.copy(
                status = ConnectionStatus.DETECTING,
                statusMessage = "Detecting API protocol, models and agent compatibility..."
            )
            AppLogStore.info("API", "API discovery started")
            appendLlmEvent(MatrixEventType.MODEL_DISCOVERY, "model:discovery", "provider:api", "API discovery started")
            try {
                val key = apiKeyInput.ifBlank { apiStore.resolveCredential(requestedProvider, baseUrl) }
                val result = discovery.discover(baseUrl, key, requestedProvider)
                require(result.provider == requestedProvider || requestedProvider == LlmProvider.UNKNOWN) { "Endpoint protocol differs from selected provider. Select the matching provider and enter its credentials." }
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
                    status = ConnectionStatus.CONFIGURED,
                    provider = result.provider,
                    baseUrl = result.baseUrl,
                    models = result.models,
                    selectedModel = selectedModel,
                    selectedAgentId = selectedAgent,
                    supportedAgents = supported,
                    hasSavedKey = apiStore.hasKeyForProvider(result.provider),
                    statusMessage = result.message + " Catalog verified; inference not yet tested.",
                    tokenMetrics = newMetrics
                )
                catalogVerified = true
                if (requestedState.provider != result.provider || requestedState.selectedModel != selectedModel || requestedState.baseUrl != result.baseUrl) createChatSession()
                _llmState.value = _llmState.value.copy(status = ConnectionStatus.VALIDATING, statusMessage = "Testing authenticated inference…")
                chatClient.send(
                    ApiConnectionConfig(result.baseUrl, key, result.provider, selectedModel, selectedAgent),
                    listOf(ChatMessage("user", "Reply only OK.")),
                    supported.firstOrNull { it.id == selectedAgent } ?: error("No chat persona available")
                ).also { probe ->
                    usageStore.record(result.provider, selectedModel, probe.usage)
                    _usage.value = usageStore.summary()
                    _llmState.value = _llmState.value.copy(tokenMetrics = _llmState.value.tokenMetrics.copy(consumedLifetimeTokens = _usage.value.totalTokens))
                }
                _llmState.value = _llmState.value.copy(status = ConnectionStatus.CONNECTED, statusMessage = "Authenticated inference verified for $selectedModel.")
                AppLogStore.info("API", "API discovery successful: " + result.provider.displayName + " (" + result.models.size + " models)")
                appendLlmEvent(
                    MatrixEventType.MODEL_DISCOVERY,
                    "provider:" + result.provider.name.lowercase(),
                    "model:" + selectedModel,
                    "API detected and model catalog loaded"
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // A verified catalog does not make failed inference healthy.
                _llmState.value = _llmState.value.copy(
                    status = if (catalogVerified) ConnectionStatus.DEGRADED else ConnectionStatus.ERROR,
                    models = if (catalogVerified) _llmState.value.models else emptyList(),
                    selectedModel = if (catalogVerified) _llmState.value.selectedModel else "",
                    statusMessage = error.message ?: "API detection failed."
                )
                AppLogStore.error("API", error.message ?: "API detection failed")
                appendLlmEvent(MatrixEventType.MODEL_FAILED, "model:discovery", "provider:api", error.message ?: "API detection failed")
            }
        }
    }

    private fun refreshProjectMemoriesInBackground() {
        if (!sharedProjectMemory.enabled() || sharedProjectMemory.activeProject().isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { sharedProjectMemory.refresh() } catch (_: Exception) { /* Local continuity is the fallback. */ }
        }
    }

    fun selectModel(modelId: String) {
        // New-model recall starts before the next user input, without blocking UI.
        refreshProjectMemoriesInBackground()
        if (_llmState.value.selectedModel != modelId) { cancelChat(); createChatSession(modelId) }
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
            status = ConnectionStatus.CONFIGURED,
            statusMessage = "Selected model; inference not yet verified.",
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
        if (text.trim().startsWith("/mission ")) { executeMission(text.trim().removePrefix("/mission "), true); return true }
        val prompt = text.trim().ifBlank {
            if (attachment == null) return false else when {
                attachment.mimeType.startsWith("audio/") -> "Transcribe and respond to this voice message."
                attachment.mimeType.startsWith("image/") -> "Please analyze the attached image."
                else -> "Please analyze the attached document."
            }
        }
        val current = _llmState.value
        val memoryProjectAtRequest = sharedProjectMemory.activeProject()
        if (!historyReady || current.status !in setOf(ConnectionStatus.CONNECTED, ConnectionStatus.CONFIGURED, ConnectionStatus.DEGRADED) || current.selectedModel.isBlank()) {
            appendAssistantError("Connect an API and select a supported model before sending a message.")
            return false
        }
        val selectedAgent = current.supportedAgents.firstOrNull { it.id == current.selectedAgentId }
        if (selectedAgent == null) {
            appendAssistantError("Select an agent supported by the current model.")
            return false
        }
        if (current.isGenerating) return false
        if (current.smartComboEnabled) {
            if (attachment != null) {
                appendAssistantError("Free-only Smart Combo currently supports text. Disable it before choosing a verified multimodal route.")
                return false
            }
            if (freeOffers.isEmpty()) {
                appendAssistantError(
                    if (current.smartComboRefreshing)
                        "Smart Combo is still checking provider catalogs. Wait for verification or explicitly switch to your selected provider."
                    else
                        "No catalog-confirmed free route. Refresh Smart Combo or choose 'Use selected provider' to disable free-only routing; the provider may charge."
                )
                return false
            }
        }
        if (attachment != null) {
            val audio = attachment.mimeType.startsWith("audio/")
            val descriptor = current.models.firstOrNull { it.id == current.selectedModel }
            val vision = descriptor?.capabilities?.contains(ModelCapability.VISION) == true
            // An authenticated 9Router catalog proves the route exists, but not its image support.
            // The user's explicit image send may try an unknown route, never a known text-only route.
            val routerImageAttempt = attachment.mimeType.startsWith("image/") &&
                ModelImagePolicy.canAttemptImage(current.provider, descriptor)
            if (audio && current.provider != LlmProvider.GEMINI && current.provider != LlmProvider.OPENAI) {
                appendAssistantError("Raw voice notes need a Gemini or OpenAI connection. Use Dictate to send speech as text with this provider.")
                return false
            }
            if (!attachment.isText && !audio && !vision && !routerImageAttempt) {
                appendAssistantError("Image input is unsupported or unverified for this model. Select a vision model; authenticated 9Router routes with unknown image support can be tried explicitly.")
                return false
            }
            if (attachment.mimeType == "application/pdf" &&
                current.provider != LlmProvider.ANTHROPIC && current.provider != LlmProvider.GEMINI) {
                appendAssistantError("PDF upload is supported with Anthropic or Gemini here. Text files work with all providers.")
                return false
            }
        }

        if (_activeChatSessionId.value == "legacy_unassigned") createChatSession()
        val requestGeneration = ++chatGeneration
        val requestSessionId = _activeChatSessionId.value
        val userMessage = ChatMessage(role = "user", content = prompt, attachment = attachment, fromVoice = fromVoice)
        _chatMessages.value = _chatMessages.value + userMessage
        // Capture the project at user-send time: a workspace switch must never inject
        // another project's history into a request that is already in flight.
        val memoryEvidenceAtRequest = sharedProjectMemory.cachedContext()
        val memoryAndSystemBudget = ChatContextPolicy.estimateTokens(memoryEvidenceAtRequest) +
            ChatContextPolicy.estimateTokens(selectedAgent.systemPrompt)
        val modelContext = ModelLimitsResolver.contextWindow(current.provider, current.selectedModel)
        val smallestFreeContext = if (current.smartComboEnabled)
            freeOffers.mapNotNull { it.route.contextWindowTokens }.minOrNull() ?: modelContext
        else modelContext
        val effectiveWindow = (minOf(modelContext, smallestFreeContext) -
            memoryAndSystemBudget).coerceAtLeast(1024)
        val selectedContext = ChatContextPolicy.bounded(_chatMessages.value, effectiveWindow)
        if (selectedContext.messages.lastOrNull() != userMessage) {
            appendAssistantError("This request exceeds the estimated model context limit. Reduce its size.")
            return false
        }
        _llmState.value = current.copy(isGenerating = true, tokenMetrics = current.tokenMetrics.copy(
            streamingEstimateActive = false,
            streamingOutputTokensEstimate = 0,
            verifiedRateRequestsRemaining = null,
            verifiedRateTokensRemaining = null,
            rateEvidenceAtEpochMs = null
        ), statusMessage = if (selectedContext.omittedMessages > 0) "Older context omitted to fit the estimated limit; no automatic summary." else current.statusMessage)

        chatJob = viewModelScope.launch {
            var autoPrepared: SmartComboFreeExecutor.Prepared? = null
            try {
            chatRepository.saveMessage(
                ChatMessageEntity(
                    sessionId = requestSessionId,
                    role = "user",
                    content = prompt,
                    fromVoice = fromVoice,
                    timestampMs = userMessage.timestampMs,
                    attachmentName = attachment?.name,
                    attachmentMimeType = attachment?.mimeType
                )
            )

                val requestHistory = selectedContext.messages
                if (current.smartComboEnabled) {
                    autoPrepared = freeExecutor.prepare(
                        requestId = UUID.randomUUID().toString(),
                        estimatedInput = selectedContext.estimatedTokens,
                        reservedOutput = 2048,
                        requiredCapabilities = selectedAgent.requiredCapabilities,
                        offers = freeRouteHealth.decorate(freeOffers, System.currentTimeMillis()),
                        resolveCredential = { provider, endpoint -> apiStore.resolveCredential(provider, endpoint) },
                        agentId = selectedAgent.id
                    )
                    require(autoPrepared != null) {
                        "No compatible, verified free route passed the capability and quota admission checks."
                    }
                }
                val config = autoPrepared?.config ?: ApiConnectionConfig(
                    baseUrl = current.baseUrl,
                    apiKey = apiStore.resolveCredential(current.provider, current.baseUrl),
                    provider = current.provider,
                    selectedModel = current.selectedModel,
                    selectedAgentId = selectedAgent.id
                )
                if (autoPrepared != null) {
                    check(freeExecutor.dispatched(requireNotNull(autoPrepared))) {
                        "Automatic request has already been dispatched."
                    }
                }
                appendLlmEvent(
                    MatrixEventType.MODEL_STARTED,
                    "agent:" + selectedAgent.id,
                    "model:" + config.selectedModel,
                    selectedAgent.name + " request started through " + config.provider.displayName
                )
                AppLogStore.info("CHAT", "Request started provider=" + config.provider.name +
                    " model=" + config.selectedModel + " agent=" + selectedAgent.id)
                val streamTime = System.currentTimeMillis()
                val streamedText = StringBuilder()
                val dispatchedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                var firstTokenObserved = false
                var lastStreamUiAtMs = Long.MIN_VALUE
                val speechInstruction = if (fromVoice) {
                    val speech = ai.drfx.maximus.matrixai.voice.SpeechLanguagePreferenceStore(
                        getApplication<Application>()
                    ).selected()
                    "\n\nVoice response language: " + speech.tag +
                        ". Reply naturally in the language of the user speech. " +
                        "For fa-IR use fluent Persian written in Persian script. " +
                        "Do not claim an audio recording or transcription was verified by the model."
                } else ""
                val equippedAgent = if (memoryEvidenceAtRequest.isBlank() && speechInstruction.isBlank()) {
                    selectedAgent
                } else {
                    selectedAgent.copy(systemPrompt = selectedAgent.systemPrompt +
                        (if (memoryEvidenceAtRequest.isBlank()) "" else "\n\n" + memoryEvidenceAtRequest) +
                        speechInstruction)
                }
                val result = chatClient.send(config, requestHistory, equippedAgent, onQuota = { windows ->
                    if (windows.isNotEmpty()) {
                        autoPrepared?.let { freeExecutor.observeProviderQuota(it, windows) }
                        viewModelScope.launch {
                            if (chatGeneration != requestGeneration) return@launch
                            val requests = windows.firstOrNull {
                                it.kind == SmartComboPlanner.LimitKind.REQUESTS_PER_DAY ||
                                    it.kind == SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE
                            }
                            val tokens = windows.firstOrNull {
                                it.kind == SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_MINUTE
                            }
                            val visible = _llmState.value
                            _llmState.value = visible.copy(tokenMetrics = visible.tokenMetrics.copy(
                                verifiedRateRequestsRemaining = requests?.remaining,
                                verifiedRateTokensRemaining = tokens?.remaining,
                                verifiedRequestRateKind = if (requests?.kind ==
                                    SmartComboPlanner.LimitKind.REQUESTS_PER_DAY) "RPD" else "RPM",
                                rateEvidenceAtEpochMs = System.currentTimeMillis()
                            ))
                        }
                    }
                }) { delta ->
                    // Record only a genuine first NONEMPTY token after dispatch.
                    // This elapsed duration, not estimated billing or synthetic latency,
                    // feeds verified-free Smart Combo performance ranking.
                    if (!firstTokenObserved && delta.isNotEmpty()) {
                        firstTokenObserved = true
                        val latency = android.os.SystemClock.elapsedRealtime() - dispatchedAtElapsedMs
                        autoPrepared?.let { freeRouteHealth.observeFirstToken(it.offer.route.id, latency) }
                    }
                    // Coalesce dense SSE tokens to <=4 UI updates per second.
                    streamedText.append(delta)
                    val elapsed = android.os.SystemClock.elapsedRealtime()
                    if (lastStreamUiAtMs == Long.MIN_VALUE || elapsed - lastStreamUiAtMs >= 250L) {
                        lastStreamUiAtMs = elapsed
                        val accumulated = streamedText.toString()
                        val approxTokens = accumulated.length / 4 + if (accumulated.length % 4 == 0) 0 else 1
                        viewModelScope.launch {
                            if (chatGeneration != requestGeneration || !_llmState.value.isGenerating) return@launch
                            streamingTimestamp = streamTime
                            val partial = ChatMessage("assistant", accumulated, streamTime)
                            _chatMessages.value = _chatMessages.value.filterNot {
                                it.timestampMs == streamTime && it.role == "assistant"
                            } + partial
                            val visible = _llmState.value
                            _llmState.value = visible.copy(
                                tokenMetrics = visible.tokenMetrics.copy(
                                    streamingOutputTokensEstimate = approxTokens,
                                    streamingEstimateActive = true
                                )
                            )
                        }
                    }
                }
                autoPrepared?.let {
                    freeExecutor.completed(it, result.usage)
                    freeRouteHealth.succeeded(it.offer.route.id, System.currentTimeMillis())
                }
                if (_activeChatSessionId.value != requestSessionId || requestGeneration != chatGeneration) return@launch
                if (result.transcript != null) {
                    val index = _chatMessages.value.indexOfLast { it.role == "user" && it.attachment?.mimeType?.startsWith("audio/") == true }
                    if (index >= 0) {
                        _chatMessages.value = _chatMessages.value.toMutableList().also { list ->
                            list[index] = list[index].copy(content = result.transcript, attachment = null)
                        }
                    }
                    chatRepository.updateMessageContent(requestSessionId, userMessage.timestampMs, "user", result.transcript)
                }
                if (_activeChatSessionId.value != requestSessionId || requestGeneration != chatGeneration) return@launch
                val assistantMessage = ChatMessage(role = "assistant", content = result.text)
                streamingTimestamp?.let { t -> _chatMessages.value = _chatMessages.value.filterNot { it.role == "assistant" && it.timestampMs == t } }
                streamingTimestamp = null
                _chatMessages.value = _chatMessages.value + assistantMessage

                chatRepository.saveMessage(
                    ChatMessageEntity(
                        sessionId = requestSessionId,
                        role = "assistant",
                        content = result.text,
                        timestampMs = assistantMessage.timestampMs
                    )
                )

                if (_activeChatSessionId.value != requestSessionId || requestGeneration != chatGeneration) return@launch
                usageStore.record(config.provider, config.selectedModel, result.usage)
                if (sharedProjectMemory.enabled()) {
                    val modelName = config.selectedModel
                    val userTask = prompt
                    val output = result.text
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try { sharedProjectMemory.storeModelProgress(memoryProjectAtRequest, modelName, userTask, output) }
                        catch (_: Exception) { /* No generated content or credentials are logged. */ }
                    }
                }
                val summary = usageStore.summary()
                _usage.value = summary

                val capacity = autoPrepared?.offer?.route?.contextWindowTokens
                    ?: ModelLimitsResolver.contextWindow(current.provider, current.selectedModel)
                val activeContextTokens = ChatContextPolicy.bounded(_chatMessages.value, capacity).estimatedTokens
                val remainingCtx = (capacity - activeContextTokens).coerceAtLeast(0)
                val budget = current.monthlyTokenBudget
                val remainingBudget = if (budget > 0) (budget - summary.totalTokens).coerceAtLeast(0L) else null
                val percent = if (capacity > 0) (activeContextTokens.toFloat() / capacity.toFloat()).coerceIn(0f, 1f) else 0f

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
                    contextUsagePercent = percent,
                    streamingEstimateActive = false,
                    streamingOutputTokensEstimate = 0
                )

                _llmState.value = _llmState.value.copy(
                    status = ConnectionStatus.CONNECTED,
                    statusMessage = "Inference succeeded: " + config.provider.displayName + " / " + config.selectedModel +
                        if (autoPrepared != null) " (verified free-text offer; account quota unknown)." else ".",
                    isGenerating = false,
                    tokenMetrics = updatedMetrics
                )
                AppLogStore.info("CHAT", "Response completed model=" + config.selectedModel + " inputTokens=" + result.usage.inputTokens + " outputTokens=" + result.usage.outputTokens + " remainingCtx=" + remainingCtx)
                appendLlmEvent(
                    MatrixEventType.MODEL_COMPLETED,
                    "model:" + config.selectedModel,
                    "agent:" + selectedAgent.id,
                    "Model response completed"
                )
            } catch (error: Throwable) {
                autoPrepared?.let {
                    freeExecutor.failed(it)
                    if (error !is CancellationException) {
                        val ledgerState = freeExecutor.requestState(it.requestId)
                        if (ledgerState == SmartComboQuotaLedger.State.UNCERTAIN) {
                            freeRouteHealth.failed(it.offer.route.id, System.currentTimeMillis())
                        }
                    }
                }
                if (error is CancellationException) {
                    if (requestGeneration == chatGeneration) _llmState.value = _llmState.value.copy(isGenerating = false, tokenMetrics = _llmState.value.tokenMetrics.copy(streamingEstimateActive = false, streamingOutputTokensEstimate = 0))
                    throw error
                }
                streamingTimestamp?.let { t -> _chatMessages.value = _chatMessages.value.filterNot { it.role == "assistant" && it.timestampMs == t } }
                streamingTimestamp = null
                if (_activeChatSessionId.value != requestSessionId || requestGeneration != chatGeneration) return@launch
                AppLogStore.error("CHAT", error.message ?: "The model request failed.")
                appendAssistantError(error.message ?: "The model request failed.")
                _llmState.value = _llmState.value.copy(isGenerating = false, status = ConnectionStatus.DEGRADED, statusMessage = error.message ?: "Inference failed", tokenMetrics = _llmState.value.tokenMetrics.copy(streamingEstimateActive = false, streamingOutputTokensEstimate = 0))
                appendLlmEvent(
                    MatrixEventType.MODEL_FAILED,
                    "model:" + (autoPrepared?.config?.selectedModel ?: current.selectedModel),
                    "agent:" + selectedAgent.id,
                    error.message ?: "Model request failed"
                )
            }
        }
        return true
    }

    fun cancelChat() {
        chatGeneration++
        streamingTimestamp?.let { t -> _chatMessages.value = _chatMessages.value.filterNot { it.role == "assistant" && it.timestampMs == t } }
        streamingTimestamp = null
        chatJob?.cancel()
        chatJob = null
        _llmState.value = _llmState.value.copy(isGenerating = false, tokenMetrics = _llmState.value.tokenMetrics.copy(streamingEstimateActive = false, streamingOutputTokensEstimate = 0))
    }

    private fun restoreChatSession(id: String) {
        restoreJob?.cancel()
        historyReady = false
        _chatMessages.value = emptyList()
        restoreJob = viewModelScope.launch {
            val session = chatRepository.getSession(id)
            if (id != "legacy_unassigned" && (session == null || session.provider != _llmState.value.provider.name || session.model != _llmState.value.selectedModel)) {
                historyReady = true
                createChatSession()
                return@launch
            }
            val entities = chatRepository.getMessages(if (id == "legacy_unassigned") "default_session" else id)
            if (_activeChatSessionId.value != id) return@launch
            // Attachment payloads were never persisted; do not replay missing bytes.
            _chatMessages.value = entities.map { e -> ChatMessage(e.role,
                e.content + if (e.attachmentName != null) "\n[Attachment unavailable after restart: ${e.attachmentName}; reattach to resend.]" else "",
                e.timestampMs, e.isError, fromVoice = e.fromVoice) }
            val state = _llmState.value
            val estimate = ChatContextPolicy.bounded(_chatMessages.value, state.tokenMetrics.contextCapacity).estimatedTokens
            _llmState.value = state.copy(tokenMetrics = state.tokenMetrics.copy(consumedSessionTokens = 0, consumedTurnTotalTokens = 0, consumedTurnInputTokens = 0, consumedTurnOutputTokens = 0, remainingContextTokens = (state.tokenMetrics.contextCapacity - estimate).coerceAtLeast(0), contextUsagePercent = estimate.toFloat() / state.tokenMetrics.contextCapacity.coerceAtLeast(1)))
            historyReady = true
        }
    }

    fun createChatSession(model: String = _llmState.value.selectedModel) {
        cancelChat()
        val state = _llmState.value
        val id = UUID.randomUUID().toString()
        _activeChatSessionId.value = id
        sessionPreferences.edit().putString("active", id).apply()
        restoreJob?.cancel()
        _chatMessages.value = emptyList()
        historyReady = true
        _llmState.value = state.copy(tokenMetrics = state.tokenMetrics.copy(consumedSessionTokens = 0, consumedTurnTotalTokens = 0, consumedTurnInputTokens = 0, consumedTurnOutputTokens = 0, remainingContextTokens = state.tokenMetrics.contextCapacity, contextUsagePercent = 0f))
        viewModelScope.launch { chatRepository.saveSession(ChatSessionEntity(id, "New conversation", state.provider.name, model, state.selectedAgentId)) }
    }

    fun switchChatSession(id: String) {
        val session = _chatSessions.value.firstOrNull { it.id == id } ?: return
        if (id != "default_session" && (session.provider != _llmState.value.provider.name || session.model != _llmState.value.selectedModel)) {
            appendAssistantError("This conversation belongs to a different provider/model. Select its provider and model before opening it.")
            return
        }
        cancelChat()
        val targetId = if (id == "default_session") "legacy_unassigned" else id
        _activeChatSessionId.value = targetId
        sessionPreferences.edit().putString("active", targetId).apply()
        restoreChatSession(targetId)
    }

    fun renameChatSession(id: String, title: String) {
        val session = _chatSessions.value.firstOrNull { it.id == id } ?: return
        if (title.isBlank()) return
        viewModelScope.launch { chatRepository.saveSession(session.copy(title = title.trim().take(100), updatedAtMs = System.currentTimeMillis())) }
    }

    fun deleteChatSession(id: String) {
        if (_activeChatSessionId.value == id || (id == "default_session" && _activeChatSessionId.value == "legacy_unassigned")) createChatSession()
        viewModelScope.launch { chatRepository.deleteSession(id) }
    }

    fun clearChat() {
        cancelChat()
        restoreJob?.cancel()
        historyReady = true
        val sessionId = _activeChatSessionId.value
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
            chatRepository.clearSession(sessionId)
        }
    }

    fun disconnectApi() {
        cancelChat()
        validationJob?.cancel()
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
        val sessionId = _activeChatSessionId.value
        viewModelScope.launch {
            chatRepository.saveMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
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
        val currentModel = _llmState.value.selectedModel
        if (currentModel.isBlank()) { _chartVisionState.value = ChartVisionUiState.Error("Select a verified vision model first."); return }

        _chartVisionState.value = ChartVisionUiState.Analyzing(
            stage = "Analyzing chart with ${_llmState.value.provider.displayName} / $currentModel…",
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
                    remainingContextTokens = currentLlm.tokenMetrics.remainingContextTokens,
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
                if (e is CancellationException) throw e
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
                relations = "llm:${analysis.modelUsed},vision:engine",
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
            append("Here is the ${analysis.modelUsed} AI Chart Vision analysis for ${analysis.assetIdentifier} (${analysis.timeframeEstimate}):\n")
            append("• Trend: ${analysis.direction.displayName} (${analysis.trendStrength})\n")
            if (analysis.patterns.isNotEmpty()) {
                append("• Key Pattern: ${analysis.patterns.first().name} (AI interpretation)\n")
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
        AppLogStore.info("MATRIX", event.type.name + " | " + event.sourceNode + " -> " + (event.targetNode ?: "-"))
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
