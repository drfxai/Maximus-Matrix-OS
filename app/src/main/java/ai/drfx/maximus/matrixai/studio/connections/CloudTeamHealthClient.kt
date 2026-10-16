package ai.drfx.maximus.matrixai.studio.connections

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Read-only Cloud Agent health probe. Token must be user-owned and supplied from
 * a separate encrypted store; never reuse a Cloudflare administrative API key.
 * The endpoint is an authenticated status probe, not proof of 24h model execution.
 */
internal class CloudTeamHealthClient(
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    data class Health(val configuredTeams: Int, val observedAt: Long, val scheduler: String)
    companion object {
        /** Never transmit an existing bearer to a different, unverified host. */
        fun matchesSavedOrigin(draft: String, saved: String): Boolean =
            saved.isNotBlank() && draft == saved

        fun origin(raw: String): String {
            val uri = URI(raw.trim())
            require(uri.scheme?.lowercase() == "https" &&
                uri.userInfo == null && uri.query == null && uri.fragment == null &&
                uri.path.orEmpty() in setOf("", "/") && uri.port in setOf(-1, 443))
            val host = requireNotNull(uri.host).lowercase(java.util.Locale.ROOT)
            require(host.matches(Regex("[a-z0-9][a-z0-9.-]{1,249}[a-z0-9]")) &&
                host.contains('.') && !host.matches(Regex("[0-9.]+")) &&
                !host.endsWith(".local") && !host.endsWith(".internal") &&
                !host.endsWith(".localhost"))
            return uri.toASCIIString().removeSuffix("/")
        }
    }
    data class Report(val teamId: String, val status: String,
                      val summary: String, val observedAt: Long)

    /** Read-only bounded reports; no untrusted link is opened or executed. */
    suspend fun reports(base: String, bearer: String): List<Report> = withContext(Dispatchers.IO) {
        require(bearer.length in 32..128 &&
            bearer.all { it.code in 33..126 && !it.isWhitespace() })
        val connection = connect(URL(origin(base) + "/v1/reports"))
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            check(connection.responseCode == 200) { "Cloud Agent reports unavailable." }
            check(connection.contentLengthLong in -1L..16384L)
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(2048)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(bytes.size() + count <= 16384) { "Cloud Agent response too large." }
                    bytes.write(buffer, 0, count)
                }
            }
            val data = JSONObject(bytes.toString("UTF-8")).optJSONArray("reports")
                ?: error("Cloud Agent reports response invalid.")
            buildList {
                for (i in 0 until data.length().coerceAtMost(30)) {
                    val row = data.optJSONObject(i) ?: continue
                    val id = row.optString("team_id")
                    val status = row.optString("status")
                    val summary = row.optString("summary")
                    val at = row.optLong("observed_at")
                    if (!id.matches(Regex("[a-f0-9]{8}(-[a-f0-9]{4}){4}")) ||
                        status !in setOf("RUNNING", "VERIFIED", "UNAVAILABLE") ||
                        at <= 0L || summary.length > 200) continue
                    add(Report(id, status, summary, at))
                }
            }
        } finally { connection.disconnect() }
    }

    suspend fun health(base: String, bearer: String): Health = withContext(Dispatchers.IO) {
        require(bearer.length in 32..128 &&
            bearer.all { it.code in 33..126 && !it.isWhitespace() })
        val url = URL(origin(base) + "/v1/health")
        val connection = connect(url)
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            val code = connection.responseCode
            check(code == 200) { "Cloud team endpoint unavailable; no health verified." }
            check(connection.contentLengthLong in -1L..4096L)
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(bytes.size() + count <= 4096)
                    bytes.write(buffer, 0, count)
                }
            }
            val result = JSONObject(bytes.toString("UTF-8"))
            check(result.optString("status") == "ready" &&
                result.optString("scheduler") == "cron" &&
                result.optBoolean("externalExecution") == false)
            val teams = result.getInt("configuredTeams")
            val at = result.getLong("observedAt")
            require(teams in 0..8 && at > 0)
            Health(teams, at, "cron")
        } finally { connection.disconnect() }
    }
}
