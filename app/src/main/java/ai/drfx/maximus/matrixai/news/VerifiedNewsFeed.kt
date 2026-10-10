package ai.drfx.maximus.matrixai.news

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Public publisher feeds only. No LLM is used to invent reporting or market prices. */
data class NewsFeedSource(val name: String, val url: String, val publisherHost: String)
data class NewsFeedResult(val articles: List<NewsArticle>, val failedSources: List<String>)

class VerifiedNewsFeed(private val fetch: (String) -> ByteArray = ::download) {
    fun refresh(sources: List<NewsFeedSource> = SOURCES, now: Long = System.currentTimeMillis()): NewsFeedResult {
        val failed = mutableListOf<String>()
        val articles = sources.flatMap { source ->
            try { parse(fetch(source.url), source, now) } catch (_: Exception) {
                failed.add(source.name)
                emptyList()
            }
        }.distinctBy { it.sourceUrl }.sortedByDescending { it.publishedAtMs ?: 0 }.take(100)
        return NewsFeedResult(articles, failed)
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        val SOURCES = listOf(
            NewsFeedSource("Federal Reserve", "https://www.federalreserve.gov/feeds/press_monetary.xml", "www.federalreserve.gov"),
            NewsFeedSource("European Central Bank", "https://www.ecb.europa.eu/rss/press.html", "www.ecb.europa.eu")
        )

        fun parse(bytes: ByteArray, source: NewsFeedSource, now: Long): List<NewsArticle> {
            require(bytes.size <= MAX_BYTES) { "Feed exceeds size limit" }
            // Reject DTD/entity declarations before XML parsing on all Android parser implementations.
            require(bytes.none { it == 0.toByte() }) { "Unsupported XML encoding" }
            val xml = bytes.toString(Charsets.UTF_8)
            require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) { "Unsafe XML" }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
            }.parse(ByteArrayInputStream(bytes))
            val items = doc.getElementsByTagName("item")
            val results = mutableListOf<NewsArticle>()
            for (i in 0 until minOf(items.length, 200)) {
                val item = items.item(i) as? Element ?: continue
                fun value(tag: String): String = item.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
                val title = clean(value("title")).take(500)
                val link = value("link")
                val uri = runCatching { URI(link) }.getOrNull() ?: continue
                if (uri.scheme != "https" || uri.userInfo != null || uri.host != source.publisherHost || title.isBlank()) continue
                val published = parseDate(value("pubDate"))
                if (published != null && published > now + 300_000) continue
                results.add(NewsArticle(
                    id = java.util.UUID.nameUUIDFromBytes(link.toByteArray()).toString(),
                    assetSymbol = "MACRO", category = NewsCategory.CENTRAL_BANKS,
                    timeAgo = published?.let { java.util.Date(it).toString() } ?: "Publication time unavailable",
                    headline = title, summary = clean(value("description")).take(2000),
                    sentiment = MarketSentiment.NEUTRAL, sourceName = source.name,
                    sourceUrl = link, publishedAtMs = published, retrievedAtMs = now,
                    aiTakeaway = "AI analysis unavailable. Open the original publisher report or request an evidence-grounded analysis."
                ))
            }
            return results.distinctBy { it.sourceUrl }
        }

        private fun clean(text: String): String = text.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        private fun parseDate(text: String): Long? = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss z", "yyyy-MM-dd'T'HH:mm:ssXXX").firstNotNullOfOrNull { format ->
            runCatching { SimpleDateFormat(format, Locale.US).apply { isLenient = false }.parse(text)?.time }.getOrNull()
        }
        private fun download(endpoint: String): ByteArray {
            require(URI(endpoint).scheme == "https")
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
                require(connection.responseCode in 200..299) { "Feed unavailable" }
                return connection.inputStream.use { stream ->
                    val bytes = stream.readBytesBounded(MAX_BYTES)
                    bytes
                }
            } finally { connection.disconnect() }
        }
        private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val size = read(buffer)
                if (size < 0) break
                require(out.size() + size <= limit) { "Feed exceeds size limit" }
                out.write(buffer, 0, size)
            }
            return out.toByteArray()
        }
    }
}
