package ai.drfx.maximus.matrixai.ui

import org.junit.Assert.*
import org.junit.Test

class WorkspaceMessageContentTest {
    @Test fun proseAndCodeRemainSeparateAndExportable() {
        val parts = messageParts("Before\n```kotlin\nval text = \"سلام\"\n```\nAfter")
        assertEquals(3, parts.size)
        assertEquals("Before", parts[0].text)
        assertEquals("kotlin", parts[1].language)
        assertEquals("val text = \"سلام\"", parts[1].text)
        assertEquals("After", parts[2].text)
    }
    @Test fun streamedUnclosedFenceStillDisplaysCode() {
        assertEquals(MessagePart("fun main() {", "kotlin"), messageParts("```kotlin\nfun main() {").single())
    }
    @Test fun inlineBackticksAndToolTagsAreNotExecutedOrDropped() {
        val input = "Use `file.kt`\n<web_search>https://example.com</web_search>"
        assertEquals(MessagePart(input), messageParts(input).single())
    }
    @Test fun multipleFencesKeepTheirOwnContents() {
        assertEquals(listOf(MessagePart("one", "sh"), MessagePart("two", "json")),
            messageParts("```sh\none\n```\n```json\ntwo\n```"))
    }
}
