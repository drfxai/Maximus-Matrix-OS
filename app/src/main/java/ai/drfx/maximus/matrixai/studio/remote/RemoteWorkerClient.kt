package ai.drfx.maximus.matrixai.studio.remote

import org.json.JSONObject
import java.net.URI
import javax.net.ssl.HttpsURLConnection

data class RemoteWorkerCapabilities(
    val workerId: String,
    val platform: String,
    val connection: String,
    val executionEnabled: Boolean,
    val operations: List<String>
)

/** Credentials can only be submitted to explicit HTTPS origins. */
object RemoteWorkerEndpointPolicy {
    fun normalize(input: String): String {
        val uri = URI(input.trim())
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Remote Worker requires HTTPS with a verified server certificate."
        }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Do not put tokens, queries or fragments in the Worker URL."
        }
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
            "Enter the Worker origin only (without a path)."
        }
        require(uri.port in -1..65535 && uri.port != 0) { "Invalid port." }
        require(uri.host.length <= 253 && input.length <= 512) { "Invalid endpoint." }
        return "https://" + (if (uri.host.contains(':')) "[" + uri.host + "]" else uri.host.lowercase()) +
            (if (uri.port != -1) ":" + uri.port else "")
    }
}

object RemoteWorkerClient {
    fun discover(endpoint: String, token: String): RemoteWorkerCapabilities {
        val origin = RemoteWorkerEndpointPolicy.normalize(endpoint)
        require(token.length in 32..4096 && !token.contains('\n') && !token.contains('\r')) {
            "Enter the Worker bearer token generated on the server."
        }
        val conn = java.net.URL(origin + "/v1/capabilities").openConnection() as HttpsURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Authorization", "Bearer " + token)
        try {
            val response = conn.responseCode
            require(response == 200) {
                when (response) {
                    401, 403 -> "Worker rejected authentication."
                    in 300..399 -> "Worker redirect rejected to protect credentials."
                    else -> "Worker request failed (HTTP " + response + ")."
                }
            }
            require(conn.contentType?.substringBefore(';')?.trim()?.equals("application/json", true) == true) {
                "Worker did not return JSON capabilities."
            }
            val bytes = conn.inputStream.use { input ->
                val stream = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(stream.size() + n <= 32 * 1024) { "Worker response too large." }
                    stream.write(buffer, 0, n)
                }
                stream.toByteArray()
            }
            return decode(String(bytes, Charsets.UTF_8))
        } finally {
            conn.disconnect()
        }
    }

    fun decode(content: String): RemoteWorkerCapabilities {
        val data = JSONObject(content)
        require(data.optString("protocol") == "maximus-runtime" && data.optInt("api_version") == 1) {
            "Incompatible Remote Worker protocol."
        }
        val id = data.getString("worker_id")
        require(id.matches(Regex("[a-zA-Z0-9_-]{16,64}"))) { "Invalid Worker ID." }
        val system = data.getString("platform")
        require(system in setOf("windows", "linux", "darwin", "unknown")) { "Invalid Worker platform." }
        val state = data.optString("connection")
        require(state == "ready") { "Remote Worker is not ready." }
        val operations = data.getJSONArray("supported_operations").let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }
        require(operations.size <= 32 && operations.all { it.length <= 64 }) {
            "Invalid capability list."
        }
        return RemoteWorkerCapabilities(
            workerId = id,
            platform = system,
            connection = state,
            executionEnabled = data.optBoolean("execution_enabled"),
            operations = operations
        )
    }
}
