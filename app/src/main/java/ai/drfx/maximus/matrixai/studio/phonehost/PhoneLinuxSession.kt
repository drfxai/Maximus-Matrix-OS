package ai.drfx.maximus.matrixai.studio.phonehost

import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class PhoneLinuxState(
    val id: String = "", val phase: String = "idle", val output: String = "", val exitCode: Int? = null
) { val busy: Boolean get() = phase == "running" }

/** A real bounded PRoot command session. Android UID privileges, never Android root. */
internal class PhoneLinuxSession(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Any()
    private val plugins = ai.drfx.maximus.matrixai.plugins.NativePlugins.registry(app)
    private var process: Process? = null
    private var cancelled = false
    private val mutable = MutableStateFlow(PhoneLinuxState())
    val state: StateFlow<PhoneLinuxState> = mutable

    fun start(id: String, command: String, approved: Boolean) = synchronized(mutex) {
        check(plugins.installed("phone-linux")) { "Install the Linux plugin first." }
        check(approved) { "Review and approve this command first." }
        check(!mutable.value.busy)
        require(UUID.fromString(id).toString() == id)
        val source = PhoneLinuxCommand.validate(command)
        val lease = PhoneRuntimeCoordinator.acquire(PhoneRuntimeCoordinator.Operation.LINUX)
        cancelled = false
        mutable.value = PhoneLinuxState(id, "running")
        scope.launch {
            var terminal: PhoneLinuxState? = null
            try {
                check(Build.VERSION.SDK_INT >= 28 && "arm64-v8a" in Build.SUPPORTED_ABIS)
                val root = File(app.filesDir, "phone-runtime/rootfs")
                val native = File(app.applicationInfo.nativeLibraryDir)
                check(File(root, ".maximus-verified-android-bundles").isFile)
                val executable = File(native, "libproot.so")
                check(executable.canExecute() && File(native, "libprootloader.so").isFile)
                val workspace = File(app.filesDir, "phone-linux-workspace").apply { check(isDirectory || mkdirs()) }
                File(root, "root/workspace").apply { check(isDirectory || mkdirs()) }
                val args = mutableListOf(executable.absolutePath, "--kill-on-exit", "--link2symlink",
                    "-0", "-r", root.absolutePath, "-b", "/dev", "-b", "/proc", "-b", "/sys")
                for (host in listOf("/system", "/apex", "/vendor", "/product")) {
                    if (File(host).isDirectory) args.addAll(listOf("-b", host))
                }
                args.addAll(listOf("-b", workspace.absolutePath + ":/root/workspace", "-w", "/root/workspace", "/bin/bash", "-c", source))
                val builder = ProcessBuilder(args).directory(workspace).redirectErrorStream(true)
                builder.environment().apply {
                    clear()
                    put("HOME", "/root")
                    put("PATH", "/opt/jdk-17.0.20.1+1/bin:/opt/gradle/gradle-8.14.3/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                    put("JAVA_HOME", "/opt/jdk-17.0.20.1+1")
                    put("ANDROID_HOME", "/root/android-sdk")
                    put("ANDROID_SDK_ROOT", "/root/android-sdk")
                    put("LANG", "C.UTF-8")
                    put("PROOT_NO_SECCOMP", "1")
                    put("PROOT_LOADER", File(native, "libprootloader.so").absolutePath)
                    put("PROOT_TMP_DIR", File(app.cacheDir, "proot-linux").apply { mkdirs() }.absolutePath)
                    put("LD_LIBRARY_PATH", native.absolutePath)
                    put("GLIBC_TUNABLES", "glibc.pthread.rseq=0")
                }
                val child = synchronized(mutex) {
                    check(!cancelled)
                    builder.start().also { process = it }
                }
                // No input stream or PTY is advertised; use noninteractive commands.
                child.outputStream.close()
                val pump = Thread {
                    runCatching {
                        child.inputStream.reader().use { input ->
                            val chars = CharArray(2048)
                            while (true) {
                                val n = input.read(chars)
                                if (n < 0) break
                                synchronized(mutex) {
                                    if (mutable.value.id == id) mutable.value = mutable.value.copy(
                                        output = PhoneLinuxCommand.tail(mutable.value.output, String(chars, 0, n)))
                                }
                            }
                        }
                    }
                }.apply { isDaemon = true; start() }
                val deadline = System.nanoTime() + PhoneLinuxCommand.TIMEOUT_SECONDS * 1_000_000_000L
                var finished = false
                while (System.nanoTime() < deadline) {
                    val revoked = !plugins.installed("phone-linux")
                    if (revoked) synchronized(mutex) { cancelled = true }
                    if (synchronized(mutex) { cancelled }) break
                    if (child.waitFor(400, TimeUnit.MILLISECONDS)) { finished = true; break }
                }
                if (!finished) child.destroy() // PRoot kills its tracees when exiting.
                if (!child.waitFor(3, TimeUnit.SECONDS)) child.destroyForcibly()
                child.waitFor(3, TimeUnit.SECONDS)
                pump.join(1500)
                synchronized(mutex) {
                    terminal = mutable.value.copy(
                        phase = if (cancelled) "cancelled" else if (!finished) "timeout" else "finished",
                        exitCode = if (finished && !cancelled) child.exitValue() else null)
                }
            } catch (_: Exception) {
                synchronized(mutex) {
                    terminal = mutable.value.copy(phase = if (cancelled) "cancelled" else "failed")
                }
            } finally {
                synchronized(mutex) {
                    process?.let { child ->
                        if (child.isAlive) child.destroy()
                        process = null
                    }
                }
                lease.close()
                synchronized(mutex) { terminal?.let { mutable.value = it } }
            }
        }
        Unit
    }
    fun reportStartFailure(id: String) = synchronized(mutex) {
        if (!mutable.value.busy) mutable.value = PhoneLinuxState(id, "failed")
    }
    fun cancel(id: String) = synchronized(mutex) {
        if (mutable.value.id == id && mutable.value.busy) {
            cancelled = true
            process?.destroy()
        }
    }
}

internal object PhoneLinuxSessions {
    @Volatile private var instance: PhoneLinuxSession? = null
    @Synchronized fun get(context: Context): PhoneLinuxSession = instance ?:
        PhoneLinuxSession(context).also { instance = it }
}
