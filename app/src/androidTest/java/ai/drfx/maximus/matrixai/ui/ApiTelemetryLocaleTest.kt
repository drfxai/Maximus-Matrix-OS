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
class ApiTelemetryLocaleTest {
    @Test fun pricingWarningsBudgetLabelsAndProviderQuotaStayLocalized() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val en = LanguagePreferenceStore.localizedContext(app, AppLanguage.ENGLISH)
        val important = listOf(
            R.string.api_pricing_warning,
            R.string.api_free_only_caution,
            R.string.api_stat_turn,
            R.string.api_stat_context_left,
            R.string.api_local_budget_input,
            R.string.api_provider_snapshot,
            R.string.api_rate_account,
            R.string.api_budget_info
        )
        for (language in AppLanguage.entries) {
            val ctx = LanguagePreferenceStore.localizedContext(app, language)
            for (id in important) {
                val copy = ctx.getString(id)
                assertTrue("Missing localized label for " + language.tag, copy.isNotBlank())
                if (language != AppLanguage.ENGLISH) {
                    assertNotEquals("Unexpected English label for " + language.tag,
                        en.getString(id), copy)
                }
            }
            val summary = ctx.getString(R.string.api_stat_in_out, "100", "200")
            assertTrue(summary.contains("100") && summary.contains("200"))
            val count = ctx.getString(R.string.api_visible_offers, 2, 5)
            assertFalse("Unformatted provider offer count", count.contains("%1"))
        }
    }
}
