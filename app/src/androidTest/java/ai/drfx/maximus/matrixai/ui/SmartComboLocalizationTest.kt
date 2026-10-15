package ai.drfx.maximus.matrixai.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import ai.drfx.maximus.matrixai.ui.theme.LanguagePreferenceStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartComboLocalizationTest {
    @Test fun noPriceAndRateWarningsTranslateInAllFiveLanguages() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val english = LanguagePreferenceStore.localizedContext(app, AppLanguage.ENGLISH)
        val labels = listOf(
            R.string.api_pricing_warning,
            R.string.api_stat_turn,
            R.string.api_free_only_caution,
            R.string.combo_diag_no_free,
            R.string.combo_diag_no_price,
            R.string.combo_diag_provider_unavailable
        )
        for (locale in AppLanguage.entries) {
            val localized = LanguagePreferenceStore.localizedContext(app, locale)
            labels.forEach { id ->
                val text = localized.getString(id)
                assertTrue("Missing localization in " + locale.tag, text.isNotBlank())
                if (locale != AppLanguage.ENGLISH) {
                    assertNotEquals("English text leaked for " + locale.tag + " / " + id,
                        english.getString(id), text)
                }
            }
            assertFalse(localized.getString(R.string.api_visible_offers, 2, 5).contains("%1"))
            assertFalse(localized.getString(R.string.combo_diag_no_context, 3).contains("%1"))
        }
    }
}
