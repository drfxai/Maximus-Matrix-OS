package ai.drfx.maximus.matrixai.ui

import ai.drfx.maximus.matrixai.llm.ChatMessage
import kotlin.math.ceil

/** Local conservative estimates, not provider usage or quota. No hidden summarization or cross-provider transfer. */
object ChatContextPolicy {
    data class Selection(val messages: List<ChatMessage>, val estimatedTokens: Int, val omittedMessages: Int)

    fun sameContext(sessionProvider: String, sessionModel: String, provider: String, model: String): Boolean =
        sessionProvider == provider && sessionModel == model && sessionProvider != "UNKNOWN" && model.isNotBlank()

    fun estimateTokens(content: String): Int = ceil(content.codePointCount(0, content.length) / 2.0).toInt() + 8

    fun bounded(messages: List<ChatMessage>, capacity: Int, reservedOutputTokens: Int = 2048): Selection {
        val budget = (capacity - reservedOutputTokens).coerceAtLeast(0)
        val eligible = messages.filter { !it.isError && it.role in setOf("user", "assistant") }
        val selected = mutableListOf<ChatMessage>()
        var tokens = 0
        for (message in eligible.asReversed()) {
            // Media requires model-specific accounting; reserve conservatively and label all counts estimated.
            val cost = estimateTokens(message.content) + if (message.attachment != null) 4096 else 0
            if (tokens + cost > budget) break
            selected.add(message)
            tokens += cost
        }
        selected.reverse()
        // Never start a truncated history with an orphaned assistant response.
        while (selected.firstOrNull()?.role == "assistant") {
            val removed = selected.removeAt(0)
            tokens -= estimateTokens(removed.content) + if (removed.attachment != null) 4096 else 0
        }
        return Selection(selected, tokens, eligible.size - selected.size)
    }
}
