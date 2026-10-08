package ai.drfx.maximus.matrixai.news

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.drfx.maximus.matrixai.MainActivity
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.database.repository.UserSessionRepository
import ai.drfx.maximus.matrixai.logging.AppLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class MarketNotificationService(
    private val context: Context,
    private val sessionRepo: UserSessionRepository
) {
    companion object {
        const val CHANNEL_ID = "drfx_market_intelligence_alerts"
        const val CHANNEL_NAME = "DrFX Market Intelligence Alerts"
        const val KEY_WATCHLIST = "news_watchlist_symbols"
        const val KEY_NOTIF_SETTINGS = "news_alert_settings"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Default institutional watchlist items
    val availableWatchlistAssets = listOf(
        WatchlistItem("XAUUSD", "Gold Spot / US Dollar", NewsCategory.GOLD),
        WatchlistItem("EURUSD", "Euro / US Dollar", NewsCategory.FOREX),
        WatchlistItem("BTC", "Bitcoin / USD", NewsCategory.CRYPTO),
        WatchlistItem("DXY", "US Dollar Currency Index", NewsCategory.MACRO),
        WatchlistItem("GBPUSD", "British Pound / USD", NewsCategory.FOREX),
        WatchlistItem("USDJPY", "US Dollar / Japanese Yen", NewsCategory.CENTRAL_BANKS),
        WatchlistItem("US10Y", "US 10-Year Treasury Yield", NewsCategory.MACRO),
        WatchlistItem("ETH", "Ethereum / USD", NewsCategory.CRYPTO),
        WatchlistItem("SPX", "S&P 500 Index", NewsCategory.MACRO),
        WatchlistItem("WTI", "Crude Oil Futures", NewsCategory.MACRO)
    )

    private val _activeWatchlist = MutableStateFlow<Set<String>>(setOf("XAUUSD", "EURUSD", "BTC", "DXY"))
    val activeWatchlist: StateFlow<Set<String>> = _activeWatchlist.asStateFlow()

    private val _recentAlerts = MutableStateFlow<List<MarketAlertNotification>>(emptyList())
    val recentAlerts: StateFlow<List<MarketAlertNotification>> = _recentAlerts.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    init {
        createNotificationChannel()
    }

    suspend fun loadSavedState() {
        try {
            val savedJson = sessionRepo.get(KEY_WATCHLIST)
            if (!savedJson.isNullOrBlank()) {
                val array = JSONArray(savedJson)
                val set = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    set.add(array.getString(i))
                }
                if (set.isNotEmpty()) {
                    _activeWatchlist.value = set
                }
            } else {
                // Initialize defaults
                saveWatchlist(_activeWatchlist.value)
            }

            val savedSettings = sessionRepo.get(KEY_NOTIF_SETTINGS)
            if (!savedSettings.isNullOrBlank()) {
                val obj = JSONObject(savedSettings)
                _notificationsEnabled.value = obj.optBoolean("enabled", true)
            }

            // Provide default initial alerts for user watchlist
            if (_recentAlerts.value.isEmpty()) {
                _recentAlerts.value = listOf(
                    MarketAlertNotification(
                        assetSymbol = "XAUUSD",
                        headline = "Fed Powell Hawkish Stance Triggers Gold Liquidity Sweep",
                        aiReasoning = "AI detected aggressive stop runs below $2,320 due to sticky PCE figures. Non-yielding assets facing immediate yield pressure.",
                        urgency = AlertUrgency.HIGH,
                        sentiment = MarketSentiment.BEARISH,
                        triggerSource = "Forex Factory AI",
                        estimatedVolatilityPips = "± 45 Pips"
                    ),
                    MarketAlertNotification(
                        assetSymbol = "EURUSD",
                        headline = "ECB Lagarde Signals Impending Rate Cut Divergence",
                        aiReasoning = "Policy divergence widening against US Federal Reserve. High probability of downside continuation towards 1.0750 liquidity pool.",
                        urgency = AlertUrgency.ELEVATED,
                        sentiment = MarketSentiment.BEARISH,
                        triggerSource = "Forex Factory AI",
                        estimatedVolatilityPips = "± 30 Pips"
                    ),
                    MarketAlertNotification(
                        assetSymbol = "BTC",
                        headline = "Bitcoin Absorbs Macro Headwinds Above $66,000",
                        aiReasoning = "Institutional spot buyers defending 200-period EMA despite rising 10-year Treasury yields.",
                        urgency = AlertUrgency.INFORMATIONAL,
                        sentiment = MarketSentiment.NEUTRAL,
                        triggerSource = "Forex Factory AI",
                        estimatedVolatilityPips = "± $1,200"
                    )
                )
            }
        } catch (e: Exception) {
            AppLogStore.warn("NOTIF", "Failed to load watchlist: ${e.message}")
        }
    }

    suspend fun toggleWatchlistAsset(symbol: String) {
        val current = _activeWatchlist.value.toMutableSet()
        if (current.contains(symbol)) {
            if (current.size > 1) { // keep at least one
                current.remove(symbol)
            }
        } else {
            current.add(symbol)
        }
        _activeWatchlist.value = current
        saveWatchlist(current)
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        try {
            val obj = JSONObject().put("enabled", enabled)
            sessionRepo.set(KEY_NOTIF_SETTINGS, obj.toString(), "news_settings")
        } catch (e: Exception) {
            AppLogStore.warn("NOTIF", "Failed to save settings: ${e.message}")
        }
    }

    private suspend fun saveWatchlist(set: Set<String>) {
        try {
            val array = JSONArray()
            set.forEach { array.put(it) }
            sessionRepo.set(KEY_WATCHLIST, array.toString(), "news_settings")
        } catch (e: Exception) {
            AppLogStore.warn("NOTIF", "Failed to persist watchlist: ${e.message}")
        }
    }

    fun addAlert(alert: MarketAlertNotification) {
        val list = _recentAlerts.value.toMutableList()
        list.add(0, alert)
        _recentAlerts.value = list.take(20)

        // Trigger system notification if enabled and permitted
        if (_notificationsEnabled.value) {
            postSystemNotification(alert)
        }
    }

    fun clearAlerts() {
        _recentAlerts.value = emptyList()
    }

    fun markAllAsRead() {
        _recentAlerts.value = _recentAlerts.value.map { it.copy(isRead = true) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time AI alerts for major market-moving catalysts matching trader watchlist."
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun postSystemNotification(alert: MarketAlertNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                alert.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.maximus_ai_icon)
                .setContentTitle("🚨 [${alert.assetSymbol}] ${alert.urgency.label}: ${alert.headline}")
                .setContentText(alert.aiReasoning)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${alert.aiReasoning}\n\nEstimated Volatility: ${alert.estimatedVolatilityPips} | Sentiment: ${alert.sentiment.displayName}")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            notificationManager.notify(alert.id.hashCode(), builder.build())
        } catch (e: Exception) {
            AppLogStore.warn("NOTIF", "Failed to trigger system notification: ${e.message}")
        }
    }
}
