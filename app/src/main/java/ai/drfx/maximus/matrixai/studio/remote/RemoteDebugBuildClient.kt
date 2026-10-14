package ai.drfx.maximus.matrixai.studio.remote

import android.util.Base64
import org.json.JSONObject
import java.io.OutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** Debug-only project artifacts. App signing credentials never reach this client. */
data class RemoteDebugJob(
    val id: String,
    val projectId: String,
    val state: String,
    val sourceSha256: String,
    val apkSha256: String,
    val error: String,
    val signed: Boolean
) {
    val finished: Boolean get() = state in setOf("succeeded", "failed")
}

object RemoteDebugBuildClient {
    private const val MAX_ZIP = 8 * 1024 * 1024
    private const val MAX_APK = 64 * 1024 * 1024
    private val jobIdPattern = Regex("[0-9a-f]{32}")

    fun submit(origin: String, token: String, projectId: String, zip: ByteArray): RemoteDebugJob {
        require(projectId.matches(Regex("[0-9a-f-]{36}"))) { "Invalid project identifier." }
        require(zip.isNotEmpty() && zip.size <= MAX_ZIP) { "ZIP exceeds the 8 MB remote upload limit." }
        val body = JSONObject().put("project_id", projectId)
            .put("request_id", UUID.randomUUID().toString())
            .put("source_sha256", sha256(zip))
            .put("zip_base64", Base64.encodeToString(zip, Base64.NO_WRAP))
            .put("approved", true)
            .toString().toByteArray(Charsets.UTF_8)
        return decodeJob(jsonRequest(origin, token, "/v1/jobs", "POST", body))
    }

    fun status(origin: String, token: String, id: String): RemoteDebugJob {
        require(jobIdPattern.matches(id))
        return decodeJob(jsonRequest(origin, token, "/v1/jobs/" + id, "GET"))
    }

    /** Verify the complete artifact before exposing it to Android's document picker. */
    fun download(origin: String, token: String, job: RemoteDebugJob, destination: OutputStream) {
        require(job.id.matches(jobIdPattern) && job.state == "succeeded" && !job.signed)
        require(job.apkSha256.matches(Regex("[a-f0-9]{64}")))
        val conn = connect(origin, token, "/v1/jobs/" + job.id + "/artifact", "GET")
        try {
            require(conn.responseCode == 200) { "Worker APK unavailable: HTTP " + conn.responseCode }
            require(conn.contentType?.substringBefore(';') == "application/vnd.android.package-archive")
            require(conn.contentLengthLong in 1..MAX_APK.toLong())
            val file = java.io.File.createTempFile("maximus-project-", ".apk")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= MAX_APK) { "APK exceeds size limit." }
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                        }
                    }
                }
                require(total == conn.contentLengthLong)
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual == job.apkSha256) { "Debug APK checksum verification failed." }
                file.inputStream().use { it.copyTo(destination) }
            } finally {
                file.delete()
            }
        } finally {
            conn.disconnect()
        }
    }

    fun decodeJob(json: JSONObject): RemoteDebugJob {
        val id = json.getString("id")
        require(jobIdPattern.matches(id))
        val state = json.getString("state")
        require(state in setOf("queued", "running", "succeeded", "failed"))
        require(!json.optBoolean("signed")) { "Remote worker cannot claim production signing." }
        val source = json.getString("source_sha256")
        val output = json.optString("apk_sha256")
        require(source.matches(Regex("[a-f0-9]{64}")))
        require(output.isBlank() || output.matches(Regex("[a-f0-9]{64}")))
        return RemoteDebugJob(
            id, json.getString("project_id"), state, source, output,
            json.optString("error").take(500), false
        )
    }

    private fun jsonRequest(
        origin: String, token: String, path: String, method: String,
        body: ByteArray? = null
    ): JSONObject {
        val conn = connect(origin, token, path, method)
        try {
            if (body != null) {
                require(body.size <= 12 * 1024 * 1024)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val expected = if (method == "POST") 202 else 200
            require(conn.responseCode == expected) { "Worker returned HTTP " + conn.responseCode }
            require(conn.contentType?.substringBefore(';') == "application/json")
            require(conn.contentLengthLong in -1L..32768L)
            val output = java.io.ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= 32768) { "Worker response exceeds size limit." }
                    output.write(buffer, 0, n)
                }
            }
            return JSONObject(output.toString("UTF-8"))
        } finally { conn.disconnect() }
    }

    private fun connect(
        origin: String, token: String, path: String, method: String
    ): HttpsURLConnection {
        val clean = RemoteWorkerEndpointPolicy.normalize(origin)
        require(token.length in 32..4096 && token.none { it == '\n' || it == '\r' })
        return (URL(clean + path).openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 18000
            useCaches = false
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
