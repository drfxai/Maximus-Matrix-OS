package ai.drfx.maximus.matrixai.ui

import android.graphics.Rect
import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import ai.drfx.maximus.matrixai.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses the real system keyboard, without Compose's animation-idling clock. */
@RunWith(AndroidJUnit4::class)
class KeyboardLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun missionComposerStaysAboveKeyboardAndRestoresAfterClose() {
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val input = objectWithDescription("Mission input")
            val closed = objectWithDescription("Mission composer").visibleBounds
            assertTrue("Home header must be visible", device.hasObject(By.text("MAXIMUS AI")))
            repeat(2) { cycle ->
                input.click()
                awaitCondition("Mission keyboard did not open") { isImeVisible(scenario) }
                objectWithDescription("Mission input").text = "Keyboard test $cycle"
                screenshot("mission-keyboard-$cycle.png")
                assertComposerAboveIme(scenario, "Mission composer")
                assertTrue("Home header moved off screen", device.hasObject(By.text("MAXIMUS AI")))
                assertFalse("Navigation should hide while typing", device.hasObject(By.text("Chat")))
                device.pressBack()
                awaitCondition("Keyboard did not close") { !isImeVisible(scenario) }
                awaitCondition("Navigation did not return") { device.hasObject(By.text("Chat")) }
                awaitCondition("Mission composer did not return to the bottom") {
                    val restored = objectWithDescription("Mission composer").visibleBounds
                    kotlin.math.abs(restored.bottom - closed.bottom) <= 8
                }
                assertTrue("Mission text was lost", device.hasObject(By.text("Keyboard test $cycle")))
            }
            screenshot("mission-keyboard-closed.png")
        }
    }

    @Test fun chatComposerStaysAboveKeyboard() {
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            checkNotNull(device.wait(Until.findObject(By.text("Chat")), 10_000)).click()
            objectWithDescription("Chat input").click()
            awaitCondition("Chat keyboard did not open") { isImeVisible(scenario) }
            objectWithDescription("Chat input").text = "Chat keyboard test"
            screenshot("chat-keyboard-open.png")
            assertComposerAboveIme(scenario, "Chat composer")
            device.pressBack()
            awaitCondition("Chat keyboard did not close") { !isImeVisible(scenario) }
            awaitCondition("Navigation did not return") { device.hasObject(By.text("Matrix")) }
            assertTrue("Chat text was lost", device.hasObject(By.text("Chat keyboard test")))
        }
    }

    private fun objectWithDescription(description: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.desc(description)), 10_000)) {
            "Missing UI element: $description"
        }

    private fun isImeVisible(scenario: ActivityScenario<MainActivity>): Boolean {
        var visible = false
        scenario.onActivity { activity ->
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    private fun assertComposerAboveIme(scenario: ActivityScenario<MainActivity>, description: String) {
        // Wait for layout and keyboard animation to settle, not just the visible flag.
        awaitCondition("Composer overlaps the keyboard or leaves a large empty gap") {
            var keyboardTop = 0
            var allowedGap = 0
            scenario.onActivity { activity ->
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

    private fun awaitCondition(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }

    private fun screenshot(name: String) {
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "keyboard-check")
        folder.mkdirs()
        assertTrue("Could not capture $name", device.takeScreenshot(File(folder, name)))
    }
}
