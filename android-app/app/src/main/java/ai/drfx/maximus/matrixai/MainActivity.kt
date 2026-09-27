package ai.drfx.maximus.matrixai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import ai.drfx.maximus.matrixai.ui.MatrixScreen
import ai.drfx.maximus.matrixai.ui.theme.MaximusMatrixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        NativeBridge.nativeAbiMarker()
        setContent {
            MaximusMatrixTheme {
                MatrixScreen()
            }
        }
    }
}
