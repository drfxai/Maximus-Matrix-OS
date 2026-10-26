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

    @Test fun pineMislabeledAsAnotherLanguageIsStillPine() {
        val source = "//@version=5\nindicator(\"EMA\", overlay=true)\nplot(ta.ema(close, 9))"
        listOf("javascript", "js", "c", "text", "pine-script").forEach { tag ->
            assertEquals(tag, "pine", CodeLanguageCatalog.resolve(tag, source).extension)
        }
        assertEquals("js", CodeLanguageCatalog.resolve("js", "const plot = () => 1").extension)
    }
}

class CodeLanguageDetectionTest {
    @Test fun untaggedCodeIsDetectedByContent() {
        assertEquals("py", CodeLanguageCatalog.detect("import os\n\ndef main():\n    print(os.getcwd())\n\nif __name__ == '__main__':\n    main()\n")?.extension)
        assertEquals("java", CodeLanguageCatalog.detect("public class Hello {\n  public static void main(String[] args) {\n    System.out.println(\"hi\");\n  }\n}")?.extension)
        assertEquals("c", CodeLanguageCatalog.detect("#include <stdio.h>\n\nint main(void) {\n  printf(\"hi\\n\");\n  return 0;\n}")?.extension)
        assertEquals("pine", CodeLanguageCatalog.detect("//@version=6\nindicator(\"RSI\")\nplot(ta.rsi(close, 14))")?.extension)
        assertEquals("json", CodeLanguageCatalog.detect("{\n  \"name\": \"demo\",\n  \"version\": 1\n}")?.extension)
    }

    @Test fun proseIsNotCode() {
        assertNull(CodeLanguageCatalog.detect("This is a plain sentence about the market today."))
    }

    @Test fun genericFenceUsesContent() {
        assertEquals("py", CodeLanguageCatalog.resolve("text", "def f(x):\n    return x\n\nfor i in range(3):\n    print(f(i))").extension)
        assertEquals("py", CodeLanguageCatalog.forFileName("bot.py")?.extension)
    }
}
