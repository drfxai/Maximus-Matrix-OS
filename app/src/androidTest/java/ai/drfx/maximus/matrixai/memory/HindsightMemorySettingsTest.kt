package ai.drfx.maximus.matrixai.memory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HindsightMemorySettingsTest {
    @Test fun userKeyEncryptedAndStatePersistsForCrossModelHandoff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = HindsightMemorySettings(context)
        settings.clear()
        try {
            assertFalse(settings.enabled())
            assertFalse(settings.autoShare())
            settings.configure("https://api.hindsight.vectorize.io",
                "hsk_owned_by_user_1234567890abcdef")
            val raw = context.getSharedPreferences(
                "maximus_shared_memory_v1", Context.MODE_PRIVATE)
                .getString("token", "").orEmpty()
            assertFalse(raw.contains("hsk_owned_by_user_1234567890abcdef"))
            assertEquals("hsk_owned_by_user_1234567890abcdef",
                HindsightMemorySettings(context).token())
            assertThrows(IllegalArgumentException::class.java) {
                settings.configure("https://another-memory.example.org", null)
            }
            assertEquals("https://api.hindsight.vectorize.io", settings.endpoint())
            settings.setActiveProject("123e4567-e89b-12d3-a456-426614174000")
            settings.setEnabled(true)
            assertTrue(settings.enabled())
            assertFalse(settings.autoShare())
            settings.setAutoShare(true)
            assertTrue(HindsightMemorySettings(context).autoShare())
            settings.rememberProgress("123e4567-e89b-12d3-a456-426614174000",
                "test-model", "Fix parser regression", "Patch proposed; tests not yet run.")
            val progress = HindsightMemorySettings(context)
                .recentProgress("123e4567-e89b-12d3-a456-426614174000")
            assertTrue(progress.contains("Fix parser regression"))
            val journalBytes = prefsValue(context, "journal_123e4567-e89b-12d3-a456-426614174000")
            assertFalse(journalBytes.contains("Fix parser regression"))
            settings.rememberProgress("123e4567-e89b-12d3-a456-426614174000",
                "test-model", "password = unsafe-secret", "Should not persist")
            assertFalse(settings.recentProgress("123e4567-e89b-12d3-a456-426614174000")
                .contains("unsafe-secret"))
            assertEquals("123e4567-e89b-12d3-a456-426614174000",
                HindsightMemorySettings(context).activeProject())
            settings.clear()
            assertFalse(settings.hasKey())
            assertFalse(settings.enabled())
            assertFalse(settings.autoShare())
        } finally { settings.clear() }
    }

    private fun prefsValue(context: Context, key: String): String =
        context.getSharedPreferences("maximus_shared_memory_v1", Context.MODE_PRIVATE)
            .getString(key, "").orEmpty()
}
