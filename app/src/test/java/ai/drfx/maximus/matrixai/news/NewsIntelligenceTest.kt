package ai.drfx.maximus.matrixai.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsIntelligenceTest {

    @Test
    fun testNewsCategoriesAndImpactLevels() {
        assertEquals("Forex", NewsCategory.FOREX.displayName)
        assertEquals("Gold", NewsCategory.GOLD.displayName)
        assertEquals("Crypto", NewsCategory.CRYPTO.displayName)
        assertEquals("Macro", NewsCategory.MACRO.displayName)
        assertEquals("Central Banks", NewsCategory.CENTRAL_BANKS.displayName)
        assertEquals("High Impact", NewsCategory.HIGH_IMPACT.displayName)

        assertEquals(3, MarketImpact.HIGH.levelNumber)
        assertEquals(2, MarketImpact.MEDIUM.levelNumber)
        assertEquals(1, MarketImpact.LOW.levelNumber)

        assertEquals("Bullish", MarketSentiment.BULLISH.displayName)
        assertEquals("Bearish", MarketSentiment.BEARISH.displayName)
        assertEquals("Neutral", MarketSentiment.NEUTRAL.displayName)
    }

    @Test
    fun testEconomicEventStructure() {
        val event = EconomicEvent(
            time = "14:30",
            currency = "USD",
            flagEmoji = "🇺🇸",
            eventName = "Non-Farm Employment Change",
            impact = MarketImpact.HIGH,
            previous = "175K",
            forecast = "180K",
            actual = "206K"
        )

        assertNotNull(event.id)
        assertEquals("14:30", event.time)
        assertEquals("USD", event.currency)
        assertEquals(MarketImpact.HIGH, event.impact)
        assertEquals("206K", event.actual)
    }

    @Test
    fun testHeroNewsStory() {
        val hero = HeroNewsStory(
            headline = "Fed Powell Signals Cautious Path Forward",
            subheadline = "Inflation prints maintain hawkish tone.",
            category = NewsCategory.MACRO,
            impact = MarketImpact.HIGH,
            timeAgo = "1h ago",
            facts = listOf("Core PCE unchanged at 0.3%", "Rates held at 5.50%"),
            aiAnalysis = "Short-term dollar strength expected; yields remain elevated.",
            whyItMatters = "Higher rates compress valuation multiples across equities and risk assets.",
            affectedAssets = listOf("DXY", "EURUSD", "XAUUSD")
        )

        assertEquals("MACRO", hero.category.name)
        assertEquals(2, hero.facts.size)
        assertEquals(3, hero.affectedAssets.size)
        assertTrue(hero.whyItMatters.isNotBlank())
    }

    @Test
    fun testNewsArticleFiltering() {
        val articles = listOf(
            NewsArticle(
                assetSymbol = "XAUUSD",
                category = NewsCategory.GOLD,
                timeAgo = "1h ago",
                headline = "Gold tests key support",
                summary = "Bears test $2,300",
                sentiment = MarketSentiment.BEARISH
            ),
            NewsArticle(
                assetSymbol = "EURUSD",
                category = NewsCategory.FOREX,
                timeAgo = "2h ago",
                headline = "Euro recovers after ECB commentary",
                summary = "Bullish price action",
                sentiment = MarketSentiment.BULLISH
            ),
            NewsArticle(
                assetSymbol = "BTC",
                category = NewsCategory.CRYPTO,
                timeAgo = "3h ago",
                headline = "Bitcoin holds $66,000 baseline",
                summary = "Consolidation phase",
                sentiment = MarketSentiment.NEUTRAL
            )
        )

        val goldArticles = articles.filter { it.category == NewsCategory.GOLD }
        assertEquals(1, goldArticles.size)
        assertEquals("XAUUSD", goldArticles.first().assetSymbol)

        val bullishArticles = articles.filter { it.sentiment == MarketSentiment.BULLISH }
        assertEquals(1, bullishArticles.size)
        assertEquals("EURUSD", bullishArticles.first().assetSymbol)
    }
}
