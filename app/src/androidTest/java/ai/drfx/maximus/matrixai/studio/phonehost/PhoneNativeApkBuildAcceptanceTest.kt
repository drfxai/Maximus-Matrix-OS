package ai.drfx.maximus.matrixai.studio.phonehost

import android.os.Build
import android.os.SystemClock
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit physical-device gate; deliberately NOT counted as an x86 emulator test. */
@RunWith(AndroidJUnit4::class)
class PhoneNativeApkBuildAcceptanceTest {
    @Test fun physicalArm64BuildsAndExportsRealApk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("approvedOwnerBuild") == "true") {
            "Explicit trusted starter-project execution approval is required."
        }
        val sourceCommit = arguments.getString("sourceCommit").orEmpty()
        require(sourceCommit.matches(Regex("[a-f0-9]{40}")))
        check(Build.VERSION.SDK_INT >= 28 && "arm64-v8a" in Build.SUPPORTED_ABIS)
        check(!Build.FINGERPRINT.contains("generic", ignoreCase = true) &&
            !Build.MODEL.contains("sdk", ignoreCase = true)) {
            "Physical ARM64 hardware is required; an emulator cannot satisfy this gate."
        }
        val context = instrumentation.targetContext
        val evidence = File(context.getExternalFilesDir(null), "physical-acceptance").apply { mkdirs() }
        val proofFile = File(evidence, "result.json")
        proofFile.delete() // An earlier success must never survive this attempt.
        val builder = PhoneOnDeviceApkBuilder(context)
        await(30_000) { !builder.state.value.busy }
        builder.verifyTools()
        await(120_000) { !builder.state.value.busy }
        check(builder.state.value.verifiedTools) { builder.state.value.message }
        val project = ProjectWorkspaceStore(context).newProject("PhoneProof" + System.currentTimeMillis())
        try {
            builder.build(project.id)
            await(16 * 60_000L) { !builder.state.value.busy }
            val state = builder.state.value
            assertEquals(state.message + "\n" + state.logTail, "succeeded", state.phase)
            val exported = File(evidence, "phone-proof-debug.apk")
            exported.outputStream().use(builder::exportVerifiedApk)
            val hash = MessageDigest.getInstance("SHA-256").digest(exported.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(state.apkSha256, hash)
            val archive = context.packageManager.getPackageArchiveInfo(exported.absolutePath, 0)
            checkNotNull(archive)
            check(archive.packageName.startsWith("ai.drfx.generated.phoneproof"))
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            proofFile.writeText(JSONObject()
                .put("schema", 1).put("source_commit", sourceCommit)
                .put("android_api", Build.VERSION.SDK_INT).put("abi", "arm64-v8a")
                .put("physical_device", true).put("build_and_export", "passed")
                .put("host_version", info.versionName)
                .put("apk_package", archive.packageName).put("apk_sha256", hash)
                .put("verified_at_epoch_ms", System.currentTimeMillis()).toString(2))
        } finally {
            builder.cancel()
            // Only the unique starter created by this test is removed.
            File(context.filesDir, "studio/projects/" + project.id).deleteRecursively()
        }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(300)
        }
        fail("Physical build acceptance timed out.")
    }
}
