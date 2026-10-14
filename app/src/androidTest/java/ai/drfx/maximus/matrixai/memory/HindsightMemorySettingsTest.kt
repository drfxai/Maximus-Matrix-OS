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
            settings.setActiveProject("123e4567-e89b-12d3-a456-426614174000")
            settings.setEnabled(true)
            assertTrue(settings.enabled())
            assertFalse(settings.autoShare())
            settings.setAutoShare(true)
            assertTrue(HindsightMemorySettings(context).autoShare())
            assertEquals("123e4567-e89b-12d3-a456-426614174000",
                HindsightMemorySettings(context).activeProject())
            settings.clear()
            assertFalse(settings.hasKey())
            assertFalse(settings.enabled())
            assertFalse(settings.autoShare())
        } finally { settings.clear() }
    }
}
