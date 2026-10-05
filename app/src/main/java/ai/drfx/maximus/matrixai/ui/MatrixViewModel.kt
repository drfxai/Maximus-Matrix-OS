package ai.drfx.maximus.matrixai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.drfx.maximus.matrixai.agent.MatrixEvent
import ai.drfx.maximus.matrixai.agent.MatrixEventType
import ai.drfx.maximus.matrixai.agent.MaximusMatrixAgent
import ai.drfx.maximus.matrixai.data.CompanyDataClient
import ai.drfx.maximus.matrixai.data.DataCenterUiState
import ai.drfx.maximus.matrixai.data.SecureDataCenterStore
import ai.drfx.maximus.matrixai.database.AppDatabase
import ai.drfx.maximus.matrixai.database.entities.ChatMessageEntity
import ai.drfx.maximus.matrixai.database.entities.ChatSessionEntity
import ai.drfx.maximus.matrixai.database.repository.ChatHistoryRepository
import ai.drfx.maximus.matrixai.database.repository.MatrixEventRepository
import ai.drfx.maximus.matrixai.database.repository.MatrixGraphRepository
import ai.drfx.maximus.matrixai.database.repository.UserSessionRepository
import ai.drfx.maximus.matrixai.llm.*
import ai.drfx.maximus.matrixai.logging.AppLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MatrixViewModel(application: Application) : AndroidViewModel(application) {
    private val agentRuntime = MaximusMatrixAgent(application)
    private val discovery = ApiDiscoveryEngine()
    private val chatClient = LlmChatClient()
    private val apiStore = SecureApiConfigStore(application)
    private val usageStore = ApiUsageStore(application)
    private val dataClient = CompanyDataClient()
    private val dataStore = SecureDataCenterStore(application)

    // Room Database & Repositories via Dependency Injection
    private val dbModule = ai.drfx.maximus.matrixai.database.di.DatabaseModule.getInstance(application)
    val graphRepository = dbModule.matrixGraphRepository
    val chatRepository = dbModule.chatHistoryRepository
    val eventRepository = dbModule.matrixEventRepository
    val userSessionRepository = dbModule.userSessionRepository

    private val _events = MutableStateFlow<List<MatrixEvent>>(emptyList())
    val events: StateFlow<List<MatrixEvent>> = _events.asStateFlow()

    private val _status = MutableStateFlow("READY")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _usage = MutableStateFlow(usageStore.summary())
    val usage: StateFlow<ApiUsageSummary> = _usage.asStateFlow()

    private val initialCapabilities = emptySet<ModelCapability>()
    private val _llmState = MutableStateFlow(
        LlmUiState(
            status = ConnectionStatus.DISCONNECTED,
            provider = apiStore.loadProvider(),
            baseUrl = apiStore.loadBaseUrl(),
            selectedModel = apiStore.loadModel(),
            selectedAgentId = apiStore.loadAgentId(),
            supportedAgents = AgentRegistry.supportedAgents(initialCapabilities),
            hasSavedKey = apiStore.hasApiKey(),
            statusMessage = if (apiStore.loadBaseUrl().isNotBlank()) {
                "Saved API configuration found. Detect the API to refresh models and compatible agents."
            } else {
                "Configure an API endpoint to begin."
            },
            subscriptionLabel = apiStore.loadSubscriptionLabel(),
            monthlyBudgetUsd = apiStore.loadMonthlyBudgetUsd()
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
            agentRuntime.eventStream.collect { event ->
                appendEvent(event)
                _status.value = when (event.type) {
                    MatrixEventType.MISSION_COMPLETED -> "READY"
                    MatrixEventType.MISSION_FAILED -> "ATTENTION"
                    MatrixEventType.CONFIRMATION_REQUIRED -> "CONFIRM"
                    else -> "EXECUTING"
                }
            }
        }
    }

    fun runMission(objective: String) {
        if (_status.value == "EXECUTING" || _status.value == "PLANNING") return
        viewModelScope.launch {
            _status.value = "PLANNING"
            agentRuntime.execute(objective)
        }
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
                val supportedAgents = AgentRegistry.supportedAgents(model?.capabilities.orEmpty())
                val rememberedAgent = apiStore.loadAgentId()
                val selectedAgent = supportedAgents.firstOrNull { it.id == rememberedAgent }?.id
                    ?: supportedAgents.firstOrNull()?.id.orEmpty()

                apiStore.save(
                    result.baseUrl,
                    key,
                    result.provider,
                    selectedModel,
                    selectedAgent,
                    apiStore.loadSubscriptionLabel(),
                    apiStore.loadMonthlyBudgetUsd()
                )
                _llmState.value = LlmUiState(
                    status = ConnectionStatus.CONNECTED,
                    provider = result.provider,
                    baseUrl = result.baseUrl,
                    models = result.models,
                    selectedModel = selectedModel,
                    selectedAgentId = selectedAgent,
                    supportedAgents = supportedAgents,
                    hasSavedKey = key.isNotBlank(),
                    statusMessage = result.message + " " + supportedAgents.size + " compatible agent(s) available.",
                    subscriptionLabel = apiStore.loadSubscriptionLabel(),
                    monthlyBudgetUsd = apiStore.loadMonthlyBudgetUsd()
                )
                chatRepository.saveSession(
                    ChatSessionEntity(
                        id = "default_session",
                        title = "Default Session",
                        provider = result.provider.name,
                        model = selectedModel,
                        agentId = selectedAgent
                    )
                )
                AppLogStore.info("API", "Detected " + result.provider.name + "; models=" + result.models.size + "; selected=" + selectedModel + "; compatibleAgents=" + supportedAgents.size)
                appendLlmEvent(
                    MatrixEventType.MODEL_COMPLETED,
                    "provider:" + result.provider.name.lowercase(),
                    "model:" + selectedModel,
                    "API detected and model catalog loaded"
                )
            } catch (error: Throwable) {
                _llmState.value = _llmState.value.copy(
                    status = ConnectionStatus.ERROR,
                    statusMessage = error.message ?: "API detection failed."
                )
                AppLogStore.error("API", error.message ?: "API detection failed")
                appendLlmEvent(MatrixEventType.MODEL_FAILED, "model:discovery", "provider:api", error.message ?: "API detection failed")
            }
        }
    }

    fun selectModel(modelId: String) {
        val current = _llmState.value
        val model = current.models.firstOrNull { it.id == modelId } ?: return
        val agents = AgentRegistry.supportedAgents(model.capabilities)
        val selectedAgent = agents.firstOrNull { it.id == current.selectedAgentId }?.id
            ?: agents.firstOrNull()?.id.orEmpty()
        _llmState.value = current.copy(
            selectedModel = modelId,
            selectedAgentId = selectedAgent,
            supportedAgents = agents
        )
        apiStore.save(
            current.baseUrl,
            "",
            current.provider,
            modelId,
            selectedAgent,
            current.subscriptionLabel,
            current.monthlyBudgetUsd
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
            current.monthlyBudgetUsd
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
            monthlyBudgetUsd.coerceAtLeast(0.0)
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
                ?.capabilities?.contains(ModelCapability.VISION) == true
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
                _usage.value = usageStore.summary()
                _llmState.value = _llmState.value.copy(isGenerating = false)
                AppLogStore.info("CHAT", "Response completed model=" + current.selectedModel + " inputTokens=" + result.usage.inputTokens + " outputTokens=" + result.usage.outputTokens + " estimated=" + result.usage.estimated)
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
        _usage.value = usageStore.summary()
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

    private fun appendEvent(event: MatrixEvent) {
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

