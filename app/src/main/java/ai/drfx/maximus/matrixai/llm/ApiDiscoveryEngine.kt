package ai.drfx.maximus.matrixai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiDiscoveryEngine {
    suspend fun discover(rawBaseUrl: String, apiKey: String): ApiDiscoveryResult = withContext(Dispatchers.IO) {
        val base = resolveBaseUrl(rawBaseUrl, apiKey)
        val hinted = providerFromUrl(base, apiKey)
        val order = when (hinted) {
            LlmProvider.OPENAI -> listOf(LlmProvider.OPENAI, LlmProvider.OPENAI_COMPATIBLE)
            LlmProvider.NVIDIA -> listOf(LlmProvider.NVIDIA)
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
                "No supported API protocol was detected. Use NVIDIA NIM, an OpenAI-compatible endpoint, Anthropic API, or Gemini API."
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
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MAXIMUS-AI/1.3")
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

    private fun providerFromUrl(url: String, apiKey: String): LlmProvider {
        val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
        return when {
            apiKey.startsWith("nvapi-") || "api.nvidia.com" in host || "nvidia.com" in host -> LlmProvider.NVIDIA
            "openai.com" in host -> LlmProvider.OPENAI
            "anthropic.com" in host -> LlmProvider.ANTHROPIC
            "googleapis.com" in host || "generativelanguage" in host -> LlmProvider.GEMINI
            else -> LlmProvider.UNKNOWN
        }
    }

    private fun normalizeDetectedProvider(candidate: LlmProvider, hinted: LlmProvider, base: String): LlmProvider {
        if (candidate != LlmProvider.OPENAI_COMPATIBLE) return candidate
        if (hinted == LlmProvider.OPENAI) return LlmProvider.OPENAI
        if (hinted == LlmProvider.NVIDIA) return LlmProvider.NVIDIA
        val host = runCatching { URL(base).host.lowercase() }.getOrDefault("")
        return when {
            "api.nvidia.com" in host || "nvidia.com" in host -> LlmProvider.NVIDIA
            "openai.com" in host -> LlmProvider.OPENAI
            else -> LlmProvider.OPENAI_COMPATIBLE
        }
    }

    private fun providerRequiresKey(provider: LlmProvider): Boolean =
        provider == LlmProvider.NVIDIA || provider == LlmProvider.ANTHROPIC || provider == LlmProvider.GEMINI

    companion object {
        const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"

        fun resolveBaseUrl(rawBaseUrl: String, apiKey: String): String {
            val input = rawBaseUrl.trim()
            if (apiKey.startsWith("nvapi-") && (input.isBlank() || input.contains("api.openai.com", ignoreCase = true))) {
                return NVIDIA_BASE_URL
            }
            require(input.isNotBlank()) { "API base URL is required." }
            return normalizeBaseUrl(input)
        }

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
