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

    @Test fun corruptRevisionCannotBecomeVerifiedContinuity() {
        rejectCorruptManifest { it.put("revision", "0".repeat(64)) }
    }

    @Test fun corruptFileMetadataCannotBecomeVerifiedContinuity() {
        rejectCorruptManifest { manifest ->
            val entry = manifest.getJSONArray("files").getJSONObject(0)
            entry.put("length", entry.getLong("length") + 1)
        }
    }

    @Test fun corruptFileHashCannotBecomeVerifiedContinuity() {
        rejectCorruptManifest { manifest ->
            manifest.getJSONArray("files").getJSONObject(0).put("sha256", "0".repeat(64))
        }
    }

    private fun rejectCorruptManifest(corrupt: (org.json.JSONObject) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val project = workspace.newProject("Checkpoint integrity test")
        val continuity = ProjectContinuityStore(context, workspace)
        val saved = continuity.save(project.id, "Review current sources")
        val file = java.io.File(context.filesDir,
            "studio/checkpoints/${project.id}/${saved.checkpointId}.json")
        val original = file.readText()
        try {
            val manifest = org.json.JSONObject(original)
            corrupt(manifest)
            file.writeText(manifest.toString())
            assertThrows(IllegalArgumentException::class.java) {
                continuity.validate(project.id, saved.checkpointId)
            }
            assertThrows(IllegalArgumentException::class.java) {
                continuity.resumePrompt(project.id)
            }
            // Invalid user-owned evidence is retained for explicit recovery.
            assertTrue(file.isFile)
        } finally { file.writeText(original) }
        assertTrue(continuity.validate(project.id, saved.checkpointId).unchanged)
    }
}
