package ai.drfx.maximus.matrixai.ui

import android.graphics.Rect
import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import ai.drfx.maximus.matrixai.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real system keyboard, without Compose's animation-idling clock. */
@RunWith(AndroidJUnit4::class)
class KeyboardLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation).also {
        // The graph animates continuously; explicit conditions own settling.
        Configurator.getInstance().setWaitForIdleTimeout(500L)
    }

    @Test fun missionComposerStaysAboveKeyboardAndRestoresAfterClose() {
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        withLaunchedActivity {
            phase("mission composer lookup")
            val closed = objectWithDescription("Mission composer").visibleBounds
            assertTrue("Home header must be visible", device.hasObject(By.text("MAXIMUS AI")))
            repeat(2) { cycle ->
                phase("mission input click cycle=$cycle")
                objectWithDescription("Mission input").click()
                awaitCondition("Mission keyboard did not open") { isImeVisible() }
                phase("mission IME visible cycle=$cycle")
                val expected = if (cycle == 0) "Keyboard test" else "Keyboard test again"
                device.executeShellCommand(if (cycle == 0) "input text Keyboard%stest" else "input text %sagain")
                awaitCondition("Mission test text was not entered") { device.hasObject(By.text(expected)) }
                screenshot("mission-keyboard-$cycle.png")
                assertComposerAboveIme("Mission composer")
                assertTrue("Home header moved off screen", device.hasObject(By.text("MAXIMUS AI")))
                assertFalse("Navigation should hide while typing", device.hasObject(By.text("Chat")))
                device.pressBack()
                awaitCondition("Keyboard did not close") { !isImeVisible() }
                awaitCondition("Navigation did not return") { device.hasObject(By.text("Chat")) }
                awaitCondition("Mission composer did not return to the bottom") {
                    val restored = objectWithDescription("Mission composer").visibleBounds
                    kotlin.math.abs(restored.bottom - closed.bottom) <= 8
                }
                assertTrue("Mission text was lost", device.hasObject(By.text(expected)))
            }
            screenshot("mission-keyboard-closed.png")
        }
    }

    @Test fun chatComposerStaysAboveKeyboard() {
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        withLaunchedActivity {
            phase("chat tab lookup")
            checkNotNull(device.wait(Until.findObject(By.text("Chat")), 10_000)).click()
            phase("chat input click")
            objectWithDescription("Chat input").click()
            awaitCondition("Chat keyboard did not open") { isImeVisible() }
            device.executeShellCommand("input text Chat%skeyboard%stest")
            awaitCondition("Chat test text was not entered") { device.hasObject(By.text("Chat keyboard test")) }
            screenshot("chat-keyboard-open.png")
            assertComposerAboveIme("Chat composer")
            device.pressBack()
            awaitCondition("Chat keyboard did not close") { !isImeVisible() }
            awaitCondition("Navigation did not return") { device.hasObject(By.text("Matrix")) }
            assertTrue("Chat text was lost", device.hasObject(By.text("Chat keyboard test")))
        }
    }

    /** ActivityScenario's launch/close wait for a globally idle main queue. The
     * continuously animated graph intentionally has no such idle condition.
     * Shell launch and explicit UI predicates retain the real Activity/IME checks.
     */
    private fun withLaunchedActivity(block: () -> Unit) {
        phase("launch requested")
        val packageName = instrumentation.targetContext.packageName
        device.executeShellCommand("am start -W -n $packageName/${MainActivity::class.java.name}")
        android.util.Log.i("KeyboardLayoutTest", "Shell launch completed; waiting for visible composer")
        try { block() } finally { withResumedActivity { it.finish() } }
    }

    private fun withResumedActivity(block: (MainActivity) -> Unit) {
        val completed = java.util.concurrent.CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
            checkNotNull(activity) { "MainActivity is not resumed." }
            block(activity)
            } catch (error: Throwable) { failure.set(error) }
            finally { completed.countDown() }
        }
        check(completed.await(10, java.util.concurrent.TimeUnit.SECONDS)) { "Main thread did not respond within 10 seconds." }
        failure.get()?.let { throw it }
    }

    private fun objectWithDescription(description: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.desc(description)), 10_000)) {
            "Missing UI element: $description"
        }

    private fun isImeVisible(): Boolean {
        var visible = false
        withResumedActivity { activity ->
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    private fun assertComposerAboveIme(description: String) {
        // Wait for layout and keyboard animation to settle, not just the visible flag.
        awaitCondition("Composer overlaps the keyboard or leaves a large empty gap") {
            var keyboardTop = 0
            var allowedGap = 0
            withResumedActivity { activity ->
                val decor = activity.window.decorView
                val insets = ViewCompat.getRootWindowInsets(decor)
                val location = IntArray(2)
                decor.getLocationOnScreen(location)
                keyboardTop = location[1] + decor.height - (insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)
                allowedGap = (20 * activity.resources.displayMetrics.density).toInt()
            }
            val bounds: Rect = objectWithDescription(description).visibleBounds
            val gap = keyboardTop - bounds.bottom
            gap in 0..allowedGap && bounds.height() > 0
        }
    }

    private fun phase(message: String) { android.util.Log.i("KeyboardLayoutTest", message) }

    private fun awaitCondition(message: String, predicate: () -> Boolean) {
        phase("Await: $message")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) { phase("Satisfied: $message"); return }
            SystemClock.sleep(100)
        }
        fail(message)
    }

    private fun screenshot(name: String) {
        // Shell-owned captures survive the test runner uninstalling the app.
        phase("Screenshot: $name")
        val path = "/data/local/tmp/keyboard-check/$name"
        device.executeShellCommand("mkdir -p /data/local/tmp/keyboard-check")
        device.executeShellCommand("screencap -p $path")
        assertTrue("Could not capture $name", device.executeShellCommand("ls $path").trim() == path)
    }
}
