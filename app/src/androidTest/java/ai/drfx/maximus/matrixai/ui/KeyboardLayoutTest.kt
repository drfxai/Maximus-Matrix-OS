package ai.drfx.maximus.matrixai.ui

import android.graphics.Rect
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
            val closed = objectWithDescription("MAXIMUS assistant composer").visibleBounds
            awaitCondition("Brain header must be visible") { device.hasObject(By.text("MAXIMUS")) }
            val graph = objectWithDescription("Interactive node graph").visibleBounds
            assertTrue("The graph should dominate the collapsed home assistant", graph.height() > closed.height())
            objectWithDescription("Expand assistant details").click()
            awaitCondition("Assistant details did not expand") {
                device.hasObject(By.desc("Collapse assistant details"))
            }
            objectWithDescription("Collapse assistant details").click()
            awaitCondition("Assistant did not restore the compact layout") {
                objectWithDescription("MAXIMUS assistant composer").visibleBounds.height() <= closed.height() + 8
            }
            captureWorkspace("home-graph.png")
            repeat(2) { cycle ->
                phase("mission input click cycle=$cycle")
                objectWithDescription("Assistant question").click()
                awaitCondition("Mission keyboard did not open") { isImeVisible() }
                phase("mission IME visible cycle=$cycle")
                val expected = if (cycle == 0) "Keyboard test" else "Keyboard test again"
                device.executeShellCommand(if (cycle == 0) "input text Keyboard%stest" else "input text %sagain")
                awaitCondition("Mission test text was not entered") { device.hasObject(By.text(expected)) }


                assertComposerAboveIme("MAXIMUS assistant composer")
                assertTrue("Home header moved off screen", device.hasObject(By.text("MAXIMUS")))
                assertFalse("Navigation should hide while typing", device.hasObject(By.desc("Neural Brain")))
                device.pressBack()
                awaitCondition("Keyboard did not close") { !isImeVisible() }
                awaitCondition("Navigation did not return") { device.hasObject(By.desc("Neural Brain")) }
                awaitCondition("Mission composer did not return to the bottom") {
                    val restored = objectWithDescription("MAXIMUS assistant composer").visibleBounds
                    kotlin.math.abs(restored.bottom - closed.bottom) <= 8
                }
                assertTrue("Mission text was lost", device.hasObject(By.text(expected)))
            }


        }
    }

    @Test fun chatComposerStaysAboveKeyboard() {
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        withLaunchedActivity {
            phase("direct chat navigation")
            objectWithDescription("Chat").click()
            objectWithDescription("Workspace controls").click()
            objectWithDescription("Hide controls").click()
            objectWithDescription("Workspace controls")
            captureWorkspace("chat-focus.png")
            phase("chat input click")
            objectWithDescription("Chat input").click()
            awaitCondition("Chat keyboard did not open") { isImeVisible() }
            device.executeShellCommand("input text Chat%skeyboard%stest")
            awaitCondition("Chat test text was not entered") { device.hasObject(By.text("Chat keyboard test")) }


            assertComposerAboveIme("Chat composer")
            device.pressBack()
            awaitCondition("Chat keyboard did not close") { !isImeVisible() }
            awaitCondition("Navigation did not return") { device.hasObject(By.desc("Neural Brain")) }
            assertTrue("Chat text was lost", device.hasObject(By.text("Chat keyboard test")))
        }
    }

    /**
     * Scroll the outer Work LazyColumn, rather than swiping over the chart's
     * nested horizontal candlestick scroller. Keep real navigation assertions.
     */
    private fun scrollWorkUntilVisible(
        label: String,
        direction: androidx.test.uiautomator.Direction
    ) {
        repeat(9) {
            if (device.hasObject(By.text(label))) return
            val workScroller = device.findObjects(By.scrollable(true))
                .filter { it.visibleBounds.height() > it.visibleBounds.width() &&
                    it.visibleBounds.height() > device.displayHeight / 3 }
                .maxByOrNull { it.visibleBounds.height() }
            val scrolled = if (workScroller != null) {
                runCatching { workScroller.scroll(direction, .72f); true }
                    .getOrDefault(false)
            } else false
            if (!scrolled) {
                // Outside the 200-candle nested horizontal viewport, inside Work.
                val x = (device.displayWidth * .94f).toInt()
                val top = (device.displayHeight * .22f).toInt()
                val bottom = (device.displayHeight * .73f).toInt()
                if (direction == androidx.test.uiautomator.Direction.DOWN)
                    device.swipe(x, bottom, x, top, 35)
                else device.swipe(x, top, x, bottom, 35)
            }
            device.waitForIdle(350)
        }
        checkNotNull(device.wait(Until.findObject(By.text(label)), 5_000)) {
            "Work vertical list never exposed: $label"
        }
    }

    @Test fun primaryWorkspacesKeepFixedNavigationAndFocusedStudioSections() {
        withLaunchedActivity {
            objectWithDescription("Project Studio").click()
            checkNotNull(device.wait(Until.findObject(By.text("Pine Studio")), 10_000))
            captureWorkspace("studio-overview.png")
            scrollWorkUntilVisible("Assistant", androidx.test.uiautomator.Direction.DOWN)
            checkNotNull(device.wait(Until.findObject(By.text("Changes")), 10_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text("No staged file changes in this local workspace.")), 10_000))
            captureWorkspace("studio-changes.png")
            checkNotNull(device.wait(Until.findObject(By.text("Assistant")), 10_000)).click()
            scrollWorkUntilVisible("Open Pine Studio", androidx.test.uiautomator.Direction.DOWN)
            captureWorkspace("studio-pine-premium.png")
            checkNotNull(device.wait(Until.findObject(By.text("Open Pine Studio")), 10_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text("Pine Studio")), 10_000))
            captureWorkspace("pine-studio.png")
            // The Android viewport cannot fit a code editor and candlestick chart at once.
            // Capture the second real, scrolled state instead of inventing a composite screen.
            device.swipe((device.displayWidth * .7).toInt(), (device.displayHeight * .57).toInt(),
                (device.displayWidth * .7).toInt(), (device.displayHeight * .22).toInt(), 30)
            checkNotNull(device.wait(Until.findObject(By.text("Market chart · OHLCV")), 10_000))
            val pinned = checkNotNull(device.wait(Until.findObject(By.desc("Pine AI composer")), 10_000))
            val visibleCandles = device.findObject(By.desc("OHLCV price candles"))
            if (visibleCandles != null && visibleCandles.visibleBounds.width() > 0 &&
                visibleCandles.visibleBounds.height() > 0) {
                assertTrue("Pine chart is obscured by the pinned composer",
                    visibleCandles.visibleBounds.bottom <= pinned.visibleBounds.top + 4)
            }
            captureWorkspace("pine-preview.png")
            checkNotNull(device.wait(Until.findObject(By.text("Expand chart")), 10_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text("Market chart · Real OHLCV")), 10_000))
            captureWorkspace("pine-chart-expanded.png")
            device.pressBack()
            device.pressBack()
            scrollWorkUntilVisible("Files", androidx.test.uiautomator.Direction.DOWN)
            checkNotNull(device.wait(Until.findObject(By.text("Files")), 10_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text("Project files")), 10_000))
            assertFalse("Files must not render the build dashboard", device.hasObject(By.text(instrumentation.targetContext.getString(ai.drfx.maximus.matrixai.R.string.phone_harness_title))))
            objectWithDescription("Agents").click()
            checkNotNull(device.wait(Until.findObject(By.text("My agents")), 10_000))
            captureWorkspace("agents-my-agents.png")
            checkNotNull(device.wait(Until.findObject(By.text("General Assistant")), 10_000))
            checkNotNull(device.wait(Until.findObject(By.text("Teams")), 10_000)).click()
            objectWithDescription("Neural Brain").click()
            objectWithDescription("MAXIMUS assistant composer")
            objectWithDescription("Chat")
            objectWithDescription("Agents")
            objectWithDescription("Project Studio")
            objectWithDescription("More")
        }
    }

    /**
     * Capture populated UI from genuine app-private project files.
     * These are local test fixtures, not fabricated GitHub branches or connections.
     */
    @Test fun localProjectWorkspaceShowsRealFilesAndEditorState() {
        val context = instrumentation.targetContext
        val store = ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore(context)
        val project = store.newProject("Visual Acceptance Workspace")
        try {
            withLaunchedActivity {
                objectWithDescription("Project Studio").click()
                awaitCondition("Local project not visible in Work") {
                    device.hasObject(By.text("Visual Acceptance Workspace"))
                }
                captureWorkspace("studio-populated.png")
                repeat(5) {
                    if (!device.hasObject(By.text("Files"))) {
                        device.swipe((device.displayWidth * .68).toInt(),
                            (device.displayHeight * .73).toInt(),
                            (device.displayWidth * .68).toInt(),
                            (device.displayHeight * .27).toInt(), 25)
                    }
                }
                checkNotNull(device.wait(Until.findObject(By.text("Files")), 10_000)).click()
                checkNotNull(device.wait(Until.findObject(By.text("Project files")), 10_000))
                captureWorkspace("studio-files-populated.png")
                assertTrue("Local project starter files missing", store.listFiles(project.id).isNotEmpty())
            }
        } finally {
            java.io.File(context.filesDir, "studio/projects/${project.id}").deleteRecursively()
        }
    }

    /** Verify actual persisted agent instructions in the editable Agents panel. */
    @Test fun savedAgentProfileRendersEditablePanel() {
        val store = PersonalAgentStore(instrumentation.targetContext)
        val agent = store.create("Visual Test Code Engineer")
        try {
            store.save(agent.copy(
                instructions = "Review Kotlin and Pine Script changes before applying edits.",
                tasks = listOf("Audit code changes", "Verify Pine Script syntax")
            ))
            withLaunchedActivity {
                objectWithDescription("Agents").click()
                checkNotNull(device.wait(Until.findObject(By.text("Visual Test Code Engineer")), 10_000))
                captureWorkspace("agents-populated.png")
                checkNotNull(device.wait(Until.findObject(By.desc("Edit agent")), 10_000)).click()
                checkNotNull(device.wait(Until.findObject(By.text("Edit agent")), 10_000))
                checkNotNull(device.wait(Until.findObject(
                    By.text("Review Kotlin and Pine Script changes before applying edits.")), 10_000))
                captureWorkspace("agents-edit-populated.png")
            }
        } finally { store.delete(agent.id) }
    }

    /**
     * Render actual production code-card/HTML-preview composables using explicitly
     * marked local test content. This does not fabricate a connected AI session.
     */
    @Test fun actualCodeCardsAndOfflineHtmlPreviewRenderSafely() {
        val fixture = listOf(
            "Local UI test fixture, not a provider-generated message.",
            "```kotlin",
            "fun greet() = \"MAXIMUS CODE FIXTURE\"",
            "```",
            "```html",
            "<main><h2>MAXIMUS Web Preview Fixture</h2><p>Locally rendered</p></main>",
            "```",
            "```css",
            "h2 { color: #c89438; }",
            "```"
        ).joinToString("\n")
        withLaunchedActivity {
            withResumedActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        Column(
                            Modifier.fillMaxSize().background(Color.Black)
                                .verticalScroll(rememberScrollState()).padding(10.dp)
                        ) {
                            Text("LOCAL UI FIXTURE · NO CONNECTED PROVIDER",
                                color = Color.White)
                            WorkspaceMessageContent(fixture, Color.White)
                        }
                    }
                }
            }
            checkNotNull(device.wait(Until.findObject(By.text("Kotlin")), 10_000))
            captureWorkspace("chat-code-fixture.png")
            repeat(5) {
                if (!device.hasObject(By.text("Preview locally"))) {
                    device.swipe((device.displayWidth * .6).toInt(),
                        (device.displayHeight * .75).toInt(),
                        (device.displayWidth * .6).toInt(),
                        (device.displayHeight * .25).toInt(), 20)
                }
            }
            checkNotNull(device.wait(Until.findObject(By.text("Preview locally")), 10_000))
                .click()
            checkNotNull(device.wait(Until.findObject(By.text("Close preview")), 10_000))
            captureWorkspace("chat-html-preview-fixture.png")
        }
    }

    @Test fun smartComboWorkspaceIsReachableAndCapturesObservedEmptyState() {
        withLaunchedActivity {
            objectWithDescription("More").click()
            awaitCondition("Tools Hub did not finish navigating") {
                device.hasObject(By.text(instrumentation.targetContext.getString(
                    ai.drfx.maximus.matrixai.R.string.ui_tools)))
            }
            captureWorkspace("tools-hub.png")
            objectWithDescription(instrumentation.targetContext.getString(
                ai.drfx.maximus.matrixai.R.string.nav_control)).click()
            checkNotNull(device.wait(Until.findObject(By.text("Smart routing")), 10_000))
            checkNotNull(device.wait(Until.findObject(By.text("Use selected model")), 10_000))
            captureWorkspace("smart-combo.png")
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
        try { block() } catch (error: Throwable) {
            dumpFailureState()
            throw error
        } finally { withResumedActivity { it.finish() } }
    }

    private fun dumpFailureState() {
        runCatching<Unit> {
            withResumedActivity { activity ->
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                phase("Failure insets: imeVisible=" + insets?.isVisible(WindowInsetsCompat.Type.ime()) +
                    " imeBottom=" + insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom)
            }
            val hierarchy = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            hierarchy.toString("UTF-8").take(180_000).chunked(2800).forEachIndexed { index, chunk ->
                phase("Failure hierarchy[$index]: $chunk")
            }
            val file = java.io.File(
                instrumentation.targetContext.getExternalFilesDir(null),
                "failure-state.png"
            )
            check(device.takeScreenshot(file)) { "Unable to save failure screenshot" }
            phase("Failure screenshot saved: ${file.absolutePath}")
        }.onFailure { phase("Failure diagnostics unavailable: " + it.javaClass.simpleName) }
    }

    private fun captureWorkspace(name: String) {
        // Test-owned, fixed paths only: Gradle may uninstall the app before host collection.
        require(name in listOf("home-graph.png", "chat-focus.png", "chat-code-fixture.png", "chat-html-preview-fixture.png", "studio-overview.png", "studio-changes.png", "studio-pine-premium.png", "studio-populated.png", "studio-files-populated.png", "pine-studio.png", "pine-preview.png", "pine-chart-expanded.png", "agents-my-agents.png", "agents-populated.png", "agents-edit-populated.png", "smart-combo.png", "tools-hub.png"))
        runCatching {
            // Let the destination crossfade finish before sampling the rendered frame.
            SystemClock.sleep(350L)
            val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name)
            check(device.takeScreenshot(file)) { "Unable to save workspace screenshot" }
            val copyResult = device.executeShellCommand(
                "cp /sdcard/Android/data/ai.drfx.maximus.matrixai/files/$name " +
                    "/data/local/tmp/maximus-keyboard-$name"
            )
            check(copyResult.isBlank()) { "Unable to retain workspace screenshot after uninstall" }
            phase("Workspace screenshot $name saved=true; retained outside app storage")
        }.onFailure { phase("Workspace screenshot unavailable: " + it.javaClass.simpleName) }.getOrThrow()
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

}
