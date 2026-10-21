package ai.drfx.maximus.matrixai.llm

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Capture ownership before withContext's cancellable return to the UI dispatcher.
 * Otherwise a successfully persisted reservation can be lost before the caller can release it.
 */
internal object SmartComboPreparation {
    suspend fun <T> captureBeforeReturn(
        prepare: () -> T,
        capture: (T) -> Unit,
        context: CoroutineContext = Dispatchers.IO
    ) {
        withContext(context) { capture(prepare()) }
    }
}
