package ai.drfx.maximus.matrixai.signals

import ai.drfx.maximus.matrixai.database.entities.TradingSignalEntity
import org.junit.Assert.*
import org.junit.Test

class SignalNotificationLogicTest {

    @Test
    fun testSignalAlertRecordCreation() {
        val record = SignalAlertRecord(
            signalId = "sig_123",
            symbol = "BTC/USDT",
            direction = "BUY",
            winProbability = 92,
            entryPrice = 67500.0,
            source = "TradingView Webhook",
            soundPlayed = true,
            notificationPosted = true,
            strategyName = "Maximus Alpha",
            rationale = "Strong momentum break"
        )

        assertEquals("BTC/USDT", record.symbol)
        assertEquals(92, record.winProbability)
        assertTrue(record.soundPlayed)
        assertTrue(record.notificationPosted)
        assertNotNull(record.formattedTime)
    }

    @Test
    fun testNotificationSettingsDefaultAndThresholdLogic() {
        val settings = SignalNotificationSettings(
            notificationsEnabled = true,
            soundAlertsEnabled = true,
            vibrateEnabled = true,
            minConfidenceThreshold = 80
        )

        assertTrue(settings.notificationsEnabled)
        assertTrue(settings.soundAlertsEnabled)
        assertTrue(settings.vibrateEnabled)
        assertEquals(80, settings.minConfidenceThreshold)

        val signalHigh = createDummySignal(winProbability = 88)
        val signalLow = createDummySignal(winProbability = 72)

        // Verifying threshold qualification logic
        val highQualifies = signalHigh.winProbability >= settings.minConfidenceThreshold
        val lowQualifies = signalLow.winProbability >= settings.minConfidenceThreshold

        assertTrue("88% should qualify as high confidence (threshold 80%)", highQualifies)
        assertFalse("72% should NOT qualify as high confidence (threshold 80%)", lowQualifies)
    }

    private fun createDummySignal(winProbability: Int): TradingSignalEntity {
        return TradingSignalEntity(
            id = "sig_test_${System.currentTimeMillis()}",
            symbol = "ETH/USDT",
            assetClass = "Crypto",
            direction = "BUY",
            signalType = "SCALPING",
            timeframe = "M5",
            entryPrice = 3500.0,
            stopLoss = 3450.0,
            takeProfit1 = 3600.0,
            takeProfit2 = 3700.0,
            currentPrice = 3510.0,
            status = "ACTIVE",
            winProbability = winProbability,
            riskRewardRatio = "1:2.0",
            strategyName = "Test Scalper",
            confluenceFactors = "RSI Divergence",
            aiRationale = "Test rationale",
            authorAgent = "TradingView Webhook"
        )
    }
}
