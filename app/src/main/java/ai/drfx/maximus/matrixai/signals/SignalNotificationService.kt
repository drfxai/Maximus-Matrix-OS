package ai.drfx.maximus.matrixai.signals

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.drfx.maximus.matrixai.MainActivity
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.database.entities.TradingSignalEntity
import ai.drfx.maximus.matrixai.logging.AppLogStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

/**
 * Record of a high-confidence signal alert triggered via webhook.
 */
data class SignalAlertRecord(
    val id: String = UUID.randomUUID().toString().take(8),
    val signalId: String,
    val symbol: String,
    val direction: String,
    val winProbability: Int,
    val entryPrice: Double,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
    val soundPlayed: Boolean = false,
    val notificationPosted: Boolean = false,
    val strategyName: String = "",
    val rationale: String = ""
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

data class SignalNotificationSettings(
    val notificationsEnabled: Boolean = true,
    val soundAlertsEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val minConfidenceThreshold: Int = 80 // Signals with winProbability >= 80 trigger alerts
)

/**
 * Dedicated Local Notification & Audio Alert Service for High-Confidence Trading Signals.
 * Triggers instant Android push notifications and sound alerts whenever incoming webhook
 * signals exceed the configured confidence threshold (default >= 80%).
 */
class SignalNotificationService(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        const val CHANNEL_ID = "high_confidence_trading_signals_v2"
        const val CHANNEL_NAME = "⚡ High-Confidence Trading Signals"
        const val CHANNEL_DESC = "Push notifications and audio chimes for high-probability signals received via TradingView and Cloudflare webhooks"
        private const val PREFS_NAME = "signal_notification_prefs"
        private const val KEY_NOTIFS_ENABLED = "key_notifs_enabled"
        private const val KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_VIBRATE_ENABLED = "key_vibrate_enabled"
        private const val KEY_MIN_THRESHOLD = "key_min_threshold"
        private const val NOTIFICATION_ID_BASE = 88000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        SignalNotificationSettings(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFS_ENABLED, true),
            soundAlertsEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
            vibrateEnabled = prefs.getBoolean(KEY_VIBRATE_ENABLED, true),
            minConfidenceThreshold = prefs.getInt(KEY_MIN_THRESHOLD, 80)
        )
    )
    val settings: StateFlow<SignalNotificationSettings> = _settings.asStateFlow()

    private val _recentAlerts = MutableStateFlow<List<SignalAlertRecord>>(emptyList())
    val recentAlerts: StateFlow<List<SignalAlertRecord>> = _recentAlerts.asStateFlow()

    private val _lastTriggeredAlert = MutableStateFlow<SignalAlertRecord?>(null)
    val lastTriggeredAlert: StateFlow<SignalAlertRecord?> = _lastTriggeredAlert.asStateFlow()

    private val _totalAlertsTriggered = MutableStateFlow(0)
    val totalAlertsTriggered: StateFlow<Int> = _totalAlertsTriggered.asStateFlow()

    init {
        createNotificationChannel()
    }

    /**
     * Initializes the High-Importance Notification Channel with sound, vibration, and lights.
     */
    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundUri = runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }.getOrNull()
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableLights(true)
                    lightColor = Color.parseColor("#00E676")
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 180, 80, 240, 80, 320)
                    if (soundUri != null) {
                        setSound(soundUri, audioAttributes)
                    }
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager?.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            AppLogStore.warn("SIGNAL_NOTIF", "Failed to create channel: ${e.message}")
        }
    }

    /**
     * Inspects an incoming signal. If it qualifies as high-confidence (winProbability >= threshold)
     * and alerts are enabled, triggers push notification, sound chime, and haptics.
     *
     * @return true if high-confidence alert was triggered, false otherwise.
     */
    fun onSignalReceived(signal: TradingSignalEntity, source: String = "Webhook Gateway"): Boolean {
        val currentSettings = _settings.value
        val isHighConfidence = signal.winProbability >= currentSettings.minConfidenceThreshold

        if (!isHighConfidence) {
            AppLogStore.info(
                "SIGNAL_NOTIF",
                "Signal ${signal.symbol} ${signal.direction} (${signal.winProbability}%) below threshold (${currentSettings.minConfidenceThreshold}%). Notification skipped."
            )
            return false
        }

        if (!currentSettings.notificationsEnabled) {
            AppLogStore.info(
                "SIGNAL_NOTIF",
                "High-confidence signal received for ${signal.symbol}, but notifications are disabled in settings."
            )
            return false
        }

        var soundSuccess = false
        var notificationSuccess = false

        // 1. Play audible sound chime if sound alerts enabled
        if (currentSettings.soundAlertsEnabled) {
            soundSuccess = playAudioAlert()
        }

        // 2. Play haptic vibration if enabled
        if (currentSettings.vibrateEnabled) {
            triggerVibration()
        }

        // 3. Post system push notification
        notificationSuccess = postPushNotification(signal, source)

        // 4. Record alert history
        val record = SignalAlertRecord(
            signalId = signal.id,
            symbol = signal.symbol,
            direction = signal.direction,
            winProbability = signal.winProbability,
            entryPrice = signal.entryPrice,
            source = source,
            soundPlayed = soundSuccess,
            notificationPosted = notificationSuccess,
            strategyName = signal.strategyName,
            rationale = signal.aiRationale
        )

        _recentAlerts.update { (listOf(record) + it).take(30) }
        _lastTriggeredAlert.value = record
        _totalAlertsTriggered.update { it + 1 }

        AppLogStore.info(
            "SIGNAL_NOTIF",
            "⚡ High-Confidence Webhook Signal Alert triggered for ${signal.symbol} ${signal.direction} (${signal.winProbability}%). Notif: $notificationSuccess, Sound: $soundSuccess"
        )

        return true
    }

    /**
     * Posts a rich Android notification with key trade metrics.
     */
    private fun postPushNotification(signal: TradingSignalEntity, source: String): Boolean {
        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                AppLogStore.warn("SIGNAL_NOTIF", "POST_NOTIFICATIONS permission not granted. Notification omitted.")
                return false
            }
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAV_DESTINATION", "signals")
                putExtra("SIGNAL_ID", signal.id)
                putExtra("SIGNAL_SYMBOL", signal.symbol)
            }

            val notifId = NOTIFICATION_ID_BASE + (signal.id.hashCode() % 1000)
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isBuy = signal.direction.equals("BUY", ignoreCase = true)
            val dirEmoji = if (isBuy) "🟢 BUY" else "🔴 SELL"
            val accentColor = if (isBuy) Color.parseColor("#00E676") else Color.parseColor("#FF1744")

            val title = "⚡ HIGH CONFIDENCE (${signal.winProbability}%): ${signal.symbol} $dirEmoji"
            val shortText = "${signal.timeframe} ${signal.signalType} @ ${signal.entryPrice} • TP1: ${signal.takeProfit1} • SL: ${signal.stopLoss}"

            val targets = buildString {
                append("TP1: ${signal.takeProfit1}")
                append(" • TP2: ${signal.takeProfit2}")
                signal.takeProfit3?.let { append(" • TP3: $it") }
            }

            val expandedBody = """
                📊 Setup: ${signal.symbol} $dirEmoji [${signal.timeframe} - ${signal.signalType}]
                💵 Entry Price: ${signal.entryPrice}
                🎯 Targets: $targets
                🛑 Stop Loss: ${signal.stopLoss} (R:R ${signal.riskRewardRatio})
                ⚡ Win Probability: ${signal.winProbability}%
                📡 Source: $source (${signal.authorAgent})
                💡 Strategy: ${signal.strategyName}
                📝 Rationale: ${signal.aiRationale}
            """.trimIndent()

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_signal_alert)
                .setContentTitle(title)
                .setContentText(shortText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .setSummaryText("${signal.assetClass} • R:R ${signal.riskRewardRatio}")
                        .bigText(expandedBody)
                )
                .setColor(accentColor)
                .setColorized(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 180, 80, 240, 80, 320))
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)

            notificationManager?.notify(notifId, builder.build())
            return true
        } catch (e: Exception) {
            AppLogStore.error("SIGNAL_NOTIF", "Failed to post notification: ${e.message}")
            return false
        }
    }

    /**
     * Plays an audible notification alert chime.
     * Uses system notification ringtone with ToneGenerator fallback.
     */
    fun playAudioAlert(): Boolean {
        return try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, soundUri)
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                ringtone.play()
                true
            } else {
                playToneGeneratorFallback()
            }
        } catch (e: Exception) {
            playToneGeneratorFallback()
        }
    }

    private fun playToneGeneratorFallback(): Boolean {
        return try {
            scope.launch(Dispatchers.IO) {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
                delay(400)
                toneGen.release()
            }
            true
        } catch (e: Exception) {
            AppLogStore.warn("SIGNAL_NOTIF", "Could not play ToneGenerator: ${e.message}")
            false
        }
    }

    /**
     * Triggers tactile vibration pulse.
     */
    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.let { vibrator ->
                    val effect = VibrationEffect.createWaveform(
                        longArrayOf(0, 150, 80, 200),
                        intArrayOf(0, 255, 0, 255),
                        -1
                    )
                    vibrator.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 200), -1)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 150, 80, 200), -1)
                }
            }
        } catch (e: Exception) {
            AppLogStore.warn("SIGNAL_NOTIF", "Vibration failed: ${e.message}")
        }
    }

    /**
     * Immediately triggers a realistic test high-confidence signal alert
     * to verify both push notification and sound alert.
     */
    fun triggerTestAlert(confidence: Int = 92): Boolean {
        val testSignal = TradingSignalEntity(
            id = "test_alert_${System.currentTimeMillis()}",
            symbol = "BTC/USDT",
            assetClass = "Crypto",
            direction = "BUY",
            signalType = "BREAKOUT",
            timeframe = "M15",
            entryPrice = 67420.50,
            stopLoss = 66800.00,
            takeProfit1 = 68500.00,
            takeProfit2 = 69800.00,
            takeProfit3 = 71200.00,
            currentPrice = 67450.00,
            status = "ACTIVE",
            winProbability = confidence,
            riskRewardRatio = "1:3.8",
            strategyName = "Maximus Alpha Momentum Breakout",
            confluenceFactors = "Bullish Order Block, 4H Trend Alignment, Liquidity Sweep",
            aiRationale = "Massive volume influx following Wyckoff re-accumulation phase with confirmed multi-timeframe divergence.",
            authorAgent = "TradingView Webhook Live Test",
            timestampMs = System.currentTimeMillis()
        )
        return onSignalReceived(testSignal, "TradingView Test Webhook")
    }

    /**
     * Updates notification settings and persists to SharedPreferences.
     */
    fun updateSettings(
        enabled: Boolean = _settings.value.notificationsEnabled,
        sound: Boolean = _settings.value.soundAlertsEnabled,
        vibrate: Boolean = _settings.value.vibrateEnabled,
        minConfidence: Int = _settings.value.minConfidenceThreshold
    ) {
        val updated = SignalNotificationSettings(
            notificationsEnabled = enabled,
            soundAlertsEnabled = sound,
            vibrateEnabled = vibrate,
            minConfidenceThreshold = minConfidence.coerceIn(50, 99)
        )
        _settings.value = updated

        prefs.edit()
            .putBoolean(KEY_NOTIFS_ENABLED, updated.notificationsEnabled)
            .putBoolean(KEY_SOUND_ENABLED, updated.soundAlertsEnabled)
            .putBoolean(KEY_VIBRATE_ENABLED, updated.vibrateEnabled)
            .putInt(KEY_MIN_THRESHOLD, updated.minConfidenceThreshold)
            .apply()

        AppLogStore.info(
            "SIGNAL_NOTIF",
            "Settings updated: enabled=$enabled, sound=$sound, vibrate=$vibrate, minConf=$minConfidence%"
        )
    }

    fun clearAlertHistory() {
        _recentAlerts.value = emptyList()
        _lastTriggeredAlert.value = null
    }
}
