package ai.drfx.maximus.matrixai.studio.connections

import java.security.MessageDigest

/** Immutable approval target. Preview limits never change the captured source sent on approval. */
internal data class SourceChangeReview(val source: GithubSourceFile, val proposed: String) {
    val sha256: String = MessageDigest.getInstance("SHA-256").digest(proposed.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun preview(): String {
        val before = source.text.lines()
        val after = proposed.lines()
        var prefix = 0
        while (prefix < minOf(before.size, after.size) && before[prefix] == after[prefix]) prefix++
        var suffix = 0
        while (suffix < minOf(before.size, after.size) - prefix && before[before.lastIndex-suffix] == after[after.lastIndex-suffix]) suffix++
        val removed = before.subList(prefix, before.size-suffix)
        val added = after.subList(prefix, after.size-suffix)
        val lines = removed.take(80).map { "- $it" } + added.take(80).map { "+ $it" }
        val body = lines.joinToString("\n")
        return "Change starts at line ${prefix+1}; removed ${removed.size}, added ${added.size}\n" +
            body.take(20_000) + if (removed.size > 80 || added.size > 80 || body.length > 20_000)
                "\nPREVIEW TRUNCATED — review full source in editor before approval." else ""
    }
}
