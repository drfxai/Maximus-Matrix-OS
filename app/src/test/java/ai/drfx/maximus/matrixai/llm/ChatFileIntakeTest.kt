package ai.drfx.maximus.matrixai.llm

import ai.drfx.maximus.matrixai.llm.ChatFileIntake.Kind
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ChatFileIntakeTest {
    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
        return output.toByteArray()
    }

    @Test fun classifiesByMagicBytesBeforeExtension() {
        assertEquals(Kind.PDF, ChatFileIntake.kindOf("notes.txt", "text/plain", "%PDF-1.7\n".toByteArray()))
        assertEquals(Kind.RAR, ChatFileIntake.kindOf("a.bin", "", byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)))
        val zip = zipOf("a.txt" to "hi".toByteArray())
        assertEquals(Kind.ZIP, ChatFileIntake.kindOf("bundle.zip", "", zip))
        assertEquals(Kind.DOCX, ChatFileIntake.kindOf("report.docx", "", zip))
        assertEquals(Kind.HTML, ChatFileIntake.kindOf("page.htm", "", "<html></html>".toByteArray()))
        assertEquals(Kind.AUDIO, ChatFileIntake.kindOf("voice.opus", "", byteArrayOf(1, 2, 3)))
        assertEquals(Kind.TEXT, ChatFileIntake.kindOf("strategy.pine", "", "//@version=6".toByteArray()))
        assertEquals(Kind.UNSUPPORTED, ChatFileIntake.kindOf("blob", "application/octet-stream", byteArrayOf(0, 1, 2, 0)))
    }

    @Test fun zipBecomesReadableTextAndSkipsBinaries() {
        val digest = ChatFileIntake.zip("project.zip", zipOf(
            "src/main.py" to "print('hello')\n".toByteArray(),
            "README.md" to "# Project".toByteArray(),
            "logo.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0)))
        assertTrue(digest.text.contains("print('hello')"))
        assertTrue(digest.text.contains("# Project"))
        assertFalse(digest.text.contains("PNG"))
        assertTrue(digest.summary, digest.summary.contains("2 text files"))
    }

    @Test fun zipBombStaysBounded() {
        val big = ByteArray(30 * 1024 * 1024) { 'a'.code.toByte() }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.setLevel(Deflater.BEST_COMPRESSION)
            repeat(8) { zip.putNextEntry(ZipEntry("big$it.txt")); zip.write(big); zip.closeEntry() }
        }
        val digest = ChatFileIntake.zip("bomb.zip", output.toByteArray())
        assertEquals(0, Regex("===== big").findAll(digest.text).count())
        assertTrue(digest.text.length < ChatFileIntake.MAX_TOTAL_TEXT_CHARS + 10_000)
    }

    @Test fun wordDocumentTextIsExtracted() {
        val xml = "<w:document><w:body><w:p><w:r><w:t>Quarterly plan</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>Revenue &amp; costs</w:t></w:r></w:p></w:body></w:document>"
        val digest = ChatFileIntake.office("plan.docx", zipOf("word/document.xml" to xml.toByteArray()), Kind.DOCX)
        assertTrue(digest.text.contains("Quarterly plan"))
        assertTrue(digest.text.contains("Revenue & costs"))
        assertTrue(digest.summary.startsWith("Word"))
    }

    @Test fun pdfShowTextOperatorsAreRead() {
        val content = "BT /F1 12 Tf 72 712 Td (Moving averages smooth price data.) Tj ET"
        val pdf = "%PDF-1.4\n1 0 obj << /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n%%EOF"
        assertEquals("Moving averages smooth price data.", ChatFileIntake.pdfText(pdf.toByteArray(Charsets.ISO_8859_1))?.trim())
    }

    @Test fun binaryIsNotText() {
        assertTrue(ChatFileIntake.looksLikeText("plain ascii\nwith lines".toByteArray()))
        assertFalse(ChatFileIntake.looksLikeText(byteArrayOf(1, 0, 2, 3)))
    }
}
