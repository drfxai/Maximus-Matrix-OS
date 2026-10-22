package ai.drfx.maximus.matrixai.studio

/** Validation for an explicit source handoff, never a compiler or execution gate. */
object PineSourceImport {
    fun validate(source: String): String {
        require(source.isNotBlank()) { "Pine source is empty." }
        require(source.length <= LargeSource.MAX_CHARS) { "Pine source exceeds ${LargeSource.MAX_LABEL}." }
        require('\u0000' !in source) { "Pine source contains an invalid character." }
        return source
    }

    private val declaration = Regex("""(?m)^\s*(?:indicator|strategy|study|library)\s*\(""")

    /** A whole script declares indicator/strategy/library; a section-only AI reply does not. */
    fun isCompleteScript(source: String): Boolean = declaration.containsMatchIn(source)

    /**
     * Rejects replacing a complete draft with a fragment, e.g. the changed sections an AI returns
     * for a large draft, which would otherwise silently drop thousands of lines.
     */
    fun validateReplacement(current: String, replacement: String): String {
        val clean = validate(replacement)
        require(!isCompleteScript(current) || isCompleteScript(clean)) {
            "This reply holds only part of the script. Open it in Pine Studio's editor to apply the changed section."
        }
        return clean
    }

    fun title(source: String): String = Regex("""(?m)^\s*(?:indicator|strategy|library)\s*\(\s*["']([^"'\r\n]{1,60})["']""")
        .find(source)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        ?: "Chat indicator"
}

/** Finds Pine Script in chat replies, whatever fence tag (or none) the model used. Never executes it. */
object PineSourceDetector {
    private val version = Regex("""(?m)^\s*//\s*@version\s*=\s*\d""")
    private val declaration = Regex("""(?m)^\s*(?:indicator|strategy|study|library)\s*\(""")
    private val markers = Regex("""\b(?:ta\.\w+|input(?:\.\w+)?\s*\(|plot(?:shape|char|arrow)?\s*\(|strategy\.\w+|request\.security|hline\s*\(|bgcolor\s*\(|alertcondition\s*\()""")
    private val markdown = Regex("""^\s*(?:#{1,6}\s|\*\*|>\s|[-*•]\s+\S|\d+[.)]\s+[A-Za-z]|```)""")
    private val heading = Regex("""^\s*[A-Z][A-Za-z'’ ]{2,60}:\s*$""")
    private val sentence = Regex("""^\s*[A-Z][A-Za-z'’]*(?:\s+[A-Za-z0-9'’,.:;()/-]+){3,}\s*$""")

    fun looksLikePine(text: String): Boolean {
        val head = text.take(4_000)
        return version.containsMatchIn(head) || (declaration.containsMatchIn(head) && markers.containsMatchIn(text))
    }

    /**
     * Splits prose that contains an unfenced script into (text, isPine) segments, in order.
     * A script starts at `//@version=` or a top-level `indicator(`/`strategy(` line and ends where
     * markdown or an English sentence resumes.
     */
    fun splitProse(text: String): List<Pair<String, Boolean>> {
        if (!looksLikePine(text)) return listOf(text to false)
        val lines = text.lines()
        val start = lines.indexOfFirst { version.containsMatchIn(it) }.takeIf { it >= 0 }
            ?: lines.indexOfFirst { declaration.containsMatchIn(it) }.takeIf { it >= 0 }
            ?: return listOf(text to false)
        var end = lines.size
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            if (markdown.containsMatchIn(line) || heading.matches(line) || (sentence.matches(line) && '=' !in line && !line.trimStart().startsWith("//"))) {
                end = i
                break
            }
        }
        val before = lines.subList(0, start).joinToString("\n").trimEnd()
        val code = lines.subList(start, end).joinToString("\n").trimEnd()
        val after = lines.subList(end, lines.size).joinToString("\n").trim()
        if (!looksLikePine(code)) return listOf(text to false)
        return buildList {
            if (before.isNotBlank()) add(before to false)
            add(code to true)
            if (after.isNotBlank()) addAll(splitProse(after))
        }
    }
}
