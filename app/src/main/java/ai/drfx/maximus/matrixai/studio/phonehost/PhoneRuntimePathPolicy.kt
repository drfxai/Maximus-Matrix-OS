package ai.drfx.maximus.matrixai.studio.phonehost

/** Pure, testable Unix guest path resolver: never follows Android-host symlinks. */
internal object PhoneRuntimePathPolicy {
    // A colon is a legal Unix filename character (dpkg uses package:arm64).
    // Reject Windows drive syntax at the beginning, not every Unix colon.
    private val drivePrefix = Regex("^[A-Za-z]:")
    private fun components(path: String): List<String> {
        val parts = mutableListOf<String>()
        for (p in path.split('/')) when (p) {
            "", "." -> Unit
            ".." -> {
                check(parts.isNotEmpty()) { "Archive path escaped the guest root." }
                parts.removeAt(parts.lastIndex)
            }
            else -> {
                check(p.none { it == '\\' || it == '\u0000' })
                parts.add(p)
            }
        }
        return parts
    }

    fun member(input: String): String {
        var value = input
        while (value.startsWith("./")) value = value.substring(2)
        require(value.length <= 512 && !value.startsWith("/") &&
            '\\' !in value && '\u0000' !in value && !drivePrefix.containsMatchIn(value)) {
            "Unsafe Linux archive member."
        }
        return components(value).joinToString("/")
    }

    fun linkTarget(guest: String, link: String): String {
        require(link.length in 1..512 && '\\' !in link && '\u0000' !in link &&
            !drivePrefix.containsMatchIn(link)) { "Invalid Linux symlink." }
        val path = if (link.startsWith("/")) link else
            guest.substringBeforeLast('/', "").let { if (it.isEmpty()) link else it + "/" + link }
        return components(path).joinToString("/")
    }

    fun mappedRelative(guest: String, links: Map<String, String>): String {
        var parts = components(guest)
        var attempts = 0
        while (true) {
            check(attempts++ < 24) { "Recursive runtime symlink." }
            var replaced = false
            // Resolve every PARENT link but not the final file/link itself.
            for (i in 1 until parts.size) {
                val prefix = parts.take(i).joinToString("/")
                val link = links[prefix] ?: continue
                parts = components(linkTarget(prefix, link) + "/" + parts.drop(i).joinToString("/"))
                replaced = true
                break
            }
            if (!replaced) break
        }
        require(parts.isNotEmpty()) { "Empty guest target." }
        return parts.joinToString("/")
    }
}
