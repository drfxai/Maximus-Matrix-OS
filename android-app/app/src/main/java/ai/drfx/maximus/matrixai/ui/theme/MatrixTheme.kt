package ai.drfx.maximus.matrixai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MatrixColors = darkColorScheme(
    primary = Color(0xFF59F0B7),
    secondary = Color(0xFF5FCBD6),
    background = Color(0xFF020405),
    surface = Color(0xFF050A0A),
    onPrimary = Color(0xFF00140D),
    onBackground = Color(0xFFDDE8E5),
    onSurface = Color(0xFFDDE8E5)
)

@Composable
fun MaximusMatrixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MatrixColors, content = content)
}
