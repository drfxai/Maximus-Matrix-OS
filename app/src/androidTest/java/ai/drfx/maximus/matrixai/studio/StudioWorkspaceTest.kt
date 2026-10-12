package ai.drfx.maximus.matrixai.studio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioWorkspaceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun starterCanBeEditedExportedAndReimportedWithoutExecution() {
        val store = ProjectWorkspaceStore(context)
        val initial = store.newProject("StudioTest")
        assertTrue(store.listFiles(initial.id).any { it.path == "app/build.gradle.kts" })
        store.saveBrief(initial.id, "Build an accessible notes app.")
        assertTrue(store.aiPlanPrompt(initial.id).contains("Build an accessible notes app."))
        val before = store.readFile(initial.id, "README.md")
        store.saveFile(initial.id, "README.md", before + "\nUpdated on device.")
        val archive = ByteArrayOutputStream()
        store.exportZip(initial.id, archive)
        val imported = store.importZip("RestoredApp", archive.toByteArray().inputStream())
        assertTrue(store.readFile(imported.id, "README.md").contains("Updated on device."))
        assertFalse(store.aiPlanPrompt(initial.id).contains("Updated on device."))
    }

    @Test fun zipTraversalAndOversizedFilesAreRejected() {
        val store = ProjectWorkspaceStore(context)
        fun archive(entry: String, data: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(entry))
                zip.write(data)
                zip.closeEntry()
            }
            return out.toByteArray()
        }
        // Android 15's framework SafeZipPathValidator can reject traversal first,
        // throwing ZipException rather than our own IllegalArgumentException.
        assertThrows(Exception::class.java) {
            store.importZip("TraversalTest", archive("../outside.txt", "bad".toByteArray()).inputStream())
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.importZip("OversizedTest", archive("big.txt", ByteArray(4 * 1024 * 1024 + 1)).inputStream())
        }
    }
}
