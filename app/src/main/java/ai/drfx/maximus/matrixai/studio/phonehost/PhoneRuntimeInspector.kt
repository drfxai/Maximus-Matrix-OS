package ai.drfx.maximus.matrixai.studio.phonehost

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

/** Evidence from the current phone, never a simulated Linux/toolchain install. */
internal data class PhoneRuntimeStatus(
    val androidApi: Int,
    val architecture: String,
    val supportedForOptionalArm64Runtime: Boolean,
    val availableStorageBytes: Long,
    val memoryClassMiB: Int,
    val installedProotExecutable: Boolean,
    val verifiedRootfsPresent: Boolean,
    val nativeRuntimeReady: Boolean
) {
    val headline: String get() = when {
        !supportedForOptionalArm64Runtime -> "Local Linux unavailable on this device"
        !installedProotExecutable -> "Linux runtime not installed"
        !verifiedRootfsPresent -> "Linux filesystem not verified"
        else -> "Files found; execution has not been verified"
    }
}

internal object PhoneRuntimeInspector {
    /**
     * A PRoot executable plus a root filesystem still cannot establish actual
     * execution: the process must pass a live spawn/exit and toolchain probe.
     * Therefore nativeRuntimeReady remains false until that engine exists.
     */
    fun inspect(context: Context): PhoneRuntimeStatus {
        val c = context.applicationContext
        val memory = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val abis = Build.SUPPORTED_ABIS.orEmpty().toList()
        val supports = Build.VERSION.SDK_INT >= 28 && abis.any { it == "arm64-v8a" }
        val runtimeDir = File(c.filesDir, "phone-runtime/rootfs")
        val proot = File(c.applicationInfo.nativeLibraryDir, "libproot.so")
        val fs = StatFs(c.filesDir.absolutePath)
        val hasRoot = File(runtimeDir, "usr/bin/bash").isFile &&
            File(runtimeDir, "etc/os-release").isFile
        val executable = proot.isFile && proot.canExecute()
        return PhoneRuntimeStatus(
            androidApi = Build.VERSION.SDK_INT,
            architecture = abis.firstOrNull().orEmpty().ifBlank { "unknown" },
            supportedForOptionalArm64Runtime = supports,
            availableStorageBytes = fs.availableBytes.coerceAtLeast(0L),
            memoryClassMiB = memory.memoryClass,
            installedProotExecutable = executable,
            verifiedRootfsPresent = hasRoot,
            nativeRuntimeReady = false
        )
    }
}
