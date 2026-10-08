package ai.drfx.maximus.matrixai.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SignalDirection {
    BUY,
    SELL,
    NEUTRAL
}

enum class SignalStatus {
    ACTIVE,
    TP1_HIT,
    TP2_HIT,
    STOPPED_OUT,
    EXPIRED,
    CANCELLED
}

enum class SignalTimeframe {
    M1,
    M5,
    M15,
    H1,
    H4,
    D1
}

enum class SignalType {
    SCALPING,
    INTRADAY,
    SWING,
    BREAKOUT,
    ORDER_BLOCK,
    MOMENTUM
}

@Entity(tableName = "live_trading_signals")
data class TradingSignalEntity(
    @PrimaryKey val id: String,
    val symbol: String,             // e.g. "XAU/USD", "BTC/USDT", "EUR/USD", "NVDA"
    val assetClass: String,         // "Crypto", "Forex", "Commodities", "Indices", "Equities"
    val direction: String,          // "BUY" or "SELL"
    val signalType: String,         // "SCALPING", "INTRADAY", "BREAKOUT", "ORDER_BLOCK"
    val timeframe: String,          // "M5", "M15", "H1"
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double? = null,
    val currentPrice: Double,
    val status: String,             // "ACTIVE", "TP1_HIT", "TP2_HIT", "STOPPED_OUT"
    val winProbability: Int,        // e.g. 87%
    val riskRewardRatio: String,    // e.g. "1:3.2"
    val strategyName: String,       // e.g. "Maximus FVG Matrix Scalper"
    val confluenceFactors: String,  // JSON or comma separated e.g. "FVG retest, RSI divergence, 200 EMA bounce"
    val aiRationale: String,        // Deep AI reasoning explanation
    val authorAgent: String,        // e.g. "Maximus Quant Alpha Agent"
    val timestampMs: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false,
    val isCustomUserSignal: Boolean = false
)
