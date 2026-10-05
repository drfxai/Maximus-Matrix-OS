package ai.drfx.maximus.matrixai.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MatrixEventBus {
    private val _events = MutableSharedFlow<MatrixEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<MatrixEvent> = _events.asSharedFlow()

    suspend fun emit(event: MatrixEvent) {
        _events.emit(event)
    }
}
