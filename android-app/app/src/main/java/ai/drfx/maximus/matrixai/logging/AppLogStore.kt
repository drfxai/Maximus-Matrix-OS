package ai.drfx.maximus.matrixai.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppLogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val level: String,
    val source: String,
    val message: String
)

object AppLogStore {
    private val _entries = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val entries: StateFlow<List<AppLogEntry>> = _entries.asStateFlow()

    fun info(source: String, message: String) = append("INFO", source, message)
    fun warn(source: String, message: String) = append("WARN", source, message)
    fun error(source: String, message: String) = append("ERROR", source, message)

    fun clear() {
        _entries.value = emptyList()
    }

    fun exportText(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return buildString {
            appendLine("MAXIMUS AI Diagnostic Log")
            appendLine("Generated: " + format.format(Date()))
            appendLine("Sensitive credentials are redacted.")
            appendLine()
            _entries.value.reversed().forEach { entry ->
                append(format.format(Date(entry.timestampMs)))
                append(" [")
                append(entry.level)
                append("] ")
                append(entry.source)
                append(": ")
                appendLine(redact(entry.message))
            }
        }
    }

    private fun append(level: String, source: String, message: String) {
        val safe = redact(message)
        _entries.value = (_entries.value + AppLogEntry(level = level, source = source, message = safe)).takeLast(500)
    }

    private fun redact(input: String): String {
        return input
            .replace(Regex("""nvapi-[A-Za-z0-9_-]{12,}"""), "nvapi-[REDACTED]")
            .replace(Regex("""sk-[A-Za-z0-9_-]{12,}"""), "sk-[REDACTED]")
            .replace(Regex("""Bearers+[A-Za-z0-9._-]{12,}""", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
            .replace(Regex("""(?i)(api[_ -]?key|token|authorization)s*[:=]s*[^s,;]+""")) {
                it.groupValues[1] + "=[REDACTED]"
            }
    }
}
