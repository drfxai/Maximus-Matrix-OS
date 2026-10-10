package ai.drfx.maximus.matrixai.news

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ai.drfx.maximus.matrixai.llm.SecureApiConfigStore

/** Publisher reporting is fetched first. AI interpretation is a separate, explicit chat action. */
class AiNewsIntelligenceAgent(
    private val context: Context,
    private val apiStore: SecureApiConfigStore = SecureApiConfigStore(context)
) {
    var lastRefreshStatus: String = "Not synchronized"
        private set
    fun getDefaultTodayEvents(): List<EconomicEvent> = emptyList()
    private val cache get() = context.getSharedPreferences("verified_public_news", Context.MODE_PRIVATE)
    fun getDefaultArticles(): List<NewsArticle> = runCatching {
        val array = org.json.JSONArray(cache.getString("articles", "[]"))
        (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            NewsArticle(id = value.getString("id"), assetSymbol = "MACRO", category = NewsCategory.CENTRAL_BANKS,
                timeAgo = "Cached · " + value.getString("published"), headline = value.getString("headline"),
                summary = value.getString("summary"), sentiment = MarketSentiment.NEUTRAL,
                sourceName = value.getString("publisher"), sourceUrl = value.getString("url"),
                publishedAtMs = value.optLong("publication", 0).takeIf { it > 0 }, retrievedAtMs = value.getLong("retrieved"))
        }.filter { article -> VerifiedNewsFeed.SOURCES.any { java.net.URI(article.sourceUrl).host == it.publisherHost } }
    }.getOrDefault(emptyList())
    fun getDefaultHeroStory() = HeroNewsStory(
        headline = "News not synchronized", subheadline = "Refresh to retrieve official publisher reports.",
        category = NewsCategory.CENTRAL_BANKS, impact = MarketImpact.LOW,
        timeAgo = "Unavailable", facts = emptyList(), aiAnalysis = "AI analysis unavailable",
        whyItMatters = "Economic calendar unavailable: no verified calendar provider is configured."
    )
    suspend fun refreshWithAi(customQuery: String = ""): Pair<HeroNewsStory, List<NewsArticle>> = withContext(Dispatchers.IO) {
        val result = VerifiedNewsFeed().refresh()
        val articles = result.articles.ifEmpty { getDefaultArticles() }
        if (result.articles.isNotEmpty()) {
            val array = org.json.JSONArray()
            result.articles.forEach { article -> array.put(org.json.JSONObject()
                .put("id", article.id).put("published", article.timeAgo).put("headline", article.headline)
                .put("summary", article.summary).put("publisher", article.sourceName).put("url", article.sourceUrl)
                .put("publication", article.publishedAtMs ?: 0).put("retrieved", article.retrievedAtMs)) }
            cache.edit().putString("articles", array.toString()).apply()
        }
        lastRefreshStatus = if (articles.isEmpty()) "Unavailable: news sources could not supply verified articles" else
            (if (result.articles.isEmpty()) "Cached publisher reports · live sources unavailable" else "Live publisher feed · ${java.util.Date()}") + if (result.failedSources.isEmpty()) "" else " · Unavailable: ${result.failedSources.joinToString()}"
        val first = articles.firstOrNull()
        val hero = if (first == null) getDefaultHeroStory().copy(headline = "News unavailable", subheadline = lastRefreshStatus) else HeroNewsStory(
            id = first.id, headline = first.headline, subheadline = first.summary,
            category = first.category, impact = MarketImpact.LOW, timeAgo = first.timeAgo,
            facts = listOf("Publisher: ${first.sourceName}", "Original source: ${first.sourceUrl}"),
            aiAnalysis = "AI analysis unavailable. Use Ask Maximus AI for an explicit analysis of this report.",
            whyItMatters = "Publisher reporting is evidence; market impact requires separate analysis.",
            sourceUrl = first.sourceUrl, sourceName = first.sourceName
        )
        hero to articles
    }
    // No market prices or calibrated alert conditions are configured. Never fabricate triggers.
    suspend fun evaluateWatchlistMarketMovingCatalysts(watchlistSymbols: Set<String>): List<MarketAlertNotification> = emptyList()
}
