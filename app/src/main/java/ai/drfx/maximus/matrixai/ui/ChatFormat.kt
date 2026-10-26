package ai.drfx.maximus.matrixai.ui

import java.util.Locale

/** Shortest readable model label: drops vendor folders and dated suffixes, keeps the version. */
internal fun shortModelName(id: String): String {
    if (id.isBlank()) return ""
    val base = id.substringAfterLast('/').substringAfter("models/")
    return base.replace(Regex("-(\\d{8}|\\d{4}-\\d{2}-\\d{2})$"), "").take(36)
}

internal fun compactTokens(count: Long): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
    count >= 10_000 -> String.format(Locale.US, "%dk", count / 1_000)
    count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0).replace(".0k", "k")
    else -> count.toString()
}
