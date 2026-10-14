package ai.drfx.maximus.matrixai.studio.phonehost

import android.content.Context
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * User-started, LOCALHOST-ONLY Android development gateway. This is a real
 * listening HTTP socket that serves app-owned project metadata and, only
 * after an explicit opt-in for one project, a bounded ZIP snapshot.
 * It does NOT implement Mobile-Harness' PRoot, PTY, Linux commands, APK
 * building, model inference or public-network access.
 */
internal class PhoneHarnessHttpServer(context: Context) : AutoCloseable {
    private val application = context.applicationContext
    private val tokenBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val bearerToken: String = tokenBytes.joinToString("") { "%02x".format(it) }
    private val workerId: String = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
    private val workspace = ProjectWorkspaceStore(application)
    private val socket = ServerSocket()
    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        thread(start = false, isDaemon = true, name = "phone-harness-client", block = { runnable.run() })
    }
    @Volatile private var stopped = false
    @Volatile var approvedProject: String? = null
        private set

    init {
        socket.reuseAddress = true
        socket.bind(
            InetSocketAddress(InetAddress.getByName("127.0.0.1"), PhoneHostPolicy.PORT), 4
        )
        thread(start = true, isDaemon = true, name = "phone-harness-loop") {
            while (!stopped) {
                try {
                    val client = socket.accept()
                    if (stopped) {
                        client.close()
                        break
                    }
                    executor.execute { handle(client) }
                } catch (_: Exception) {
                    if (stopped) break
                }
            }
        }
    }

    /** Only project IDs already present in the app can be shared. */
    fun approveProject(id: String?) {
        if (id == null) {
            approvedProject = null
            return
        }
        require(workspace.projects().any { it.id == id }) { "Unknown project." }
        approvedProject = id
    }

    private fun handle(client: Socket) {
        client.use { conn ->
            try {
                conn.soTimeout = 3000
                conn.tcpNoDelay = true
                val request = readRequest(conn.getInputStream()) ?: run {
                    respond(conn, 400, "application/json", error("BAD_REQUEST"))
                    return
                }
                val expected = ("Bearer " + bearerToken).toByteArray(StandardCharsets.US_ASCII)
                val received = request.auth.toByteArray(StandardCharsets.US_ASCII)
                if (!MessageDigest.isEqual(received, expected)) {
                    respond(conn, 401, "application/json", error("UNAUTHORIZED"))
                    return
                }
                if (request.origin != null || !request.host.matches(
                        Regex("(127\\.0\\.0\\.1|localhost):" + PhoneHostPolicy.PORT)
                    )) {
                    respond(conn, 403, "application/json", error("LOCAL_ONLY"))
                    return
                }
                val path = PhoneHostPolicy.route(request.method, request.target)
                if (path == null) {
                    respond(conn, 404, "application/json", error("NOT_FOUND"))
                    return
                }
                when (path) {
                    PhoneHostPolicy.Route.Capabilities -> respond(
                        conn, 200, "application/json", JSONObject()
                            .put("protocol", "maximus-phone-host")
                            .put("api_version", 1)
                            .put("worker_id", workerId)
                            .put("platform", "android")
                            .put("connection", "ready")
                            .put("execution_enabled", false)
                            .put("runtime_engine", "not_installed")
                            .put("network_scope", "loopback_only")
                            .put("supported_operations", JSONArray()
                                .put("capabilities.read")
                                .put("projects.list")
                                .put("projects.manifest")
                                .put("projects.export.with_approval"))
                            .toString().toByteArray(Charsets.UTF_8)
                    )
                    PhoneHostPolicy.Route.Projects -> {
                        val projects = JSONArray()
                        workspace.projects().take(100).forEach { project ->
                            projects.put(JSONObject()
                                .put("id", project.id)
                                .put("name", project.name)
                                .put("file_count", project.fileCount))
                        }
                        respond(conn, 200, "application/json", JSONObject()
                            .put("projects", projects).toString().toByteArray(Charsets.UTF_8))
                    }
                    is PhoneHostPolicy.Route.Manifest -> {
                        if (approvedProject != path.id) {
                            respond(conn, 403, "application/json", error("PROJECT_NOT_APPROVED"))
                            return
                        }
                        val files = JSONArray()
                        workspace.listFiles(path.id).take(500).forEach { file ->
                            files.put(JSONObject().put("path", file.path).put("bytes", file.length))
                        }
                        respond(conn, 200, "application/json",
                            JSONObject().put("project_id", path.id)
                                .put("files", files).toString().toByteArray(Charsets.UTF_8))
                    }
                    is PhoneHostPolicy.Route.Export -> {
                        if (approvedProject != path.id) {
                            respond(conn, 403, "application/json", error("PROJECT_NOT_APPROVED"))
                            return
                        }
                        // Bounded in memory, app-private source only; no filesystem roots exposed.
                        val zip = object : ByteArrayOutputStream() {
                            override fun write(b: Int) {
                                check(count < PhoneHostPolicy.MAX_EXPORT_BYTES) { "Export too large." }
                                super.write(b)
                            }
                            override fun write(b: ByteArray, off: Int, len: Int) {
                                check(count.toLong() + len <= PhoneHostPolicy.MAX_EXPORT_BYTES) {
                                    "Export too large."
                                }
                                super.write(b, off, len)
                            }
                        }
                        workspace.exportZip(path.id, zip)
                        respond(conn, 200, "application/zip", zip.toByteArray())
                    }
                }
            } catch (_: Exception) {
                runCatching { respond(conn, 500, "application/json", error("REQUEST_FAILED")) }
            }
        }
    }

    private data class Request(
        val method: String,
        val target: String,
        val host: String,
        val auth: String,
        val origin: String?
    )

    private fun readRequest(input: InputStream): Request? {
        val bytes = ByteArrayOutputStream()
        var prev = 0
        var prev2 = 0
        var prev3 = 0
        while (bytes.size() < PhoneHostPolicy.MAX_HEADER_BYTES) {
            val b = input.read()
            if (b < 0 || b == 0 || b > 127) return null
            bytes.write(b)
            if (prev3 == 13 && prev2 == 10 && prev == 13 && b == 10) break
            prev3 = prev2
            prev2 = prev
            prev = b
        }
        val raw = bytes.toString("US-ASCII")
        if (!raw.endsWith("\r\n\r\n")) return null
        val lines = raw.dropLast(4).split("\r\n")
        if (lines.isEmpty() || lines.size > 40) return null
        val start = lines.first().split(' ')
        if (start.size != 3 || start[2] !in setOf("HTTP/1.0", "HTTP/1.1")) return null
        val headerMap = linkedMapOf<String, String>()
        for (line in lines.drop(1)) {
            val index = line.indexOf(':')
            if (index <= 0) return null
            val name = line.substring(0, index).lowercase(java.util.Locale.ROOT)
            if (name in headerMap || !name.matches(Regex("[a-z0-9-]+"))) return null
            headerMap[name] = line.substring(index + 1).trim()
        }
        if (headerMap.containsKey("transfer-encoding") ||
            headerMap["content-length"]?.let { it != "0" } == true) return null
        return Request(start[0], start[1],
            headerMap["host"] ?: "", headerMap["authorization"] ?: "",
            headerMap["origin"])
    }

    private fun error(value: String): ByteArray =
        JSONObject().put("error", value).toString().toByteArray(Charsets.UTF_8)

    private fun respond(conn: Socket, status: Int, mime: String, body: ByteArray) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val header = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $mime\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"
        conn.getOutputStream().write(header.toByteArray(StandardCharsets.US_ASCII))
        conn.getOutputStream().write(body)
        conn.getOutputStream().flush()
    }

    override fun close() {
        stopped = true
        approvedProject = null
        socket.close()
        executor.shutdownNow()
        tokenBytes.fill(0)
    }
}
