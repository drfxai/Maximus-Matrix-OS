package ai.drfx.maximus.matrixai.localmodels

import java.io.InputStream
import java.security.MessageDigest

/**
 * Bounded streaming digest for user-selected model files.
 * A matching SHA-256 verifies bytes against the supplied expected hash only;
 * it does NOT establish model provenance, runnable inference or modality support.
 */
object LocalModelIntegrityVerifier {
    data class Result(val accepted: Boolean, val bytesRead: Long, val sha256: String)
    fun verify(input: InputStream, expectedBytes: Long, expectedSha256: String): Result {
        require(expectedBytes in 8L..LocalModelIntakePolicy.MAX_MODEL_BYTES)
        require(expectedSha256.matches(Regex("[0-9a-fA-F]{64}")))
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(64 * 1024)
        var consumed = 0L
        while (true) {
            val limit = minOf(bytes.size.toLong(), expectedBytes - consumed + 1).toInt()
            val count = input.read(bytes, 0, limit)
            if (count < 0) break
            if (count == 0) continue
            consumed += count
            if (consumed > expectedBytes) return Result(false, consumed, "")
            digest.update(bytes, 0, count)
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        val approved = consumed == expectedBytes &&
            MessageDigest.isEqual(actual.toByteArray(Charsets.US_ASCII),
                expectedSha256.lowercase(java.util.Locale.ROOT).toByteArray(Charsets.US_ASCII))
        return Result(approved, consumed, actual)
    }
}
