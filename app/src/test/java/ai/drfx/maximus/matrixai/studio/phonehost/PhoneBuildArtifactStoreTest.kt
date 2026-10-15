package ai.drfx.maximus.matrixai.studio.phonehost

import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PhoneBuildArtifactStoreTest {
    private val project = "12345678-abcd-abcd-abcd-abcdefabcdef"

    @Test fun restoresOnlyMatchingDigestAfterNewStoreInstance() = withDirectory { root ->
        val source = File(root, "source.apk").apply { writeBytes(ByteArray(2048) { it.toByte() }) }
        val storeRoot = File(root, "artifacts")
        val saved = PhoneBuildArtifactStore(storeRoot).save(source, project, "ai.drfx.demo")
        assertEquals(saved, PhoneBuildArtifactStore(storeRoot).restore())
        saved.file.appendText("changed")
        assertNull(PhoneBuildArtifactStore(storeRoot).restore())
    }

    @Test fun retainsOneArtifactAndRejectsIncompleteReplacement() = withDirectory { root ->
        val source = File(root, "source.apk").apply { writeBytes(ByteArray(2048)) }
        val storeRoot = File(root, "artifacts")
        val store = PhoneBuildArtifactStore(storeRoot)
        val first = store.save(source, project, "ai.drfx.first")
        source.writeBytes(ByteArray(2048) { 1 })
        val second = store.save(source, project, "ai.drfx.second")
        assertFalse(first.file.exists())
        assertEquals(1, storeRoot.listFiles()!!.count { it.isDirectory })
        assertEquals(second, store.restore())
        source.writeText("not an APK")
        assertThrows(IllegalArgumentException::class.java) {
            store.save(source, project, "ai.drfx.invalid")
        }
        assertEquals(second, store.restore())
    }

    @Test fun rejectsTraversalAndUnsupportedMetadata() = withDirectory { root ->
        val manifest = File(root, "current.bin")
        DataOutputStream(manifest.outputStream()).use {
            it.writeInt(1)
            it.writeUTF("../../outside")
            it.writeUTF(project)
            it.writeUTF("0".repeat(64))
            it.writeUTF("ai.drfx.demo")
        }
        assertNull(PhoneBuildArtifactStore(root).restore())
        manifest.writeBytes(ByteArray(3000))
        assertNull(PhoneBuildArtifactStore(root).restore())
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("phone-artifact-test").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
