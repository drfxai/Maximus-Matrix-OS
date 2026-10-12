package ai.drfx.maximus.matrixai.news

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Connects only to an endpoint configured by the owner of the app. Times must include an offset. */
class NewsFeedRepository(context: Context) {
    private val preferences = context.getSharedPreferences("news_feed", Context.MODE_PRIVATE)
    var endpoint: String
        get() = preferences.getString("endpoint", "") ?: ""
        set(value) { preferences.edit().putString("endpoint", value.trim()).apply() }

    data class Snapshot(val events: List<EconomicEvent>, val articles: List<NewsArticle>, val fetchedAt: Long)

    suspend fun fetch(): Snapshot = withContext(Dispatchers.IO) {
        val address = endpoint
        val uri = URI(address)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "Enter a valid HTTPS feed URL in News settings."
        }
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MaximusMatrixOS/1.0")
            require(connection.responseCode in 200..299) { "Feed returned HTTP ${connection.responseCode}." }
            val bytes = connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 2_000_000) { "Feed exceeds the 2 MB limit." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            val events = root.optJSONArray("events") ?: JSONArray()
            val articles = root.optJSONArray("articles") ?: JSONArray()
            Snapshot(
                (0 until minOf(events.length(), 500)).mapNotNull { index -> parseEvent(events.optJSONObject(index)) }
                    .sortedBy { it.startsAtMillis ?: Long.MAX_VALUE },
                (0 until minOf(articles.length(), 100)).mapNotNull { index -> parseArticle(articles.optJSONObject(index)) },
                System.currentTimeMillis()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEvent(item: JSONObject?): EconomicEvent? {
        if (item == null) return null
        val title = item.optString("title").ifBlank { item.optString("eventName") }
        val date = item.optString("date")
        val instant = runCatching { Instant.parse(date) }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(date).toInstant() }.getOrNull()
            ?: return null
        if (title.isBlank()) return null
        val currency = item.optString("currency").uppercase()
        val impact = when (item.optString("impact").lowercase()) {
            "high" -> MarketImpact.HIGH
            "medium" -> MarketImpact.MEDIUM
            else -> MarketImpact.LOW
        }
        val local = instant.atZone(ZoneId.systemDefault())
        return EconomicEvent(
            id = item.optString("id").ifBlank { "$currency:$title:$date" },
            time = local.format(DateTimeFormatter.ofPattern("HH:mm")),
            currency = currency,
            flagEmoji = "",
            eventName = title,
            impact = impact,
            previous = item.optString("previous").takeIf { it.isNotBlank() },
            forecast = item.optString("forecast").takeIf { it.isNotBlank() },
            actual = item.optString("actual").takeIf { it.isNotBlank() },
            startsAtMillis = instant.toEpochMilli()
        )
    }

    private fun parseArticle(item: JSONObject?): NewsArticle? {
        if (item == null || item.optString("headline").isBlank()) return null
        val category = NewsCategory.values().firstOrNull {
            it.name.equals(item.optString("category"), ignoreCase = true)
        } ?: NewsCategory.MACRO
        return NewsArticle(
            id = item.optString("id").ifBlank { item.optString("headline") },
            assetSymbol = item.optString("symbol").ifBlank { "MARKET" },
            category = category,
            timeAgo = item.optString("publishedAt").ifBlank { "Latest" },
            headline = item.optString("headline"),
            summary = item.optString("summary"),
            sentiment = MarketSentiment.NEUTRAL,
            sourceName = item.optString("source").ifBlank { "Connected feed" },
            fullContent = item.optString("summary")
        )
    }
}
