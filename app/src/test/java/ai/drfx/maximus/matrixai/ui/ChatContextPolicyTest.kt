package ai.drfx.maximus.matrixai.ui

import ai.drfx.maximus.matrixai.llm.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class ChatContextPolicyTest {
    @Test fun providerAndModelMustBothMatch() {
        assertTrue(ChatContextPolicy.sameContext("GEMINI", "a", "GEMINI", "a"))
        assertFalse(ChatContextPolicy.sameContext("GEMINI", "a", "OPENAI", "a"))
        assertFalse(ChatContextPolicy.sameContext("GEMINI", "a", "GEMINI", "b"))
        assertFalse(ChatContextPolicy.sameContext("UNKNOWN", "a", "UNKNOWN", "a"))
    }
    @Test fun boundedHistoryRetainsNewestUserTurnAndOrdering() {
        val history = listOf(ChatMessage("user", "old"), ChatMessage("assistant", "old"), ChatMessage("user", "latest"))
        val selected = ChatContextPolicy.bounded(history, 25, 0)
        assertEquals(listOf("latest"), selected.messages.map { it.content })
        assertEquals(2, selected.omittedMessages)
        assertTrue(selected.estimatedTokens <= 25)
    }
    @Test fun errorsAreExcludedAndOversizedInputIsNotSilentlyTruncated() {
        assertTrue(ChatContextPolicy.bounded(listOf(ChatMessage("user", "x".repeat(1000))), 20, 0).messages.isEmpty())
        assertTrue(ChatContextPolicy.bounded(listOf(ChatMessage("assistant", "error", isError = true)), 500, 0).messages.isEmpty())
    }
}
