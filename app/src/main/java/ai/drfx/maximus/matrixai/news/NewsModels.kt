package ai.drfx.maximus.matrixai.news

import java.util.UUID

enum class NewsCategory(val displayName: String, val emoji: String) {
    FOREX("Forex", "🌐"),
    GOLD("Gold", "⚱️"),
    CRYPTO("Crypto", "₿"),
    MACRO("Macro", "📊"),
    CENTRAL_BANKS("Central Banks", "🏛️"),
    HIGH_IMPACT("High Impact", "🔥")
}

enum class MarketImpact(val displayName: String, val levelNumber: Int) {
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1)
}

enum class MarketSentiment(val displayName: String, val symbol: String) {
    BULLISH("Bullish", "↑"),
    BEARISH("Bearish", "↓"),
    NEUTRAL("Neutral", "—")
}

data class EconomicEvent(
    val id: String = UUID.randomUUID().toString(),
    val time: String,
    val currency: String,
    val flagEmoji: String,
    val eventName: String,
    val impact: MarketImpact,
    val previous: String? = null,
    val forecast: String? = null,
    val actual: String? = null
)

data class HeroNewsStory(
    val id: String = UUID.randomUUID().toString(),
    val headline: String,
    val subheadline: String,
    val category: NewsCategory,
    val impact: MarketImpact,
    val timeAgo: String,
    val facts: List<String>,
    val aiAnalysis: String,
    val whyItMatters: String,
    val affectedAssets: List<String> = emptyList(),
    val isBookmarked: Boolean = false
)

data class NewsArticle(
    val id: String = UUID.randomUUID().toString(),
    val assetSymbol: String,
    val category: NewsCategory,
    val timeAgo: String,
    val headline: String,
    val summary: String,
    val sentiment: MarketSentiment,
    val sourceName: String = "Forex Factory",
    val fullContent: String = "",
    val aiTakeaway: String = ""
)

data class NewsIntelligenceUiState(
    val selectedCategoryFilter: NewsCategory? = null,
    val selectedSubTab: String = "All",
    val todayEvents: List<EconomicEvent> = emptyList(),
    val heroStory: HeroNewsStory? = null,
    val articles: List<NewsArticle> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastUpdatedText: String = "Just now",
    val selectedArticleForDetail: NewsArticle? = null
)
