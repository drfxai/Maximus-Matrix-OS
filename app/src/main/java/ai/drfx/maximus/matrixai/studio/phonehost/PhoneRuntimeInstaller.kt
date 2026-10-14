package ai.drfx.maximus.matrixai.studio.phonehost

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.system.Os
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

internal data class PhoneToolchainProgress(
    val phase: String = "idle",
    val message: String = "Install verified ARM64 Linux and Android build tools.",
    val fraction: Float = 0f,
    val transferred: Long = 0L,
    val total: Long = 0L
) {
    val busy: Boolean get() = phase in setOf("checking", "downloading", "extracting", "activating")
}

/** Pinned artifacts, no arbitrary URL, no scripts or imported project execution. */
internal class PhoneRuntimeInstaller(context: Context) {
    private val bundles = PhoneRuntimeBundlePins.ALL
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopped = AtomicBoolean(false)
    @Volatile private var stopReason = "Installation stopped. Downloaded chunks are retained for retry."
    private val progressStore = app.getSharedPreferences("phone_runtime_setup_status", Context.MODE_PRIVATE)
    private val base = File(app.filesDir, "phone-runtime")
    private val downloads = File(base, "downloads")
    private val target = File(base, "rootfs")
    private val staging = File(base, "rootfs.installing")
    private val old = File(base, "rootfs.previous")
    private val stateValue = MutableStateFlow(restoreLastOutcome())
    val state: StateFlow<PhoneToolchainProgress> get() = stateValue

    /** Persist terminal diagnostics only; never persist URLs with credentials or file contents. */
    private fun restoreLastOutcome(): PhoneToolchainProgress {
        val savedPhase = progressStore.getString("phase", null)
        val phase = savedPhase?.takeIf { it in setOf("cancelled", "interrupted", "failed", "installed") }
            ?: return PhoneToolchainProgress()
        return PhoneToolchainProgress(
            phase = phase,
            message = progressStore.getString("message", null)
                ?.take(400) ?: "Previous setup ended. Verify or resume installation."
        )
    }

    private fun finish(phase: String, message: String, transferred: Long = 0L, total: Long = 0L) {
        val safe = message.take(400)
        stateValue.value = PhoneToolchainProgress(phase, safe,
            if (phase == "installed") 1f else 0f, transferred, total)
        progressStore.edit().putString("phase", phase).putString("message", safe).apply()
    }

    fun storedDownloadBytes(): Long = bundles.sumOf { bundle ->
        listOf(File(downloads, bundle.name + ".verified"),
            File(downloads, bundle.name + ".part"))
            .firstOrNull { it.isFile && it.length() in 1L..bundle.size }
            ?.length() ?: 0L
    }

    fun installed(): Boolean =
        File(target, ".maximus-verified-android-bundles").isFile &&
        File(target, "usr/bin/bash").isFile &&
        File(target, "opt/gradle/gradle-8.14.3/bin/gradle").isFile &&
        File(target, "root/android-sdk/build-tools/35.0.0/aapt2").isFile

    @Synchronized fun install() {
        if (stateValue.value.busy) return
        stopped.set(false)
        stopReason = "Installation stopped. Tap Resume to keep using downloaded archive chunks."
        progressStore.edit().remove("phase").remove("message").apply()
        stateValue.value = PhoneToolchainProgress("checking", "Checking device and storage…")
        scope.launch {
            try {
                check(Build.VERSION.SDK_INT >= 28 && Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
                    "Requires ARM64 Android 9+."
                }
                check(StatFs(app.filesDir.absolutePath).availableBytes >= 3L * 1024 * 1024 * 1024) {
                    "At least 3 GiB free internal storage is required."
                }
                check(downloads.isDirectory || downloads.mkdirs())
                val total = bundles.sumOf { it.size }
                var finished = 0L
                val archives = bundles.map { bundle ->
                    val archive = download(bundle) { bytes ->
                        stateValue.value = PhoneToolchainProgress("downloading",
                            "Downloading " + bundle.title + "…",
                            ((finished + bytes).toDouble() / total * .7).toFloat(), finished + bytes, total)
                    }
                    finished += bundle.size
                    archive
                }
                check(!stopped.get()) { "Cancelled." }
                staging.deleteRecursively()
                check(staging.mkdirs())
                val links = linkedMapOf<String, String>()
                archives.forEachIndexed { index, file ->
                    stateValue.value = PhoneToolchainProgress("extracting",
                        "Unpacking " + bundles[index].title + "…",
                        .7f + index * .1f, finished, total)
                    unpack(file, staging, links, bundles[index].expandedLimit)
                }
                check(!stopped.get()) { "Cancelled." }
                check(File(staging, "usr/bin/bash").isFile)
                check(File(staging, "opt/gradle/gradle-8.14.3/bin/gradle").isFile)
                check(File(staging, "root/android-sdk/build-tools/35.0.0/aapt2").isFile)
                prepareGradleEnvironment(staging)
                File(staging, ".maximus-verified-android-bundles").writeText(
                    bundles.joinToString("\n") { it.name + " " + it.hash })
                stateValue.value = PhoneToolchainProgress("activating",
                    "Activating verified toolchain…", .95f, finished, total)
                old.deleteRecursively()
                val previous = target.exists()
                if (previous) check(target.renameTo(old)) { "Cannot backup previous runtime." }
                try {
                    check(staging.renameTo(target)) { "Cannot activate installed runtime." }
                } catch (e: Exception) {
                    if (previous) old.renameTo(target)
                    throw e
                }
                old.deleteRecursively()
                archives.forEach { it.delete() }
                finish("installed",
                    "Toolchain files verified. Run Verify tools to prove Gradle/JDK/AAPT2 execution.",
                    finished, total)
            } catch (e: Exception) {
                staging.deleteRecursively()
                val phase = if (stopped.get()) {
                    if (stopReason.startsWith("Cancelled by user")) "cancelled" else "interrupted"
                } else "failed"
                val reason = if (stopped.get()) stopReason
                    else (e.message ?: "Runtime setup failed. Tap Resume to retry.")
                finish(phase, reason)
            }
        }
    }

    /**
     * Distinguish a deliberate Cancel tap from Android destroying the service.
     * Downloads are not deleted, so a later install() can resume the same archives.
     */
    fun cancel(userRequested: Boolean = true) {
        if (stopped.compareAndSet(false, true)) {
            stopReason = if (userRequested)
                "Cancelled by user. Tap Resume to continue verified downloads."
            else
                "Android interrupted the installation service. Tap Resume to continue; downloaded chunks are retained."
        }
    }

    private fun sha256(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buf = ByteArray(131072)
            while (true) {
                check(!stopped.get()) { "Cancelled." }
                val n = stream.read(buf)
                if (n < 0) break
                hash.update(buf, 0, n)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun download(bundle: PhoneRuntimeBundlePins.Bundle, update: (Long) -> Unit): File {
        val verified = File(downloads, bundle.name + ".verified")
        if (verified.isFile && verified.length() == bundle.size && sha256(verified) == bundle.hash) {
            update(bundle.size)
            return verified
        }
        verified.delete()
        val partial = File(downloads, bundle.name + ".part")
        if (partial.length() > bundle.size) partial.delete()
        if (partial.isFile && partial.length() == bundle.size) {
            if (sha256(partial) == bundle.hash) {
                check(partial.renameTo(verified)) { "Cannot commit completed runtime download." }
                update(bundle.size)
                return verified
            }
            partial.delete()
        }
        var offset = partial.length()
        var url = URL("https://github.com/" + PhoneRuntimeBundlePins.OWNER_REPO +
            "/releases/download/" + PhoneRuntimeBundlePins.RELEASE + "/" + bundle.name)
        var redirects = 0
        while (true) {
            check(!stopped.get()) { "Cancelled." }
            check(url.protocol == "https" && url.host.lowercase() in setOf(
                "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"
            )) { "Untrusted download redirect host." }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 35_000
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0L) connection.setRequestProperty("Range", "bytes=" + offset + "-")
                when (val code = connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        check(redirects++ < 6) { "Too many redirects." }
                        url = URL(url, connection.getHeaderField("Location")
                            ?: error("Missing redirect address."))
                        continue
                    }
                    200, 206 -> {
                        if (code == 206) {
                            val range = connection.getHeaderField("Content-Range").orEmpty()
                            check(range.startsWith("bytes " + offset + "-") &&
                                range.endsWith("/" + bundle.size)) {
                                "Resume response did not match the archive size."
                            }
                        } else if (offset > 0L) {
                            partial.delete()
                            offset = 0L
                        }
                        FileOutputStream(partial, offset != 0L).use { output ->
                            connection.inputStream.use { input ->
                                val buf = ByteArray(131072)
                                var written = offset
                                while (true) {
                                    check(!stopped.get()) { "Cancelled." }
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    written += n
                                    check(written <= bundle.size) { "Bundle exceeded pinned size." }
                                    output.write(buf, 0, n)
                                    if (written % 2_097_152L < buf.size) update(written)
                                }
                                update(written)
                            }
                        }
                        check(partial.length() == bundle.size) { "Incomplete download. Retry to resume." }
                        val digest = sha256(partial)
                        if (digest != bundle.hash) {
                            partial.delete()
                            error("Pinned SHA-256 verification failed.")
                        }
                        check(partial.renameTo(verified)) { "Cannot finish verified archive." }
                        return verified
                    }
                    else -> error("Runtime download HTTP " + code)
                }
            } finally { connection.disconnect() }
        }
    }

    /** Configure the archive's pinned local Maven mirror, AAPT2 and low-memory Gradle. */
    private fun prepareGradleEnvironment(root: File) {
        val maven = File(root, "root/maven/localMvnRepository")
        check(maven.isDirectory && !maven.list().isNullOrEmpty()) {
            "Pinned Android bundle lacks the offline Maven dependency repository."
        }
        val initDir = File(root, "root/.gradle/init.d")
        check(initDir.isDirectory || initDir.mkdirs())
        File(initDir, "maximus-android.gradle").writeText(
            """
            def localMaven = uri('/root/maven/localMvnRepository')
            beforeSettings { settings ->
                settings.pluginManagement.repositories {
                    maven { url = localMaven }
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            settingsEvaluated { settings ->
                settings.dependencyResolutionManagement.repositories {
                    maven { url = localMaven }
                }
            }
            gradle.beforeProject { project ->
                project.extensions.extraProperties.set(
                    'android.aapt2FromMavenOverride',
                    '/root/android-sdk/build-tools/35.0.0/aapt2'
                )
                project.buildscript.repositories {
                    maven { url = localMaven }
                }
            }
            """.trimIndent() + "\n"
        )
        val gradleHome = File(root, "root/.gradle")
        File(gradleHome, "gradle.properties").writeText(
            "org.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8\n" +
                "org.gradle.workers.max=1\norg.gradle.parallel=false\n" +
                "kotlin.compiler.execution.strategy=in-process\n" +
                "android.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2\n"
        )
        val tools = File(root, "root/android-sdk/build-tools/35.0.0")
        listOf("aapt", "aapt2", "aidl", "apksigner", "d8",
            "dexdump", "zipalign", "split-select").forEach { name ->
            val tool = File(tools, name)
            if (tool.isFile) Os.chmod(tool.absolutePath, 493)
        }
        Os.chmod(File(root, "opt/gradle/gradle-8.14.3/bin/gradle").absolutePath, 493)
    }

    private fun tar(file: File, process: (TarArchiveInputStream) -> Unit) {
        ZstdCompressorInputStream(BufferedInputStream(file.inputStream(), 131072)).use { zstd ->
            TarArchiveInputStream(zstd, 131072).use(process)
        }
    }

    private fun member(path: String): String = PhoneRuntimePathPolicy.member(path)
    private fun linkTarget(guest: String, link: String): String =
        PhoneRuntimePathPolicy.linkTarget(guest, link)

    private fun destination(root: File, member: String, links: Map<String, String>): File {
        val safeRelative = PhoneRuntimePathPolicy.mappedRelative(member, links)
        val rootPath = root.absoluteFile.toPath().normalize()
        val path = rootPath.resolve(safeRelative).normalize()
        check(path != rootPath && path.startsWith(rootPath)) { "Escaping Linux staging root." }
        return path.toFile()
    }

    /**
     * Two sequential passes: validate bounds and discover symlinks, then extract.
     * Symlink parents are resolved in GUEST space; no Android-host absolute links
     * are traversed while writing data. No scripts are executed during install.
     */
    private fun unpack(
        archive: File, root: File, links: MutableMap<String, String>, maximum: Long
    ) {
        val newLinks = linkedMapOf<String, String>()
        var seen = 0
        var expanded = 0L
        tar(archive) { stream ->
            while (true) {
                check(!stopped.get()) { "Cancelled." }
                val e = stream.nextTarEntry ?: break
                val name = member(e.name)
                if (name.isEmpty()) continue
                check(++seen <= 250_000) { "Too many Linux files." }
                check(e.size in 0L..300_000_000L)
                expanded += e.size
                check(expanded <= maximum) { "Bundle decompressed beyond allowed size." }
                if (e.isSymbolicLink) {
                    linkTarget(name, e.linkName)
                    newLinks[name] = e.linkName
                } else check(e.isFile || e.isDirectory || e.isLink) {
                    "Unsupported Linux archive entry type."
                }
            }
        }
        links.putAll(newLinks)
        val hardlinks = mutableListOf<Pair<String, String>>()
        tar(archive) { stream ->
            var written = 0L
            while (true) {
                check(!stopped.get()) { "Cancelled." }
                val e = stream.nextTarEntry ?: break
                val name = member(e.name)
                if (name.isEmpty() || e.isSymbolicLink) continue
                if (e.isLink) {
                    hardlinks += name to member(e.linkName)
                    continue
                }
                val path = destination(root, name, links)
                if (e.isDirectory) {
                    if (!Files.isSymbolicLink(path.toPath())) {
                        check(path.isDirectory || path.mkdirs())
                    }
                    continue
                }
                check(e.isFile)
                check(path.parentFile?.isDirectory == true || path.parentFile?.mkdirs() == true)
                if (Files.isSymbolicLink(path.toPath())) check(path.delete())
                FileOutputStream(path, false).use { output ->
                    var remain = e.size
                    val buffer = ByteArray(131072)
                    while (remain > 0) {
                        check(!stopped.get()) { "Cancelled." }
                        val n = stream.read(buffer, 0, minOf(remain, buffer.size.toLong()).toInt())
                        check(n > 0) { "Truncated runtime bundle." }
                        output.write(buffer, 0, n)
                        remain -= n
                        written += n
                        check(written <= maximum) { "Runtime exceeded expanded size." }
                    }
                }
                Os.chmod(path.absolutePath, e.mode and 511)
            }
        }
        for ((name, target) in newLinks) {
            check(!stopped.get()) { "Cancelled." }
            val file = destination(root, name, links)
            file.parentFile?.mkdirs()
            if (file.exists() || Files.isSymbolicLink(file.toPath())) {
                check(!file.isDirectory || file.list().isNullOrEmpty()) {
                    "Conflicting runtime symlink."
                }
                check(file.delete())
            }
            Os.symlink(target, file.absolutePath)
        }
        for ((name, original) in hardlinks) {
            val file = destination(root, name, links)
            val source = destination(root, original, links)
            check(source.isFile && !Files.isSymbolicLink(source.toPath()))
            file.parentFile?.mkdirs()
            if (file.exists()) check(file.delete())
            Files.createLink(file.toPath(), source.toPath())
        }
    }
}

internal object PhoneRuntimeInstallSession {
    @Volatile private var instance: PhoneRuntimeInstaller? = null
    @Synchronized fun forContext(context: Context): PhoneRuntimeInstaller =
        instance ?: PhoneRuntimeInstaller(context.applicationContext).also { instance = it }
}
