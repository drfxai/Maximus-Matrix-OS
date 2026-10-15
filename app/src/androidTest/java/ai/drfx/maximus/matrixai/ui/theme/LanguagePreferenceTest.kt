package ai.drfx.maximus.matrixai.ui.theme

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.drfx.maximus.matrixai.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguagePreferenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun everySupportedLanguageHasLocalizedResourcesAndDirection() {
        val localized = AppLanguage.entries.map { language ->
            val wrapped = LanguagePreferenceStore.localizedContext(context, language)
            val config = wrapped.resources.configuration
            assertEquals(language.tag, config.locales[0].language)
            assertEquals(
                if (language.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                config.layoutDirection
            )
            assertTrue(wrapped.getString(R.string.language_title).isNotBlank())
            assertTrue(wrapped.getString(R.string.studio_title).isNotBlank())
            wrapped.getString(R.string.language_title)
        }
        assertEquals(5, localized.distinct().size)
    }

    @Test fun smartComboPricingAndConsentAreTranslatedInAllFiveLanguages() {
        val english = LanguagePreferenceStore.localizedContext(context, AppLanguage.ENGLISH)
        val englishNoFree = english.getString(R.string.combo_state_no_free)
        val englishConfirm = english.getString(R.string.chat_combo_confirm_action)
        for (language in AppLanguage.entries) {
            val wrapped = LanguagePreferenceStore.localizedContext(context, language)
            val status = wrapped.getString(R.string.combo_state_no_free)
            val check = wrapped.getString(R.string.combo_check_missing_context, 2)
            val message = wrapped.getString(
                R.string.chat_combo_confirm_body, "NVIDIA NIM", "a-model-id"
            )
            val available = wrapped.getString(R.string.api_verified_offers, 3)
            assertTrue("Missing catalog warning in " + language.tag, status.isNotBlank())
            assertTrue("Missing context diagnosis in " + language.tag,
                check.isNotBlank() && !check.contains("%1"))
            assertTrue("Missing model ID in consent warning", message.contains("a-model-id"))
            assertTrue("Missing provider in consent warning", message.contains("NVIDIA NIM"))
            assertTrue("Missing formatted offer count", available.isNotBlank() &&
                !available.contains("%1"))
            if (language != AppLanguage.ENGLISH) {
                assertNotEquals("English catalog fallback: " + language.tag, englishNoFree, status)
                assertNotEquals("English consent fallback: " + language.tag,
                    englishConfirm, wrapped.getString(R.string.chat_combo_confirm_action))
            }
        }
    }

    @Test fun languageSelectionPersistsAndUnsupportedValueFallsBackToEnglish() {
        val store = LanguagePreferenceStore(context)
        val previous = store.load()
        try {
            for (language in AppLanguage.entries) {
                store.save(language)
                assertEquals(language, LanguagePreferenceStore(context).load())
            }
            assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("not-supported"))
            assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(null))
        } finally {
            store.save(previous)
        }
    }
}
