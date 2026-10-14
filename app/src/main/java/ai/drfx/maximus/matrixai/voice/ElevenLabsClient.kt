package ai.drfx.maximus.matrixai.voice

import ai.drfx.maximus.matrixai.llm.CancellableHttp
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

data class ElevenVoice(val id: String, val name: String)
data class ElevenQuota(val tier: String, val used: Long, val limit: Long) {
    val remaining: Long get() = (limit - used).coerceAtLeast(0)
}

/**
 * Direct, user-owned ElevenLabs key. No gateway secrets or SDK telemetry.
 * Network I/O executes on cancellable background workers and only speaks on explicit action.
 * "character_limit" is the API's reported subscription unit; it is NOT an LLM token balance.
 */
internal class ElevenLabsClient {
    companion object {
        const val ORIGIN = "https://api.elevenlabs.io"
        const val DEFAULT_MODEL = "eleven_multilingual_v2"
        const val LOW_LATENCY_MODEL = "eleven_flash_v2_5"
        const val PERSIAN_MODEL = "eleven_v3"
        const val MAX_SPOKEN_CHARACTERS = 2500
        private const val MAX_AUDIO_BYTES = 8 * 1024 * 1024
        private const val MAX_JSON_BYTES = 192 * 1024
        private const val MAX_KEY = 512
        fun validVoiceId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{12,80}"))
        fun validModel(value: String): Boolean = value in setOf(DEFAULT_MODEL, LOW_LATENCY_MODEL, PERSIAN_MODEL)
        fun validKey(value: String): Boolean =
            value.length in 16..MAX_KEY && value.none { it.isWhitespace() || it.code < 32 || it.code == 127 }
    }

    private val inFlight = AtomicInteger(0)

    suspend fun quota(key: String): ElevenQuota = CancellableHttp.execute { subscription(key) }

    suspend fun voices(key: String): List<ElevenVoice> = CancellableHttp.execute {
        val connection = connect("/v2/voices?page_size=50", "GET", key)
        try {
            val json = JSONObject(readBounded(connection, MAX_JSON_BYTES).toString(Charsets.UTF_8))
            val array = json.optJSONArray("voices") ?: error("Voice catalog unavailable.")
            buildList {
                for (i in 0 until array.length().coerceAtMost(50)) {
                    val v = array.optJSONObject(i) ?: continue
                    val id = v.optString("voice_id")
                    if (validVoiceId(id)) add(ElevenVoice(id, v.optString("name").ifBlank { "Voice" }.take(80)))
                }
            }.distinctBy { it.id }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Fetch authoritative subscription before every synthesis; block overage and requests
     * larger than available reported allowance. Concurrent requests are rejected rather
     * than pretending separate devices share an atomic credit ledger.
     */
    suspend fun synthesize(key: String, voice: String, model: String, text: String): ByteArray =
        CancellableHttp.execute {
            require(validVoiceId(voice)) { "Choose a verified ElevenLabs voice." }
            require(validModel(model)) { "Unsupported ElevenLabs voice model." }
            require(text.isNotBlank() && text.length <= MAX_SPOKEN_CHARACTERS) {
                "Speak 1 to $MAX_SPOKEN_CHARACTERS characters at a time."
            }
            check(inFlight.compareAndSet(0, 1)) { "Another voice request is still active." }
            try {
                val available = subscription(key)
                check(available.remaining >= text.length) {
                    "Insufficient reported allowance: ${available.remaining} remaining. No overages allowed."
                }
                val payload = JSONObject().put("text", text).put("model_id", model).toString()
                    .toByteArray(Charsets.UTF_8)
                val connection = connect(
                    "/v1/text-to-speech/$voice?output_format=mp3_44100_128", "POST", key
                )
                try {
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(payload.size)
                    connection.outputStream.use { it.write(payload) }
                    val mime = connection.contentType?.substringBefore(';')?.lowercase()
                    require(mime == "audio/mpeg" || mime == "audio/mp3" || mime == "application/octet-stream") {
                        if (connection.responseCode !in 200..299) "Voice provider returned HTTP ${connection.responseCode}."
                        else "Unexpected audio response."
                    }
                    val bytes = readBounded(connection, MAX_AUDIO_BYTES)
                    require(bytes.size > 128) { "Voice provider returned empty audio." }
                    bytes
                } finally { connection.disconnect() }
            } finally { inFlight.set(0) }
        }

    private fun subscription(key: String): ElevenQuota {
        val conn = connect("/v1/user/subscription", "GET", key)
        return try {
            val json = JSONObject(readBounded(conn, MAX_JSON_BYTES).toString(Charsets.UTF_8))
            val used = json.optLong("character_count", -1)
            val limit = json.optLong("character_limit", -1)
            require(used >= 0 && limit >= 0) {
                "No authoritative ElevenLabs usage limit was returned; synthesis disabled."
            }
            ElevenQuota(json.optString("tier", "unknown").take(40), used, limit)
        } finally { conn.disconnect() }
    }

    private fun connect(path: String, method: String, key: String): HttpURLConnection {
        require(validKey(key)) { "Enter a valid user-owned ElevenLabs API key." }
        require(path.startsWith("/") && !path.contains("://")) { "Invalid API path." }
        return CancellableHttp.register(URL(ORIGIN + path).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 20000
            useCaches = false
            setRequestProperty("xi-api-key", key)
            setRequestProperty("Accept", if (method == "GET") "application/json" else "audio/mpeg")
        }
    }

    private fun readBounded(conn: HttpURLConnection, limit: Int): ByteArray {
        val code = conn.responseCode
        if (code !in 200..299) {
            throw IllegalStateException(
                when (code) {
                    401, 403 -> "ElevenLabs API access denied. Check key permissions."
                    402 -> "ElevenLabs subscription or credit required."
                    429 -> "ElevenLabs quota or rate limit reached."
                    in 500..599 -> "ElevenLabs service temporarily unavailable."
                    in 300..399 -> "ElevenLabs redirect rejected for credential safety."
                    else -> "ElevenLabs API error (HTTP $code)."
                }
            )
        }
        if ((conn.contentLengthLong) > limit) error("ElevenLabs response exceeds safe size.")
        return conn.inputStream.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count == -1) break
                require(out.size() + count <= limit) { "ElevenLabs response exceeds safe size." }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
    }
}
