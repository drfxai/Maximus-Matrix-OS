package ai.drfx.maximus.matrixai.ui

import org.junit.Assert.*
import org.junit.Test

class ChatPresentationTest {
    @Test fun toolTextIsNotPresentedAsExecuted() {
        val response = "<function_calls><invoke name=\"create_file\">" +
            "<parameter name=\"path\">index.html</parameter>" +
            "<parameter name=\"contents\"><!DOCTYPE html><html><body>Hello</body></html></parameter>" +
            "</invoke></function_calls>"
        val draft = ChatPresentation.draft(response)!!
        assertEquals("create_file", draft.tool)
        assertEquals("index.html", draft.path)
        assertTrue(draft.description.contains("not executed"))
        val exported = ChatPresentation.export(response)
        assertEquals("index.html", exported.name)
        assertEquals("text/html", exported.mimeType)
        assertTrue(exported.bytes.toString(Charsets.UTF_8).startsWith("<!DOCTYPE html>"))
    }
    @Test fun ordinaryTextIsNotPretendedToolOutput() {
        assertNull(ChatPresentation.draft("Here is an HTML example."))
        assertEquals("maximus-response.md", ChatPresentation.export("Hello").name)
    }
    @Test fun unsafePathsNeverBecomeDownloadNames() {
        val raw = "<invoke name=\"create_file\"><parameter name=\"path\">../../.secret</parameter>" +
            "<parameter name=\"contents\">private</parameter></invoke>"
        assertEquals("maximus-response.md", ChatPresentation.export(raw).name)
    }
}
