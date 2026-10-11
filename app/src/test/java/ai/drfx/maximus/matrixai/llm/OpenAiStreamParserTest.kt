package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class OpenAiStreamParserTest {
    @Test fun streamAccumulatesRealDeltasAndUsage() {
        val deltas = mutableListOf<String>()
        val parser = OpenAiStreamParser { deltas.add(it) }
        parser.line("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}")
        parser.line("data: {\"choices\":[{\"delta\":{\"content\":\" world\"},\"finish_reason\":\"stop\"}]}")
        parser.line("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":2,\"total_tokens\":6}}")
        assertFalse(parser.line("data: [DONE]"))
        assertEquals("Hello world", parser.text())
        assertEquals(listOf("Hello", " world"), deltas)
        assertEquals(6, parser.usage?.totalTokens)
        assertTrue(parser.completed)
    }
    @Test fun incompleteStreamIsNotCompleted() {
        val parser = OpenAiStreamParser {}
        parser.line("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}")
        assertFalse(parser.completed)
    }
    @Test(expected = IllegalArgumentException::class) fun streamErrorNeverProducesSuccess() {
        OpenAiStreamParser {}.line("data: {\"error\":{\"message\":\"private provider diagnostic\"}}")
    }
}
