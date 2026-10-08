package ai.drfx.maximus.matrixai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import ai.drfx.maximus.matrixai.ui.MatrixScreen
import ai.drfx.maximus.matrixai.ui.theme.MaximusMatrixTheme
import ai.drfx.maximus.matrixai.ui.theme.ThemePreferenceStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themeStore = ThemePreferenceStore(applicationContext)

        setContent {
            var currentThemeMode by remember { mutableStateOf(themeStore.load()) }

            MaximusMatrixTheme(mode = currentThemeMode) {
                MatrixScreen(
                    themeMode = currentThemeMode,
                    onThemeModeChange = { newMode ->
                        currentThemeMode = newMode
                        themeStore.save(newMode)
                    }
                )
            }
        }
    }
}

