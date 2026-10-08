package ai.drfx.maximus.matrixai.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.random.Random

object SampleChartGenerator {

    data class Candle(
        val open: Float,
        val high: Float,
        val low: Float,
        val close: Float,
        val volume: Float
    )

    fun generatePresetBitmap(preset: SampleChartPreset, width: Int = 1000, height: Int = 650): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val bgPaint = Paint().apply { color = Color.parseColor("#0B0E14") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Generate synthetic candles matching pattern
        val candles = generateCandleSeries(preset, count = 42)
        val minPrice = candles.minOf { it.low } * 0.992f
        val maxPrice = candles.maxOf { it.high } * 1.008f
        val priceRange = (maxPrice - minPrice).coerceAtLeast(1f)

        val chartTop = 80f
        val chartBottom = height - 120f
        val chartLeft = 50f
        val chartRight = width - 110f
        val chartHeight = chartBottom - chartTop
        val chartWidth = chartRight - chartLeft

        // Grid lines
        val gridPaint = Paint().apply {
            color = Color.parseColor("#1C2333")
            strokeWidth = 1.2f
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#8B949E")
            textSize = 22f
            isAntiAlias = true
        }

        // Horizontal price grid (5 levels)
        for (i in 0..4) {
            val y = chartTop + (chartHeight * i / 4f)
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            val priceVal = maxPrice - ((priceRange * i) / 4f)
            val priceStr = String.format("%.2f", priceVal)
            canvas.drawText(priceStr, chartRight + 12f, y + 8f, textPaint)
        }

        // Vertical time grid (5 levels)
        for (i in 0..5) {
            val x = chartLeft + (chartWidth * i / 5f)
            canvas.drawLine(x, chartTop, x, chartBottom, gridPaint)
        }

        // Draw volume base
        val volTop = height - 100f
        val maxVol = candles.maxOf { it.volume }.coerceAtLeast(1f)

        val candleStep = chartWidth / candles.size
        val candleWidth = candleStep * 0.65f

        val greenPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            isAntiAlias = true
        }
        val redPaint = Paint().apply {
            color = Color.parseColor("#FF5252")
            isAntiAlias = true
        }
        val wickGreenPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        val wickRedPaint = Paint().apply {
            color = Color.parseColor("#FF5252")
            strokeWidth = 2.5f
            isAntiAlias = true
        }

        fun priceToY(price: Float): Float {
            val norm = (price - minPrice) / priceRange
            return chartBottom - (norm * chartHeight)
        }

        val maPath = Path()
        var hasMaStart = false

        // Draw Candlesticks & Volume
        candles.forEachIndexed { index, candle ->
            val cx = chartLeft + (index + 0.5f) * candleStep
            val yOpen = priceToY(candle.open)
            val yClose = priceToY(candle.close)
            val yHigh = priceToY(candle.high)
            val yLow = priceToY(candle.low)

            val isBullish = candle.close >= candle.open
            val paint = if (isBullish) greenPaint else redPaint
            val wickPaint = if (isBullish) wickGreenPaint else wickRedPaint

            // Wicks
            canvas.drawLine(cx, yHigh, cx, yLow, wickPaint)

            // Body
            val topBody = minOf(yOpen, yClose)
            val bottomBody = maxOf(yOpen, yClose).coerceAtLeast(topBody + 2f)
            val left = cx - candleWidth / 2f
            val right = cx + candleWidth / 2f
            canvas.drawRect(left, topBody, right, bottomBody, paint)

            // Volume bar
            val volHeight = (candle.volume / maxVol) * 60f
            val volPaint = Paint().apply {
                color = if (isBullish) Color.parseColor("#3300E676") else Color.parseColor("#33FF5252")
            }
            canvas.drawRect(left, height - 30f - volHeight, right, height - 30f, volPaint)

            // Moving average trace
            if (index >= 3) {
                val window = candles.subList(maxOf(0, index - 4), index + 1)
                val avg = window.map { it.close }.average().toFloat()
                val maY = priceToY(avg)
                if (!hasMaStart) {
                    maPath.moveTo(cx, maY)
                    hasMaStart = true
                } else {
                    maPath.lineTo(cx, maY)
                }
            }
        }

        // Draw Moving Average (Cyan Line)
        val maStrokePaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawPath(maPath, maStrokePaint)

        // Draw Trendline annotation according to preset
        when (preset) {
            SampleChartPreset.BTC_ASCENDING_TRIANGLE -> {
                // Flat resistance line at top
                val resY = priceToY(candles.takeLast(20).maxOf { it.high } * 0.999f)
                val linePaint = Paint().apply {
                    color = Color.parseColor("#FFD600")
                    strokeWidth = 3.5f
                    pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
                    isAntiAlias = true
                }
                canvas.drawLine(chartLeft + chartWidth * 0.35f, resY, chartRight, resY, linePaint)

                // Ascending trendline (higher lows)
                val p1X = chartLeft + chartWidth * 0.35f
                val p1Y = priceToY(candles[14].low)
                val p2X = chartRight - candleStep * 2
                val p2Y = priceToY(candles.last().low * 1.002f)
                val trendPaint = Paint().apply {
                    color = Color.parseColor("#00E676")
                    strokeWidth = 3.5f
                    isAntiAlias = true
                }
                canvas.drawLine(p1X, p1Y, p2X, p2Y, trendPaint)
            }
            SampleChartPreset.EUR_DOUBLE_BOTTOM -> {
                // Support neckline line
                val suppY = priceToY(candles.minOf { it.low } * 1.002f)
                val linePaint = Paint().apply {
                    color = Color.parseColor("#00E676")
                    strokeWidth = 3.5f
                    isAntiAlias = true
                }
                canvas.drawLine(chartLeft + 50f, suppY, chartRight, suppY, linePaint)
            }
            SampleChartPreset.NVDA_BULL_FLAG -> {
                // Parallel channel lines
                val flagPaint = Paint().apply {
                    color = Color.parseColor("#FF9100")
                    strokeWidth = 3f
                    pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
                    isAntiAlias = true
                }
                val top1X = chartLeft + chartWidth * 0.55f
                val top1Y = priceToY(candles[24].high)
                val top2X = chartRight
                val top2Y = priceToY(candles.last().high)
                canvas.drawLine(top1X, top1Y, top2X, top2Y, flagPaint)
            }
        }

        // Header Title & Asset Info
        val headerPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subHeaderPaint = Paint().apply {
            color = Color.parseColor("#58A6FF")
            textSize = 20f
            isAntiAlias = true
        }

        canvas.drawText("${preset.asset} · ${preset.timeframe} · BINANCE", chartLeft, 44f, headerPaint)
        canvas.drawText("Pattern: ${preset.patternTheme} (EMA 20 Visible)", chartLeft + 360f, 44f, subHeaderPaint)

        // Watermark in bottom corner
        val watermarkPaint = Paint().apply {
            color = Color.parseColor("#448B949E")
            textSize = 18f
            isAntiAlias = true
        }
        canvas.drawText("MAXIMUS AI · CHART VISION SCAN", chartLeft, height - 12f, watermarkPaint)

        return bitmap
    }

    private fun generateCandleSeries(preset: SampleChartPreset, count: Int): List<Candle> {
        val list = mutableListOf<Candle>()
        val rnd = Random(preset.ordinal * 1337 + 42)

        var current = when (preset) {
            SampleChartPreset.BTC_ASCENDING_TRIANGLE -> 91400f
            SampleChartPreset.EUR_DOUBLE_BOTTOM -> 1.0820f
            SampleChartPreset.NVDA_BULL_FLAG -> 138.50f
        }

        for (i in 0 until count) {
            val drift = when (preset) {
                SampleChartPreset.BTC_ASCENDING_TRIANGLE -> {
                    // Gradual push towards resistance with higher lows
                    if (i < 15) (rnd.nextFloat() - 0.45f) * (current * 0.006f)
                    else (rnd.nextFloat() - 0.35f) * (current * 0.007f)
                }
                SampleChartPreset.EUR_DOUBLE_BOTTOM -> {
                    // Dip, bounce, dip again (double bottom), strong bounce
                    if (i in 5..12) -(current * 0.003f)
                    else if (i in 13..20) (current * 0.003f)
                    else if (i in 21..28) -(current * 0.003f)
                    else (current * 0.004f)
                }
                SampleChartPreset.NVDA_BULL_FLAG -> {
                    // Huge flagpole early, tight consolidation downward, breakout
                    if (i < 14) (current * 0.009f)
                    else if (i < 34) -(current * 0.002f)
                    else (current * 0.008f)
                }
            }

            val open = current
            val change = drift + ((rnd.nextFloat() - 0.5f) * (current * 0.003f))
            val close = open + change
            val wickUp = rnd.nextFloat() * (current * 0.003f)
            val wickDown = rnd.nextFloat() * (current * 0.003f)
            val high = maxOf(open, close) + wickUp
            val low = minOf(open, close) - wickDown
            val volume = 1000f + rnd.nextFloat() * 4000f + (if (i > count - 4) 3500f else 0f)

            list.add(Candle(open, high, low, close, volume))
            current = close
        }
        return list
    }
}
