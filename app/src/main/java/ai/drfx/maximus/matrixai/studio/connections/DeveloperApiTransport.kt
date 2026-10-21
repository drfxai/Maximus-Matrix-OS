package ai.drfx.maximus.matrixai.studio.connections

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal class DeveloperRequestNotDispatchedException : IllegalStateException(
    "The request was blocked before any HTTP dispatch was attempted."
)

/** The production HTTP boundary; injected connections are used only by recording contract tests. */
internal class DeveloperApiTransport(
    private val snapshot: (DevelopmentService) -> DeveloperCredentialSnapshot,
    private val revision: (DevelopmentService) -> String,
    private val installed: (DevelopmentService) -> Boolean,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    companion object {
        const val MAX_RESPONSE = 1024 * 1024
        const val MAX_BODY = 512 * 1024
    }

    suspend fun call(service: DevelopmentService, path: String, method: String = "GET",
        body: ByteArray? = null, expectedRevision: String? = null): ByteArray = withContext(Dispatchers.IO) {
        var dispatchAttempted = false
        try {
        require(service == DevelopmentService.GITHUB || service == DevelopmentService.CLOUDFLARE)
        require(path.startsWith("/") && !path.startsWith("//") && '#' !in path)
        require(method in setOf("GET", "POST", "PUT"))
        require(body == null || (method != "GET" && body.size <= MAX_BODY))
        check(installed(service)) { "Install this native plugin from the Plugin Store first." }
        val credentials = snapshot(service)
        check(credentials.revision.isNotBlank() && credentials.token.isNotBlank()) {
            "Connect this service with a restricted API token first."
        }
        if (expectedRevision != null) check(credentials.revision == expectedRevision) {
            "Connection changed. Prepare and approve a fresh operation."
        }
        val base = if (service == DevelopmentService.GITHUB) "https://api.github.com"
            else "https://api.cloudflare.com/client/v4"
        val url = URL(base + path)
        check(url.protocol == "https" && url.userInfo == null && url.ref == null &&
            url.port in setOf(-1, 443) && url.host ==
            if (service == DevelopmentService.GITHUB) "api.github.com" else "api.cloudflare.com")
        val connection = open(url)
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 22_000
            connection.requestMethod = method
            connection.setRequestProperty("Accept", if (service == DevelopmentService.GITHUB)
                "application/vnd.github+json" else "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${credentials.token}")
            connection.setRequestProperty("User-Agent", "Maximus-Matrix-Mobile/1.0")
            if (service == DevelopmentService.GITHUB)
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            // Both outputStream and responseCode can initiate I/O. Recheck immediately before either.
            check(installed(service) && revision(service) == credentials.revision &&
                (expectedRevision == null || revision(service) == expectedRevision)) {
                "Connection or plugin changed before dispatch."
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(body.size)
                dispatchAttempted = true
                connection.outputStream.use { it.write(body) }
            }
            dispatchAttempted = true
            val code = connection.responseCode
            check(code in 200..299) { "Service request unavailable (HTTP $code). No change was confirmed." }
            check(connection.contentLengthLong <= MAX_RESPONSE) { "API response exceeds the editor limit." }
            val content = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    check(content.size() + n <= MAX_RESPONSE) { "API response exceeds the editor limit." }
                    content.write(buffer, 0, n)
                }
            }
            content.toByteArray()
        } finally { connection.disconnect() }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (!dispatchAttempted) throw DeveloperRequestNotDispatchedException()
            throw failure
        }
    }
}
