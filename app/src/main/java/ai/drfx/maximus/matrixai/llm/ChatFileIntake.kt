package ai.drfx.maximus.matrixai.llm

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/**
 * Turns a picked file into something a chat model can read. Nothing here executes, renders or
 * writes the file: archives and office documents are read in memory with strict size limits and
 * only their text is extracted.
 */
object ChatFileIntake {
    enum class Kind { TEXT, HTML, IMAGE, PDF, AUDIO, ZIP, RAR, SEVEN_ZIP, TAR, GZIP, DOCX, PPTX, XLSX, UNSUPPORTED }

    /** Limits for archive expansion; they bound memory, not just what is shown to the model. */
    const val MAX_ARCHIVE_ENTRIES = 600
    const val MAX_EXPANDED_BYTES = 24L * 1024 * 1024
    const val MAX_FILE_TEXT_CHARS = 400_000
    const val MAX_TOTAL_TEXT_CHARS = 900_000

    private val textExtensions = setOf(
        "txt", "md", "markdown", "rst", "log", "csv", "tsv", "json", "jsonc", "xml", "yaml", "yml", "toml", "ini", "cfg",
        "conf", "properties", "env", "gitignore", "dockerfile", "makefile", "gradle", "kts", "kt", "java", "py", "pyw",
        "ipynb", "c", "h", "cc", "cpp", "cxx", "hpp", "hh", "cs", "go", "rs", "swift", "dart", "rb", "php", "js", "mjs",
        "cjs", "jsx", "ts", "tsx", "vue", "svelte", "css", "scss", "sass", "less", "sql", "sh", "bash", "zsh", "ps1",
        "bat", "cmd", "lua", "r", "m", "scala", "pl", "pine", "pinescript", "mq4", "mq5", "mqh", "svg", "tex", "srt", "vtt"
    )
    private val htmlExtensions = setOf("html", "htm", "xhtml")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")
    private val audioExtensions = mapOf(
        "mp3" to "audio/mpeg", "wav" to "audio/wav", "m4a" to "audio/m4a", "mp4a" to "audio/m4a", "aac" to "audio/aac",
        "ogg" to "audio/ogg", "oga" to "audio/ogg", "opus" to "audio/ogg", "flac" to "audio/flac", "webm" to "audio/webm",
        "amr" to "audio/amr", "3gp" to "audio/3gpp"
    )

    fun extension(name: String): String = name.substringAfterLast('/').let {
        if ('.' in it) it.substringAfterLast('.').lowercase() else it.lowercase()
    }

    /** Classifies by magic bytes first, then extension, then the provider-reported MIME type. */
    fun kindOf(name: String, mimeType: String, head: ByteArray): Kind {
        val ext = extension(name)
        fun startsWith(vararg bytes: Int) = head.size >= bytes.size && bytes.indices.all { head[it] == bytes[it].toByte() }
        val ascii = String(head.copyOfRange(0, minOf(head.size, 16)), Charsets.ISO_8859_1)
        return when {
            ascii.startsWith("%PDF-") -> Kind.PDF
            startsWith(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> Kind.RAR
            startsWith(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> Kind.SEVEN_ZIP
            startsWith(0x50, 0x4B, 0x03, 0x04) || startsWith(0x50, 0x4B, 0x05, 0x06) -> when (ext) {
                "docx" -> Kind.DOCX
                "pptx" -> Kind.PPTX
                "xlsx" -> Kind.XLSX
                else -> Kind.ZIP
            }
            startsWith(0x1F, 0x8B) -> if (name.lowercase().let { it.endsWith(".tar.gz") || it.endsWith(".tgz") }) Kind.TAR else Kind.GZIP
            head.size > 262 && String(head.copyOfRange(257, 262), Charsets.ISO_8859_1) == "ustar" -> Kind.TAR
            ext in htmlExtensions || mimeType == "text/html" -> Kind.HTML
            ext in imageExtensions || mimeType.startsWith("image/") && mimeType != "image/svg+xml" -> Kind.IMAGE
            ext in audioExtensions || mimeType.startsWith("audio/") -> Kind.AUDIO
            ext in textExtensions || mimeType.startsWith("text/") || mimeType in setOf("application/json",
                "application/xml", "application/javascript", "image/svg+xml", "application/x-sh") -> Kind.TEXT
            looksLikeText(head) -> Kind.TEXT
            else -> Kind.UNSUPPORTED
        }
    }

    fun audioMime(name: String, reported: String): String =
        audioExtensions[extension(name)] ?: reported.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"

    /** A sample is text when it is valid-looking UTF-8 without NULs or many control characters. */
    fun looksLikeText(sample: ByteArray): Boolean {
        if (sample.isEmpty()) return false
        if (sample.any { it == 0.toByte() }) return false
        val text = String(sample, Charsets.UTF_8)
        val bad = text.count { it == '\uFFFD' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') }
        return bad <= text.length / 50
    }

    /** MIME type the chat adapters accept for a text payload of this file. */
    fun textMime(name: String): String = when (extension(name)) {
        "json", "jsonc", "ipynb" -> "application/json"
        "xml", "svg" -> "application/xml"
        "html", "htm", "xhtml" -> "text/html"
        "md", "markdown" -> "text/markdown"
        "csv" -> "text/csv"
        else -> "text/plain"
    }

    data class Digest(val text: String, val summary: String)

    private class Budget {
        var expanded = 0L
        var entries = 0
        val out = StringBuilder()
        val skipped = mutableListOf<String>()
        var textFiles = 0
        var truncated = false
    }

    private fun readBounded(stream: InputStream, budget: Budget, limit: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            budget.expanded += read
            require(budget.expanded <= MAX_EXPANDED_BYTES) { "The archive expands beyond the 24 MB safety limit." }
            if (output.size() + read > limit) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun addEntry(path: String, size: Long, open: () -> InputStream?, budget: Budget) {
        budget.entries++
        require(budget.entries <= MAX_ARCHIVE_ENTRIES) { "The archive has more than $MAX_ARCHIVE_ENTRIES entries." }
        val clean = path.replace('\\', '/').trimStart('/')
        if (clean.endsWith("/") || clean.split('/').any { it.startsWith("__MACOSX") || it == ".git" }) return
        val ext = extension(clean)
        val maybeText = ext in textExtensions || ext in htmlExtensions || ('.' !in clean.substringAfterLast('/'))
        if (!maybeText || size > MAX_FILE_TEXT_CHARS * 4L || budget.out.length >= MAX_TOTAL_TEXT_CHARS) {
            budget.skipped += clean + (if (size >= 0) " (" + sizeLabel(size) + ")" else "")
            if (budget.out.length >= MAX_TOTAL_TEXT_CHARS) budget.truncated = true
            return
        }
        val bytes = open()?.use { readBounded(it, budget, MAX_FILE_TEXT_CHARS * 4L) }
        if (bytes == null || !looksLikeText(bytes.copyOfRange(0, minOf(bytes.size, 4096)))) {
            budget.skipped += clean
            return
        }
        var text = String(bytes, Charsets.UTF_8)
        if (text.length > MAX_FILE_TEXT_CHARS) { text = text.take(MAX_FILE_TEXT_CHARS); budget.truncated = true }
        val room = MAX_TOTAL_TEXT_CHARS - budget.out.length
        if (text.length > room) { text = text.take(room.coerceAtLeast(0)); budget.truncated = true }
        budget.out.append("\n===== ").append(clean).append(" =====\n").append(text).append('\n')
        budget.textFiles++
    }

    private fun finish(label: String, name: String, budget: Budget): Digest {
        val header = StringBuilder("$label archive “$name”: ${budget.textFiles} text file(s) included")
        if (budget.skipped.isNotEmpty()) header.append(", ${budget.skipped.size} other file(s) listed only")
        if (budget.truncated) header.append("; long content was truncated to fit")
        header.append(".\n")
        if (budget.skipped.isNotEmpty()) {
            header.append("Other files:\n")
            budget.skipped.take(200).forEach { header.append(" - ").append(it).append('\n') }
            if (budget.skipped.size > 200) header.append(" - … ${budget.skipped.size - 200} more\n")
        }
        return Digest(header.append(budget.out).toString(),
            "$label · ${budget.textFiles} text file" + (if (budget.textFiles == 1) "" else "s") +
                (if (budget.skipped.isNotEmpty()) " · ${budget.skipped.size} other" else ""))
    }

    fun zip(name: String, bytes: ByteArray): Digest {
        val budget = Budget()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) addEntry(entry.name, entry.size, { NonClosing(zip) }, budget)
            }
        }
        return finish("ZIP", name, budget)
    }

    fun rar(name: String, bytes: ByteArray): Digest {
        val budget = Budget()
        try {
            com.github.junrar.Archive(ByteArrayInputStream(bytes)).use { archive ->
                require(!archive.isEncrypted) { "This RAR archive is password protected." }
                for (header in archive.fileHeaders) {
                    if (header.isDirectory) continue
                    addEntry(header.fileName, header.fullUnpackSize, { archive.getInputStream(header) }, budget)
                }
            }
        } catch (_: com.github.junrar.exception.UnsupportedRarV5Exception) {
            throw IllegalArgumentException("RAR5 archives cannot be opened on the phone yet. Re-save it as ZIP or RAR4.")
        } catch (error: com.github.junrar.exception.RarException) {
            throw IllegalArgumentException("This RAR archive could not be read: " + (error.message ?: "unsupported format"))
        }
        return finish("RAR", name, budget)
    }

    fun sevenZip(name: String, bytes: ByteArray): Digest {
        val budget = Budget()
        org.apache.commons.compress.archivers.sevenz.SevenZFile.builder()
            .setSeekableByteChannel(org.apache.commons.compress.utils.SeekableInMemoryByteChannel(bytes)).get().use { archive ->
                for (entry in archive.entries) {
                    if (entry.isDirectory || !entry.hasStream()) continue
                    addEntry(entry.name, entry.size, { archive.getInputStream(entry) }, budget)
                }
            }
        return finish("7-Zip", name, budget)
    }

    fun tar(name: String, bytes: ByteArray, gzipped: Boolean): Digest {
        val budget = Budget()
        val raw: InputStream = ByteArrayInputStream(bytes)
        val source = if (gzipped) org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(raw) else raw
        org.apache.commons.compress.archivers.tar.TarArchiveInputStream(source).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (entry.isFile) addEntry(entry.name, entry.size, { NonClosing(tar) }, budget)
            }
        }
        return finish("TAR", name, budget)
    }

    fun gzip(name: String, bytes: ByteArray): Digest {
        val budget = Budget()
        val inner = name.removeSuffix(".gz").removeSuffix(".GZ")
        addEntry(inner, -1, { java.util.zip.GZIPInputStream(ByteArrayInputStream(bytes)) }, budget)
        return finish("GZIP", name, budget)
    }

    /** Plain text of Word, PowerPoint and Excel (OOXML) files, read from their XML parts. */
    fun office(name: String, bytes: ByteArray, kind: Kind): Digest {
        val parts = sortedMapOf<String, String>(compareBy<String>({ it.length }, { it }))
        val budget = Budget()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val wanted = when (kind) {
                    Kind.DOCX -> entry.name == "word/document.xml"
                    Kind.PPTX -> entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")
                    else -> entry.name == "xl/sharedStrings.xml" || (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml"))
                }
                if (wanted) readBounded(NonClosing(zip), budget, 8L * 1024 * 1024)?.let { parts[entry.name] = String(it, Charsets.UTF_8) }
            }
        }
        val text = StringBuilder()
        parts.forEach { (part, xml) ->
            when (kind) {
                Kind.DOCX -> text.append(xmlText(xml.replace("</w:p>", "\n</w:p>").replace("<w:tab/>", "\t")))
                Kind.PPTX -> text.append("--- ").append(part.substringAfterLast('/').removeSuffix(".xml")).append(" ---\n")
                    .append(Regex("<a:t>([^<]*)</a:t>").findAll(xml).joinToString("\n") { unescape(it.groupValues[1]) }).append("\n\n")
                else -> if (part.endsWith("sharedStrings.xml"))
                    text.append(Regex("<t[^>]*>([^<]*)</t>").findAll(xml).joinToString("\n") { unescape(it.groupValues[1]) }).append('\n')
                else text.append("--- ").append(part.substringAfterLast('/').removeSuffix(".xml")).append(" values ---\n")
                    .append(Regex("<v>([^<]*)</v>").findAll(xml).take(20_000).joinToString(" ") { it.groupValues[1] }).append('\n')
            }
            if (text.length > MAX_TOTAL_TEXT_CHARS) return@forEach
        }
        val label = when (kind) { Kind.DOCX -> "Word"; Kind.PPTX -> "PowerPoint"; else -> "Excel" }
        val body = text.toString().take(MAX_TOTAL_TEXT_CHARS).trim()
        require(body.isNotBlank()) { "No readable text was found in this $label file." }
        return Digest("$label document “$name”:\n$body", "$label · " + sizeLabel(body.length.toLong()) + " text")
    }

    /**
     * Best-effort text from a PDF's content streams (Tj/TJ string operators, Flate streams).
     * Returns null when the text looks like font-encoded noise rather than words.
     */
    fun pdfText(bytes: ByteArray): String? {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val out = StringBuilder()
        var index = 0
        while (out.length < MAX_TOTAL_TEXT_CHARS) {
            val start = raw.indexOf("stream", index)
            if (start < 0) break
            var dataStart = start + 6
            if (raw.startsWith("\r\n", dataStart)) dataStart += 2 else if (raw.startsWith("\n", dataStart)) dataStart += 1
            val end = raw.indexOf("endstream", dataStart)
            if (end < 0) break
            val dictionary = raw.substring(maxOf(0, start - 400), start)
            val data = bytes.copyOfRange(dataStart, end)
            index = end + 9
            if ("/Image" in dictionary || "/XObject" in dictionary && "/Form" !in dictionary) continue
            val content = if ("FlateDecode" in dictionary.substringAfterLast("<<")) inflate(data) ?: continue else data
            extractShowText(String(content, Charsets.ISO_8859_1), out)
        }
        val text = out.toString().replace(Regex("[ \\t]+\n"), "\n").replace(Regex("\n{3,}"), "\n\n").trim()
        if (text.length < 20) return null
        val letters = text.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,;:!?()-'\"%$/" }
        return if (letters >= text.length * 0.85) text else null
    }

    private fun inflate(data: ByteArray): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(data)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            output.write(buffer, 0, count)
            if (output.size() > 8 * 1024 * 1024) break
        }
        inflater.end()
        output.toByteArray()
    }.getOrNull()

    private fun extractShowText(content: String, out: StringBuilder) {
        if ("BT" !in content) return
        var i = 0
        val line = StringBuilder()
        while (i < content.length) {
            val c = content[i]
            when {
                c == '(' -> {
                    var depth = 1
                    i++
                    while (i < content.length && depth > 0) {
                        val ch = content[i]
                        when {
                            ch == '\\' && i + 1 < content.length -> {
                                val next = content[i + 1]
                                when (next) {
                                    'n' -> line.append('\n'); 'r' -> {}; 't' -> line.append('\t')
                                    '(', ')', '\\' -> line.append(next)
                                    in '0'..'7' -> {
                                        var octal = ""
                                        var k = i + 1
                                        while (k < content.length && octal.length < 3 && content[k] in '0'..'7') { octal += content[k]; k++ }
                                        line.append(octal.toInt(8).toChar())
                                        i = k - 2
                                    }
                                    else -> {}
                                }
                                i += 2
                                continue
                            }
                            ch == '(' -> { depth++; line.append(ch) }
                            ch == ')' -> { depth--; if (depth > 0) line.append(ch) }
                            else -> line.append(ch)
                        }
                        i++
                    }
                    continue
                }
                content.startsWith("ET", i) && (i + 2 >= content.length || !content[i + 2].isLetter()) -> {
                    if (line.isNotBlank()) out.append(line.toString().trim()).append('\n')
                    line.clear()
                }
                (content.startsWith("Td", i) || content.startsWith("TD", i) || content.startsWith("T*", i)) && line.isNotEmpty() -> {
                    out.append(line.toString().trimEnd()).append('\n'); line.clear()
                }
            }
            i++
        }
        if (line.isNotBlank()) out.append(line.toString().trim()).append('\n')
    }

    private fun xmlText(xml: String): String = unescape(xml.replace(Regex("<[^>]+>"), "")).replace(Regex("\n{3,}"), "\n\n")

    private fun unescape(text: String) = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")

    fun sizeLabel(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /** Lets a per-entry reader close without closing the archive stream underneath it. */
    private class NonClosing(private val source: InputStream) : InputStream() {
        override fun read(): Int = source.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, len)
        override fun close() {}
    }
}
