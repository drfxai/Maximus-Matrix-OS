package ai.drfx.maximus.matrixai.news

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Loaded only from the same verified publisher as the original article. */
@Composable
fun PublisherNewsPhoto(article: NewsArticle?, modifier: Modifier = Modifier) {
    val image = produceState<Bitmap?>(initialValue = null, article?.imageUrl) {
        value = withContext(Dispatchers.IO) {
            article?.takeIf { it.imageUrl.isNotBlank() }?.let { loadPublisherImage(it) }
        }
    }.value
    val accent = when (article?.category) {
        NewsCategory.CRYPTO -> Color(0xFFF5A623)
        NewsCategory.GOLD -> Color(0xFFFFD56B)
        NewsCategory.FOREX -> Color(0xFF6BA9FF)
        else -> Color(0xFF4ED7C5)
    }
    Box(modifier.clip(RoundedCornerShape(10.dp))
        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.25f),
            MaterialTheme.colorScheme.surfaceVariant))), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(bitmap = image.asImageBitmap(), contentDescription = "Publisher image for: " + article?.headline,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val placeholder = when (article?.category) {
                NewsCategory.CRYPTO -> Icons.Default.CurrencyBitcoin
                NewsCategory.FOREX -> Icons.Default.CurrencyExchange
                NewsCategory.CENTRAL_BANKS -> Icons.Default.AccountBalance
                else -> Icons.Default.ShowChart
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(placeholder, contentDescription = "Category illustration — no publisher photo",
                    tint = accent, modifier = Modifier.size(28.dp))
                Text("Illustration", fontSize = 9.sp, color = accent, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun loadPublisherImage(article: NewsArticle): Bitmap? {
    return runCatching {
    val allowedHosts = VerifiedNewsFeed.SOURCES.map { it.publisherHost }.toSet()
    val host = URI(article.sourceUrl).host
    if (host !in allowedHosts || !NewsImagePolicy.isAllowed(article.imageUrl, host)) return null
    val connection = URL(article.imageUrl).openConnection() as HttpURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 6500
        connection.readTimeout = 8000
        connection.setRequestProperty("Accept", "image/jpeg,image/png,image/webp")
        if (connection.responseCode != 200) return null
        if (connection.contentType?.substringBefore(';') !in setOf("image/jpeg","image/png","image/webp"))
            return null
        if (connection.contentLengthLong > 2L * 1024 * 1024) return null
        val data = connection.inputStream.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val bytes = ByteArray(4096)
            while (true) {
                val n = stream.read(bytes)
                if (n < 0) break
                if (out.size() + n > 2 * 1024 * 1024) return null
                out.write(bytes, 0, n)
            }
            out.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth.toLong() * bounds.outHeight > 28_000_000) return null
        var sample = 1
        while (bounds.outWidth / sample > 960 || bounds.outHeight / sample > 960) sample *= 2
        BitmapFactory.decodeByteArray(data, 0, data.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
    } finally { connection.disconnect() }
    }.getOrNull()
}
