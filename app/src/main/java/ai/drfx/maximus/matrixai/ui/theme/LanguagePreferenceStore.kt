package ai.drfx.maximus.matrixai.ui.theme

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** User-controlled language; independent of Android's system-wide locale. */
enum class AppLanguage(val tag: String, val nativeName: String, val rtl: Boolean) {
    ENGLISH("en", "English", false),
    RUSSIAN("ru", "Русский", false),
    TURKISH("tr", "Türkçe", false),
    ARABIC("ar", "العربية", true),
    PERSIAN("fa", "فارسی", true);

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

class LanguagePreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("maximus_app_language", Context.MODE_PRIVATE)

    fun load(): AppLanguage = AppLanguage.fromTag(prefs.getString("language", AppLanguage.ENGLISH.tag))

    fun save(value: AppLanguage) {
        // commit() completes before Activity.recreate(), guaranteeing restored settings.
        check(prefs.edit().putString("language", value.tag).commit()) { "Unable to save language" }
    }

    companion object {
        fun localizedContext(base: Context, language: AppLanguage): Context {
            val configuration = Configuration(base.resources.configuration)
            val locale = Locale.forLanguageTag(language.tag)
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            return base.createConfigurationContext(configuration)
        }
    }
}
