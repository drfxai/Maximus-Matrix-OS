package ai.drfx.maximus.matrixai.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import ai.drfx.maximus.matrixai.MainActivity
import ai.drfx.maximus.matrixai.operations.EvidenceState
import ai.drfx.maximus.matrixai.operations.OperationsRepository
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import ai.drfx.maximus.matrixai.ui.theme.LanguagePreferenceStore
import ai.drfx.maximus.matrixai.ui.theme.MaximusMatrixTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
        assertNotNull(device.wait(Until.findObject(By.desc("Neural Galaxy · Brain")), 10_000))
        assertNotNull(device.wait(Until.findObject(By.text("Module map + saved graph")), 20_000))
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
                    Scaffold(containerColor = Color.Black,
                        contentWindowInsets = WindowInsets.safeDrawing) { insets ->
                        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)
                            .verticalScroll(rememberScrollState())) {
                            Text("ANDROID ACCEPTANCE · REAL LOCAL STATE", color = Color.White)
                            DeveloperConnectionsPanel()
                        }
                    }
                }
            }
        }
        assertNotNull(device.wait(Until.findObject(By.text("Developer operations")), 10_000))
        assertBelowStatusBar("Developer operations")
        capture("developer-operations.png")
        // Rendering evidence does not grant a token or run a remote operation.
        assertFalse(ai.drfx.maximus.matrixai.studio.connections.DeveloperCredentialVault(context)
            .configured(ai.drfx.maximus.matrixai.studio.connections.DevelopmentService.GITHUB))
    }

    @Test fun graphSearchSelectionAndCameraControlsKeepTheirStateAcrossLayouts() = inEnglishActivity {
        selectLayout("Rings")
        // Disable actual graph motion through the production control before pixel comparison.
        openLayouts()
        scrollToText("Motion: on", "Motion: off")
        device.findObject(By.text("Motion: on"))?.click()
        assertNotNull(device.wait(Until.findObject(By.text("Motion: off")), 5_000))
        device.pressBack()
        SystemClock.sleep(350)
        val graph = graph()
        val before = graphFingerprint()
        graph.pinchOpen(.35f)
        SystemClock.sleep(350)
        val zoomed = graphFingerprint()
        assertNotEquals("Pinch gesture did not change the actual rendered graph", before, zoomed)
        openLayouts()
        scrollToText("Fit to viewport").click()
        SystemClock.sleep(350)
        assertNotEquals("Fit control did not change the zoomed graph", zoomed, graphFingerprint())

        checkNotNull(device.wait(Until.findObject(By.desc("Search and inspect nodes")), 10_000)).click()
        val search = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000))
        search.text = "NoSuchMaximusNode-acceptance"
        assertNotNull(device.wait(Until.findObject(By.text("No matching modules")), 10_000))
        search.text = "Embedding Engine"
        // The editable query has the same text as the result. Select the result label,
        // never the EditText whose click would merely refocus the keyboard.
        checkNotNull(device.wait(Until.findObject(
            By.text("Embedding Engine").clazz("android.widget.TextView")), 10_000)).click()
        // The production selected-node strip yields space to the IME while editing.
        // Close the keyboard before asserting the strip's accessible controls.
        dismissImeIfVisible()
        assertNotNull(device.wait(Until.findObject(By.desc("Clear selection")), 10_000))
        selectLayout("Circle")
        assertNotNull(device.wait(Until.findObject(By.text("Embedding Engine")), 10_000))
        assertNotNull(device.wait(Until.findObject(By.desc("Clear selection")), 10_000))
        capture("galaxy-selected.png")
        checkNotNull(device.wait(Until.findObject(By.desc("Clear selection")), 10_000)).click()
        assertTrue(device.wait(Until.gone(By.desc("Clear selection")), 10_000))
        selectLayout("Brain")
    }

    @Test fun smartComboEvidenceCanBeInspectedWithoutInventingProviderActivity() = inEnglishActivity {
        checkNotNull(device.wait(Until.findObject(By.desc("More")), 10_000)).click()
        checkNotNull(device.wait(Until.findObject(By.desc(context.getString(ai.drfx.maximus.matrixai.R.string.nav_control))), 10_000)).click()
        assertNotNull(device.wait(Until.findObject(By.text("Smart routing")), 10_000))
        // Inspect the compact observed state before expanding the taller details item.
        // LazyColumn can re-anchor after its height changes; a fixed upward search
        // after expansion can leave this evidence below the viewport indefinitely.
        scrollToText("Actual dispatch: None active")
        assertNotNull(device.wait(Until.findObject(By.text("Actual dispatch: None active")), 10_000))
        assertNotNull(device.wait(Until.findObject(By.textContains("TPM snapshot: Unknown")), 10_000))
        capture("smart-combo-evidence.png")
        scrollToText("Inspect evidence and candidates").click()
        scrollToText("Provider credit: Unknown · no account balance API evidence")
        scrollToText("Automatic paid fallback: Disabled")
        capture("smart-combo-usage.png")
    }

    @Test fun operationalDashboardsAreReachableThroughHubAndDisplayLocalEvidence() = inEnglishActivity {
        checkNotNull(device.wait(Until.findObject(By.desc("More")), 10_000)).click()
        scrollToText("Operations · Agents, Pulse, Memory & Runtime").click()
        assertNotNull(device.wait(Until.findObject(By.text("Agent Command Center")), 10_000))
        assertNotNull(device.wait(Until.findObject(By.text("Refresh observations")), 15_000))
        val pages = listOf(
            Triple("Agents", "Agent Command Center", "operations-agents.png"),
            Triple("Pulse", "Pulse Control Center", "operations-pulse.png"),
            Triple("Memory", "Memory Explorer", "operations-memory.png"),
            Triple("Runtime", "Runtime & Infrastructure", "operations-runtime.png"),
            Triple("Security", "Security & Audit", "operations-security.png")
        )
        for ((tab, heading, frame) in pages) {
            operationTab(tab).click()
            assertNotNull(device.wait(Until.findObject(By.text(heading)), 10_000))
            assertNotNull(device.wait(Until.findObject(
                By.text("Authoritative state, observed locally · no background remote probes")), 10_000))
            when (tab) {
                "Agents" -> assertNotNull(device.wait(Until.findObject(By.text(
                    "Definitions are not online agents. Project team state comes from its durable execution checkpoint.")), 10_000))
                "Pulse" -> assertNotNull(device.wait(Until.findObject(By.text(
                    "Android WorkManager · best effort under Doze, network and battery restrictions.")), 10_000))
                "Memory" -> assertNotNull(device.wait(Until.findObject(By.text(
                    "Project-isolated banks remain in Hindsight. Recalled text is untrusted evidence, never tool authorization.")), 10_000))
                "Security" -> assertNotNull(device.wait(Until.findObject(By.text(
                    "Local command history · bounded to 30 audit entries; timestamps are epoch milliseconds.")), 10_000))
            }
            capture(frame)
        }
        checkNotNull(device.wait(Until.findObject(By.text("Back to Hub")), 10_000)).click()
        assertNotNull(device.wait(Until.findObject(
            By.text("Operations · Agents, Pulse, Memory & Runtime")), 10_000))
    }

    @Test fun hubReselectionExitsOperationsAndControlSectionsAreImmediatelyReachable() = inEnglishActivity {
        checkNotNull(device.wait(Until.findObject(By.desc("More")), 10_000)).click()
        scrollToText("Operations · Agents, Pulse, Memory & Runtime").click()
        assertNotNull(device.wait(Until.findObject(By.text("Operations")), 10_000))
        checkNotNull(device.wait(Until.findObject(By.desc("More")), 10_000)).click()
        scrollToText("Smart Combo & API Control").click()
        assertNotNull(device.wait(Until.findObject(By.text("Control Center")), 10_000))
        operationTab("API Control").click()
        // Assert actual API content, rather than the section label that was already visible.
        assertNotNull(scrollToText(context.getString(ai.drfx.maximus.matrixai.R.string.api_presets)))
        // Control's BackHandler returns an advanced section to Smart Combo. The tab row
        // is now scrolled right, so looking for Smart Combo via a right-only helper is invalid.
        device.pressBack()
        assertNotNull(device.wait(Until.findObject(By.text("Smart routing")), 10_000))
        // The top return action is distinct from the persistent bottom Hub destination.
        checkNotNull(device.findObjects(By.text("Hub")).minByOrNull { it.visibleBounds.top }).click()
        assertNotNull(device.wait(Until.findObject(By.text("Operations · Agents, Pulse, Memory & Runtime")), 10_000))
    }

    @Test fun operationsSnapshotDoesNotMutateAuthoritativeLocalStores() = runBlocking {
        // Use the real application context and existing records. Do not launch a screen,
        // create a workspace, configure an account or restore/erase any user data.
        withContext(Dispatchers.IO) {
            val names = listOf("maximus_shared_memory_v1", "maximus_agent_teams_v1",
                "personal_agents_v1", "maximus_operations_audit_v1")
            fun preferences() = names.associateWith { name ->
                context.getSharedPreferences(name, 0).all.mapValues { (_, value) ->
                    if (value is Set<*>) value.toSet() else value
                }
            }
            fun directories() = listOf("studio", "studio/projects").associateWith { path ->
                val directory = File(context.filesDir, path)
                Triple(directory.exists(), directory.isDirectory,
                    directory.list()?.toList()?.sorted())
            }
            val beforePreferences = preferences()
            val beforeDirectories = directories()
            val started = System.currentTimeMillis()
            val repository = OperationsRepository(context)
            val observations = listOf(repository.snapshot(), repository.snapshot())
            val completed = System.currentTimeMillis()
            // Boolean assertions deliberately keep all preference values out of failure logs.
            assertTrue("Read-only projection changed stored preferences",
                beforePreferences == preferences())
            assertTrue("Read-only projection created or changed workspace directories",
                beforeDirectories == directories())
            observations.forEach { snapshot ->
                assertTrue("Observation timestamp falls outside this read",
                    snapshot.observedAt in started..completed)
                listOf("Hindsight remote health", "Cloud worker health").forEach { source ->
                    assertEquals("Local projection must not infer remote connectivity",
                        EvidenceState.UNKNOWN, snapshot.evidence.single { it.source == source }.state)
                }
            }
        }
    }

    private fun inEnglishActivity(block: () -> Unit) {
        val languages = LanguagePreferenceStore(context)
        val previous = languages.load()
        val visualization = context.getSharedPreferences("neural_galaxy_visualization", 0)
        val originalVisualization = visualization.all.toMap()
        try {
            languages.save(AppLanguage.ENGLISH)
            device.executeShellCommand(
                "am start -W -f 0x10008000 -n ${context.packageName}/${MainActivity::class.java.name}")
            try { block() } catch (failure: Throwable) {
                // Preserve the actual failed application frame without suppressing the failure.
                runCatching {
                    check(device.takeScreenshot(File(context.getExternalFilesDir(null), "failure-state.png")))
                    // Gradle may uninstall the target before shell collection; preserve diagnostics.
                    device.executeShellCommand("cp /sdcard/Android/data/${context.packageName}/files/failure-state.png /data/local/tmp/maximus-keyboard-failure-state.png")
                }
                throw failure
            } finally { runCatching { onActivity { it.finish() } } }
        } finally {
            try {
                val restore = visualization.edit().clear()
                originalVisualization.forEach { (key, value) ->
                    when (value) {
                        is String -> restore.putString(key, value)
                        is Boolean -> restore.putBoolean(key, value)
                        is Int -> restore.putInt(key, value)
                        is Long -> restore.putLong(key, value)
                        is Float -> restore.putFloat(key, value)
                        is Set<*> -> restore.putStringSet(key, value.map {
                            checkNotNull(it as? String)
                        }.toSet())
                        else -> error("Unexpected visualization preference type")
                    }
                }
                check(restore.commit())
            } finally { languages.save(previous) }
        }
    }

    private fun capture(name: String) {
        require(name in listOf("galaxy-brain.png", "galaxy-rings.png", "galaxy-circle.png",
            "galaxy-areas.png", "galaxy-links.png", "galaxy-timeline.png", "galaxy-orbit.png",
            "galaxy-selected.png", "smart-combo-evidence.png", "smart-combo-usage.png",
            "operations-agents.png", "operations-pulse.png", "operations-memory.png",
            "operations-runtime.png", "operations-security.png",
            "developer-operations.png"))
        SystemClock.sleep(400)
        val file = File(context.getExternalFilesDir(null), name)
        check(device.takeScreenshot(file)) { "Unable to capture real Android frame" }
        check(device.executeShellCommand(
            "cp /sdcard/Android/data/ai.drfx.maximus.matrixai/files/$name " +
                "/data/local/tmp/maximus-keyboard-$name").isBlank())
    }

    private fun openLayouts() = checkNotNull(device.wait(
        Until.findObject(By.desc("Neural Galaxy layouts")), 10_000)).click()

    private fun selectLayout(label: String) {
        openLayouts()
        scrollToText(label).click()
        assertNotNull(device.wait(Until.findObject(By.desc("Neural Galaxy · $label")), 10_000))
        assertNotNull(device.wait(Until.findObject(By.text("Module map + saved graph")), 20_000))
    }

    private fun graph(): UiObject2 = checkNotNull(device.wait(
        Until.findObject(By.desc("Interactive node graph")), 10_000))

    private fun graphFingerprint(): Long {
        val bounds = graph().visibleBounds
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            var hash = 1125899906842597L
            for (y in 10 until 46) for (x in 3 until 45) {
                val px = (bounds.left + bounds.width() * x / 48).coerceIn(0, bitmap.width - 1)
                val py = (bounds.top + bounds.height() * y / 48).coerceIn(0, bitmap.height - 1)
                hash = hash * 31L + bitmap.getPixel(px, py)
            }
            return hash
        } finally { bitmap.recycle() }
    }

    private fun scrollToText(vararg labels: String, direction: Direction = Direction.DOWN): UiObject2 {
        repeat(24) { attempt ->
            try {
                // Evidence clocks legitimately recompose these semantics every second.
                // Re-query after a stale node; never retain UiObject2 across a scroll/recomposition.
                labels.forEach { label ->
                    device.findObject(By.text(label))?.takeIf { it.visibleBounds.height() > 0 }?.let { return it }
                }
                val bounds = device.findObjects(By.scrollable(true)).map { it.visibleBounds }
                    .filter { it.height() > 160 && it.width() > 120 }
                    .maxByOrNull { it.height() }
                val top = bounds?.let { it.top + it.height() * 7 / 20 } ?: (device.displayHeight * 2 / 5)
                val bottom = bounds?.let { it.bottom - it.height() * 7 / 20 } ?: (device.displayHeight * 3 / 5)
                val x = bounds?.centerX() ?: (device.displayWidth / 4)
                val move = if (attempt < 8) direction else
                    if (direction == Direction.UP) Direction.DOWN else Direction.UP
                // Inject one bounded gesture from copied coordinates. UiObject2.scroll waits
                // across semantic updates, so a once-per-second evidence clock can detach it.
                // Short, slow overlapping swipes avoid flinging past a small label entirely.
                // Search both directions: expanded items and menus can retain scroll position.
                device.swipe(x, if (move == Direction.UP) top else bottom,
                    x, if (move == Direction.UP) bottom else top, 100)
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                // Only a detached accessibility node is retried. Required UI assertions remain.
            }
            SystemClock.sleep(150)
        }
        error("Requested text was not exposed by the production scroll container: " + labels.joinToString())
    }

    private fun dismissImeIfVisible() {
        var visible = false
        onActivity { activity ->
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        if (visible) device.pressBack()
    }

    private fun operationTab(label: String): UiObject2 {
        repeat(7) {
            device.findObjects(By.text(label)).filter { it.visibleBounds.height() > 0 }
                .minByOrNull { it.visibleBounds.top }?.let { return it }
            val horizontal = device.findObjects(By.scrollable(true)).firstOrNull {
                it.visibleBounds.width() > it.visibleBounds.height() * 2 &&
                    it.visibleBounds.height() < device.displayHeight / 4
            }
            checkNotNull(horizontal) { "Operations tabs have no reachable horizontal scroll container" }
                .scroll(Direction.RIGHT, .65f)
            SystemClock.sleep(150)
        }
        error("Operations tab is not visible: $label")
    }

    private fun assertBelowStatusBar(label: String) {
        var statusBarBottom = 0
        onActivity { activity ->
            val decor = activity.window.decorView
            val location = IntArray(2)
            decor.getLocationOnScreen(location)
            statusBarBottom = location[1] + (ViewCompat.getRootWindowInsets(decor)
                ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0)
        }
        assertTrue("Developer evidence overlaps the system status bar",
            checkNotNull(device.findObject(By.text(label))).visibleBounds.top >= statusBarBottom)
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
