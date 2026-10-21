package ai.drfx.maximus.matrixai.studio

import org.junit.Assert.*
import org.junit.Test

class PineSourceImportTest {
    @Test fun sourceRemainsExactAndTitleIsBounded() {
        val source = "//@version=6\nindicator(\"My RSI\", overlay=false)\nplot(close)\n"
        assertEquals(source, PineSourceImport.validate(source))
        assertEquals("My RSI", PineSourceImport.title(source))
    }
    @Test fun unnamedDraftHasSafeFallback() {
        assertEquals("Chat indicator", PineSourceImport.title("plot(close)"))
    }
    @Test fun oversizedAndEmptySourcesAreRejected() {
        listOf("", "  ", "a".repeat(100_001), "plot(close)\u0000").forEach {
            assertTrue(runCatching { PineSourceImport.validate(it) }.isFailure)
        }
    }
}
