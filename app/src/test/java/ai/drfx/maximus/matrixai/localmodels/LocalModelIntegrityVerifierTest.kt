package ai.drfx.maximus.matrixai.localmodels

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class LocalModelIntegrityVerifierTest {
    private val bytes = "GGUF\\u0003\\u0000\\u0000\\u0000model".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    @Test fun exactBytesAndTrustedExpectedHashMatch() {
        val result = LocalModelIntegrityVerifier.verify(ByteArrayInputStream(bytes),
            bytes.size.toLong(), hash)
        assertTrue(result.accepted)
        assertEquals(bytes.size.toLong(), result.bytesRead)
        assertEquals(hash, result.sha256)
    }

    @Test fun rejectsTamperedTruncatedAndOversizedFiles() {
        assertFalse(LocalModelIntegrityVerifier.verify(
            ByteArrayInputStream(bytes.clone().also { it[it.lastIndex] = 1 }),
            bytes.size.toLong(), hash).accepted)
        assertFalse(LocalModelIntegrityVerifier.verify(
            ByteArrayInputStream(bytes.copyOf(bytes.size - 1)),
            bytes.size.toLong(), hash).accepted)
        assertFalse(LocalModelIntegrityVerifier.verify(
            ByteArrayInputStream(bytes),
            (bytes.size - 1).toLong(), hash).accepted)
    }

    @Test fun rejectsMalformedExpectedHashWithoutReadingModel() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelIntegrityVerifier.verify(ByteArrayInputStream(bytes), bytes.size.toLong(), "unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelIntegrityVerifier.verify(ByteArrayInputStream(bytes), 0L, hash)
        }
    }
}
