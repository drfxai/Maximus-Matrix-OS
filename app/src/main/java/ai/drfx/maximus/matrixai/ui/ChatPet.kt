package ai.drfx.maximus.matrixai.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val petBlue = Color(0xFF3F6BFF)
private val petBlueDeep = Color(0xFF2747D8)
private val petScreen = Color(0xFF0D1846)
private val petFace = Color(0xFF7FE9FF)

/** Whether the user kept the workspace companion on (Hub → appearance). */
@Composable
internal fun chatPetEnabled(): Boolean {
    val context = LocalContext.current
    return remember { context.getSharedPreferences("workspace_appearance", 0).getBoolean("companion", true) }
}

/**
 * The Chat pet: a small blue cloud-headed robot drawn natively. Its screen face alternates
 * between a terminal prompt ("> _" with a blinking cursor) and happy eyes ("^ ^"); it bobs, and
 * now and then hops and waves. [working] makes it livelier while a reply is being written.
 */
@Composable
internal fun ChatPet(modifier: Modifier = Modifier, size: Dp = 40.dp, working: Boolean = false) {
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val cycle = if (working) 4_200 else 6_400
    val transition = rememberInfiniteTransition(label = "chat-pet")
    val time by transition.animateFloat(0f, if (motion) 1f else 0f,
        infiniteRepeatable(tween(cycle, easing = LinearEasing)), label = "chat-pet-time")
    Canvas(modifier.size(size).testTag("chat_pet").semantics { contentDescription = "Maximus pet" }) {
        drawPet(time, working && motion)
    }
}

private fun DrawScope.drawPet(t: Float, working: Boolean) {
    val unit = size.minDimension / 100f
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun s(w: Float, h: Float) = Size(w * unit, h * unit)
    // Six beats per cycle: prompt, happy, prompt, happy, prompt, hop + wave (happy).
    val beat = (t * 6f).toInt().coerceIn(0, 5)
    val beatPhase = t * 6f - beat
    val hop = if (beat == 5) sin(beatPhase * PI).toFloat() else 0f
    val bob = sin(t * 2f * PI.toFloat() * (if (working) 6f else 3f)) * (if (working) 2.2f else 1.4f)
    val happy = beat % 2 == 1 || beat == 5
    val wave = if (beat == 5) sin(beatPhase * PI * 4).toFloat() else 0f
    translate(top = (bob - hop * 9f) * unit) {
        // Soft shadow shrinks while hopping.
        drawOval(Color.Black.copy(alpha = .28f - hop * .15f), topLeft = p(34f + hop * 4, 92f - bob - (-hop * 9f)),
            size = s(32f - hop * 8, 5f))
        // Legs and body.
        drawRoundRect(petBlueDeep, p(39f, 80f), s(8f, 10f), CornerRadius(3f * unit))
        drawRoundRect(petBlueDeep, p(53f, 80f), s(8f, 10f), CornerRadius(3f * unit))
        drawRoundRect(Brush.verticalGradient(listOf(petBlue, petBlueDeep), startY = 60f * unit, endY = 84f * unit),
            p(35f, 60f), s(30f, 23f), CornerRadius(8f * unit))
        // Chest prompt ">-".
        val chest = Path().apply { moveTo(43f * unit, 68f * unit); lineTo(47f * unit, 71f * unit); lineTo(43f * unit, 74f * unit) }
        drawPath(chest, petFace, style = Stroke(1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(petFace, p(50f, 74f), p(56f, 74f), 1.8f * unit, StrokeCap.Round)
        // Arms: resting, or raised and waving on the hop beat.
        val armLift = if (beat == 5) 1f else 0f
        rotate(-25f * armLift - 20f * wave * armLift, pivot = p(35f, 63f)) {
            drawRoundRect(petBlue, p(27f, 62f), s(8f, 15f), CornerRadius(4f * unit))
        }
        rotate(25f * armLift + 20f * wave * armLift, pivot = p(65f, 63f)) {
            drawRoundRect(petBlue, p(65f, 62f), s(8f, 15f), CornerRadius(4f * unit))
        }
        // Cloud head: a rounded block with puffs on top and sides.
        val head = Brush.verticalGradient(listOf(Color(0xFF5B84FF), petBlue, petBlueDeep), startY = 8f * unit, endY = 64f * unit)
        listOf(Triple(30f, 26f, 13f), Triple(50f, 20f, 15f), Triple(70f, 26f, 13f),
            Triple(20f, 40f, 11f), Triple(80f, 40f, 11f)).forEach { (x, y, r) ->
            drawCircle(head, r * unit, p(x, y))
        }
        drawRoundRect(head, p(14f, 24f), s(72f, 38f), CornerRadius(16f * unit))
        // Glossy highlight.
        drawCircle(Color.White.copy(alpha = .18f), 6f * unit, p(38f, 18f))
        // Screen.
        drawRoundRect(petScreen, p(25f, 31f), s(50f, 25f), CornerRadius(8f * unit))
        val glow = 1.6f * unit
        if (happy) {
            fun eye(cx: Float) {
                val arc = Path().apply {
                    moveTo((cx - 5f) * unit, 47f * unit)
                    quadraticBezierTo(cx * unit, 37f * unit, (cx + 5f) * unit, 47f * unit)
                }
                drawPath(arc, petFace, style = Stroke(2.6f * unit, cap = StrokeCap.Round))
            }
            eye(40f); eye(60f)
        } else {
            val chevron = Path().apply {
                moveTo(35f * unit, 38.5f * unit); lineTo(42f * unit, 43.5f * unit); lineTo(35f * unit, 48.5f * unit)
            }
            drawPath(chevron, petFace, style = Stroke(2.6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Cursor blinks twice per beat.
            if (beatPhase < .25f || beatPhase in .5f..0.75f || abs(beatPhase - 1f) < .01f) {
                drawLine(petFace, p(48f, 49f), p(58f, 49f), 2.6f * unit, StrokeCap.Round)
            }
        }
        drawRoundRect(petFace.copy(alpha = .10f), p(25f, 31f), s(50f, 25f), CornerRadius(8f * unit), style = Stroke(glow))
    }
}

/** Pet with a softly pulsing "Working" label, used while a reply is being produced. */
@Composable
internal fun ChatPetWorking(
    modifier: Modifier = Modifier,
    label: String = "Working",
    petSize: Dp = 34.dp,
    fontSize: TextUnit = 15.sp
) {
    val transition = rememberInfiniteTransition(label = "pet-working")
    val pulse by transition.animateFloat(.45f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing),
        RepeatMode.Reverse), label = "pet-working-pulse")
    Row(modifier.testTag("chat_pet_working"), verticalAlignment = Alignment.CenterVertically) {
        ChatPet(size = petSize, working = true)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color(0xFFB4B9C4), fontSize = fontSize, fontWeight = FontWeight.Medium,
            modifier = Modifier.graphicsLayer { alpha = pulse })
    }
}
