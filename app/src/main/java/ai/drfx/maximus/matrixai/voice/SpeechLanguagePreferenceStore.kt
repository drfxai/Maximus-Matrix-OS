package ai.drfx.maximus.matrixai.voice

import android.content.Context
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import ai.drfx.maximus.matrixai.ui.theme.LanguagePreferenceStore
import java.util.Locale

/** Speech locale must follow the selected APP language, not the Android system locale. */
enum class SpeechLanguage(val tag: String, val label: String) {
    PERSIAN("fa-IR", "فارسی"),
    ENGLISH("en-US", "English"),
    ARABIC("ar-SA", "العربية"),
    TURKISH("tr-TR", "Türkçe"),
    RUSSIAN("ru-RU", "Русский");

    fun locale(): Locale = Locale.forLanguageTag(tag)

    companion object {
        fun fromApp(language: AppLanguage): SpeechLanguage = when (language) {
            AppLanguage.PERSIAN -> PERSIAN
            AppLanguage.ARABIC -> ARABIC
            AppLanguage.TURKISH -> TURKISH
            AppLanguage.RUSSIAN -> RUSSIAN
            AppLanguage.ENGLISH -> ENGLISH
        }

        fun fromTag(value: String?): SpeechLanguage? = entries.firstOrNull { it.tag == value }
    }
}

internal object SpeechLanguagePolicy {
    /** Decide the TTS locale independently of the host device's system language. */
    fun preferredTtsLanguage(text: String, selected: SpeechLanguage): SpeechLanguage {
        if (selected == SpeechLanguage.ARABIC || selected == SpeechLanguage.PERSIAN) {
            // If the user picked Persian/Arabic use it for both recognition and TTS.
            return selected
        }
        if (text.any { it in "پچژگکیۀ" }) return SpeechLanguage.PERSIAN
        return selected
    }
}

class SpeechLanguagePreferenceStore(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("maximus_speech_language", Context.MODE_PRIVATE)

    /** Null means follow the currently selected interface language. */
    fun explicitChoice(): SpeechLanguage? =
        SpeechLanguage.fromTag(preferences.getString("speech_language", null))

    fun selected(): SpeechLanguage =
        explicitChoice() ?: SpeechLanguage.fromApp(LanguagePreferenceStore(app).load())

    fun set(choice: SpeechLanguage?) {
        val editor = preferences.edit()
        if (choice == null) editor.remove("speech_language")
        else editor.putString("speech_language", choice.tag)
        check(editor.commit()) { "Could not save speech language." }
    }
}
