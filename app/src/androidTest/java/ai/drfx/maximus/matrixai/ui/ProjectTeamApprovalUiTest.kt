package ai.drfx.maximus.matrixai.ui

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import ai.drfx.maximus.matrixai.MainActivity
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.agent.teams.ProjectTeamExecutor
import ai.drfx.maximus.matrixai.agent.teams.ProjectTeamRun
import ai.drfx.maximus.matrixai.agent.teams.ProjectTeamRunStore
import ai.drfx.maximus.matrixai.memory.HindsightMemorySettings
import ai.drfx.maximus.matrixai.ui.theme.MaximusMatrixTheme
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real panel approval and durable executor with a recording request adapter; no credentials/network. */
@RunWith(AndroidJUnit4::class)
class ProjectTeamApprovalUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun approvalDispatchesExactlyTwoBoundedRequestsAndRecoveredDispatchIsNeverReplayed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "team-ui-" + UUID.randomUUID() + ".json")
        val project = UUID.randomUUID().toString()
        // The test account is a local fixture; cancelling the preview must
        // never dispatch any memory request or need a real Hindsight token.
        val memorySettings = HindsightMemorySettings(context)
        memorySettings.disconnect()
        memorySettings.configure("https://memory.example.org", null)
        memorySettings.setActiveProject(project)
        memorySettings.setEnabled(true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val state = MutableStateFlow<ProjectTeamRun?>(null)
        val store = ProjectTeamRunStore(file, "ui-process")
        val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
        try {
            UiDevice.getInstance(instrumentation).executeShellCommand(
                "am start -W -f 0x10008000 -n ${context.packageName}/${MainActivity::class.java.name}")
            onActivity { activity ->
                activity.setContent {
                    MaximusMatrixTheme {
                        val run by state.collectAsState()
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            ProjectTeamRunPanel(project, run, "", prepare = {
                                scope.launch { state.value = store.prepare(project, "a".repeat(64), "Fixture project metadata", "Fixture route") }
                            }, approve = { id ->
                                scope.launch {
                                    state.value = store.change(id, setOf(ProjectTeamRun.State.AWAITING_APPROVAL)) {
                                        it.copy(state = ProjectTeamRun.State.PLANNER_READY)
                                    }
                                    ProjectTeamExecutor(store).execute(id, {}, { role, prompt ->
                                        ProjectTeamExecutor.Dispatch("OPENAI_COMPATIBLE", "fixture-model", { _, cap ->
                                            assertEquals(1024, cap)
                                            assertTrue(store.read()!!.dispatched)
                                            if (role == "reviewer") assertTrue(prompt.contains("Planner fixture output"))
                                            calls += role
                                            ProjectTeamRun.Evidence("OPENAI_COMPATIBLE", "fixture-model",
                                                if (role == "planner") "Planner fixture output" else "Reviewer fixture output", 10, 4, true)
                                        })
                                    }, { state.value = it })
                                }
                            }, cancel = {})
                        }
                    }
                }
            }
            compose.onNodeWithTag("project-team-prepare").performScrollTo().performClick()
            compose.waitUntil(10_000) { state.value?.state == ProjectTeamRun.State.AWAITING_APPROVAL }
            assertTrue(calls.isEmpty())
            compose.onNodeWithTag("project-team-approve").performScrollTo().performClick()
            compose.waitUntil(10_000) { state.value?.state == ProjectTeamRun.State.COMPLETE }
            assertEquals(listOf("planner", "reviewer"), calls.toList())
            // A completed team result is only previewed; no memory upload is
            // allowed until a second, explicit confirmation is pressed.
            compose.onNodeWithTag("project-team-hindsight-preview").performScrollTo().performClick()
            compose.onNodeWithTag("project-team-hindsight-confirm").assertExists()
            compose.onNodeWithText(context.getString(R.string.studio_cancel)).performClick()
            assertEquals("Preview cancellation must not send another model request", 2, calls.size)
            assertEquals(ProjectTeamRun.State.COMPLETE, ProjectTeamRunStore(file, "new-process").read()!!.state)
            // Reproduce death after a durable dispatch barrier, before any response was checkpointed.
            val pending = store.prepare(project, "b".repeat(64), "Second fixture metadata")
            store.change(pending.id, setOf(pending.state)) { it.copy(state = ProjectTeamRun.State.PLANNER_DISPATCHED) }
            state.value = ProjectTeamRunStore(file, "restarted-process").read()
            compose.waitForIdle()
            assertEquals(ProjectTeamRun.State.UNCERTAIN, state.value!!.state)
            assertEquals(2, calls.size)
            compose.onNodeWithTag("project-team-prepare").performScrollTo()
        } finally {
            memorySettings.disconnect()
            scope.cancel()
            file.delete()
            File(file.parentFile, file.name + ".pending").delete()
        }
    }

    private fun onActivity(action: (MainActivity) -> Unit) {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        Handler(Looper.getMainLooper()).post {
            try {
                action(checkNotNull(ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()))
            } catch (error: Throwable) { failure.set(error) }
            finally { done.countDown() }
        }
        check(done.await(10, TimeUnit.SECONDS))
        failure.get()?.let { throw it }
    }
}
