package ai.drfx.maximus.matrixai.studio

/**
 * Shared limits and helpers for very large sources (10k+ line indicators and project files).
 * Pure text utilities: nothing here parses, compiles or executes the source.
 */
object LargeSource {
    /** About 20k lines of typical Pine; drafts, imports, the engine and editors all share it. */
    const val MAX_CHARS = 1_000_000
    const val MAX_LABEL = "1,000,000 characters"
    /** Saved revisions are snapshots of the full source, so they share one character budget. */
    const val REVISION_BUDGET_CHARS = 2_000_000
    /** Sources up to this size are sent to the AI whole; larger ones get an outline and focused windows. */
    const val FULL_PROMPT_CHARS = 160_000

    /** Line start offsets for O(log n) offset-to-line lookups on huge texts. */
    class LineIndex(private val text: String) {
        private val starts: IntArray = run {
            var count = 1
            for (c in text) if (c == '\n') count++
            val result = IntArray(count)
            var line = 1
            for (i in text.indices) if (text[i] == '\n') result[line++] = i + 1
            result
        }
        val lineCount: Int get() = starts.size

        /** 1-based line number containing [offset]. */
        fun lineOf(offset: Int): Int {
            val target = offset.coerceIn(0, text.length)
            var low = 0
            var high = starts.size - 1
            while (low < high) {
                val mid = (low + high + 1) ushr 1
                if (starts[mid] <= target) low = mid else high = mid - 1
            }
            return low + 1
        }

        /** Offset of the first character of 1-based [line], clamped to the text. */
        fun startOf(line: Int): Int = starts[(line - 1).coerceIn(0, starts.size - 1)]

        /** Text of 1-based [line] without its line break. */
        fun line(line: Int): String {
            val index = (line - 1).coerceIn(0, starts.size - 1)
            val end = if (index + 1 < starts.size) starts[index + 1] - 1 else text.length
            return text.substring(starts[index], end).removeSuffix("\r")
        }
    }

    /** Next match of [query] at or after [from], wrapping to the start; -1 when absent. */
    fun find(text: String, query: String, from: Int, ignoreCase: Boolean = true): Int {
        if (query.isEmpty() || text.isEmpty()) return -1
        val start = from.coerceIn(0, text.length)
        val hit = text.indexOf(query, start, ignoreCase)
        return if (hit >= 0) hit else text.indexOf(query, 0, ignoreCase).takeIf { it in 0 until start } ?: -1
    }

    /** Previous match of [query] strictly before [before], wrapping to the end; -1 when absent. */
    fun findPrevious(text: String, query: String, before: Int, ignoreCase: Boolean = true): Int {
        if (query.isEmpty() || text.isEmpty()) return -1
        val hit = if (before <= 0) -1 else text.lastIndexOf(query, before - 1, ignoreCase)
        return if (hit >= 0) hit else text.lastIndexOf(query, text.length, ignoreCase).takeIf { it >= before } ?: -1
    }

    fun countMatches(text: String, query: String, ignoreCase: Boolean = true, limit: Int = 9_999): Int {
        if (query.isEmpty()) return 0
        var count = 0
        var index = text.indexOf(query, 0, ignoreCase)
        while (index >= 0 && count < limit) {
            count++
            index = text.indexOf(query, index + query.length, ignoreCase)
        }
        return count
    }

    private val outlineLine = Regex(
        """^\s*(?://\s*(?:#region|@function|={3,}|-{3,}|#{2,})|(?:export\s+)?(?:method|type|enum)\s+\w|""" +
            """(?:indicator|strategy|library)\s*\(|[A-Za-z_]\w*\s*\([^()]*\)\s*=>|""" +
            """(?:export\s+)?(?:fun|func|function|def|class|object|interface|struct)\s+\w)"""
    )

    /** Declarations and section markers with their 1-based line numbers, for navigation and prompts. */
    fun outline(text: String, limit: Int = 400): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        var lineNo = 0
        for (line in text.lineSequence()) {
            lineNo++
            if (outlineLine.containsMatchIn(line)) {
                result += lineNo to line.trim().take(120)
                if (result.size >= limit) break
            }
        }
        return result
    }

    /**
     * Source context for an AI edit request. Small sources are sent whole. Large ones get an outline,
     * the header, the windows that mention the request's identifiers, and the tail, all line-numbered,
     * so the model can return targeted replacements instead of a truncated rewrite.
     */
    fun promptContext(source: String, request: String, budgetChars: Int = FULL_PROMPT_CHARS): PromptContext {
        val index = LineIndex(source)
        if (source.length <= budgetChars) return PromptContext(source, complete = true, lines = index.lineCount)
        val keywords = Regex("[A-Za-z_][A-Za-z0-9_.]{3,}").findAll(request).map { it.value.lowercase() }
            .filterNot { it in stopWords }.distinct().take(12).toList()
        val chosen = sortedSetOf<Int>()
        (1..minOf(80, index.lineCount)).forEach(chosen::add)
        (maxOf(1, index.lineCount - 40)..index.lineCount).forEach(chosen::add)
        val windowBudget = budgetChars / 2
        var used = 0
        if (keywords.isNotEmpty()) for (line in 1..index.lineCount) {
            val text = index.line(line).lowercase()
            if (keywords.any { it in text }) {
                for (n in maxOf(1, line - 12)..minOf(index.lineCount, line + 12)) if (chosen.add(n)) used += index.line(n).length + 8
                if (used > windowBudget) break
            }
        }
        val body = StringBuilder()
        body.append("Outline (line: declaration):\n")
        outline(source, 250).forEach { (line, text) -> body.append(line).append(": ").append(text).append('\n') }
        body.append("\nExcerpts (line|source); omitted ranges are marked:\n")
        var previous = 0
        for (line in chosen) {
            if (body.length > budgetChars) { body.append("… excerpt budget reached …\n"); break }
            if (line > previous + 1) body.append("… lines ${previous + 1}-${line - 1} omitted …\n")
            body.append(line).append('|').append(index.line(line)).append('\n')
            previous = line
        }
        if (previous < index.lineCount && body.length <= budgetChars) body.append("… lines ${previous + 1}-${index.lineCount} omitted …\n")
        return PromptContext(body.toString(), complete = false, lines = index.lineCount)
    }

    data class PromptContext(val text: String, val complete: Boolean, val lines: Int)

    /** How many of the newest revisions fit the shared snapshot budget (always at least one). */
    fun revisionsWithinBudget(sizes: List<Int>, budget: Int = REVISION_BUDGET_CHARS, maxCount: Int = 30): Int {
        var total = 0L
        var count = 0
        for (size in sizes.take(maxCount)) {
            if (count > 0 && total + size > budget) break
            total += size
            count++
        }
        return count
    }

    fun sizeLabel(text: String): String {
        val lines = text.count { it == '\n' } + if (text.isEmpty()) 0 else 1
        val kb = text.length / 1024.0
        return "%,d lines · %s".format(lines, if (kb >= 1024) "%.1f MB".format(kb / 1024) else "%.0f KB".format(kb.coerceAtLeast(1.0)))
    }

    private val stopWords = setOf("this", "that", "with", "from", "into", "make", "please", "change", "should",
        "would", "could", "when", "then", "than", "also", "only", "line", "lines", "code", "script", "pine",
        "indicator", "strategy", "add", "remove", "update", "fix", "the", "and", "for")
}
