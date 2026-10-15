package ai.drfx.maximus.matrixai.studio.phonehost

import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/** Process-local requests are capabilities, never automatic code-execution consent. */
internal class PhoneBuildJobs(
    private val root: File,
    private val snapshot: (String, OutputStream) -> Unit,
    private val cancelOwned: (String) -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000L }
) : AutoCloseable {
    data class View(val id: String, val projectId: String, val phase: String,
        val sha256: String = "", val bytes: Long = 0, val files: List<String> = emptyList(),
        val log: String = "", val artifactSha256: String = "")
    data class Claim(val id: String, val projectId: String, val archive: File, val sha256: String, val isValid: () -> Boolean)
    private data class Job(var view: View, val directory: File, val created: Long, val authorizationRevision: Long, val authorizationValid: () -> Boolean,
        var artifact: File? = null)
    private val jobs = linkedMapOf<String, Job>()
    @Volatile private var closed = false
    private val revision = java.util.concurrent.atomic.AtomicLong()
    /** Lock-free revocation; expensive cleanup follows on IO. */
    fun invalidateApprovals() { revision.incrementAndGet() }
    fun invalidate() { closed = true; invalidateApprovals() }

    @Synchronized fun request(project: String, isAuthorized: () -> Boolean = { true }): View {
        check(!closed && isAuthorized())
        require(project.matches(UUID_PATTERN))
        expire()
        check(jobs.values.none { it.view.phase in ACTIVE }) { "A build request is already pending or running." }
        while (jobs.size >= 16) {
            val first = jobs.entries.first()
            first.value.directory.deleteRecursively()
            jobs.remove(first.key)
        }
        val capturedRevision = revision.get()
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { check(mkdirs()) }
        val job = Job(View(id, project, "capturing"), dir, now(), capturedRevision, isAuthorized)
        jobs[id] = job
        try {
            val zip = File(dir, "source.zip")
            zip.outputStream().use { raw ->
                val bounded = object : OutputStream() {
                    var bytes = 0L
                    override fun write(b: Int) { check(++bytes <= MAX_ZIP); raw.write(b) }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        bytes += len; check(bytes <= MAX_ZIP); raw.write(b, off, len)
                    }
                    override fun flush() = raw.flush()
                }
                snapshot(project, bounded)
            }
            val files = inspect(zip)
            check(!closed && isAuthorized() && revision.get() == capturedRevision) { "Project approval was revoked." }
            job.view = job.view.copy(phase = "awaiting_approval", sha256 = digest(zip),
                bytes = zip.length(), files = files)
        } catch (e: Exception) {
            job.directory.deleteRecursively()
            job.view = job.view.copy(phase = "failed")
            throw e
        }
        return job.view
    }

    @Synchronized fun views(): List<View> { expire(); return jobs.values.map { it.view } }
    @Synchronized fun get(id: String): View? { expire(); return jobs[id]?.view }

    /** Called only by visible phone UI, never routed from HTTP. */
    @Synchronized fun approve(id: String, sha256: String) {
        expire()
        val job = jobs[id] ?: error("Build request expired.")
        check(!closed && job.authorizationValid() && job.authorizationRevision == revision.get() && job.view.phase == "awaiting_approval" && job.view.sha256 == sha256)
        check(digest(File(job.directory, "source.zip")) == sha256)
        check(!closed && job.authorizationValid() && job.authorizationRevision == revision.get())
        job.view = job.view.copy(phase = "approved")
    }

    /** One-time consumption by the user-started foreground build service. */
    @Synchronized fun start(id: String, launch: (Claim) -> Unit) {
        val claim = claim(id)
        try { check(claim.isValid()); launch(claim) } catch (e: Exception) {
            jobs[id]?.let { it.view = it.view.copy(phase = "failed"); it.directory.deleteRecursively() }
            throw e
        }
    }

    @Synchronized fun claim(id: String): Claim {
        expire()
        val job = jobs[id] ?: error("Unknown build request.")
        check(!closed && job.authorizationValid() && job.authorizationRevision == revision.get() && job.view.phase == "approved")
        val archive = File(job.directory, "source.zip")
        check(digest(archive) == job.view.sha256)
        check(!closed && job.authorizationValid() && job.authorizationRevision == revision.get())
        job.view = job.view.copy(phase = "building")
        val capturedRevision = job.authorizationRevision
        return Claim(id, job.view.projectId, archive, job.view.sha256) {
            !closed && job.authorizationValid() && revision.get() == capturedRevision
        }
    }

    @Synchronized fun observe(id: String, state: PhoneBuildState) {
        val job = jobs[id] ?: return
        if (job.view.phase != "building" || closed || state.ownerJobId != id || state.projectId != job.view.projectId) return
        job.view = job.view.copy(log = state.logTail.takeLast(8192))
        if (state.busy) return
        if (state.phase == "succeeded") {
            runCatching {
                val source = File(requireNotNull(state.apkPath))
                check(source.isFile && source.length() in 1024L..MAX_APK)
                check(digest(source) == state.apkSha256)
                val apk = File(job.directory, "debug.apk")
                source.inputStream().use { input -> apk.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count; check(total <= MAX_APK)
                        output.write(buffer, 0, count)
                    }
                } }
                check(digest(apk) == state.apkSha256)
                job.artifact = apk
                job.view = job.view.copy(phase = "succeeded", artifactSha256 = state.apkSha256.orEmpty())
            }.onFailure {
                File(job.directory, "debug.apk").delete()
                job.view = job.view.copy(phase = "failed")
            }
        } else job.view = job.view.copy(phase = if (state.phase == "cancelled") "cancelled" else "failed")
        File(job.directory, "source.zip").delete()
    }

    @Synchronized fun artifact(id: String): File? {
        val job = jobs[id] ?: return null
        val apk = job.artifact ?: return null
        return apk.takeIf { !closed && job.view.phase == "succeeded" && it.isFile &&
            it.length() in 1024L..MAX_APK && digest(it) == job.view.artifactSha256 }
    }

    @Synchronized fun cancel(id: String): Boolean {
        val job = jobs[id] ?: return false
        if (job.view.phase !in ACTIVE) return true
        if (job.view.phase == "building") cancelOwned(id)
        job.view = job.view.copy(phase = "cancelled")
        File(job.directory, "source.zip").delete()
        return true
    }

    @Synchronized fun revokeProject(project: String?) {
        jobs.values.filter { project == null || it.view.projectId == project }
            .map { it.view.id }.forEach { id ->
                cancel(id)
                jobs.remove(id)?.directory?.deleteRecursively()
            }
    }

    @Synchronized override fun close() {
        invalidate()
        revokeProject(null)
        jobs.values.forEach { it.directory.deleteRecursively() }
        jobs.clear()
        root.deleteRecursively()
    }

    private fun expire() {
        jobs.values.filter { it.view.phase in setOf("awaiting_approval", "approved") &&
            now() - it.created >= 10 * 60_000L }.forEach {
            it.view = it.view.copy(phase = "expired"); it.directory.deleteRecursively()
        }
    }

    companion object {
        val UUID_PATTERN = Regex("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")
        private val ACTIVE = setOf("capturing", "awaiting_approval", "approved", "building")
        private const val MAX_ZIP = 8L * 1024 * 1024
        private const val MAX_APK = 128L * 1024 * 1024
        fun digest(file: File): String {
            val sha = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; sha.update(buffer, 0, n) }
            }
            return sha.digest().joinToString("") { "%02x".format(it) }
        }
        private fun inspect(file: File): List<String> {
            require(file.length() in 1..MAX_ZIP)
            val names = linkedSetOf<String>()
            var total = 0L
            ZipInputStream(file.inputStream()).use { zip ->
                val buffer = ByteArray(8192)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = entry.name.trimEnd('/')
                    require(path.length in 1..512 && !path.startsWith('/') &&
                        !path.contains('\\') && !path.contains(':') && !path.contains('\u0000') &&
                        path.split('/').none { it.isBlank() || it == "." || it == ".." })
                    require(names.add(path) && names.size <= 500)
                    var size = 0L
                    while (true) {
                        val n = zip.read(buffer); if (n < 0) break
                        size += n; total += n
                        require(size <= 4L * 1024 * 1024 && total <= 32L * 1024 * 1024)
                    }
                }
            }
            require(names.isNotEmpty())
            return names.toList()
        }
    }
}
