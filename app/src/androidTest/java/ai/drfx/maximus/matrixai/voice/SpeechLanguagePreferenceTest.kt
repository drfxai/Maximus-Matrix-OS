package ai.drfx.maximus.matrixai.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import ai.drfx.maximus.matrixai.ui.theme.LanguagePreferenceStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechLanguagePreferenceTest {
    @Test fun speechDefaultsToChosenInterfaceLanguageAndAcceptsManualOverride() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val interfacePrefs = LanguagePreferenceStore(app)
        val voicePrefs = SpeechLanguagePreferenceStore(app)
        val previousInterface = interfacePrefs.load()
        val previousSpeech = voicePrefs.explicitChoice()
        try {
            voicePrefs.set(null)
            interfacePrefs.save(AppLanguage.PERSIAN)
            assertEquals(SpeechLanguage.PERSIAN, voicePrefs.selected())
            voicePrefs.set(SpeechLanguage.ENGLISH)
            assertEquals(SpeechLanguage.ENGLISH, voicePrefs.selected())
            voicePrefs.set(null)
            assertEquals(SpeechLanguage.PERSIAN, voicePrefs.selected())
        } finally {
            voicePrefs.set(previousSpeech)
            interfacePrefs.save(previousInterface)
        }
    }
}
