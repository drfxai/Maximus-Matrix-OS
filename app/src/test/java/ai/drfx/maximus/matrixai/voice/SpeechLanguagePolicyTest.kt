package ai.drfx.maximus.matrixai.voice

import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SpeechLanguagePolicyTest {
    @Test fun persianAppLanguageProducesExplicitFaIranLocale() {
        val speech = SpeechLanguage.fromApp(AppLanguage.PERSIAN)
        assertEquals("fa-IR", speech.tag)
        assertEquals("fa", speech.locale().language)
        assertEquals("IR", speech.locale().country)
    }

    @Test fun everySupportedAppLanguageHasAnExplicitSpeechLocale() {
        assertEquals(AppLanguage.entries.size,
            AppLanguage.entries.map(SpeechLanguage::fromApp).distinct().size)
        assertEquals("ar-SA", SpeechLanguage.fromApp(AppLanguage.ARABIC).tag)
        assertEquals("tr-TR", SpeechLanguage.fromApp(AppLanguage.TURKISH).tag)
        assertEquals("ru-RU", SpeechLanguage.fromApp(AppLanguage.RUSSIAN).tag)
        assertEquals("en-US", SpeechLanguage.fromApp(AppLanguage.ENGLISH).tag)
    }

    @Test fun ttsLanguagePrefersSelectedPersianAndDoesNotAssumeEnglishDeviceLanguage() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals(SpeechLanguage.PERSIAN,
                SpeechLanguagePolicy.preferredTtsLanguage("سلام، چطوری؟", SpeechLanguage.PERSIAN))
            assertEquals(SpeechLanguage.PERSIAN,
                SpeechLanguagePolicy.preferredTtsLanguage("خوشحالم", SpeechLanguage.ENGLISH))
            assertEquals(SpeechLanguage.ARABIC,
                SpeechLanguagePolicy.preferredTtsLanguage("مرحبا", SpeechLanguage.ARABIC))
        } finally {
            Locale.setDefault(old)
        }
    }
}
