package ai.drfx.maximus.matrixai.studio

import org.junit.Assert.*
import org.junit.Test

class PineMarketDataSourceTest {
    @Test fun parsesDescendingExchangeCandlesAsAscending() {
        val data = """[[200,9,14,10,13,5],[100,8,12,9,10,3]]"""
        val candles = PineCandleParser.parseCoinbase(data)
        assertEquals(2, candles.size)
        assertEquals(100L, candles[0].epochSeconds)
        assertEquals(8.0, candles[0].low, 0.001)
        assertEquals(14.0, candles[1].high, 0.001)
        assertEquals(5.0, candles[1].volume, 0.001)
    }

    @Test fun rejectsInvalidOhlcv() {
        listOf(
            """[[100,11,12,9,10,4]]""",
            """[[100,5,9,10,8,3]]""",
            """[[100,5,12,9,10,-1]]""",
            """[[100,1e308,1e308,1e308,1e308,1]]""",
            "[]"
        ).forEach { payload ->
            try {
                PineCandleParser.parseCoinbase(payload)
                fail("Rejected invalid exchange payload: $payload")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun deduplicatesAndCapsHistoryToTwoHundred() {
        val data = (1..240).reversed().joinToString(prefix = "[", postfix = "]") { t ->
            "[$t,1,3,2,2.5,1]"
        }
        val candles = PineCandleParser.parseCoinbase(data)
        assertEquals(200, candles.size)
        assertEquals(41L, candles.first().epochSeconds)
        assertEquals(240L, candles.last().epochSeconds)
    }

    @Test fun supportsExactlyFourRequestedTimeframes() {
        assertEquals(listOf(60, 300, 900, 3600),
            PineTimeframe.entries.map { it.seconds })
    }
}
