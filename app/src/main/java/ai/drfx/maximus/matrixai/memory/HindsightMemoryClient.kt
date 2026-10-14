package ai.drfx.maximus.matrixai.memory

import ai.drfx.maximus.matrixai.llm.CancellableHttp
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Hindsight Retain/Recall API (v1/default/banks). No superuser key is embedded in the app.
 * Every project has its own bank, authenticated with the USER's own cloud key or private edge.
 * Never follow redirects with Authorization, accept cleartext, or execute memory as commands.
 */
internal class HindsightMemoryClient {
    companion object {
        const val DEFAULT_ENDPOINT = "https://api.hindsight.vectorize.io"
        private const val MAX_BODY = 128_000
        private const val MAX_MEMORY = 2400

        fun normalizedEndpoint(value: String): String {
            val uri = URI(value.trim())
            require(uri.scheme?.lowercase() == "https" && uri.host != null &&
                uri.userInfo == null && uri.query == null && uri.fragment == null &&
                (uri.path.isNullOrEmpty() || uri.path == "/") &&
                uri.port in listOf(-1, 443, 8443)) { "Use an HTTPS Hindsight API origin without paths or credentials." }
            val host = uri.host.lowercase()
            require(host.matches(Regex("[a-z0-9](?:[a-z0-9.-]{1,248})[a-z0-9]")) &&
                host.contains('.') && !host.endsWith(".local") && !host.endsWith(".internal") &&
                host != "localhost" && !host.matches(Regex("[0-9.]+")) &&
                !host.endsWith(".localhost")) { "Use a public HTTPS domain or authenticated private DNS domain." }
            return uri.toASCIIString().removeSuffix("/")
        }

        fun bankFor(projectId: String): String {
            require(projectId.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")))
            return "maximus-project-" + projectId
        }

        /** Credential and source-code guardrails. False negatives are possible; explicit
         * consent is still required before anything is uploaded. */
        fun shareable(text: String): Boolean {
            if (text.isBlank() || text.length > MAX_MEMORY) return false
            return !Regex(
                "(?i)(-----BEGIN [A-Z ]*PRIVATE KEY-----|(?:sk|ghp|github_pat|hsk|AIza)[_-][A-Za-z0-9_-]{12,}|password\\s*[:=]|api[_ -]?key\\s*[:=]|authorization\\s*[:=]|bearer\\s+[A-Za-z0-9_-]{12,})"
            ).containsMatchIn(text)
        }

        fun safeMemo(text: String): String =
            text.take(MAX_MEMORY).replace(Regex("[\\p{Cc}&&[^\\n\\t]]"), " ")
                .replace("<<<", "").replace(">>>", "")
    }

    data class Memory(val id: String, val text: String, val type: String)

    suspend fun retain(origin: String, key: String, projectId: String, content: String, context: String) =
        CancellableHttp.execute {
            require(shareable(content)) { "Possible secret or oversized content. Remote memory upload blocked." }
            require(context in setOf("project-checkpoint", "model-progress")) { "Unsupported memory context." }
            val request = JSONObject().put("items", JSONArray().put(
                JSONObject().put("content", safeMemo(content)).put("context", context)
            )).put("async", false)
            call(origin, key, projectId, "memories", request)
        }

    suspend fun recall(origin: String, key: String, projectId: String, query: String): List<Memory> =
        CancellableHttp.execute {
            require(query.length in 5..1000)
            val request = JSONObject().put("query", query).put("max_tokens", 650).put("budget", "low")
            val result = call(origin, key, projectId, "memories/recall", request)
            val memories = result.optJSONArray("results") ?: JSONArray()
            buildList {
                for (i in 0 until memories.length().coerceAtMost(8)) {
                    val item = memories.optJSONObject(i) ?: continue
                    val text = item.optString("text")
                    if (text.isBlank() || text.length > 1200 || !shareable(text)) continue
                    add(Memory(item.optString("id").take(80), safeMemo(text),
                        item.optString("type").take(32)))
                }
            }
        }

    private fun call(
        origin: String, key: String, projectId: String, operation: String, body: JSONObject
    ): JSONObject {
        val endpoint = normalizedEndpoint(origin)
        require(operation in setOf("memories", "memories/recall"))
        if (endpoint == DEFAULT_ENDPOINT) require(key.isNotBlank()) { "Hindsight Cloud key is missing." }
        require(key.length <= 512 && key.none { it.isWhitespace() || it.code < 32 || it.code == 127 })
        val url = "$endpoint/v1/default/banks/${bankFor(projectId)}/$operation"
        val payload = body.toString().toByteArray(Charsets.UTF_8)
        require(payload.size <= 8_192) { "Memory request too large." }
        val connection = CancellableHttp.register(URL(url).openConnection() as HttpURLConnection)
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 2_500
            connection.readTimeout = 10_000
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (key.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $key")
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            if (code !in 200..299) {
                error(when (code) {
                    401,403 -> "Hindsight authorization denied; check user key and bank access."
                    404 -> "Hindsight API or project bank unavailable."
                    429 -> "Hindsight rate limit; local checkpoint remains available."
                    in 300..399 -> "Hindsight redirected request; credentials were not forwarded."
                    else -> "Hindsight HTTP error $code. Local memory is unaffected."
                })
            }
            if (connection.contentLengthLong > MAX_BODY) error("Memory response exceeds safe size.")
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    require(out.size() + n <= MAX_BODY) { "Memory response exceeds safe size." }
                    out.write(buf, 0, n)
                }
            }
            val response = out.toString("UTF-8")
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally { connection.disconnect() }
    }
}
