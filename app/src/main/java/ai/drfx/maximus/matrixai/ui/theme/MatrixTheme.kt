package ai.drfx.maximus.matrixai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MatrixDarkColors = darkColorScheme(
    primary = Color(0xFF5CF0BC),
    secondary = Color(0xFF58C1D7),
    tertiary = Color(0xFFE4B34D),
    background = Color(0xFF030707),
    surface = Color(0xFF071110),
    surfaceVariant = Color(0xFF0A1714),
    onPrimary = Color(0xFF00140D),
    onBackground = Color(0xFFDDE8E5),
    onSurface = Color(0xFFDDE8E5),
    onSurfaceVariant = Color(0xFF9BB0AA),
    outline = Color(0xFF15342D),
    error = Color(0xFFE96E91)
)

private val MatrixLightColors = lightColorScheme(
    primary = Color(0xFF006B52),
    secondary = Color(0xFF006878),
    tertiary = Color(0xFF7B5900),
    background = Color(0xFFF4F8F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE5F0EC),
    onPrimary = Color.White,
    onBackground = Color(0xFF10201B),
    onSurface = Color(0xFF10201B),
    onSurfaceVariant = Color(0xFF4D625A),
    outline = Color(0xFFB6CAC2),
    error = Color(0xFFB3261E)
)

private val MatrixTypography = Typography(
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun MaximusMatrixTheme(
    mode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) MatrixDarkColors else MatrixLightColors,
        typography = MatrixTypography,
        content = content
    )
}
