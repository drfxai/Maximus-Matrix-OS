package ai.drfx.maximus.matrixai.studio.phonehost

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.drfx.maximus.matrixai.plugins.NativePlugins
import ai.drfx.maximus.matrixai.plugins.PluginCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Deliberate physical test only, never substitutes an x86 emulator for ARM64 execution. */
@RunWith(AndroidJUnit4::class)
class PhoneLinuxAcceptanceTest {
    @Test fun realCommandsPersistWorkspaceAndCanBeCancelled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(InstrumentationRegistry.getArguments().getString("approvedOwnerLinux") == "true")
        check(Build.VERSION.SDK_INT >= 28 && "arm64-v8a" in Build.SUPPORTED_ABIS)
        check(!Build.FINGERPRINT.contains("generic", true) && !Build.MODEL.contains("sdk", true))
        val context = instrumentation.targetContext
        val registry = NativePlugins.registry(context)
        val plugin = PluginCatalog.find("phone-linux")
        val previouslyInstalled = registry.installed(plugin.id)
        val session = PhoneLinuxSession(context)
        val filename = "linux-proof-" + UUID.randomUUID().toString() + ".txt"
        val value = "MAXIMUS_LINUX_" + UUID.randomUUID().toString()
        val proof = File(context.filesDir, "phone-linux-workspace/$filename")
        var id = ""
        try {
            registry.install(plugin.id, plugin.fingerprint, approved = true)
            id = UUID.randomUUID().toString()
            session.start(id, "printf '%s' '$value' > '$filename'; uname -m", approved = true)
            await { !session.state.value.busy }
            assertEquals("finished", session.state.value.phase)
            assertEquals(0, session.state.value.exitCode)
            assertTrue(session.state.value.output.contains("aarch64"))
            assertEquals(value, proof.readText())
            id = UUID.randomUUID().toString()
            session.start(id, "cat '$filename'", approved = true)
            await { !session.state.value.busy }
            assertEquals(value, session.state.value.output)
            id = UUID.randomUUID().toString()
            session.start(id, "sleep 120", approved = true)
            session.cancel(id)
            await { !session.state.value.busy }
            assertEquals("cancelled", session.state.value.phase)
            assertNull(PhoneRuntimeCoordinator.current())
        } finally {
            session.cancel(id)
            proof.delete()
            if (!previouslyInstalled) registry.remove(plugin.id)
        }
    }
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 60_000
        while (SystemClock.elapsedRealtime() < until) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail("Physical Linux acceptance timed out")
    }
}
