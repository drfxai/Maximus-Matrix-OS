package ai.drfx.maximus.matrixai.studio

/** Validation for an explicit source handoff, never a compiler or execution gate. */
object PineSourceImport {
    fun validate(source: String): String {
        require(source.isNotBlank()) { "Pine source is empty." }
        require(source.length <= 100_000) { "Pine source exceeds 100,000 characters." }
        require('\u0000' !in source) { "Pine source contains an invalid character." }
        return source
    }

    fun title(source: String): String = Regex("""(?m)^\s*(?:indicator|strategy|library)\s*\(\s*["']([^"'\r\n]{1,60})["']""")
        .find(source)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        ?: "Chat indicator"
}
