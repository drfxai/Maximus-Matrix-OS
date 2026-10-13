package ai.drfx.maximus.matrixai.news

import org.junit.Assert.*
import org.junit.Test

class PublisherMediaTest {
    private val src = NewsFeedSource("Example", "https://news.example/rss", "news.example")
    @Test fun publisherHostedImagesAreParsedAndExternalImagesRejected() {
        val xml = """
            <rss xmlns:media="http://search.yahoo.com/mrss/"><channel><item>
            <title>Economic announcement</title>
            <link>https://news.example/report</link>
            <description>Official release</description>
            <media:content url="https://news.example/media/report.webp" type="image/webp"/>
            <media:thumbnail url="https://tracker.example/pixel"/>
            </item></channel></rss>
        """.trimIndent()
        val first = VerifiedNewsFeed.parse(xml.toByteArray(), src, System.currentTimeMillis()).single()
        assertEquals("https://news.example/media/report.webp", first.imageUrl)
        assertFalse(NewsImagePolicy.isAllowed("https://tracker.example/pixel", src.publisherHost))
        assertFalse(NewsImagePolicy.isAllowed("http://news.example/pixel", src.publisherHost))
        assertFalse(NewsImagePolicy.isAllowed("https://user@news.example/pixel", src.publisherHost))
    }

    @Test fun parsesAtomEntriesWithValidPublisherLink() {
        val atom = """
            <feed xmlns="http://www.w3.org/2005/Atom">
            <entry><title>Policy report</title><link href="https://news.example/policy"/>
            <summary>Official report</summary><updated>2026-10-01T12:00:00Z</updated></entry>
            </feed>
        """.trimIndent()
        val article = VerifiedNewsFeed.parse(atom.toByteArray(), src, System.currentTimeMillis()).single()
        assertEquals("Policy report", article.headline)
        assertEquals("https://news.example/policy", article.sourceUrl)
        assertNotNull(article.publishedAtMs)
    }
}
