package ai.drfx.maximus.matrixai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import java.util.UUID

class LlmChatClient {
    suspend fun send(
        config: ApiConnectionConfig,
        history: List<ChatMessage>,
        agent: AgentDescriptor,
        onDelta: ((String) -> Unit)? = null
    ): ChatCompletionResult = CancellableHttp.execute {
        EndpointPolicy.validate(config.baseUrl, config.provider)
        require(config.selectedModel.isNotBlank()) { "Select a discovered model before inference." }
        require(history.sumOf { it.content.length.toLong() + (it.attachment?.data?.length ?: 0) } <= AttachmentPolicy.MAX_BYTES * 4L / 3) { "Conversation payload exceeds the safe request limit. Start a new session or remove old attachments." }
        history.forEach { it.attachment?.let { attachment -> AttachmentPolicy.validate(config.provider, attachment) } }
        val last = history.lastOrNull()
        val audio = last?.attachment?.takeIf { it.mimeType.startsWith("audio/") }
        if (config.provider == LlmProvider.OPENAI && audio != null) {
            val transcript = transcribeOpenAi(config, audio)
            val textHistory = history.dropLast(1) + requireNotNull(last).copy(content = transcript, attachment = null)
            return@execute openAiWithFallback(config, textHistory, agent).copy(transcript = transcript)
        }
        when (config.provider) {
            LlmProvider.GEMINI -> gemini(config, history, agent)
            LlmProvider.NVIDIA -> openAiCompatible(config, history, agent, onDelta)
            LlmProvider.ROUTER_9_SMART, LlmProvider.ROUTER_9_COMBO -> openAiCompatible(config, history, agent, onDelta)
            LlmProvider.ANTHROPIC -> anthropic(config, history, agent)
            LlmProvider.OPENAI -> openAiCompatible(config, history, agent, onDelta)
            LlmProvider.OPENAI_COMPATIBLE, LlmProvider.UNKNOWN -> openAiCompatible(config, history, agent, onDelta)
        }
    }

    /** Uploads the recorded M4A only to the configured OpenAI transcription endpoint. */
    private fun transcribeOpenAi(config: ApiConnectionConfig, attachment: ChatAttachment): String {
        val boundary = "MaximusVoice" + UUID.randomUUID().toString().replace("-", "")
        val endpoint = ApiDiscoveryEngine.apiRoot(config.baseUrl) + "/audio/transcriptions"
        val connection = CancellableHttp.register(URL(endpoint).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer " + config.apiKey)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
        }
        try {
            connection.outputStream.use { output ->
                fun write(text: String) = output.write(text.toByteArray(Charsets.UTF_8))
                write("--$boundary\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-1\r\n")
                write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.m4a\"\r\n")
                write("Content-Type: audio/m4a\r\n\r\n")
                output.write(Base64.decode(attachment.data, Base64.DEFAULT))
                write("\r\n--$boundary--\r\n")
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = BoundedApiResponse.read(stream)
            if (connection.responseCode !in 200..299) {
                throw ProviderRequestException.fromHttp(connection.responseCode)
            }
            return JSONObject(response).optString("text").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Audio transcription returned no text.")
        } finally {
            connection.disconnect()
        }
    }

    private fun openAiWithFallback(
        config: ApiConnectionConfig,
        history: List<ChatMessage>,
        agent: AgentDescriptor
    ): ChatCompletionResult {
        // Explicit retries belong to the UI. Automatic secondary requests can duplicate costs/actions.
        return openAiCompatible(config, history, agent)
    }

    private fun router9(
        config: ApiConnectionConfig,
        history: List<ChatMessage>,
        agent: AgentDescriptor
    ): ChatCompletionResult {
        // 9Router deployments expose OpenAI-compatible models, including user-created combos.
        // No undocumented mode header or invented model alias is sent.
        return openAiCompatible(config, history, agent)
    }

    private fun openAiCompatible(
        config: ApiConnectionConfig,
        history: List<ChatMessage>,
        agent: AgentDescriptor,
        onDelta: ((String) -> Unit)? = null
    ): ChatCompletionResult {
        val endpoint = ApiDiscoveryEngine.apiRoot(config.baseUrl) + "/chat/completions"
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", agent.systemPrompt))
        history.takeLast(30).forEach { message ->
            val attachment = message.attachment
            val content: Any = if (attachment != null && attachment.mimeType.startsWith("image/") && !attachment.isText) {
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", message.content))
                    .put(JSONObject().put("type", "image_url").put("image_url",
                        JSONObject().put("url", "data:${attachment.mimeType};base64,${attachment.data}")))
            } else textWithFile(message)
            messages.put(JSONObject().put("role", message.role).put("content", content))
        }
        val body = JSONObject()
            .put("model", config.selectedModel)
            .put("messages", messages)
        if (config.provider == LlmProvider.NVIDIA) {
            body.put("max_tokens", 4096)
            body.put("stream", false)
            body.put("temperature", 0.3)
        } else {
            body.put("temperature", 0.35)
        }
        val headers = if (config.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer " + config.apiKey)
        if (onDelta != null) {
            body.put("stream", true)
            if (config.provider == LlmProvider.OPENAI) body.put("stream_options", JSONObject().put("include_usage", true))
            return streamOpenAi(endpoint, body.toString(), headers, history, onDelta)
        }
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
            (if (it.role == "assistant") "ASSISTANT" else "USER") + ": " + textWithFile(it)
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
            val attachment = message.attachment
            val content: Any = if (attachment != null && !attachment.isText) {
                val block = if (attachment.mimeType == "application/pdf")
                    JSONObject().put("type", "document")
                        .put("source", JSONObject().put("type", "base64").put("media_type", attachment.mimeType).put("data", attachment.data))
                else JSONObject().put("type", "image")
                    .put("source", JSONObject().put("type", "base64").put("media_type", attachment.mimeType).put("data", attachment.data))
                JSONArray().put(JSONObject().put("type", "text").put("text", message.content)).put(block)
            } else textWithFile(message)
            messages.put(
                JSONObject()
                    .put("role", if (message.role == "assistant") "assistant" else "user")
                    .put("content", content)
            )
        }
        val body = JSONObject()
            .put("model", config.selectedModel)
            .put("max_tokens", 4096)
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
        val cleanModel = config.selectedModel.removePrefix("models/")
        val endpoint = root + "/models/" + java.net.URLEncoder.encode(cleanModel, "UTF-8") + ":generateContent"
        val contents = JSONArray()
        history.filter { it.role != "system" }.takeLast(30).forEach { message ->
            val attachment = message.attachment
            val parts = JSONArray().put(JSONObject().put("text", textWithFile(message)))
            if (attachment != null && !attachment.isText) {
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().put("mimeType", attachment.mimeType).put("data", attachment.data)
                    )
                )
            }
            contents.put(
                JSONObject()
                    .put("role", if (message.role == "assistant") "model" else "user")
                    .put("parts", parts)
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", agent.systemPrompt))))
            .put("contents", contents)
        val json = JSONObject(post(endpoint, body.toString(), mapOf("x-goog-api-key" to config.apiKey)))
        val candidates = json.optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        val texts = buildList {
            if (parts != null) for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (text.isNotBlank()) add(text)
            }
        }
        val text = texts.joinToString("\n").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini returned no assistant response text.")
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
        val input = history.sumOf { (it.content.length / 4).coerceAtLeast(1) }
        val output = (response.length / 4).coerceAtLeast(1)
        return ChatUsage(input, output, input + output, estimated = true)
    }

    private fun textWithFile(message: ChatMessage): String {
        val file = message.attachment
        return if (file != null && file.isText)
            message.content + "\n\n[Attached text file: " + file.name + "]\n" + file.data
        else message.content
    }

    private fun streamOpenAi(
        endpoint: String, body: String, headers: Map<String, String>,
        history: List<ChatMessage>, onDelta: (String) -> Unit
    ): ChatCompletionResult {
        val connection = CancellableHttp.register(URL(endpoint).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw ProviderRequestException.fromHttp(connection.responseCode)
            require(connection.contentType?.startsWith("text/event-stream", true) == true) { "Provider did not return a supported event stream." }
            val parser = OpenAiStreamParser(onDelta)
            connection.inputStream.bufferedReader().use { reader ->
                val line = StringBuilder()
                var count = 0
                while (true) {
                    val next = reader.read()
                    if (next == -1) break
                    require(++count <= 4 * 1024 * 1024) { "Provider event stream exceeds the safe response limit." }
                    if (next == 10) {
                        if (!parser.line(line.toString().trimEnd('\r'))) break
                        line.setLength(0)
                    } else {
                        require(line.length < 512 * 1024) { "Provider event exceeds the safe response limit." }
                        line.append(next.toChar())
                    }
                }
                if (line.isNotEmpty()) parser.line(line.toString())
            }
            require(parser.completed) { "Provider stream ended unexpectedly. Retry explicitly; partial text was not saved as success." }
            val text = parser.text()
            require(text.isNotBlank()) { "Provider stream returned no assistant text." }
            return ChatCompletionResult(text, parser.usage ?: estimateUsage(history, text))
        } finally { connection.disconnect() }
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val connection = CancellableHttp.register(URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MAXIMUS-AI/1.3")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val text = BoundedApiResponse.read(if (code in 200..299) connection.inputStream else connection.errorStream)
        if (code !in 200..299) {
            val message = runCatching {
                val root = JSONObject(text)
                root.optJSONObject("error")?.optString("message")
                    ?.ifBlank { root.optJSONObject("error")?.optString("type") }
                    ?.ifBlank { root.optString("message") }
            }.getOrNull()
            connection.disconnect()
            throw ProviderRequestException.fromHttp(code)
        }
        connection.disconnect()
        return text
    }
}
