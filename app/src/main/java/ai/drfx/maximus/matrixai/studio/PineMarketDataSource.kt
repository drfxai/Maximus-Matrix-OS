package ai.drfx.maximus.matrixai.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/** Actual exchange-provided OHLCV values; no random/demo candles are returned as live prices. */
data class PineCandle(
    val epochSeconds: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

enum class PineTimeframe(val seconds: Int, val label: String) {
    ONE_MINUTE(60, "1m"),
    FIVE_MINUTES(300, "5m"),
    FIFTEEN_MINUTES(900, "15m"),
    ONE_HOUR(3600, "1h"),
    SIX_HOURS(21600, "6h"),
    ONE_DAY(86400, "1D")
}

data class PineMarketSeries(
    val product: String,
    val source: String,
    val timeframe: PineTimeframe,
    val candles: List<PineCandle>,
    val fetchedAtMillis: Long
)

/** Strictly parse Coinbase Exchange candles [timestamp, low, high, open, close, volume]. */
object PineCandleParser {
    fun parseCoinbase(payload: String, limit: Int = 200, maxEpochExclusive: Long? = null): List<PineCandle> {
        require(limit in 1..200)
        val rows = JSONArray(payload)
        require(rows.length() > 0) { "Exchange returned no candles." }
        val unique = LinkedHashMap<Long, PineCandle>()
        for (index in 0 until rows.length()) {
            val r = rows.getJSONArray(index)
            require(r.length() >= 6) { "Invalid exchange candle." }
            val time = r.getLong(0)
            val low = r.getDouble(1)
            val high = r.getDouble(2)
            val open = r.getDouble(3)
            val close = r.getDouble(4)
            val volume = r.getDouble(5)
            require(time > 0 &&
                listOf(low, high, open, close, volume).all { it.isFinite() } &&
                listOf(low, high, open, close).all { it <= PineIndicatorPreview.MAX_PRICE } &&
                low > 0 && high >= max(open, close) && low <= min(open, close) &&
                high >= low && volume >= 0) { "Invalid OHLCV exchange response." }
            unique[time] = PineCandle(time, open, high, low, close, volume)
        }
        return unique.values.asSequence().filter {
            maxEpochExclusive == null || it.epochSeconds < maxEpochExclusive
        }.sortedBy { it.epochSeconds }.toList().takeLast(limit)
    }
}

class PineMarketDataSource(
    private val fetch: (String) -> String = ::fetchHttps
) {
    companion object {
        val PRODUCTS = listOf("BTC-USD", "ETH-USD", "SOL-USD", "XRP-USD", "DOGE-USD", "ADA-USD",
            "AVAX-USD", "LINK-USD", "LTC-USD", "BCH-USD")
        private fun fetchHttps(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 9_000
                connection.readTimeout = 9_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "MaximusMatrixOS/1.0 Android")
                require(connection.responseCode == 200) {
                    "Exchange HTTP ${connection.responseCode}. Retry or change your network."
                }
                connection.inputStream.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 300_000) { "Exchange response is too large." }
                        output.write(buffer, 0, count)
                    }
                    return output.toString(Charsets.UTF_8.name())
                }
            } finally { connection.disconnect() }
        }
    }

    suspend fun load(
        product: String,
        timeframe: PineTimeframe,
        nowSeconds: Long = System.currentTimeMillis() / 1000
    ): PineMarketSeries = withContext(Dispatchers.IO) {
        require(product in PRODUCTS) { "Unsupported market symbol." }
        val currentOpen = (nowSeconds / timeframe.seconds) * timeframe.seconds
        val end = currentOpen - timeframe.seconds
        val start = end - (206L * timeframe.seconds)
        val url = "https://api.exchange.coinbase.com/products/$product/candles" +
            "?granularity=${timeframe.seconds}" +
            "&start=${DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(start))}" +
            "&end=${DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(end))}"
        val candles = PineCandleParser.parseCoinbase(fetch(url), maxEpochExclusive = currentOpen).filter {
            it.epochSeconds >= start
        }
        require(candles.isNotEmpty()) { "No recent candles from Coinbase Exchange." }
        PineMarketSeries(product, "Coinbase Exchange", timeframe, candles.takeLast(200),
            System.currentTimeMillis())
    }
}
