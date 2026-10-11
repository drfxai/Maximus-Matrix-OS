package ai.drfx.maximus.matrixai.news

import android.content.Context

/** Market intelligence requires a verifiable, timestamped market-data source. None is configured. */
class AiNewsIntelligenceAgent(context: Context) {
    fun getDefaultTodayEvents(): List<EconomicEvent> = emptyList()
    fun getDefaultHeroStory(): HeroNewsStory? = null
    fun getDefaultArticles(): List<NewsArticle> = emptyList()

    /** An LLM response alone cannot establish that a market event occurred. */
    suspend fun refreshWithAi(customQuery: String = ""): Pair<HeroNewsStory?, List<NewsArticle>> =
        null to emptyList()

    /** Never send a price-moving notification without a verified trigger. */
    suspend fun evaluateWatchlistMarketMovingCatalysts(
        watchlistSymbols: Set<String>
    ): List<MarketAlertNotification> = emptyList()
}
