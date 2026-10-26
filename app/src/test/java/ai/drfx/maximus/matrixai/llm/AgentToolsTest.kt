package ai.drfx.maximus.matrixai.llm

import ai.drfx.maximus.matrixai.llm.AgentTools.Tool
import org.junit.Assert.*
import org.junit.Test

class AgentToolsTest {
    @Test fun parsesLookupsOutsideCodeAndCapsThem() {
        val reply = "Let me check.\n```\n<web_search>ignored inside code</web_search>\n```\n" +
            "<web_search>BTC funding rate today</web_search>\n<pine_inspect/>\n" +
            "<fetch_url>https://www.tradingview.com/pine-script-docs/</fetch_url>\n<web_search>fourth</web_search>"
        val calls = AgentTools.parse(reply)
        assertEquals(listOf(Tool.WEB_SEARCH, Tool.PINE_INSPECT, Tool.FETCH_URL), calls.map { it.tool })
        assertEquals("BTC funding rate today", calls[0].argument)
        val shown = AgentTools.stripTags(reply)
        assertTrue(shown.startsWith("Let me check."))
        assertFalse(shown.contains("BTC funding rate today"))
        assertFalse(shown.contains("<pine_inspect"))
    }

    @Test fun duplicateRequestsCollapse() {
        assertEquals(1, AgentTools.parse("<web_search>gold</web_search><web_search>gold</web_search>").size)
    }

    @Test fun privateAndLocalAddressesAreRefused() {
        listOf("http://localhost:8080/", "http://127.0.0.1/", "http://192.168.1.1/admin", "http://10.0.0.5/",
            "http://172.20.1.1/", "http://169.254.169.254/latest", "http://[::1]/", "file:///sdcard/a.txt",
            "https://user:pass@example.com/", "http://router/", "http://printer.local/", "https://example.com:22/"
        ).forEach { assertFalse(it, AgentTools.isPublicWebUrl(it)) }
        listOf("https://example.com/", "http://news.ycombinator.com/item?id=1", "https://8.8.8.8/")
            .forEach { assertTrue(it, AgentTools.isPublicWebUrl(it)) }
        assertTrue(AgentTools.parse("<fetch_url>http://192.168.0.1/</fetch_url>").isEmpty())
    }

    @Test fun browserBarTakesUrlsDomainsOrGoogleSearches() {
        assertEquals("https://example.com/a", AgentTools.browserTarget("https://example.com/a"))
        assertEquals("https://tradingview.com", AgentTools.browserTarget("tradingview.com"))
        assertEquals("https://www.google.com/search?q=pine+script+ta.rsi", AgentTools.browserTarget("pine script ta.rsi"))
        assertEquals("https://www.google.com/", AgentTools.browserTarget(" "))
    }

    @Test fun duckDuckGoResultsAreDecoded() {
        val html = """
            <div class="result"><h2><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.tradingview.com%2Fpine%2F&amp;rut=x">Pine <b>Script</b> docs</a></h2>
            <a class="result__snippet" href="#">The <b>reference</b> &amp; manual.</a></div>
            <div class="result"><a class="result__a" href="https://duckduckgo.com/y.js?ad">Ad</a></div>
        """.trimIndent()
        val results = AgentTools.parseSearchResults(html)
        assertEquals(1, results.size)
        assertEquals("Pine Script docs", results[0].title)
        assertEquals("https://www.tradingview.com/pine/", results[0].url)
        assertEquals("The reference & manual.", results[0].snippet)
        assertTrue(AgentTools.formatSearch("pine", results).contains("1. Pine Script docs"))
    }

    @Test fun pageTextDropsScriptsAndChrome() {
        val page = AgentTools.pageText("<html><head><title>RSI &amp; MACD</title><style>p{}</style></head><body>" +
            "<nav>Menu</nav><h1>Momentum</h1><p>RSI measures speed.</p><script>alert(1)</script><ul><li>One</li></ul></body></html>")
        assertEquals("RSI & MACD", page.title)
        assertTrue(page.text.contains("Momentum"))
        assertTrue(page.text.contains("RSI measures speed."))
        assertTrue(page.text.contains("• One"))
        assertFalse(page.text.contains("alert"))
        assertFalse(page.text.contains("Menu"))
    }
}
