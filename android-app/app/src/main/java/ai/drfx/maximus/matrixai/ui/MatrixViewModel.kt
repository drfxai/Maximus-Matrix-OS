package ai.drfx.maximus.matrixai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.drfx.maximus.matrixai.agent.MatrixEvent
import ai.drfx.maximus.matrixai.agent.MatrixEventType
import ai.drfx.maximus.matrixai.agent.MaximusMatrixAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MatrixViewModel(application: Application) : AndroidViewModel(application) {
    private val agent = MaximusMatrixAgent(application)
    private val _events = MutableStateFlow<List<MatrixEvent>>(emptyList())
    val events: StateFlow<List<MatrixEvent>> = _events.asStateFlow()
    private val _status = MutableStateFlow("READY")
    val status: StateFlow<String> = _status.asStateFlow()

    init {
        viewModelScope.launch {
            agent.eventStream.collect { event ->
                _events.value = (listOf(event) + _events.value).take(60)
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
        if (_status.value == "EXECUTING") return
        viewModelScope.launch {
            _status.value = "PLANNING"
            agent.execute(objective)
        }
    }
}
