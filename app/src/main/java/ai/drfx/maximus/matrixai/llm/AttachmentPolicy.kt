package ai.drfx.maximus.matrixai.llm

object AttachmentPolicy {
    const val MAX_BYTES = 10 * 1024 * 1024
    private val images = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
    fun validate(provider: LlmProvider, attachment: ChatAttachment) {
        require(attachment.data.isNotBlank()) { "Attachment payload is unavailable. Attach the file again." }
        require(attachment.data.length <= MAX_BYTES * 4 / 3 + 8) { "Attachment exceeds the 10 MiB limit." }
        if (attachment.isText) {
            require(attachment.mimeType.startsWith("text/") || attachment.mimeType in setOf("application/json", "application/javascript", "application/xml")) {
                "Unsupported text attachment MIME type."
            }
            require(attachment.data.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Text attachment exceeds the 10 MiB limit." }
            return
        }
        require(attachment.data.length % 4 == 0 && attachment.data.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) {
            "Malformed attachment encoding. Select the file again."
        }
        val decoded = runCatching { java.util.Base64.getDecoder().decode(attachment.data) }.getOrElse { throw IllegalArgumentException("Malformed attachment encoding.") }
        require(decoded.size <= MAX_BYTES) { "Attachment exceeds the 10 MiB limit." }
        val mime = attachment.mimeType
        val prefix = decoded.take(12).toByteArray()
        val validSignature = when (mime) {
            "image/png" -> decoded.size >= 8 && decoded[0] == 0x89.toByte() && decoded.copyOfRange(1, 4).toString(Charsets.US_ASCII) == "PNG"
            "image/jpeg" -> decoded.size >= 3 && decoded[0] == 0xff.toByte() && decoded[1] == 0xd8.toByte() && decoded[2] == 0xff.toByte()
            "image/gif" -> prefix.toString(Charsets.US_ASCII).startsWith("GIF8")
            "image/webp" -> decoded.size >= 12 && decoded.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && decoded.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
            "application/pdf" -> prefix.toString(Charsets.US_ASCII).startsWith("%PDF-")
            else -> true
        }
        require(validSignature) { "Attachment content does not match its declared format." }
        require(mime in images || mime == "application/pdf" || mime in setOf("audio/m4a", "audio/mp4", "audio/x-m4a")) {
            "Unsupported attachment format."
        }
        require(!mime.startsWith("audio/") || provider in setOf(LlmProvider.GEMINI, LlmProvider.OPENAI)) {
            "This provider has no implemented audio transcription adapter."
        }
        require(mime != "application/pdf" || provider in setOf(LlmProvider.GEMINI, LlmProvider.ANTHROPIC)) {
            "PDF upload is supported only by the implemented Gemini and Anthropic adapters. Extract text or select one of those providers."
        }
    }
}
