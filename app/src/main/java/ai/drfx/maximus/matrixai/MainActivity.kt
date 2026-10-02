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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NativeBridge.nativeAbiMarker()
        val themeStore = ThemePreferenceStore(this)
        setContent {
            var themeMode by remember { mutableStateOf(themeStore.load()) }
            MaximusMatrixTheme(mode = themeMode) {
                MatrixScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        themeStore.save(mode)
                    }
                )
            }
        }
    }
}
