package ai.drfx.maximus.matrixai.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource

/** Labels and checked semantics remain provided by the surrounding settings row. */
@Composable
internal fun MotionSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbContent: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    colors: SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.error,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerLow,
        uncheckedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = .6f)
    )
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier,
        thumbContent = thumbContent, enabled = enabled, colors = colors, interactionSource = interactionSource)
}
