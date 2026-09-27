package ai.drfx.maximus.matrixai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LlmChatClient {
    suspend fun send(
        config: ApiConnectionConfig,
        history: List<ChatMessage>,
        agent: AgentDescriptor
    ): ChatCompletionResult = withContext(Dispatchers.IO) {
        when (config.provider) {
            LlmProvider.ANTHROPIC -> anthropic(config, history, agent)
            LlmProvider.GEMINI -> gemini(config, history, agent)
            LlmProvider.OPENAI -> openAiWithFallback(config, history, agent)
            LlmProvider.NVIDIA -> openAiCompatible(config, history, agent)
            LlmProvider.OPENAI_COMPATIBLE, LlmProvider.UNKNOWN -> openAiCompatible(config, history, agent)
        }
    }

    private fun openAiWithFallback(
        config: ApiConnectionConfig,
        history: List<ChatMessage>,
        agent: AgentDescriptor
    ): ChatCompletionResult {
        return try {
            openAiCompatible(config, history, agent)
        } catch (chatError: Throwable) {
            try {
                openAiResponses(config, history, agent)
            } catch (responsesError: Throwable) {
                throw IllegalStateException(
                    "OpenAI chat failed: " + (chatError.message ?: "unknown") +
                        " | Responses API failed: " + (responsesError.message ?: "unknown")
                )
            }
        }
    }

    private fun openAiCompatible(
        config: ApiConnectionConfig,
        history: List<ChatMessage>,
        agent: AgentDescriptor
    ): ChatCompletionResult {
        val endpoint = ApiDiscoveryEngine.apiRoot(config.baseUrl) + "/chat/completions"
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", agent.systemPrompt))
        history.takeLast(30).forEach { message ->
            messages.put(JSONObject().put("role", message.role).put("content", message.content))
        }
        val body = JSONObject()
            .put("model", config.selectedModel)
            .put("messages", messages)
        if (config.provider == LlmProvider.NVIDIA) {
            body.put("max_tokens", 2048)
            body.put("stream", false)
        } else {
            body.put("temperature", 0.35)
        }
        val headers = if (config.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer " + config.apiKey)
        val json = JSONObject(post(endpoint, body.toString(), headers))
        val text = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("The provider returned no assistant message.")
        return ChatCompletionResult(text, parseOpenAiUsage(json) ?: estimateUsage(history, text))
    }

    private fun openAiResponses(
        config: ApiConnectionConfig,
        history: List<ChatMessage>,
        agent: AgentDescriptor
    ): ChatCompletionResult {
        val endpoint = ApiDiscoveryEngine.apiRoot(config.baseUrl) + "/responses"
        val transcript = history.takeLast(24).joinToString("\n") {
            (if (it.role == "assistant") "ASSISTANT" else "USER") + ": " + it.content
        }
        val body = JSONObject()
            .put("model", config.selectedModel)
            .put("instructions", agent.systemPrompt)
            .put("input", transcript)
        val headers = if (config.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer " + config.apiKey)
        val json = JSONObject(post(endpoint, body.toString(), headers))
        val direct = json.optString("output_text")
        val text = if (direct.isNotBlank()) direct else extractResponsesText(json)
        if (text.isBlank()) throw IllegalStateException("The Responses API returned no assistant text.")
        return ChatCompletionResult(text, parseResponsesUsage(json) ?: estimateUsage(history, text))
    }

    private fun extractResponsesText(json: JSONObject): String {
        val output = json.optJSONArray("output") ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                val text = part.optString("text")
                if (text.isNotBlank()) parts += text
            }
        }
        return parts.joinToString("\n")
    }

    private fun anthropic(
        config: ApiConnectionConfig,
        history: List<ChatMessage>,
        agent: AgentDescriptor
    ): ChatCompletionResult {
        val endpoint = ApiDiscoveryEngine.apiRoot(config.baseUrl) + "/messages"
        val messages = JSONArray()
        history.filter { it.role != "system" }.takeLast(30).forEach { message ->
            messages.put(
                JSONObject()
                    .put("role", if (message.role == "assistant") "assistant" else "user")
                    .put("content", message.content)
            )
        }
        val body = JSONObject()
            .put("model", config.selectedModel)
            .put("max_tokens", 2048)
            .put("system", agent.systemPrompt)
            .put("messages", messages)
        val json = JSONObject(
            post(
                endpoint,
                body.toString(),
                mapOf(
                    "x-api-key" to config.apiKey,
                    "anthropic-version" to "2023-06-01"
                )
            )
        )
        val content = json.optJSONArray("content")
        val parts = buildList {
            if (content != null) for (i in 0 until content.length()) {
                val item = content.optJSONObject(i) ?: continue
                if (item.optString("type") == "text") add(item.optString("text"))
            }
        }
        val text = parts.joinToString("\n").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("The provider returned no assistant message.")
        val usage = json.optJSONObject("usage")
        return ChatCompletionResult(
            text,
            usage?.let {
                ChatUsage(
                    inputTokens = it.optInt("input_tokens", 0),
                    outputTokens = it.optInt("output_tokens", 0),
                    estimated = false
                )
            } ?: estimateUsage(history, text)
        )
    }

    private fun gemini(
        config: ApiConnectionConfig,
        history: List<ChatMessage>,
        agent: AgentDescriptor
    ): ChatCompletionResult {
        val root = ApiDiscoveryEngine.geminiRoot(config.baseUrl)
        val endpoint = root + "/models/" + config.selectedModel + ":generateContent?key=" +
            java.net.URLEncoder.encode(config.apiKey, "UTF-8")
        val contents = JSONArray()
        history.filter { it.role != "system" }.takeLast(30).forEach { message ->
            contents.put(
                JSONObject()
                    .put("role", if (message.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", message.content)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", agent.systemPrompt))))
            .put("contents", contents)
        val json = JSONObject(post(endpoint, body.toString(), emptyMap()))
        val candidates = json.optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        val texts = buildList {
            if (parts != null) for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (text.isNotBlank()) add(text)
            }
        }
        val text = texts.joinToString("\n").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("The provider returned no assistant message.")
        val usage = json.optJSONObject("usageMetadata")
        return ChatCompletionResult(
            text,
            usage?.let {
                ChatUsage(
                    inputTokens = it.optInt("promptTokenCount", 0),
                    outputTokens = it.optInt("candidatesTokenCount", 0),
                    totalTokens = it.optInt("totalTokenCount", 0),
                    estimated = false
                )
            } ?: estimateUsage(history, text)
        )
    }

    private fun parseOpenAiUsage(json: JSONObject): ChatUsage? {
        val usage = json.optJSONObject("usage") ?: return null
        return ChatUsage(
            inputTokens = usage.optInt("prompt_tokens", 0),
            outputTokens = usage.optInt("completion_tokens", 0),
            totalTokens = usage.optInt("total_tokens", 0),
            estimated = false
        )
    }

    private fun parseResponsesUsage(json: JSONObject): ChatUsage? {
        val usage = json.optJSONObject("usage") ?: return null
        return ChatUsage(
            inputTokens = usage.optInt("input_tokens", 0),
            outputTokens = usage.optInt("output_tokens", 0),
            totalTokens = usage.optInt("total_tokens", 0),
            estimated = false
        )
    }

    private fun estimateUsage(history: List<ChatMessage>, response: String): ChatUsage {
        val inputChars = history.takeLast(30).sumOf { it.content.length }
        val input = (inputChars / 4.0).toInt().coerceAtLeast(1)
        val output = (response.length / 4.0).toInt().coerceAtLeast(1)
        return ChatUsage(input, output, input + output, estimated = true)
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MAXIMUS-AI/1.2")
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
                    ?.ifBlank { root.optString("message") }
            }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "HTTP " + code)
        }
        return text
    }
}
