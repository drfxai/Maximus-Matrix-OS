package ai.drfx.maximus.matrixai.studio.phonehost

/**
 * Immutable, verified GitHub release asset metadata. NEVER relax checksum checks on failures.
 * Source: techjarves/Mobile-Harness runtime-2026.09.4 GitHub release asset digests.
 */
internal object PhoneRuntimeBundlePins {
    const val RELEASE = "runtime-2026.09.4"
    const val OWNER_REPO = "techjarves/Mobile-Harness"

    data class Bundle(
        val title: String,
        val name: String,
        val size: Long,
        val hash: String,
        val expandedLimit: Long
    ) {
        init {
            require(size > 0L && expandedLimit >= size)
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "Invalid pinned SHA-256" }
        }
    }

    val ALL = listOf(
        Bundle(
            "Linux base", "pocketdev-core-arm64-2026.09.5.tar.zst",
            72_185_773L,
            "df0cf7251c74f82d424231e3804114a4ca66b16130eea9abab11e220dc7ac012",
            600_000_000L
        ),
        Bundle(
            "Android SDK/Gradle", "pocketdev-android-arm64-2026.09.1.tar.zst",
            569_652_007L,
            "01bea058ebcb17416d1eb08c0211b3782da3228eb3c8348eb3719f2d61dd3ec6",
            1_400_000_000L
        )
    )

    init {
        require(ALL.map { it.name }.distinct().size == ALL.size)
    }
}
