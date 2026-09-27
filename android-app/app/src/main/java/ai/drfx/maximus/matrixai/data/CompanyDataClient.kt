package ai.drfx.maximus.matrixai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CompanyDataClient {
    suspend fun status(baseUrl: String, token: String): DataCenterStatus = withContext(Dispatchers.IO) {
        val root = normalize(baseUrl)
        val json = JSONObject(get(root + "/v1/status", token))
        DataCenterStatus(
            connected = true,
            name = json.optString("name").ifBlank { "Company Data Center" },
            pineSources = json.optNullableInt("pineSources"),
            documents = json.optNullableInt("documents"),
            projects = json.optNullableInt("projects"),
            primitives = json.optNullableInt("primitives"),
            message = json.optString("message").ifBlank { "Connected." }
        )
    }

    suspend fun search(baseUrl: String, token: String, query: String): List<KnowledgeItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val root = normalize(baseUrl)
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = JSONObject(get(root + "/v1/search?q=" + encoded, token))
        val array = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    KnowledgeItem(
                        id = item.optString("id").ifBlank { i.toString() },
                        title = item.optString("title").ifBlank { "Untitled" },
                        type = item.optString("type").ifBlank { "knowledge" },
                        summary = item.optString("summary")
                    )
                )
            }
        }
    }

    private fun get(url: String, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MAXIMUS-AI/1.2")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer " + token)
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching {
                JSONObject(text).optJSONObject("error")?.optString("message")
                    ?.ifBlank { JSONObject(text).optString("message") }
            }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "HTTP " + code)
        }
        return text
    }

    private fun normalize(input: String): String {
        var value = input.trim().trimEnd('/')
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value
        return value
    }

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null
}
