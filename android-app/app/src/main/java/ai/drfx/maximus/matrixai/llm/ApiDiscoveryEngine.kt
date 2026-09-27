package ai.drfx.maximus.matrixai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiDiscoveryEngine {
    suspend fun discover(rawBaseUrl: String, apiKey: String): ApiDiscoveryResult = withContext(Dispatchers.IO) {
        require(rawBaseUrl.isNotBlank()) { "API base URL is required." }
        require(apiKey.isNotBlank()) { "API key is required." }

        val base = normalizeBaseUrl(rawBaseUrl)
        val hinted = providerFromUrl(base)
        val order = when (hinted) {
            LlmProvider.OPENAI -> listOf(LlmProvider.OPENAI, LlmProvider.OPENAI_COMPATIBLE)
            LlmProvider.ANTHROPIC -> listOf(LlmProvider.ANTHROPIC)
            LlmProvider.GEMINI -> listOf(LlmProvider.GEMINI)
            else -> listOf(LlmProvider.OPENAI_COMPATIBLE, LlmProvider.ANTHROPIC, LlmProvider.GEMINI)
        }

        val errors = mutableListOf<String>()
        for (provider in order) {
            try {
                val models = fetchModels(base, apiKey, provider)
                if (models.isNotEmpty()) {
                    val detected = if (provider == LlmProvider.OPENAI_COMPATIBLE && hinted == LlmProvider.OPENAI) LlmProvider.OPENAI else provider
                    return@withContext ApiDiscoveryResult(
                        provider = detected,
                        baseUrl = base,
                        models = models.distinctBy { it.id }.sortedBy { it.displayName.lowercase() },
                        message = "Detected " + providerLabel(detected) + " with " + models.size + " supported model(s)."
                    )
                }
            } catch (error: Throwable) {
                errors += providerLabel(provider) + ": " + (error.message ?: "request failed")
            }
        }
        throw IllegalStateException(errors.joinToString(" | ").ifBlank { "No compatible model endpoint was detected." })
    }

    private fun fetchModels(base: String, apiKey: String, provider: LlmProvider): List<ModelDescriptor> {
        val url = when (provider) {
            LlmProvider.GEMINI -> geminiRoot(base) + "/models?key=" + encode(apiKey)
            else -> apiRoot(base) + "/models"
        }
        val connection = open(url, "GET")
        when (provider) {
            LlmProvider.ANTHROPIC -> {
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            }
            LlmProvider.GEMINI -> Unit
            else -> connection.setRequestProperty("Authorization", "Bearer " + apiKey)
        }
        val body = read(connection)
        val json = JSONObject(body)
        return when (provider) {
            LlmProvider.GEMINI -> {
                val array = json.optJSONArray("models") ?: return emptyList()
                buildList {
                    for (i in 0 until array.length()) {
                        val model = array.optJSONObject(i) ?: continue
                        val methods = model.optJSONArray("supportedGenerationMethods")
                        val supportsGenerate = methods?.let { arr ->
                            (0 until arr.length()).any { arr.optString(it) == "generateContent" }
                        } ?: true
                        if (!supportsGenerate) continue
                        val rawName = model.optString("name")
                        if (rawName.isBlank()) continue
                        val id = rawName.removePrefix("models/")
                        add(ModelDescriptor(id, model.optString("displayName").ifBlank { id }, setOf("chat")))
                    }
                }
            }
            else -> {
                val array = json.optJSONArray("data") ?: return emptyList()
                buildList {
                    for (i in 0 until array.length()) {
                        val model = array.optJSONObject(i) ?: continue
                        val id = model.optString("id")
                        if (id.isNotBlank()) add(ModelDescriptor(id, id, setOf("chat")))
                    }
                }
            }
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MAXIMUS-AI/1.1")
        }

    private fun read(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "HTTP " + code)
        }
        return text
    }

    private fun providerFromUrl(url: String): LlmProvider {
        val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
        return when {
            "openai.com" in host -> LlmProvider.OPENAI
            "anthropic.com" in host -> LlmProvider.ANTHROPIC
            "googleapis.com" in host || "generativelanguage" in host -> LlmProvider.GEMINI
            else -> LlmProvider.UNKNOWN
        }
    }

    companion object {
        fun normalizeBaseUrl(input: String): String {
            var value = input.trim().trimEnd('/')
            if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value
            return value
        }

        fun apiRoot(base: String): String = if (base.endsWith("/v1")) base else base + "/v1"

        fun geminiRoot(base: String): String {
            val normalized = base.trimEnd('/')
            return when {
                normalized.endsWith("/v1beta") -> normalized
                normalized.endsWith("/v1") -> normalized.dropLast(3) + "/v1beta"
                else -> normalized + "/v1beta"
            }
        }

        private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
        private fun providerLabel(provider: LlmProvider): String = provider.name.replace('_', ' ')
    }
}
