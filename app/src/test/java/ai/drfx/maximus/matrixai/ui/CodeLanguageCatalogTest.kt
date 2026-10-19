package ai.drfx.maximus.matrixai.ui

import org.junit.Assert.*
import org.junit.Test

class CodeLanguageCatalogTest {
    @Test fun recognizesProgrammingLanguageFamilies() {
        val cases = mapOf("pine" to "pine", "mql5" to "mq5", "kotlin" to "kt",
            "python" to "py", "typescript" to "ts", "rust" to "rs",
            "go" to "go", "html" to "html", "css" to "css", "js" to "js",
            "sql" to "sql")
        cases.forEach { (tag, ext) ->
            assertEquals(tag, ext, CodeLanguageCatalog.resolve(tag, "").extension)
        }
    }

    @Test fun recognizesUnmarkedPineAndHtmlWithoutRunningThem() {
        assertEquals("pine", CodeLanguageCatalog.resolve(null,
            "//@version=6\nindicator(\"Test\")").extension)
        assertTrue(CodeLanguageCatalog.resolve("", "<!doctype html><html></html>")
            .canPreviewHtml)
    }

    @Test fun unknownCodeIsNotAutomaticallyExecutableHtml() {
        val item = CodeLanguageCatalog.resolve("unknown", "print('hello')")
        assertEquals("txt", item.extension)
        assertFalse(item.canPreviewHtml)
    }
}
