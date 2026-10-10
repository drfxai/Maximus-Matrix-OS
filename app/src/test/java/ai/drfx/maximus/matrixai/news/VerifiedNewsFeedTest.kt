package ai.drfx.maximus.matrixai.news

import org.junit.Assert.*
import org.junit.Test

class VerifiedNewsFeedTest {
    private val source = NewsFeedSource("Publisher", "https://news.example/rss", "news.example")
    private val now = 1_800_000_000_000L
    private fun item(link: String = "https://news.example/report", title: String = "Report") =
        "<item><title>$title</title><link>$link</link><description>Publisher summary</description><pubDate>Tue, 01 Sep 2026 12:00:00 GMT</pubDate></item>"
    private fun rss(items: String) = "<rss><channel>$items</channel></rss>".toByteArray()

    @Test fun preservesPublisherEvidenceAndDeduplicates() {
        val result = VerifiedNewsFeed.parse(rss(item() + item()), source, now)
        assertEquals(1, result.size)
        assertEquals("Publisher", result.single().sourceName)
        assertEquals("https://news.example/report", result.single().sourceUrl)
        assertNotNull(result.single().publishedAtMs)
        assertEquals(now, result.single().retrievedAtMs)
        assertEquals(MarketSentiment.NEUTRAL, result.single().sentiment)
    }
    @Test fun rejectsWrongHostAndInsecureLinks() {
        assertTrue(VerifiedNewsFeed.parse(rss(item("https://attacker.example/a") + item("http://news.example/a")), source, now).isEmpty())
    }
    @Test fun downtimeReturnsUnavailableWithoutFabricatedData() {
        val result = VerifiedNewsFeed { throw java.io.IOException("offline") }.refresh(listOf(source), now)
        assertTrue(result.articles.isEmpty())
        assertEquals(listOf("Publisher"), result.failedSources)
    }
    @Test fun deduplicatesAcrossFeeds() {
        assertEquals(1, VerifiedNewsFeed { rss(item()) }.refresh(listOf(source, source), now).articles.size)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedFeed() {
        VerifiedNewsFeed.parse(ByteArray(VerifiedNewsFeed.MAX_BYTES + 1), source, now)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsExternalEntities() {
        VerifiedNewsFeed.parse("<!DOCTYPE rss [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><rss/>".toByteArray(), source, now)
    }
    @Test fun malformedFeedIsReportedAsFailed() {
        val result = VerifiedNewsFeed { "<rss".toByteArray() }.refresh(listOf(source), now)
        assertTrue(result.articles.isEmpty())
        assertEquals(1, result.failedSources.size)
    }
}
