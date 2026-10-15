package ai.drfx.maximus.matrixai.memory

/**
 * Journal entries are appended oldest to newest. Put the newest eligible result
 * first before applying any prompt budget, so a model handoff retains current work.
 * Callers must apply project, age and shareability checks before assembly.
 */
internal object ProjectProgressContextPolicy {
    const val MAX_CONTEXT_CHARS = 2100

    fun newestFirst(chronologicalEntries: List<String>, maxChars: Int = MAX_CONTEXT_CHARS): String {
        require(maxChars in 0..MAX_CONTEXT_CHARS)
        val result = StringBuilder(maxChars)
        for (entry in chronologicalEntries.asReversed()) {
            if (entry.isBlank()) continue
            val separator = if (result.isEmpty()) "" else "\n"
            val available = maxChars - result.length - separator.length
            if (available <= 0) break
            result.append(separator).append(entry, 0, minOf(entry.length, available))
        }
        return result.toString()
    }
}
