package ai.drfx.maximus.matrixai.studio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectContinuityStoreTest {
    @Test fun localCheckpointValidatesFilesAndRejectsChangedWorkspace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val project = workspace.newProject("Checkpoint Sample")
        val continuity = ProjectContinuityStore(context, workspace)
        val initial = continuity.save(project.id, "Review Kotlin sources")
        assertTrue(initial.unchanged)
        assertEquals("Review Kotlin sources", initial.nextTask)
        assertTrue(continuity.resumePrompt(project.id).contains(initial.revision))
        workspace.saveFile(project.id, "README.md", "Changed workspace file")
        val changed = continuity.validate(project.id, initial.checkpointId)
        assertFalse(changed.unchanged)
        assertTrue("README.md" in changed.modifiedPaths)
        assertThrows(IllegalArgumentException::class.java) {
            continuity.resumePrompt(project.id)
        }
        assertTrue(continuity.save(project.id, "Continue after review").unchanged)
    }
}
