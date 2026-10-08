package ai.drfx.maximus.matrixai.news

import java.util.UUID

enum class AlertUrgency(val label: String, val colorHex: Long) {
    CRITICAL("CRITICAL BREAKING", 0xFFFF1744),
    HIGH("HIGH IMPACT", 0xFFFF9100),
    ELEVATED("ELEVATED", 0xFF00E5FF),
    INFORMATIONAL("INTEL", 0xFFB388FF)
}

data class MarketAlertNotification(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val assetSymbol: String,
    val headline: String,
    val aiReasoning: String,
    val urgency: AlertUrgency,
    val sentiment: MarketSentiment,
    val triggerSource: String = "Forex Factory AI",
    val estimatedVolatilityPips: String = "High",
    val isRead: Boolean = false
)

data class WatchlistItem(
    val symbol: String,
    val name: String,
    val category: NewsCategory,
    val isEnabled: Boolean = true
)

data class WatchlistAlertConfig(
    val enabledAssets: Set<String> = setOf("XAUUSD", "EURUSD", "BTC", "DXY"),
    val minImpact: MarketImpact = MarketImpact.MEDIUM,
    val systemNotificationsEnabled: Boolean = true,
    val soundVibrateEnabled: Boolean = true
)
