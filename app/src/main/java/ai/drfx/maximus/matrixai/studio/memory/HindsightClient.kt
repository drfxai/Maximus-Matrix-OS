package ai.drfx.maximus.matrixai.studio.memory

import ai.drfx.maximus.matrixai.llm.CancellableHttp
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ProjectMemory(val id: String, val text: String, val kind: String)
data class ProjectMemoryResult(val entries: List<ProjectMemory>, val bankId: String)

/**
 * Official Hindsight Cloud REST adapter. Bank contents are shared by ALL models using
 * the SAME bank and authorized Hindsight credentials. No model-owned memory stores.
 *
 * Sensitive project materials are never uploaded automatically. Source files, transcripts
 * and credentials are not accepted here; only explicit bounded human-approved summaries.
 */
internal class HindsightClient {
    companion object {
        const val BASE = "https://api.hindsight.vectorize.io"
        fun validBankId(id: String): Boolean = id.matches(Regex("[a-z0-9][a-z0-9_-]{5,95}"))
        fun validKey(key: String): Boolean = key.length in 16..512 &&
            key.none { it.isWhitespace() || it.code < 32 || it.code == 127 }
        fun retentionPayload(summary: String, context: String): JSONObject {
            require(summary.length in 1..4000 && context.length <= 160) { "Memory summary is too large." }
            require(HindsightMemoryPolicy.mayShare(summary)) { "Potentially sensitive data. Edit the summary before sharing." }
            return JSONObject().put("items", JSONArray().put(
                JSONObject().put("content", summary).put("context", context)
            ))
        }
        fun recallPayload(query: String): JSONObject {
            require(query.length in 1..512)
            return JSONObject().put("query", query)
                .put("budget", "low")
                .put("max_tokens", 1200)
        }
        fun parseRecall(bankId: String, data: JSONObject): ProjectMemoryResult {
            val array = data.optJSONArray("results") ?: error("Memory server did not return results.")
            val values = buildList {
                for (i in 0 until minOf(array.length(), 12)) {
                    val item = array.optJSONObject(i) ?: continue
                    val text = item.optString("text").take(900)
                    if (text.isNotBlank()) add(ProjectMemory(
                        id = item.optString("id").take(100),
                        text = text,
                        kind = item.optString("type").take(40)
                    ))
                }
            }
            return ProjectMemoryResult(values, bankId)
        }
    }

    suspend fun retain(key: String, bankId: String, text: String, context: String) =
        CancellableHttp.execute {
            post(key, bankId, "/memories", retentionPayload(text, context))
        }

    suspend fun recall(key: String, bankId: String, query: String): ProjectMemoryResult =
        CancellableHttp.execute {
            parseRecall(bankId, post(key, bankId, "/memories/recall", recallPayload(query)))
        }

    private fun post(key: String, bankId: String, suffix: String, data: JSONObject): JSONObject {
        require(validKey(key)) { "Configure a user-owned Hindsight Cloud API key." }
        require(validBankId(bankId)) { "Invalid Hindsight memory bank ID." }
        // Fixed origin and strict bank syntax prevent mobile client SSRF and path injection.
        val conn = CancellableHttp.register(
            URL("$BASE/v1/default/banks/$bankId$suffix").openConnection() as HttpURLConnection
        )
        try {
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.useCaches = false
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            val bytes = data.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size < 8192)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            check(code in 200..299) {
                when (code) {
                    401, 403 -> "Hindsight authorization denied."
                    402 -> "Hindsight account quota or billing unavailable."
                    404 -> "Hindsight memory bank not found."
                    429 -> "Hindsight rate limit reached."
                    in 300..399 -> "Untrusted Hindsight redirect blocked."
                    else -> "Hindsight service error (HTTP $code)."
                }
            }
            val bound = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val chunk = ByteArray(4096)
                while (true) {
                    val amount = input.read(chunk)
                    if (amount < 0) break
                    require(bound.size() + amount <= 128 * 1024) { "Hindsight response exceeded limit." }
                    bound.write(chunk, 0, amount)
                }
            }
            return JSONObject(bound.toString("UTF-8"))
        } finally { conn.disconnect() }
    }
}

/**
 * Conservative prevention of accidentally uploading API credentials or private keys.
 * A user must inspect and explicitly approve the human-readable synopsis anyway.
 */
internal object HindsightMemoryPolicy {
    private val secretPattern = Regex(
        "(?i)(-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\\b(?:api[_ -]?key|secret|password|access[_ -]?token|authorization|bearer)\\s*[:=]\\s*\\S+|gh[pousr]_[A-Za-z0-9]{15,}|sk-[A-Za-z0-9_-]{18,})"
    )
    fun mayShare(text: String): Boolean =
        text.isNotBlank() && text.length <= 4000 && !secretPattern.containsMatchIn(text)
}
