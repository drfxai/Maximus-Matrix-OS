package ai.drfx.maximus.matrixai.localmodels

import org.junit.Assert.*
import org.junit.Test

class LocalModelIntakePolicyTest {
    private fun gguf(version: Int) = byteArrayOf(
        'G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(),
        version.toByte(), 0, 0, 0
    )

    @Test fun recognizesSupportedGgufHeaderButDoesNotClaimRunnableInference() {
        for (version in 2..3) {
            val probe = LocalModelIntakePolicy.inspect("gemma-q4.gguf", 1024L, gguf(version))
            assertEquals(LocalModelFormat.GGUF, probe.format)
            assertTrue(probe.headerVerified)
            assertFalse(probe.canRun)
            assertTrue(probe.note.contains("NOT verified"))
        }
    }

    @Test fun rejectsSpoofedOrUnsupportedGgufEvenIfExtensionLooksValid() {
        val spoofed = LocalModelIntakePolicy.inspect("forged.gguf", 1024L, "AAAA".toByteArray())
        assertEquals(LocalModelFormat.UNSUPPORTED, spoofed.format)
        assertFalse(spoofed.headerVerified)
        val future = LocalModelIntakePolicy.inspect("future.gguf", 1024L, gguf(255))
        assertEquals(LocalModelFormat.UNSUPPORTED, future.format)
    }

    @Test fun litertPackageIsOnlyAnExtensionHintAndNeverApprovedForExecution() {
        val p = LocalModelIntakePolicy.inspect("gemma.litertlm", 500L, "ZIP?????".toByteArray())
        assertEquals(LocalModelFormat.LITERT_LM, p.format)
        assertFalse(p.headerVerified)
        assertFalse(p.canRun)
        assertTrue(p.note.contains("unverified"))
    }

    @Test fun rejectsScriptsArchivesAndUnknownFormats() {
        for (name in listOf("model.zip", "update.sh", "model.gguf.exe", "sample.bin")) {
            val p = LocalModelIntakePolicy.inspect(name, 100L, gguf(3))
            assertEquals(LocalModelFormat.UNSUPPORTED, p.format)
        }
    }

    @Test fun enforcesFiniteKnownSizeAndSafeName() {
        for (size in listOf(-1L, 0L, 7L, LocalModelIntakePolicy.MAX_MODEL_BYTES + 1)) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalModelIntakePolicy.inspect("tiny.gguf", size, gguf(3))
            }
        }
        for (name in listOf("../private.gguf", "dir/model.gguf", "dir\\model.gguf", "")) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalModelIntakePolicy.inspect(name, 1024L, gguf(3))
            }
        }
    }

    @Test fun fileNameDoesNotEstablishMultimodalCapabilities() {
        val p = LocalModelIntakePolicy.inspect("vision-audio-video.gguf", 1024L, gguf(3))
        assertFalse(p.canRun)
        assertFalse(p.note.contains("vision supported"))
    }
}
