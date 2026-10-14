package ai.drfx.maximus.matrixai.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ElevenVoiceSettingsTest {
    @Test fun encryptedKeyPersistsSeparatelyAndCanBeRevoked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ElevenVoiceSettings(context)
        store.clear()
        try {
            assertFalse(store.elevenSelected())
            val secret = "user-eleven-token-1234567890-abcdef"
            store.saveKey(secret)
            assertTrue(store.hasKey())
            assertEquals(secret, ElevenVoiceSettings(context).key())
            val prefs = context.getSharedPreferences("maximus_eleven_voice", Context.MODE_PRIVATE)
            val stored = prefs.getString("encrypted_key", "").orEmpty()
            assertFalse(stored.contains(secret))
            assertFalse(stored.isBlank())
            store.saveVoice("JBFqnCBsd6RMkjVDRZzb")
            store.saveModel(ElevenLabsClient.DEFAULT_MODEL)
            store.selectEleven(true)
            assertTrue(ElevenVoiceSettings(context).elevenSelected())
            assertEquals("JBFqnCBsd6RMkjVDRZzb", ElevenVoiceSettings(context).selectedVoice())
            store.clear()
            assertFalse(store.hasKey())
            assertEquals("", store.key())
            assertFalse(store.elevenSelected())
        } finally {
            store.clear()
        }
    }
}
