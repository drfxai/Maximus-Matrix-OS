package ai.drfx.maximus.matrixai.studio.phonehost

import org.junit.Assert.*
import org.junit.Test

/** Regression: a previously mistyped Android SDK checksum rejected genuine downloads. */
class PhoneRuntimeBundlePinsTest {
    @Test fun pinnedGithubReleaseMetadataMatchesPublisherDeclaredHashes() {
        assertEquals("runtime-2026.09.4", PhoneRuntimeBundlePins.RELEASE)
        val core = PhoneRuntimeBundlePins.ALL.single { it.name == "pocketdev-core-arm64-2026.09.5.tar.zst" }
        val android = PhoneRuntimeBundlePins.ALL.single { it.name == "pocketdev-android-arm64-2026.09.1.tar.zst" }
        assertEquals(72_185_773L, core.size)
        assertEquals("df0cf7251c74f82d424231e3804114a4ca66b16130eea9abab11e220dc7ac012", core.hash)
        assertEquals(569_652_007L, android.size)
        assertEquals("01bea058ebcb17416d1eb08c0211b3782da3228eb3c8348eb3719f2d61dd3ec6", android.hash)
        assertTrue(PhoneRuntimeBundlePins.ALL.all { it.hash.length == 64 })
    }
}
