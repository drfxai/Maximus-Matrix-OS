package ai.drfx.maximus.matrixai.news

import java.net.URI

/** Only HTTPS publisher-hosted media is displayed. No URL is synthesized from a headline. */
object NewsImagePolicy {
    fun isAllowed(url: String, publisherHost: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", true) && uri.host?.lowercase() == publisherHost.lowercase() &&
            uri.rawUserInfo == null && uri.port in setOf(-1, 443) &&
            uri.rawFragment == null && url.length <= 1200
    }.getOrDefault(false)
}
