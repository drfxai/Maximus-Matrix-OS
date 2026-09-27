package ai.drfx.maximus.matrixai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MatrixColors = darkColorScheme(
    primary = Color(0xFF5CF0BC),
    secondary = Color(0xFF58C1D7),
    tertiary = Color(0xFFE4B34D),
    background = Color(0xFF030707),
    surface = Color(0xFF071110),
    surfaceVariant = Color(0xFF0A1714),
    onPrimary = Color(0xFF00140D),
    onBackground = Color(0xFFDDE8E5),
    onSurface = Color(0xFFDDE8E5),
    outline = Color(0xFF15342D)
)

private val MatrixTypography = Typography(
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun MaximusMatrixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MatrixColors,
        typography = MatrixTypography,
        content = content
    )
}
