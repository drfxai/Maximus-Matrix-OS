package ai.drfx.maximus.matrixai.news

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*

object NewsGraphics {

    @Composable
    fun FedPowellHeroGraphic(modifier: Modifier = Modifier) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Dark cyber-blue deep space background
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070B18),
                        Color(0xFF0F1A35),
                        Color(0xFF0A1226)
                    )
                )
            )

            // 2. Glowing Candlestick Chart background
            val candlePaints = listOf(
                Color(0x5500E5FF), Color(0x667C4DFF), Color(0x5500E676), Color(0x5500E5FF)
            )
            for (i in 0..12) {
                val cx = w * (0.08f + i * 0.075f)
                val base = h * 0.42f + sin(i * 0.8f) * 45f
                val barH = 30f + ((i * 13) % 40)
                drawLine(
                    color = candlePaints[i % candlePaints.size],
                    start = Offset(cx, base - barH * 0.7f),
                    end = Offset(cx, base + barH * 0.7f),
                    strokeWidth = 2f
                )
                drawRect(
                    color = candlePaints[i % candlePaints.size],
                    topLeft = Offset(cx - 5f, base - barH * 0.4f),
                    size = Size(10f, barH * 0.8f)
                )
            }

            // 3. Cyber grid lines and luminous horizon
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x66448AFF), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.35f),
                    radius = w * 0.6f
                )
            )

            // 4. Stylized Profile Silhouette / Portrait of Fed Chair
            val headCenter = Offset(w * 0.5f, h * 0.36f)
            // Shoulders & Suit
            val suitPath = Path().apply {
                moveTo(w * 0.15f, h * 0.75f)
                lineTo(w * 0.35f, h * 0.52f)
                lineTo(w * 0.65f, h * 0.52f)
                lineTo(w * 0.85f, h * 0.75f)
                lineTo(w * 0.85f, h)
                lineTo(w * 0.15f, h)
                close()
            }
            drawPath(
                path = suitPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                )
            )

            // Shirt collar & tie
            val tiePath = Path().apply {
                moveTo(w * 0.46f, h * 0.54f)
                lineTo(w * 0.54f, h * 0.54f)
                lineTo(w * 0.52f, h * 0.74f)
                lineTo(w * 0.48f, h * 0.74f)
                close()
            }
            drawPath(tiePath, color = Color(0xFF1D4ED8))

            // Head silhouette with silver hair
            drawCircle(
                color = Color(0xFFE2E8F0),
                radius = w * 0.17f,
                center = headCenter
            )
            // Silver hair contour
            drawArc(
                color = Color(0xFF94A3B8),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(headCenter.x - w * 0.18f, headCenter.y - w * 0.20f),
                size = Size(w * 0.36f, w * 0.32f)
            )
            // Glasses frame
            drawRoundRect(
                color = Color(0xFF334155),
                topLeft = Offset(w * 0.42f, h * 0.34f),
                size = Size(w * 0.16f, h * 0.04f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                style = Stroke(2.5f)
            )

            // 5. Stylized Federal Reserve Seal at Bottom
            val sealCenter = Offset(w * 0.5f, h * 0.78f)
            val sealRadius = w * 0.26f

            // Outer Seal Ring
            drawCircle(
                color = Color(0xF00F172A),
                radius = sealRadius,
                center = sealCenter
            )
            drawCircle(
                color = Color(0xFF60A5FA),
                radius = sealRadius,
                center = sealCenter,
                style = Stroke(2.5f)
            )
            drawCircle(
                color = Color(0x8893C5FD),
                radius = sealRadius * 0.85f,
                center = sealCenter,
                style = Stroke(1.2f)
            )

            // Stars around perimeter
            for (i in 0 until 12) {
                val angle = i * (PI.toFloat() * 2f / 12f)
                val starX = sealCenter.x + cos(angle) * (sealRadius * 0.92f)
                val starY = sealCenter.y + sin(angle) * (sealRadius * 0.92f)
                drawCircle(Color(0xFFBFDBFE), radius = 2f, center = Offset(starX, starY))
            }

            // Eagle crest in center of seal
            val crestPath = Path().apply {
                moveTo(sealCenter.x, sealCenter.y - sealRadius * 0.55f)
                lineTo(sealCenter.x + sealRadius * 0.45f, sealCenter.y - sealRadius * 0.1f)
                lineTo(sealCenter.x + sealRadius * 0.25f, sealCenter.y + sealRadius * 0.45f)
                lineTo(sealCenter.x - sealRadius * 0.25f, sealCenter.y + sealRadius * 0.45f)
                lineTo(sealCenter.x - sealRadius * 0.45f, sealCenter.y - sealRadius * 0.1f)
                close()
            }
            drawPath(
                path = crestPath,
                color = Color(0x333B82F6)
            )
            drawPath(
                path = crestPath,
                color = Color(0xFF93C5FD),
                style = Stroke(1.5f)
            )
        }
    }

    @Composable
    fun GoldBarsGraphic(modifier: Modifier = Modifier) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Background
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF332005), Color(0xFF0F0A02)),
                    radius = w
                )
            )

            // Gold Bar 1 (Base)
            val bar1 = Path().apply {
                moveTo(w * 0.12f, h * 0.75f)
                lineTo(w * 0.88f, h * 0.75f)
                lineTo(w * 0.78f, h * 0.42f)
                lineTo(w * 0.22f, h * 0.42f)
                close()
            }
            drawPath(
                path = bar1,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD700), Color(0xFFB8860B), Color(0xFFFFE066)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            // Gold Bar 2 (Top Stack)
            val bar2 = Path().apply {
                moveTo(w * 0.25f, h * 0.48f)
                lineTo(w * 0.75f, h * 0.48f)
                lineTo(w * 0.68f, h * 0.22f)
                lineTo(w * 0.32f, h * 0.22f)
                close()
            }
            drawPath(
                path = bar2,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF099), Color(0xFFFFC107), Color(0xFFD4AF37)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            // Metallic gleam highlight
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(w * 0.35f, h * 0.24f),
                end = Offset(w * 0.65f, h * 0.24f),
                strokeWidth = 2.5f
            )
        }
    }

    @Composable
    fun EuFlagGraphic(modifier: Modifier = Modifier) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // European Blue banner
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF003399), Color(0xFF001A4D))
                )
            )

            // Circle of 12 Golden Stars
            val center = Offset(w * 0.5f, h * 0.5f)
            val circleRadius = min(w, h) * 0.32f
            for (i in 0 until 12) {
                val angle = i * (PI.toFloat() * 2f / 12f)
                val starCenter = Offset(
                    center.x + cos(angle) * circleRadius,
                    center.y + sin(angle) * circleRadius
                )
                drawCircle(Color(0xFFFFCC00), radius = 3.5f, center = starCenter)
            }
        }
    }

    @Composable
    fun BtcCoinGraphic(modifier: Modifier = Modifier) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Background
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2A1B03), Color(0xFF0B0701)),
                    radius = w
                )
            )

            val center = Offset(w * 0.5f, h * 0.5f)
            val radius = min(w, h) * 0.38f

            // Bitcoin gold coin rim
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD700), Color(0xFFB8860B), Color(0xFFFFA000))
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color(0xFF0F0A02),
                radius = radius * 0.88f,
                center = center
            )
            drawCircle(
                color = Color(0xFFFFD700),
                radius = radius * 0.86f,
                center = center,
                style = Stroke(2f)
            )

            // Stylized 'B' with twin vertical spines
            val spineX1 = center.x - radius * 0.18f
            val spineX2 = center.x + radius * 0.05f
            val topY = center.y - radius * 0.55f
            val botY = center.y + radius * 0.55f

            drawLine(Color(0xFFFFD700), Offset(spineX1, topY), Offset(spineX1, botY), strokeWidth = 3f)
            drawLine(Color(0xFFFFD700), Offset(spineX2, topY), Offset(spineX2, botY), strokeWidth = 3f)

            // B lobes
            drawArc(
                color = Color(0xFFFFD700),
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.15f, center.y - radius * 0.45f),
                size = Size(radius * 0.55f, radius * 0.42f),
                style = Stroke(3.5f)
            )
            drawArc(
                color = Color(0xFFFFD700),
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.15f, center.y - radius * 0.05f),
                size = Size(radius * 0.62f, radius * 0.46f),
                style = Stroke(3.5f)
            )
        }
    }
}
