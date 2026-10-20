package ai.drfx.maximus.matrixai.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ai.drfx.maximus.matrixai.MainActivity
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import ai.drfx.maximus.matrixai.ui.theme.LanguagePreferenceStore
import ai.drfx.maximus.matrixai.ui.theme.MaximusMatrixTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Genuine app frames. No synthetic provider, deployment or online-agent evidence. */
@RunWith(AndroidJUnit4::class)
class OperationalAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation).also {
        Configurator.getInstance().setWaitForIdleTimeout(500L)
    }

    @Test fun sixGalaxyLayoutsKeepBrainAccessibleAndCaptureActualFrames() = inEnglishActivity {
        assertNotNull(device.wait(Until.findObject(By.desc("MAXIMUS assistant composer")), 10_000))
        capture("galaxy-brain.png")
        val layouts = listOf(
            "Rings" to "galaxy-rings.png",
            "Circle" to "galaxy-circle.png",
            "Areas" to "galaxy-areas.png",
            "Links" to "galaxy-links.png",
            "Timeline" to "galaxy-timeline.png",
            "3D Orbit" to "galaxy-orbit.png"
        )
        for ((label, frame) in layouts) {
            checkNotNull(device.wait(Until.findObject(By.desc("Neural Galaxy layouts")), 10_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text(label)), 10_000)).click()
            assertNotNull(device.wait(Until.findObject(By.desc("Neural Galaxy · " + label)), 10_000))
            assertNotNull(device.wait(Until.findObject(By.desc("MAXIMUS assistant composer")), 10_000))
            assertNotNull(device.wait(Until.findObject(By.text("Module map + saved graph")), 20_000))
            capture(frame)
        }
        checkNotNull(device.wait(Until.findObject(By.desc("Neural Galaxy layouts")), 10_000)).click()
        checkNotNull(device.wait(Until.findObject(By.text("Brain")), 10_000)).click()
        assertNotNull(device.wait(Until.findObject(By.desc("Neural Galaxy · Brain")), 10_000))
        assertNotNull(device.wait(Until.findObject(By.desc("Interactive node graph")), 10_000))
    }

    @Test fun developerOperationsShowsActualUnconfiguredState() = inEnglishActivity {
        onActivity { activity ->
            activity.setContent {
                MaximusMatrixTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("ANDROID ACCEPTANCE · REAL LOCAL STATE")
                        DeveloperConnectionsPanel()
                    }
                }
            }
        }
        assertNotNull(device.wait(Until.findObject(By.text("Developer operations")), 10_000))
        capture("developer-operations.png")
        // Rendering evidence does not grant a token or run a remote operation.
        assertFalse(ai.drfx.maximus.matrixai.studio.connections.DeveloperCredentialVault(context)
            .configured(ai.drfx.maximus.matrixai.studio.connections.DevelopmentService.GITHUB))
    }

    private fun inEnglishActivity(block: () -> Unit) {
        val languages = LanguagePreferenceStore(context)
        val previous = languages.load()
        try {
            languages.save(AppLanguage.ENGLISH)
            device.executeShellCommand(
                "am start -W -f 0x10008000 -n ${context.packageName}/${MainActivity::class.java.name}")
            try { block() } finally { runCatching { onActivity { it.finish() } } }
        } finally { languages.save(previous) }
    }

    private fun capture(name: String) {
        require(name in listOf("galaxy-brain.png", "galaxy-rings.png", "galaxy-circle.png",
            "galaxy-areas.png", "galaxy-links.png", "galaxy-timeline.png", "galaxy-orbit.png",
            "developer-operations.png"))
        SystemClock.sleep(400)
        val file = File(context.getExternalFilesDir(null), name)
        check(device.takeScreenshot(file)) { "Unable to capture real Android frame" }
        check(device.executeShellCommand(
            "cp /sdcard/Android/data/ai.drfx.maximus.matrixai/files/$name " +
                "/data/local/tmp/maximus-keyboard-$name").isBlank())
    }

    private fun onActivity(action: (MainActivity) -> Unit) {
        val completed = CountDownLatch(1)
        val error = AtomicReference<Throwable?>(null)
        Handler(Looper.getMainLooper()).post {
            try {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
                action(checkNotNull(activity))
            } catch (failure: Throwable) { error.set(failure) }
            finally { completed.countDown() }
        }
        check(completed.await(10, TimeUnit.SECONDS))
        error.get()?.let { throw it }
    }
}
