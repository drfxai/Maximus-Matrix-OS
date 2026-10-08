package ai.drfx.maximus.matrixai.signals

import org.junit.Assert.*
import org.junit.Test

class SignalWebhookParserTest {

    @Test
    fun testParseTradingViewStandardJson() {
        val json = """
            {
              "ticker": "BTCUSDT",
              "action": "BUY",
              "price": 68450.0,
              "sl": 67100.0,
              "tp1": 69800.0,
              "tp2": 71200.0,
              "timeframe": "15m",
              "strategy": "SuperTrend AI Matrix",
              "confluence": "Bullish Order Block retest",
              "secret": "my_secret_token"
            }
        """.trimIndent()

        val result = SignalWebhookParser.parse(json, "TradingView")
        assertTrue("Expected Success result", result is WebhookParseResult.Success)

        val success = result as WebhookParseResult.Success
        val signal = success.signal

        assertEquals("BTC/USDT", signal.symbol)
        assertEquals("BUY", signal.direction)
        assertEquals(68450.0, signal.entryPrice, 0.001)
        assertEquals(67100.0, signal.stopLoss, 0.001)
        assertEquals(69800.0, signal.takeProfit1, 0.001)
        assertEquals(71200.0, signal.takeProfit2, 0.001)
        assertEquals("M15", signal.timeframe)
        assertEquals("SuperTrend AI Matrix", signal.strategyName)
        assertEquals("Crypto", signal.assetClass)
        assertEquals("ACTIVE", signal.status)
        assertTrue(signal.isCustomUserSignal)
    }

    @Test
    fun testParseCloudflareRelayedFormat() {
        val json = """
            {
              "source": "cloudflare",
              "worker": "maximus-signal-gateway",
              "signal": {
                "symbol": "XAU/USD",
                "direction": "SELL",
                "price": 2648.5,
                "sl": 2662.0,
                "tp1": 2625.0,
                "timeframe": "1H",
                "strategy": "Liquidity Sweep Scalper",
                "confluence": "Asian session high sweep"
              }
            }
        """.trimIndent()

        val result = SignalWebhookParser.parse(json, "Cloudflare Worker")
        assertTrue("Expected Success result", result is WebhookParseResult.Success)

        val success = result as WebhookParseResult.Success
        val signal = success.signal

        assertEquals("XAU/USD", signal.symbol)
        assertEquals("SELL", signal.direction)
        assertEquals(2648.5, signal.entryPrice, 0.001)
        assertEquals("Commodities", signal.assetClass)
        assertEquals("Cloudflare Worker Gateway", signal.authorAgent)
    }

    @Test
    fun testParsePlainTextAlert() {
        val text = "BUY ETHUSDT 3450.0 SL:3380.0 TP:3600.0"
        val result = SignalWebhookParser.parse(text, "Text Alert")

        assertTrue("Expected Success result for text alert", result is WebhookParseResult.Success)
        val signal = (result as WebhookParseResult.Success).signal

        assertEquals("ETH/USDT", signal.symbol)
        assertEquals("BUY", signal.direction)
        assertEquals(3450.0, signal.entryPrice, 0.001)
        assertEquals(3380.0, signal.stopLoss, 0.001)
        assertEquals(3600.0, signal.takeProfit1, 0.001)
    }

    @Test
    fun testParseEmptyPayloadFailsGracefully() {
        val result = SignalWebhookParser.parse("", "Empty")
        assertTrue("Expected Failure result", result is WebhookParseResult.Failure)
    }

    @Test
    fun testParseMissingPriceFailsGracefully() {
        val json = """{"ticker": "BTCUSDT", "action": "BUY"}"""
        val result = SignalWebhookParser.parse(json, "TradingView")
        assertTrue("Expected Failure result", result is WebhookParseResult.Failure)
    }

    @Test
    fun testConfidenceParsingVariants() {
        val json1 = """{"symbol": "SOLUSDT", "action": "BUY", "price": 178.5, "sl": 170.0, "tp1": 195.0, "confidence": 94}"""
        val res1 = SignalWebhookParser.parse(json1, "TradingView") as WebhookParseResult.Success
        assertEquals(94, res1.signal.winProbability)

        val json2 = """{"symbol": "ETHUSDT", "action": "BUY", "price": 3400.0, "sl": 3300.0, "tp1": 3600.0, "confidence": 0.88}"""
        val res2 = SignalWebhookParser.parse(json2, "TradingView") as WebhookParseResult.Success
        assertEquals(88, res2.signal.winProbability)

        val json3 = """{"symbol": "BTCUSDT", "action": "BUY", "price": 68000.0, "sl": 67000.0, "tp1": 70000.0, "confidence": "91%"}"""
        val res3 = SignalWebhookParser.parse(json3, "TradingView") as WebhookParseResult.Success
        assertEquals(91, res3.signal.winProbability)
    }
}
