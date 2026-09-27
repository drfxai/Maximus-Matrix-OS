package ai.drfx.maximus.matrixai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LlmChatClient {
    suspend fun send(config: ApiConnectionConfig, history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        when (config.provider) {
            LlmProvider.ANTHROPIC -> anthropic(config, history)
            LlmProvider.GEMINI -> gemini(config, history)
            LlmProvider.OPENAI, LlmProvider.OPENAI_COMPATIBLE, LlmProvider.UNKNOWN -> openAiCompatible(config, history)
        }
    }

    private fun openAiCompatible(config: ApiConnectionConfig, history: List<ChatMessage>): String {
        val endpoint = ApiDiscoveryEngine.apiRoot(config.baseUrl) + "/chat/completions"
        val messages = JSONArray()
        history.takeLast(30).forEach { message ->
            messages.put(JSONObject().put("role", message.role).put("content", message.content))
        }
        val body = JSONObject()
            .put("model", config.selectedModel)
            .put("messages", messages)
            .put("temperature", 0.4)
        val json = post(endpoint, body.toString(), mapOf("Authorization" to "Bearer " + config.apiKey))
        return JSONObject(json)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("The provider returned no assistant message.")
    }

    private fun anthropic(config: ApiConnectionConfig, history: List<ChatMessage>): String {
        val endpoint = ApiDiscoveryEngine.apiRoot(config.baseUrl) + "/messages"
        val messages = JSONArray()
        history.filter { it.role != "system" }.takeLast(30).forEach { message ->
            messages.put(JSONObject().put("role", if (message.role == "assistant") "assistant" else "user").put("content", message.content))
        }
        val body = JSONObject()
            .put("model", config.selectedModel)
            .put("max_tokens", 2048)
            .put("messages", messages)
        val json = post(endpoint, body.toString(), mapOf(
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01"
        ))
        val content = JSONObject(json).optJSONArray("content")
        val parts = buildList {
            if (content != null) for (i in 0 until content.length()) {
                val item = content.optJSONObject(i) ?: continue
                if (item.optString("type") == "text") add(item.optString("text"))
            }
        }
        return parts.joinToString("\n").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("The provider returned no assistant message.")
    }

    private fun gemini(config: ApiConnectionConfig, history: List<ChatMessage>): String {
        val root = ApiDiscoveryEngine.geminiRoot(config.baseUrl)
        val endpoint = root + "/models/" + config.selectedModel + ":generateContent?key=" + java.net.URLEncoder.encode(config.apiKey, "UTF-8")
        val contents = JSONArray()
        history.filter { it.role != "system" }.takeLast(30).forEach { message ->
            contents.put(
                JSONObject()
                    .put("role", if (message.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", message.content)))
            )
        }
        val body = JSONObject().put("contents", contents)
        val json = post(endpoint, body.toString(), emptyMap())
        val candidates = JSONObject(json).optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        val texts = buildList {
            if (parts != null) for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (text.isNotBlank()) add(text)
            }
        }
        return texts.joinToString("\n").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("The provider returned no assistant message.")
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MAXIMUS-AI/1.1")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching {
                val root = JSONObject(text)
                root.optJSONObject("error")?.optString("message")
                    ?.ifBlank { root.optJSONObject("error")?.optString("type") }
            }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "HTTP " + code)
        }
        return text
    }
}
