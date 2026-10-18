package ai.drfx.maximus.matrixai.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R

/** Native transforms; respects Android's animation switch and the user's companion preference. */
@Composable
internal fun WorkspaceCompanion(working: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("workspace_appearance", 0) }
    if (!preferences.getBoolean("companion", true)) return
    var tapped by remember { mutableStateOf(false) }
    val motion = ValueAnimator.areAnimatorsEnabled()
    val transition = rememberInfiniteTransition(label = "companion-breath")
    val phase by transition.animateFloat(0f, if (motion) 1f else 0f,
        infiniteRepeatable(tween(if (working) 650 else 2200), RepeatMode.Reverse), label = "companion-float")
    val tilt by animateFloatAsState(if (tapped && motion) -12f else 0f, spring(dampingRatio = .35f), label = "companion-reaction")
    LaunchedEffect(tapped) { if (tapped) { kotlinx.coroutines.delay(450); tapped = false } }
    Image(painterResource(R.drawable.maximus_companion), stringResource(R.string.ui_companion),
        modifier.size(72.dp).graphicsLayer {
            translationY = -phase * 4.dp.toPx()
            rotationZ = tilt
            scaleX = 1f + phase * .025f; scaleY = scaleX
        }.clickable { tapped = true })
}
