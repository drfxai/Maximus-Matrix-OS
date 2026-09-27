package ai.drfx.maximus.matrixai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.drfx.maximus.matrixai.agent.MatrixEvent
import ai.drfx.maximus.matrixai.agent.MatrixEventType
import ai.drfx.maximus.matrixai.agent.MaximusMatrixAgent
import ai.drfx.maximus.matrixai.llm.ApiConnectionConfig
import ai.drfx.maximus.matrixai.llm.ApiDiscoveryEngine
import ai.drfx.maximus.matrixai.llm.ChatMessage
import ai.drfx.maximus.matrixai.llm.ConnectionStatus
import ai.drfx.maximus.matrixai.llm.LlmChatClient
import ai.drfx.maximus.matrixai.llm.LlmProvider
import ai.drfx.maximus.matrixai.llm.LlmUiState
import ai.drfx.maximus.matrixai.llm.SecureApiConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MatrixViewModel(application: Application) : AndroidViewModel(application) {
    private val agent = MaximusMatrixAgent(application)
    private val discovery = ApiDiscoveryEngine()
    private val chatClient = LlmChatClient()
    private val apiStore = SecureApiConfigStore(application)

    private val _events = MutableStateFlow<List<MatrixEvent>>(emptyList())
    val events: StateFlow<List<MatrixEvent>> = _events.asStateFlow()

    private val _status = MutableStateFlow("READY")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _llmState = MutableStateFlow(
        LlmUiState(
            status = ConnectionStatus.DISCONNECTED,
            provider = apiStore.loadProvider(),
            baseUrl = apiStore.loadBaseUrl(),
            selectedModel = apiStore.loadModel(),
            hasSavedKey = apiStore.hasApiKey(),
            statusMessage = if (apiStore.hasApiKey()) {
                "Saved API configuration found. Detect the API to refresh supported models."
            } else {
                "Configure an API endpoint to begin."
            }
        )
    )
    val llmState: StateFlow<LlmUiState> = _llmState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    init {
        viewModelScope.launch {
            agent.eventStream.collect { event ->
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
            agent.execute(objective)
        }
    }

    fun detectApi(baseUrl: String, apiKeyInput: String) {
        if (_llmState.value.status == ConnectionStatus.DETECTING) return
        viewModelScope.launch {
            val key = apiKeyInput.ifBlank { apiStore.loadApiKey() }
            _llmState.value = _llmState.value.copy(
                status = ConnectionStatus.DETECTING,
                statusMessage = "Detecting provider and supported models..."
            )
            appendLlmEvent(MatrixEventType.MODEL_DISCOVERY, "model:discovery", "provider:api", "API discovery started")
            try {
                val result = discovery.discover(baseUrl, key)
                val remembered = apiStore.loadModel()
                val selected = result.models.firstOrNull { it.id == remembered }?.id
                    ?: result.models.firstOrNull()?.id.orEmpty()
                apiStore.save(result.baseUrl, key, result.provider, selected)
                _llmState.value = LlmUiState(
                    status = ConnectionStatus.CONNECTED,
                    provider = result.provider,
                    baseUrl = result.baseUrl,
                    models = result.models,
                    selectedModel = selected,
                    hasSavedKey = true,
                    statusMessage = result.message
                )
                appendLlmEvent(
                    MatrixEventType.MODEL_COMPLETED,
                    "provider:" + result.provider.name.lowercase(),
                    "model:" + selected,
                    result.message
                )
            } catch (error: Throwable) {
                _llmState.value = _llmState.value.copy(
                    status = ConnectionStatus.ERROR,
                    statusMessage = error.message ?: "API detection failed."
                )
                appendLlmEvent(
                    MatrixEventType.MODEL_FAILED,
                    "model:discovery",
                    "provider:api",
                    error.message ?: "API detection failed"
                )
            }
        }
    }

    fun selectModel(modelId: String) {
        val current = _llmState.value
        if (current.models.none { it.id == modelId }) return
        _llmState.value = current.copy(selectedModel = modelId)
        apiStore.save(current.baseUrl, "", current.provider, modelId)
    }

    fun sendChat(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank()) return
        val current = _llmState.value
        if (current.status != ConnectionStatus.CONNECTED || current.selectedModel.isBlank()) {
            _chatMessages.value = _chatMessages.value + ChatMessage(
                role = "assistant",
                content = "Connect an API and select a supported model before sending a message.",
                isError = true
            )
            return
        }
        if (current.isGenerating) return

        val userMessage = ChatMessage(role = "user", content = prompt)
        _chatMessages.value = _chatMessages.value + userMessage
        _llmState.value = current.copy(isGenerating = true)
        appendLlmEvent(
            MatrixEventType.MODEL_STARTED,
            "chat:user",
            "model:" + current.selectedModel,
            "Model request started"
        )

        viewModelScope.launch {
            try {
                val config = ApiConnectionConfig(
                    baseUrl = current.baseUrl,
                    apiKey = apiStore.loadApiKey(),
                    provider = current.provider,
                    selectedModel = current.selectedModel
                )
                val answer = chatClient.send(config, _chatMessages.value)
                _chatMessages.value = _chatMessages.value + ChatMessage(role = "assistant", content = answer)
                _llmState.value = _llmState.value.copy(isGenerating = false)
                appendLlmEvent(
                    MatrixEventType.MODEL_COMPLETED,
                    "model:" + current.selectedModel,
                    "chat:assistant",
                    "Model response completed"
                )
            } catch (error: Throwable) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    role = "assistant",
                    content = error.message ?: "The model request failed.",
                    isError = true
                )
                _llmState.value = _llmState.value.copy(isGenerating = false)
                appendLlmEvent(
                    MatrixEventType.MODEL_FAILED,
                    "model:" + current.selectedModel,
                    "chat:assistant",
                    error.message ?: "Model request failed"
                )
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }

    fun disconnectApi() {
        apiStore.clear()
        _llmState.value = LlmUiState()
        appendLlmEvent(MatrixEventType.MODEL_DISCOVERY, "provider:api", "model:none", "API configuration cleared")
    }

    private fun appendEvent(event: MatrixEvent) {
        _events.value = (listOf(event) + _events.value).take(100)
    }

    private fun appendLlmEvent(type: MatrixEventType, source: String, target: String, message: String) {
        appendEvent(
            MatrixEvent(
                missionId = "chat-" + UUID.randomUUID().toString(),
                type = type,
                sourceNode = source,
                targetNode = target,
                message = message,
                metadata = mapOf("domain" to "llm")
            )
        )
    }
}
