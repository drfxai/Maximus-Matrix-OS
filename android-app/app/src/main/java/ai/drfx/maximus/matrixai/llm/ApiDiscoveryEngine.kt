package ai.drfx.maximus.matrixai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiDiscoveryEngine {
    suspend fun discover(rawBaseUrl: String, apiKey: String): ApiDiscoveryResult = withContext(Dispatchers.IO) {
        require(rawBaseUrl.isNotBlank()) { "API base URL is required." }

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
                if (providerRequiresKey(provider) && apiKey.isBlank()) {
                    errors += providerLabel(provider) + ": API key required"
                    continue
                }
                val models = fetchModels(base, apiKey, provider)
                if (models.isNotEmpty()) {
                    val detected = normalizeDetectedProvider(provider, hinted, base)
                    val profiled = models.map { model ->
                        model.copy(capabilities = ModelCapabilityResolver.resolve(detected, model.id))
                    }
                    return@withContext ApiDiscoveryResult(
                        provider = detected,
                        baseUrl = base,
                        models = profiled,
                        message = "Detected " + providerLabel(detected) + " with " + profiled.size + " model(s)."
                    )
                }
            } catch (error: Throwable) {
                errors += providerLabel(provider) + ": " + (error.message ?: "request failed")
            }
        }
        throw IllegalStateException(
            errors.joinToString(" | ").ifBlank {
                "No supported API protocol was detected. Use an OpenAI-compatible endpoint, Anthropic API, or Gemini API."
            }
        )
    }

    private fun fetchModels(base: String, apiKey: String, provider: LlmProvider): List<ModelDescriptor> {
        val url = when (provider) {
            LlmProvider.GEMINI -> geminiRoot(base) + "/models?key=" + encode(apiKey)
            else -> apiRoot(base) + "/models"
        }
        val connection = open(url, "GET")
        applyAuth(connection, provider, apiKey)
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
                        add(ModelDescriptor(id, model.optString("displayName").ifBlank { id }))
                    }
                }
            }
            else -> {
                val array = json.optJSONArray("data") ?: return emptyList()
                buildList {
                    for (i in 0 until array.length()) {
                        val model = array.optJSONObject(i) ?: continue
                        val id = model.optString("id")
                        if (id.isNotBlank()) add(ModelDescriptor(id, id))
                    }
                }
            }
        }
    }

    private fun applyAuth(connection: HttpURLConnection, provider: LlmProvider, apiKey: String) {
        if (apiKey.isBlank()) return
        when (provider) {
            LlmProvider.ANTHROPIC -> {
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            }
            LlmProvider.GEMINI -> Unit
            else -> connection.setRequestProperty("Authorization", "Bearer " + apiKey)
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MAXIMUS-AI/1.2")
        }

    private fun read(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching {
                val root = JSONObject(text)
                root.optJSONObject("error")?.optString("message")
                    ?.ifBlank { root.optString("message") }
            }.getOrNull()
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

    private fun normalizeDetectedProvider(candidate: LlmProvider, hinted: LlmProvider, base: String): LlmProvider {
        if (candidate != LlmProvider.OPENAI_COMPATIBLE) return candidate
        if (hinted == LlmProvider.OPENAI) return LlmProvider.OPENAI
        val host = runCatching { URL(base).host.lowercase() }.getOrDefault("")
        return if ("openai.com" in host) LlmProvider.OPENAI else LlmProvider.OPENAI_COMPATIBLE
    }

    private fun providerRequiresKey(provider: LlmProvider): Boolean =
        provider == LlmProvider.ANTHROPIC || provider == LlmProvider.GEMINI

    companion object {
        fun normalizeBaseUrl(input: String): String {
            var value = input.trim().trimEnd('/')
            if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value
            return value
        }

        fun apiRoot(base: String): String {
            val normalized = base.trimEnd('/')
            return if (normalized.endsWith("/v1")) normalized else normalized + "/v1"
        }

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
