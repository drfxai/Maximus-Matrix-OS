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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.thread

/**
 * User-started, LOCALHOST-ONLY Android development gateway. This is a real
 * listening HTTP socket that serves app-owned project metadata and, only
 * after an explicit opt-in for one project, a bounded ZIP snapshot.
 * Bounded build requests require a separate on-phone approval before the
 * foreground service runs the real PRoot builder. No shell/PTY, arbitrary
 * commands, model inference or public-network access are exposed.
 */
internal class PhoneHarnessHttpServer(
    context: Context,
    private val buildReady: () -> Boolean = { PhoneBuildSession.forContext(context).state.value.verifiedTools },
    private val snapshotter: ((String, java.io.OutputStream) -> Unit)? = null
) : AutoCloseable {
    private val application = context.applicationContext
    private val tokenBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val bearerToken: String = tokenBytes.joinToString("") { "%02x".format(it) }
    private val workerId: String = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
    private val workspace = ProjectWorkspaceStore(application)
    val jobs = PhoneBuildJobs(
        java.io.File(application.cacheDir, "phone-server-jobs/" + java.util.UUID.randomUUID()),
        { project, output ->
            if (snapshotter == null) workspace.exportZip(project, output) else snapshotter.invoke(project, output)
        },
        { id -> PhoneBuildSession.forContext(application).cancelOwned(id) }
    )
    private val socket = ServerSocket()
    private val executor = ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4),
        { runnable ->
            thread(start = false, isDaemon = true, name = "phone-harness-client") {
                runnable.run()
            }
        },
        ThreadPoolExecutor.AbortPolicy()
    )
    private val projectAuthorization = Any()
    @Volatile private var stopped = false
    @Volatile private var projectChanging = false
    private val authorizationRevision = java.util.concurrent.atomic.AtomicLong()
    fun invalidateAuthorization(): Long {
        projectChanging = true
        jobs.invalidateApprovals()
        return authorizationRevision.incrementAndGet()
    }
    fun invalidate() { stopped = true; jobs.invalidate(); runCatching { socket.close() } }
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
                    try {
                        executor.execute { handle(client) }
                    } catch (_: RejectedExecutionException) {
                        // Bounded queue. A local flood must not retain sockets forever.
                        client.close()
                    }
                } catch (_: Exception) {
                    if (stopped) break
                }
            }
        }
    }

    /** Only project IDs already present in the app can be shared. */
    fun approveProject(id: String?, expectedRevision: Long = invalidateAuthorization()): Boolean {
        synchronized(projectAuthorization) {
            if (stopped || authorizationRevision.get() != expectedRevision) return false
            if (id != null) require(workspace.projects().any { it.id == id }) { "Unknown project." }
            jobs.revokeProject(approvedProject)
            approvedProject = id
            projectChanging = false
            return true
        }
    }

    private fun handle(client: Socket) {
        client.use { conn ->
            var streamingResponseStarted = false
            try {
                conn.soTimeout = 3000
                conn.tcpNoDelay = true
                val request = readRequest(conn.getInputStream()) ?: run {
                    respond(conn, 400, "application/json", error("BAD_REQUEST"))
                    return
                }
                if (stopped) { respond(conn, 403, "application/json", error("HOST_STOPPED")); return }
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
                    PhoneHostPolicy.Route.BuildRequest -> {
                        if (!buildReady()) {
                            respond(conn, 409, "application/json", error("TOOLS_NOT_VERIFIED")); return
                        }
                        val match = Regex("^\\s*\\{\\s*\"project_id\"\\s*:\\s*\"([a-f0-9-]{36})\"\\s*}\\s*$")
                            .matchEntire(request.body)
                        val project = match?.groupValues?.get(1)
                        if (project == null || !PhoneBuildJobs.UUID_PATTERN.matches(project)) {
                            respond(conn, 400, "application/json", error("INVALID_BUILD_REQUEST")); return
                        }
                        val job = try { synchronized(projectAuthorization) {
                            val epoch = authorizationRevision.get()
                            if (approvedProject != project || stopped || projectChanging) {
                                respond(conn, 403, "application/json", error("PROJECT_NOT_APPROVED")); return
                            }
                            jobs.request(project) {
                                !stopped && !projectChanging && authorizationRevision.get() == epoch && approvedProject == project
                            }
                        } } catch (_: Exception) {
                            respond(conn, 409, "application/json", error("BUILD_REQUEST_UNAVAILABLE")); return
                        }
                        respond(conn, 202, "application/json", jobJson(job))
                    }
                    is PhoneHostPolicy.Route.Job -> {
                        val job = synchronized(projectAuthorization) {
                            jobs.get(path.id)?.takeIf { !stopped && !projectChanging && approvedProject == it.projectId }
                        }
                        if (job == null) { respond(conn, 404, "application/json", error("UNKNOWN_JOB")); return }
                        when (path.operation) {
                            "cancel" -> {
                                if (request.body.isNotEmpty()) { respond(conn, 400, "application/json", error("EMPTY_BODY_REQUIRED")); return }
                                jobs.cancel(path.id)
                                respond(conn, 200, "application/json", jobJson(requireNotNull(jobs.get(path.id))))
                            }
                            "artifact" -> {
                                val apk = jobs.artifact(path.id)
                                if (apk == null) { respond(conn, 409, "application/json", error("ARTIFACT_UNAVAILABLE")); return }
                                apk.inputStream().use { input ->
                                    val contentLength = apk.length()
                                    require(contentLength in 1024L..(128L * 1024 * 1024))
                                    val output = conn.getOutputStream()
                                    streamingResponseStarted = true
                                    output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\n" +
                                        "Content-Length: ${contentLength}\r\nCache-Control: no-store\r\n" +
                                        "X-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                                    var remaining = contentLength
                                    val buffer = ByteArray(8192)
                                    while (remaining > 0L) {
                                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                        check(count > 0)
                                        output.write(buffer, 0, count)
                                        remaining -= count
                                    }
                                    output.flush()
                                }
                            }
                            "log" -> respond(conn, 200, "text/plain; charset=utf-8", job.log.toByteArray(Charsets.UTF_8).takeLast(8192).toByteArray())
                            else -> respond(conn, 200, "application/json", jobJson(job))
                        }
                    }
                    PhoneHostPolicy.Route.Capabilities -> respond(
                        conn, 200, "application/json", JSONObject()
                            .put("protocol", "maximus-phone-host")
                            .put("api_version", 1)
                            .put("worker_id", workerId)
                            .put("platform", "android")
                            .put("connection", "ready")
                            .put("execution_enabled", false)
                            .put("arbitrary_execution_enabled", false)
                            .put("build_execution_ready", buildReady())
                            .put("build_execution_approval", "phone_required")
                            .put("build_requests", "phone_approval_required")
                            .put("runtime_engine", if (buildReady()) "proot_toolchain_verified" else "execution_unverified")
                            .put("execution_mode", "phone_approved_build_jobs")
                            .put("network_scope", "loopback_only")
                            .put("supported_operations", JSONArray()
                                .put("capabilities.read")
                                .put("projects.list")
                                .put("projects.manifest")
                                .put("projects.export.with_approval")
                                .put("build_jobs.request.with_phone_approval")
                                .put("build_jobs.status").put("build_jobs.cancel").put("build_jobs.artifact"))
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
                if (!streamingResponseStarted) runCatching {
                    respond(conn, 500, "application/json", error("REQUEST_FAILED"))
                }
            }
        }
    }

    private data class Request(
        val method: String,
        val target: String,
        val host: String,
        val auth: String,
        val origin: String?,
        val body: String
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
        if (headerMap.containsKey("transfer-encoding")) return null
        val length = headerMap["content-length"]?.toIntOrNull() ?: if (headerMap.containsKey("content-length")) return null else 0
        if (length !in 0..512 || (start[0] != "POST" && length != 0)) return null
        if (length > 0 && headerMap["content-type"] != "application/json") return null
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(body, offset, length - offset)
            if (count < 0) return null
            offset += count
        }
        if (body.any { it < 0 || it.toInt() == 0 }) return null
        return Request(start[0], start[1],
            headerMap["host"] ?: "", headerMap["authorization"] ?: "",
            headerMap["origin"], body.toString(Charsets.US_ASCII))
    }

    private fun jobJson(view: PhoneBuildJobs.View): ByteArray = JSONObject()
        .put("job_id", view.id).put("project_id", view.projectId).put("phase", view.phase)
        .put("source_sha256", view.sha256).put("source_bytes", view.bytes)
        .put("file_count", view.files.size).put("artifact_sha256", view.artifactSha256)
        .toString().toByteArray(Charsets.UTF_8)

    private fun error(value: String): ByteArray =
        JSONObject().put("error", value).toString().toByteArray(Charsets.UTF_8)

    private fun respond(conn: Socket, status: Int, mime: String, body: ByteArray) {
        val reason = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            409 -> "Conflict"
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
        invalidate()
        synchronized(projectAuthorization) {
            jobs.close()
            approvedProject = null
        }
        socket.close()
        executor.shutdownNow()
        tokenBytes.fill(0)
    }
}
