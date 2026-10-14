package ai.drfx.maximus.matrixai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import ai.drfx.maximus.matrixai.ui.MatrixScreen
import ai.drfx.maximus.matrixai.ui.theme.MaximusMatrixTheme
import ai.drfx.maximus.matrixai.ui.theme.ThemePreferenceStore
import ai.drfx.maximus.matrixai.ui.theme.LanguagePreferenceStore
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val chosen = LanguagePreferenceStore(newBase).load()
        // Keep date/number formatting and speech defaults consistent with the
        // in-app selection, not the independent system-wide device language.
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(chosen.tag))
        super.attachBaseContext(LanguagePreferenceStore.localizedContext(newBase, chosen))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themeStore = ThemePreferenceStore(applicationContext)
        val languageStore = LanguagePreferenceStore(applicationContext)
        val selectedLanguage = languageStore.load()

        setContent {
            var currentThemeMode by remember { mutableStateOf(themeStore.load()) }

            CompositionLocalProvider(
                LocalLayoutDirection provides if (selectedLanguage.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                MaximusMatrixTheme(mode = currentThemeMode) {
                    MatrixScreen(
                        themeMode = currentThemeMode,
                        onThemeModeChange = { newMode ->
                            currentThemeMode = newMode
                            themeStore.save(newMode)
                        },
                        selectedLanguage = selectedLanguage,
                        onLanguageChange = { newLanguage ->
                            if (newLanguage != selectedLanguage) {
                                languageStore.save(newLanguage)
                                recreate()
                            }
                        }
                    )
                }
            }
        }
    }
}

