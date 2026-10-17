package ai.drfx.maximus.matrixai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** AMOLED canvas with deliberately limited, visibly navy raised surfaces. */
private val MatrixDarkColors = darkColorScheme(
    primary = Color(0xFFF4C35A), onPrimary = Color(0xFF211700),
    primaryContainer = Color(0xFF142C4C), onPrimaryContainer = Color(0xFFFAFAFC),
    secondary = Color(0xFFFFE079), onSecondary = Color(0xFF211700),
    secondaryContainer = Color(0xFF302710), onSecondaryContainer = Color(0xFFFFE079),
    tertiary = Color(0xFFFF8D38), onTertiary = Color(0xFF271200),
    tertiaryContainer = Color(0xFF38200D), onTertiaryContainer = Color(0xFFFFBE8C),
    background = Color.Black, onBackground = Color(0xFFFAFAFC),
    surface = Color(0xFF070B11), onSurface = Color(0xFFFAFAFC),
    surfaceVariant = Color(0xFF142C4C), onSurfaceVariant = Color(0xFFB9C5D6),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF10213A),
    surfaceContainer = Color(0xFF0A111B),
    surfaceContainerHigh = Color(0xFF142C4C),
    surfaceContainerHighest = Color(0xFF1A3659),
    outline = Color(0xFF52657E), outlineVariant = Color(0xFF24334A),
    error = Color(0xFFF05262), onError = Color(0xFF210007),
    errorContainer = Color(0xFF300D15), onErrorContainer = Color(0xFFFFD9DE),
    inverseSurface = Color(0xFFFAFAFC), inverseOnSurface = Color(0xFF10213A),
    surfaceTint = Color.Transparent
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
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp),
            extraLarge = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
