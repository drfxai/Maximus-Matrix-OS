package ai.drfx.maximus.matrixai.studio.memory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HindsightMemorySettingsTest {
    @Test fun encryptedKeyAndPerProjectBankAreIsolatedAndRevocable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = HindsightMemorySettings(context)
        val id = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        val token = "test-owned-hindsight-credential-123456789"
        settings.removeKey()
        try {
            settings.saveKey(token)
            assertTrue(settings.hasKey())
            assertEquals(token, HindsightMemorySettings(context).key())
            val prefs = context.getSharedPreferences("matrix_hindsight_memory", Context.MODE_PRIVATE)
            val stored = prefs.getString("encrypted_key", "").orEmpty()
            assertTrue(stored.isNotBlank())
            assertFalse(stored.contains(token))
            assertFalse(settings.enabledFor(id))
            val bank = settings.bank(id)
            assertNotEquals(bank, settings.bank(other))
            settings.enable(id, true)
            assertTrue(HindsightMemorySettings(context).enabledFor(id))
            settings.setBank(id, "shared_bank_2026")
            assertEquals("shared_bank_2026", HindsightMemorySettings(context).bank(id))
            settings.removeKey()
            assertFalse(settings.enabledFor(id))
            assertTrue(settings.key().isBlank())
        } finally {
            settings.removeKey()
        }
    }
}
