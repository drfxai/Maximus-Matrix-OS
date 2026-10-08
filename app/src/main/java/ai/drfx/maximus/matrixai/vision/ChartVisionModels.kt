package ai.drfx.maximus.matrixai.vision

import java.util.UUID

enum class TrendDirection(val displayName: String, val symbol: String) {
    BULLISH("Bullish Trend", "↗"),
    BEARISH("Bearish Trend", "↘"),
    RANGING("Sideways / Ranging", "↔")
}

enum class TrendStrength {
    STRONG,
    MODERATE,
    WEAK
}

data class ChartTrendline(
    val type: String,
    val description: String,
    val angle: String = "",
    val significance: String = "Major"
)

data class ChartPatternItem(
    val name: String,
    val patternType: String,
    val confidencePercent: Int,
    val status: String,
    val implication: String
)

data class PriceZone(
    val level: String,
    val type: String,
    val description: String = ""
)

data class CandleSignal(
    val name: String,
    val location: String,
    val implication: String
)

data class TradePlan(
    val bias: String,
    val entryZone: String,
    val stopLoss: String,
    val target1: String,
    val target2: String = "",
    val riskRewardRatio: String,
    val invalidationReason: String = ""
)

data class ChartVisionAnalysis(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val modelUsed: String = "gemini-3.8-flash",
    val assetIdentifier: String = "Unspecified Asset",
    val timeframeEstimate: String = "Not detected",
    val direction: TrendDirection = TrendDirection.BULLISH,
    val trendStrength: TrendStrength = TrendStrength.STRONG,
    val trendSummary: String = "",
    val trendlines: List<ChartTrendline> = emptyList(),
    val patterns: List<ChartPatternItem> = emptyList(),
    val keyLevels: List<PriceZone> = emptyList(),
    val candleSignals: List<CandleSignal> = emptyList(),
    val tradePlan: TradePlan = TradePlan("NEUTRAL", "-", "-", "-", "-", "1:1"),
    val comprehensiveReport: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val processingMs: Long = 0L,
    val imageBase64: String? = null
) {
    fun toShareableReport(): String {
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(timestampMs))

        return buildString {
            appendLine("══════════════════════════════════════════════════")
            appendLine(" MAXIMUS AI · CHART VISION INTELLIGENCE REPORT")
            appendLine("══════════════════════════════════════════════════")
            appendLine("Asset: $assetIdentifier | Timeframe: $timeframeEstimate")
            appendLine("Engine: $modelUsed | Timestamp: $dateStr")
            appendLine()
            appendLine("📊 MARKET STRUCTURE & TREND")
            appendLine("• Direction: ${direction.symbol} ${direction.displayName} (${trendStrength.name})")
            if (trendSummary.isNotBlank()) appendLine("• Summary: $trendSummary")
            appendLine()
            if (patterns.isNotEmpty()) {
                appendLine("🔮 DETECTED CHART PATTERNS")
                patterns.forEach { p ->
                    appendLine("• ${p.name} [${p.patternType}]: ${p.confidencePercent}% Confidence (${p.status})")
                    if (p.implication.isNotBlank()) appendLine("  → ${p.implication}")
                }
                appendLine()
            }
            if (trendlines.isNotEmpty()) {
                appendLine("📐 KEY TRENDLINES & CHANNELS")
                trendlines.forEach { t ->
                    val angleText = if (t.angle.isNotBlank()) " [Angle: ${t.angle}]" else ""
                    appendLine("• ${t.type}$angleText: ${t.description} (${t.significance})")
                }
                appendLine()
            }
            appendLine("🎯 ACTIONABLE TRADE SETUP")
            appendLine("• Bias: ${tradePlan.bias}")
            appendLine("• Entry Zone: ${tradePlan.entryZone}")
            appendLine("• Stop Loss: ${tradePlan.stopLoss}")
            appendLine("• Target 1: ${tradePlan.target1}")
            if (tradePlan.target2.isNotBlank()) appendLine("• Target 2: ${tradePlan.target2}")
            appendLine("• Risk/Reward Ratio: ${tradePlan.riskRewardRatio}")
            if (tradePlan.invalidationReason.isNotBlank()) {
                appendLine("• Invalidation: ${tradePlan.invalidationReason}")
            }
            appendLine()
            if (keyLevels.isNotEmpty()) {
                appendLine("🛡️ KEY SUPPORT & RESISTANCE ZONES")
                keyLevels.forEach { lvl ->
                    val desc = if (lvl.description.isNotBlank()) ": " + lvl.description else ""
                    appendLine("• ${lvl.level} (${lvl.type})$desc")
                }
                appendLine()
            }
            if (candleSignals.isNotEmpty()) {
                appendLine("🕯️ CANDLESTICK SIGNALS")
                candleSignals.forEach { c ->
                    appendLine("• ${c.name} (${c.location}): ${c.implication}")
                }
                appendLine()
            }
            appendLine("══════════════════════════════════════════════════")
            appendLine("Generated with MAXIMUS AI · DrFXAi Matrix OS")
            appendLine("══════════════════════════════════════════════════")
        }
    }
}

sealed interface ChartVisionUiState {
    data object Idle : ChartVisionUiState
    data class Analyzing(
        val stage: String,
        val progressPercent: Float,
        val assetHint: String? = null
    ) : ChartVisionUiState
    data class Success(
        val analysis: ChartVisionAnalysis
    ) : ChartVisionUiState
    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : ChartVisionUiState
}

enum class SampleChartPreset(
    val title: String,
    val subtitle: String,
    val asset: String,
    val timeframe: String,
    val patternTheme: String
) {
    BTC_ASCENDING_TRIANGLE(
        title = "BTC/USDT Breakout",
        subtitle = "Ascending Triangle & Volume Expansion",
        asset = "BTC/USDT",
        timeframe = "4H",
        patternTheme = "Ascending Triangle"
    ),
    EUR_DOUBLE_BOTTOM(
        title = "EUR/USD Reversal",
        subtitle = "W-Bottom at Key Weekly Demand Zone",
        asset = "EUR/USD",
        timeframe = "1D",
        patternTheme = "Double Bottom"
    ),
    NVDA_BULL_FLAG(
        title = "NVDA Momentum Flag",
        subtitle = "High-Tight Flag above 20 EMA",
        asset = "NVDA",
        timeframe = "1H",
        patternTheme = "Bull Flag"
    )
}
