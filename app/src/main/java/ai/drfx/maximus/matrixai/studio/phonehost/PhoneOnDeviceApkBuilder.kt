package ai.drfx.maximus.matrixai.studio.phonehost

import android.content.Context
import android.os.Build
import android.os.StatFs
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

internal data class PhoneBuildState(
    val phase: String = "idle",
    val ownerJobId: String? = null,
    val message: String = "Verify the on-phone Android build tools to enable compilation.",
    val projectId: String? = null,
    val logTail: String = "",
    val apkSha256: String? = null,
    val apkPath: String? = null,
    val verifiedTools: Boolean = false,
    val startedAtMs: Long = 0L,
    val apkBytes: Long = 0L
) {
    val busy: Boolean get() = phase == "restoring" || phase == "verifying" || phase == "building"
}

/**
 * Performs a REAL Gradle :app:assembleDebug inside a PRoot guest on this phone.
 * It cannot work until compatible PRoot loader, rootfs, JDK, Gradle, SDK and
 * ARM64 AAPT2 are installed and successfully tested. No result is fabricated.
 *
 * Trusted owner-controlled source ONLY: PRoot is not a hardened sandbox, and
 * Gradle build scripts are executable code with the Matrix app's Android UID.
 */
internal class PhoneOnDeviceApkBuilder(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Any()
    private val cancelled = AtomicBoolean(false)
    private var process: Process? = null
    private var ownerJobId: String? = null
    private val _state = MutableStateFlow(PhoneBuildState(phase = "restoring", message = "Checking saved build artifact…"))
    val state: StateFlow<PhoneBuildState> get() = _state
    private val rootfs get() = File(app.filesDir, "phone-runtime/rootfs")
    private val proot get() = File(app.applicationInfo.nativeLibraryDir, "libproot.so")
    private val loader get() = File(app.applicationInfo.nativeLibraryDir, "libprootloader.so")
    private val jobs get() = File(app.cacheDir, "phone-apk-jobs")

    private val artifacts = PhoneBuildArtifactStore(File(app.filesDir, "phone-build-artifacts"))

    init {
        scope.launch {
            val saved = artifacts.restore()
            val archive = saved?.let { runCatching {
                app.packageManager.getPackageArchiveInfo(it.file.absolutePath, 0)
            }.getOrNull() }
            synchronized(mutex) {
                if (saved != null && archive?.packageName == saved.packageName) {
                    _state.value = PhoneBuildState(
                        phase = "succeeded", message = "Saved Debug APK verified. Run tool probes before a new build.",
                        projectId = saved.projectId, apkSha256 = saved.sha256,
                        apkPath = saved.file.absolutePath, apkBytes = saved.file.length()
                    )
                } else _state.value = PhoneBuildState()
            }
        }
    }

    fun verifyTools() {
        synchronized(mutex) {
            if (_state.value.busy) return
            cancelled.set(false)
            ownerJobId = null
            _state.value = _state.value.copy(phase = "verifying", ownerJobId = null, message = "Running actual on-device tool probes…", verifiedTools = false, startedAtMs = System.currentTimeMillis())
        }
        scope.launch {
            try {
                requireDeviceAndTools()
                val probeWorkspace = File(app.cacheDir, "phone-android-probe").apply { mkdirs() }
                val proof = execute(
                    workspace = probeWorkspace,
                    command = "command -v gradle >/dev/null && command -v java >/dev/null && " +
                        "test -f /root/.gradle/init.d/maximus-android.gradle && " +
                        "test -d /root/maven/localMvnRepository && " +
                        "test -f /root/android-sdk/platforms/android-36/android.jar && " +
                        "test -x /root/android-sdk/build-tools/35.0.0/aapt2 && " +
                        "gradle --version && /root/android-sdk/build-tools/35.0.0/aapt2 version",
                    timeoutSeconds = 90
                )
                check(proof.exit == 0 && !proof.cancelled) {
                    "The installed Gradle/JDK/SDK/AAPT2 tools did not pass execution checks."
                }
                _state.value = _state.value.copy(
                    phase = "ready",
                    verifiedTools = true,
                    message = "Gradle, Java, Android SDK and ARM64 AAPT2 ran successfully on this phone.",
                    logTail = proof.log.takeLast(4000)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    phase = "unavailable",
                    message = (e.message ?: "On-phone runtime verification failed.").take(500),
                    verifiedTools = false
                )
            }
        }
    }

    /** The Compose screen must obtain explicit per-build consent before calling this. */
    fun build(projectId: String) = buildInternal(projectId, null)

    fun buildApproved(claim: PhoneBuildJobs.Claim) = buildInternal(claim.projectId, claim)
    fun buildForService(projectId: String, ownerId: String, isValid: () -> Boolean) =
        buildInternal(projectId, null, ownerId, isValid)

    fun cancelOwned(id: String) {
        synchronized(mutex) { if (ownerJobId == id && _state.value.busy) cancel() }
    }

    private fun buildInternal(projectId: String, claim: PhoneBuildJobs.Claim?, ownerId: String? = claim?.id, allowed: () -> Boolean = { true }) {
        synchronized(mutex) {
            require(!_state.value.busy) { "Another local operation is running." }
            check(allowed() && (claim == null || claim.isValid())) { "Build approval was revoked." }
            require(_state.value.verifiedTools) { "Verify on-phone build tools first." }
            require(projectId.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))) {
                "Choose an existing Studio project."
            }
            cancelled.set(false)
            ownerJobId = ownerId
            _state.value = PhoneBuildState(
                phase = "building", ownerJobId = ownerId, message = "Creating a private snapshot; building on your phone…",
                projectId = projectId, verifiedTools = true, startedAtMs = System.currentTimeMillis()
            )
        }
        scope.launch {
            var jobRoot: File? = null
            try {
                requireDeviceAndTools()
                val freeBytes = StatFs(app.cacheDir.absolutePath).availableBytes
                require(freeBytes >= 2L * 1024 * 1024 * 1024) {
                    "At least 2 GiB free cache/storage space is required for a Gradle build."
                }
                val temp = File(jobs, UUID.randomUUID().toString()).apply {
                    require(mkdirs()) { "Cannot create a local build workspace." }
                }
                jobRoot = temp
                val workspace = File(temp, "workspace").apply { mkdirs() }
                val project = ProjectWorkspaceStore(app)
                val archive = File(temp, "project.zip")
                check(allowed() && (claim == null || claim.isValid())) { "Build approval was revoked." }
                if (claim == null) archive.outputStream().use { project.exportZip(projectId, it) }
                else {
                    // The exact ZIP reviewed on phone, not the mutable Studio project.
                    claim.archive.inputStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
                    check(PhoneBuildJobs.digest(archive) == claim.sha256) { "Approved source snapshot changed." }
                }
                require(archive.length() in 1..(8L * 1024 * 1024)) {
                    "Project ZIP must be under 8 MiB."
                }
                unpackTrustedStudioSnapshot(archive, workspace)
                require(File(workspace, "settings.gradle.kts").isFile &&
                    File(workspace, "app/build.gradle.kts").isFile) {
                    "This workspace is not a Gradle Kotlin Android project."
                }
                archive.delete()
                check(!cancelled.get()) { "Build cancelled." }
                val startedAt = System.currentTimeMillis()
                val result = execute(
                    workspace = workspace,
                    command = "cd /workspace && gradle --offline --no-daemon --max-workers=1 " +
                        "--console=plain -Dorg.gradle.jvmargs=-Xmx768m " +
                        "-Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 " +
                        ":app:assembleDebug",
                    timeoutSeconds = 15 * 60,
                    allowed = { allowed() && (claim == null || claim.isValid()) }
                )
                if (result.cancelled || cancelled.get()) {
                    _state.value = PhoneBuildState(
                        phase = "cancelled", ownerJobId = ownerId, message = "Local build cancelled.",
                        projectId = projectId, logTail = result.log
                    )
                    return@launch
                }
                if (result.timedOut) error("Gradle exceeded the 15-minute limit. The process was stopped; inspect the build log.")
                require(result.exit == 0) {
                    "Gradle failed with exit code " + result.exit + ". Inspect the build log."
                }
                val outputDir = File(workspace, "app/build/outputs/apk/debug")
                val apks = outputDir.listFiles().orEmpty().filter {
                    it.isFile && it.extension == "apk" && it.length() in 1024L..(128L * 1024 * 1024) &&
                        it.lastModified() >= startedAt - 2000L
                }
                require(apks.size == 1) { "Expected one newly built Debug APK; found " + apks.size }
                val apk = apks.single()
                // Do not advertise a merely renamed ZIP as an Android package.
                val archiveInfo = app.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
                require(archiveInfo?.packageName?.isNotBlank() == true) {
                    "Generated APK could not be parsed as an Android package."
                }
                val artifact = artifacts.save(apk, projectId, archiveInfo.packageName)
                _state.value = PhoneBuildState(
                    phase = "succeeded", ownerJobId = ownerId,
                    message = "Debug APK compiled ON THIS PHONE. Package: " + archiveInfo.packageName,
                    projectId = projectId, logTail = result.log,
                    apkSha256 = artifact.sha256, apkPath = artifact.file.absolutePath,
                    verifiedTools = true, apkBytes = artifact.file.length()
                )
            } catch (e: Exception) {
                _state.value = PhoneBuildState(
                    phase = if (cancelled.get()) "cancelled" else "failed", ownerJobId = ownerId,
                    message = (e.message ?: "Local Android build failed.").take(500),
                    projectId = projectId, logTail = _state.value.logTail,
                    verifiedTools = false
                )
            } finally {
                // Keep only a successful APK, not potentially sensitive source snapshots.
                synchronized(mutex) { if (ownerJobId == ownerId) ownerJobId = null }
                jobRoot?.let { root ->
                    File(root, "workspace").deleteRecursively()
                    File(root, "project.zip").delete()
                    root.deleteRecursively()
                }
            }
        }
    }

    fun cancel() {
        cancelled.set(true)
        synchronized(mutex) { process?.destroyForcibly() }
    }

    fun exportVerifiedApk(output: java.io.OutputStream) = exportVerifiedApk(output, null)

    fun exportVerifiedApk(output: java.io.OutputStream, expectedSha256: String?) {
        val ready = _state.value
        require(expectedSha256 == null || ready.apkSha256 == expectedSha256) {
            "The selected APK changed. Choose Save APK again."
        }
        require(!ready.busy && ready.apkPath != null &&
            ready.apkSha256?.matches(Regex("[a-f0-9]{64}")) == true) {
            "No verified on-device APK is available."
        }
        val artifact = File(ready.apkPath)
        require(artifact.isFile && artifact.length() in 1024L..(128L * 1024 * 1024))
        check(sha256(artifact) == ready.apkSha256) { "Local APK SHA-256 changed before export." }
        artifact.inputStream().use { it.copyTo(output) }
    }

    private fun requireDeviceAndTools() {
        require(Build.VERSION.SDK_INT >= 28 &&
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "Native PRoot Android builds require ARM64 and Android 9 or newer."
        }
        require(proot.isFile && proot.canExecute() && loader.isFile) {
            "Compatible ARM64 PRoot launcher/loader is not installed in this APK."
        }
        require(File(rootfs, "bin/sh").exists() || File(rootfs, "usr/bin/bash").isFile) {
            "Verified Linux root filesystem is not installed."
        }
        require(File(rootfs, "opt/gradle/gradle-8.14.3/bin/gradle").isFile &&
            File(rootfs, "root/android-sdk/build-tools/35.0.0/aapt2").isFile) {
            "On-device Android build toolchain is missing."
        }
    }

    private data class Execution(val exit: Int, val cancelled: Boolean, val log: String, val timedOut: Boolean = false)

    private fun execute(workspace: File, command: String, timeoutSeconds: Int, allowed: () -> Boolean = { true }): Execution {
        val arguments = mutableListOf(
            proot.absolutePath, "--link2symlink", "-0",
            "-r", rootfs.absolutePath, "-b", "/dev", "-b", "/proc", "-b", "/sys"
        )
        listOf("/system", "/apex", "/vendor", "/product").forEach {
            if (File(it).isDirectory) arguments.addAll(listOf("-b", it))
        }
        arguments.addAll(listOf(
            "-b", workspace.absolutePath + ":/workspace",
            "-w", "/workspace", "/bin/sh", "-c", command
        ))
        val pb = ProcessBuilder(arguments).directory(app.filesDir).redirectErrorStream(true)
        val env = pb.environment()
        env.clear()
        env["HOME"] = "/root"
        env["JAVA_HOME"] = "/opt/jdk-17.0.20.1+1"
        env["GRADLE_HOME"] = "/opt/gradle/gradle-8.14.3"
        env["ORG_GRADLE_PROJECT_android.aapt2FromMavenOverride"] =
            "/root/android-sdk/build-tools/35.0.0/aapt2"
        env["PATH"] = "/opt/jdk-17.0.20.1+1/bin:/opt/gradle/gradle-8.14.3/bin:" +
            "/root/android-sdk/build-tools/35.0.0:" +
            "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["ANDROID_HOME"] = "/root/android-sdk"
        env["ANDROID_SDK_ROOT"] = "/root/android-sdk"
        env["GRADLE_USER_HOME"] = "/root/.gradle"
        env["LANG"] = "C.UTF-8"
        env["PROOT_NO_SECCOMP"] = "1"
        env["PROOT_LOADER"] = loader.absolutePath
        env["PROOT_TMP_DIR"] = File(app.cacheDir, "proot-temp").apply { mkdirs() }.absolutePath
        env["LD_LIBRARY_PATH"] = app.applicationInfo.nativeLibraryDir
        env["GLIBC_TUNABLES"] = "glibc.pthread.rseq=0"
        val log = StringBuilder()
        val executionOwner = _state.value.ownerJobId
        val child = synchronized(mutex) {
            check(!cancelled.get() && allowed()) { "Build cancelled or approval revoked before process start." }
            pb.start().also { process = it }
        }
        val pump = Thread {
            try {
                child.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(log) {
                            log.append(line.take(512)).append('\n')
                            if (log.length > 48_000) log.delete(0, log.length - 40_000)
                            val current = _state.value
                            if (current.ownerJobId == executionOwner && (current.phase == "building" || current.phase == "verifying")) {
                                _state.value = current.copy(logTail = log.toString())
                            }
                        }
                    }
                }
            } catch (_: Exception) { /* Process may have been killed. */ }
        }.apply { isDaemon = true; start() }
        val deadline = System.nanoTime() + timeoutSeconds.toLong() * 1_000_000_000L
        try {
            while (true) {
                if (cancelled.get() || !allowed() || System.nanoTime() >= deadline) {
                    child.destroyForcibly()
                    return Execution(-1, cancelled.get() || !allowed(), synchronized(log) { log.toString() }, timedOut = !cancelled.get() && allowed())
                }
                if (child.waitFor(400L, TimeUnit.MILLISECONDS)) {
                    pump.join(1500)
                    return Execution(child.exitValue(), false, synchronized(log) { log.toString() })
                }
            }
        } finally {
            if (child.isAlive) child.destroyForcibly()
            synchronized(mutex) { if (process === child) process = null }
        }
    }

    private fun unpackTrustedStudioSnapshot(archive: File, destination: File) {
        var total = 0L
        var entries = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= 500)
                val name = entry.name
                require(name.length in 1..512 && !name.startsWith("/") &&
                    !name.contains('\\') && !name.contains(':') && !name.contains('\u0000') &&
                    name.trimEnd('/').split('/').none { it.isBlank() || it == ".." || it == "." }) {
                    "Unsafe project source path."
                }
                val target = File(destination, name.trimEnd('/')).canonicalFile
                require(target.toPath().startsWith(destination.canonicalFile.toPath()) &&
                    target != destination.canonicalFile)
                if (entry.isDirectory) target.mkdirs() else {
                    require(!target.exists()) { "Duplicate project source." }
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out ->
                        val bytes = ByteArray(8192)
                        while (true) {
                            val count = zip.read(bytes)
                            if (count < 0) break
                            total += count
                            require(total <= 32L * 1024 * 1024 &&
                                target.length() + count <= 4L * 1024 * 1024) {
                                "Expanded project is too large."
                            }
                            out.write(bytes, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal object PhoneBuildSession {
    @Volatile private var instance: PhoneOnDeviceApkBuilder? = null
    @Synchronized fun forContext(context: Context): PhoneOnDeviceApkBuilder =
        instance ?: PhoneOnDeviceApkBuilder(context.applicationContext).also { instance = it }
}
