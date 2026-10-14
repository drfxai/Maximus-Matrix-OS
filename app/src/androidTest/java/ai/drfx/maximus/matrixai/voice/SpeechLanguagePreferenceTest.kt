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
    @Test fun majorSpeechAndMobileHarnessLabelsAreTranslatedInAllFiveLanguages() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val english = LanguagePreferenceStore.localizedContext(app, AppLanguage.ENGLISH)
        val englishVoice = english.getString(ai.drfx.maximus.matrixai.R.string.speech_conversation_label)
        val englishBuild = english.getString(ai.drfx.maximus.matrixai.R.string.phone_install)
        for (language in AppLanguage.entries.filter { it != AppLanguage.ENGLISH }) {
            val localized = LanguagePreferenceStore.localizedContext(app, language)
            val voice = localized.getString(ai.drfx.maximus.matrixai.R.string.speech_conversation_label)
            val build = localized.getString(ai.drfx.maximus.matrixai.R.string.phone_install)
            assertTrue("Voice label missing for " + language.tag, voice.isNotBlank() && voice != englishVoice)
            assertTrue("Install label missing for " + language.tag, build.isNotBlank() && build != englishBuild)
        }
    }

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
