package ai.drfx.maximus.matrixai.terminal

/**
 * Command palette for the Matrix terminal workspace. This is intentionally NOT a shell.
 * The only executable path is an existing policy-gated Matrix mission.
 * Third-party command text and model output never gain implicit OS permissions.
 */
sealed interface TerminalIntent {
    data object Help : TerminalIntent
    data object Status : TerminalIntent
    data object Model : TerminalIntent
    data object Events : TerminalIntent
    data object Tools : TerminalIntent
    data object Clear : TerminalIntent
    data object History : TerminalIntent
    data object Team : TerminalIntent
    data object SmartCombo : TerminalIntent
    data class Mission(val objective: String) : TerminalIntent
    data class Unsupported(val name: String) : TerminalIntent
    data object ShellBlocked : TerminalIntent
}

object TerminalCommandPolicy {
    private const val MAX_OBJECTIVE = 4_000
    private val shellPrefixes = Regex(
        """^(?:\$\s*|\.\/\S+|(?:sudo|su|rm|curl|wget|ssh|scp|adb|bash|sh|zsh|ls|cd|pwd|cat|chmod|chown|python[0-9.]*|pip[0-9.]*|npm|npx|node|git|gradle)(?:\s|$))""",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String): TerminalIntent {
        val text = input.trim()
        if (text.isEmpty()) return TerminalIntent.Unsupported("")
        if (text.length > MAX_OBJECTIVE) return TerminalIntent.Unsupported("too_long")
        if (text.startsWith("/")) {
            val head = text.substringBefore(' ').lowercase()
            val tail = text.substringAfter(' ', "").trim()
            return when (head) {
                "/help" -> TerminalIntent.Help
                "/status" -> TerminalIntent.Status
                "/model" -> TerminalIntent.Model
                "/events" -> TerminalIntent.Events
                "/tools" -> TerminalIntent.Tools
                "/clear" -> TerminalIntent.Clear
                "/history" -> TerminalIntent.History
                "/team" -> TerminalIntent.Team
                "/combo" -> TerminalIntent.SmartCombo
                "/mission" -> if (tail.isBlank()) TerminalIntent.Unsupported("mission_requires_text")
                              else TerminalIntent.Mission(tail)
                else -> TerminalIntent.Unsupported(head.take(48))
            }
        }
        // A pasted Unix command must not silently become an agent instruction.
        if (shellPrefixes.containsMatchIn(text)) return TerminalIntent.ShellBlocked
        return TerminalIntent.Mission(text)
    }

    fun redact(value: String): String = value
        .replace(Regex("""(?i)Bearer\s+[a-z0-9._~+/=-]{8,}"""), "Bearer [REDACTED]")
        .replace(Regex("""(?i)(?:sk-|nvapi-|ghp_|gho_)[a-z0-9_-]{10,}"""), "[REDACTED_KEY]")
        .replace(Regex("""(?i)(api[_-]?key|access[_-]?token|authorization)\s*[:=]\s*[^\s,;&]+""")) {
            it.groupValues[1] + "=[REDACTED]"
        }
}
