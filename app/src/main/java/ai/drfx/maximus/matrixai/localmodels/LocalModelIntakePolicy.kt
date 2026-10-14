package ai.drfx.maximus.matrixai.localmodels

/**
 * Offline model intake is intentionally independent of any runtime. Recording a
 * file must never be interpreted as successful inference or hardware support.
 */
enum class LocalModelFormat(val displayName: String) {
    GGUF("GGUF · llama.cpp"),
    LITERT_LM("LiteRT-LM package"),
    UNSUPPORTED("Unsupported")
}

data class LocalModelProbe(
    val format: LocalModelFormat,
    val headerVerified: Boolean,
    val canRun: Boolean,
    val note: String
)

object LocalModelIntakePolicy {
    const val MAX_MODEL_BYTES: Long = 64L * 1024L * 1024L * 1024L

    /**
     * Only reads a tiny caller-provided prefix. Never decodes a model, extracts
     * archives, opens executable content, or guesses vision/audio capabilities.
     */
    fun inspect(displayName: String, size: Long, prefix: ByteArray): LocalModelProbe {
        require(displayName.isNotBlank() && displayName.length <= 128) { "Invalid model name." }
        require('/' !in displayName && '\\' !in displayName && '\u0000' !in displayName) {
            "Model name cannot contain a path."
        }
        require(size in 8L..MAX_MODEL_BYTES) {
            "Model must have a known size between 8 bytes and 64 GiB."
        }
        val name = displayName.trim().lowercase(java.util.Locale.ROOT)
        if (name.endsWith(".gguf")) {
            val hasMagic = prefix.size >= 8 &&
                prefix[0] == 'G'.code.toByte() && prefix[1] == 'G'.code.toByte() &&
                prefix[2] == 'U'.code.toByte() && prefix[3] == 'F'.code.toByte()
            if (!hasMagic) return LocalModelProbe(
                LocalModelFormat.UNSUPPORTED, false, false,
                "GGUF signature is absent. The file was not registered."
            )
            val version =
                (prefix[4].toInt() and 0xff) or
                ((prefix[5].toInt() and 0xff) shl 8) or
                ((prefix[6].toInt() and 0xff) shl 16) or
                ((prefix[7].toInt() and 0xff) shl 24)
            if (version !in 2..3) return LocalModelProbe(
                LocalModelFormat.UNSUPPORTED, false, false,
                "Unrecognized GGUF version. A compatible runtime is required."
            )
            return LocalModelProbe(
                LocalModelFormat.GGUF, true, false,
                "GGUF header v$version recognized; weights and hardware are NOT verified. " +
                    "llama.cpp integration is not installed."
            )
        }
        if (name.endsWith(".litertlm")) {
            // File extension is not cryptographic proof of a LiteRT-LM package.
            return LocalModelProbe(
                LocalModelFormat.LITERT_LM, false, false,
                "LiteRT-LM extension only; package, ABI and Android API support are " +
                    "unverified. A supported runtime is not installed."
            )
        }
        return LocalModelProbe(
            LocalModelFormat.UNSUPPORTED, false, false,
            "Only .gguf and .litertlm are inventoried in this first phase."
        )
    }
}
