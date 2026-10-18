package ai.drfx.maximus.matrixai.studio.phonehost

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class PhoneRuntimeArchiveExtractorTest {
    private val extractor = PhoneRuntimeArchiveExtractor({ false }, { _, _ -> },
        { link, file -> Files.createSymbolicLink(file.toPath(), File(link).toPath()); Unit })

    private fun archive(vararg entries: Pair<TarArchiveEntry, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            TarArchiveOutputStream(bytes).use { tar ->
                for ((entry, content) in entries) {
                    entry.size = content.size.toLong()
                    tar.putArchiveEntry(entry); tar.write(content); tar.closeArchiveEntry()
                }
            }
        }.toByteArray()

    private fun unpack(bytes: ByteArray, root: File, limit: Long = 100_000L) =
        extractor.unpack({ TarArchiveInputStream(ByteArrayInputStream(bytes)) }, root,
            linkedMapOf(), limit)

    @Test fun multiarchMetadataAndGuestSymlinkParentsExtractTogether() {
        val root = Files.createTempDirectory("runtime-colon").toFile()
        try {
            val link = TarArchiveEntry("lib", TarConstants.LF_SYMLINK)
                .also { it.linkName = "/usr/lib" }
            unpack(archive(link to byteArrayOf(),
                TarArchiveEntry("var/lib/dpkg/info/libc6:arm64.list") to "installed".toByteArray(),
                TarArchiveEntry("lib/libc6:arm64/data") to "guest".toByteArray()), root)
            assertEquals("installed", File(root, "var/lib/dpkg/info/libc6:arm64.list").readText())
            assertEquals("guest", File(root, "usr/lib/libc6:arm64/data").readText())
            assertEquals("/usr/lib", Files.readSymbolicLink(File(root, "lib").toPath()).toString())
        } finally { PhoneRuntimeFiles.deleteTree(root) }
    }

    @Test fun maliciousSymlinkIsRejectedBeforeWritingPayload() {
        val root = Files.createTempDirectory("runtime-escape").toFile()
        try {
            val link = TarArchiveEntry("escape", TarConstants.LF_SYMLINK)
                .also { it.linkName = "../../outside" }
            assertThrows(Exception::class.java) {
                unpack(archive(link to byteArrayOf(), TarArchiveEntry("escape/data") to byteArrayOf(1)), root)
            }
            assertTrue(root.list().isNullOrEmpty())
        } finally { PhoneRuntimeFiles.deleteTree(root) }
    }

    @Test fun expandedLimitAndUnsafeMemberStillRejectArchive() {
        val root = Files.createTempDirectory("runtime-limit").toFile()
        try {
            assertThrows(Exception::class.java) {
                unpack(archive(TarArchiveEntry("valid") to byteArrayOf(1, 2)), root, 1L)
            }
            assertThrows(Exception::class.java) {
                unpack(archive(TarArchiveEntry("../outside") to byteArrayOf(1)), root)
            }
            assertTrue(root.list().isNullOrEmpty())
        } finally { PhoneRuntimeFiles.deleteTree(root) }
    }

    @Test fun cleanupNeverWalksGuestLinksIntoHostDirectories() {
        val root = Files.createTempDirectory("runtime-cleanup").toFile()
        val outside = Files.createTempDirectory("runtime-host").toFile()
        try {
            val marker = File(outside, "keep").also { it.writeText("host") }
            Files.createSymbolicLink(File(root, "host").toPath(), outside.toPath())
            Files.createSymbolicLink(File(root, "dangling").toPath(), File(outside, "missing").toPath())
            PhoneRuntimeFiles.deleteTree(root)
            assertFalse(root.exists())
            assertEquals("host", marker.readText())
        } finally { PhoneRuntimeFiles.deleteTree(root); PhoneRuntimeFiles.deleteTree(outside) }
    }

    /** CI supplies both SHA-256-verified upstream archives, decompressed but never extracted. */
    @Test fun completePinnedLinuxAndAndroidBundlesExtractUsingProductionCode() {
        val fixtureDirectory = System.getenv("MAXIMUS_RUNTIME_FIXTURES")
        assumeNotNull(fixtureDirectory)
        val root = Files.createTempDirectory("runtime-pinned").toFile()
        try {
            val links = linkedMapOf<String, String>()
            for (bundle in PhoneRuntimeBundlePins.ALL) {
                val tar = File(requireNotNull(fixtureDirectory), bundle.name.removeSuffix(".zst"))
                assertTrue("Missing fixture: ${bundle.name}", tar.isFile)
                extractor.unpack({ TarArchiveInputStream(tar.inputStream().buffered()) }, root,
                    links, bundle.expandedLimit)
            }
            for (name in listOf("usr/bin/bash", "opt/gradle/gradle-8.14.3/bin/gradle",
                "root/android-sdk/build-tools/35.0.0/aapt2")) {
                assertTrue("Missing runtime tool: $name", File(root, name).isFile)
            }
            assertTrue(File(root, "var/lib/dpkg/info/libc6:arm64.list").isFile)
            assertTrue(File(root, "root/maven/localMvnRepository").isDirectory)
        } finally { PhoneRuntimeFiles.deleteTree(root) }
    }
}
