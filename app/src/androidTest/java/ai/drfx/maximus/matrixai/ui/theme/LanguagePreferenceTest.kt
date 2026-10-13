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
