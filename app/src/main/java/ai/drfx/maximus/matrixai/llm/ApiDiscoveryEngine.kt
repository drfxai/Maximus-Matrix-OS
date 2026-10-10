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
        val provider = if (hinted == LlmProvider.UNKNOWN) LlmProvider.OPENAI_COMPATIBLE else hinted
        require(apiKey.isNotBlank()) { "Credentials are required to verify the catalog." }
        EndpointPolicy.validate(base, provider)
        val models = fetchModels(base, apiKey, provider)
        require(models.isNotEmpty()) { "No chat models were returned by the endpoint." }
        ApiDiscoveryResult(provider, base, models.map { it.copy(verified = true, capabilities = ModelCapabilityResolver.resolve(provider, it.id)) },
            "Authenticated catalog verified (${models.size} models). Inference has not been tested.")
    }

    private fun fetchModels(base: String, apiKey: String, provider: LlmProvider): List<ModelDescriptor> {
        val url = when (provider) {
            LlmProvider.GEMINI -> geminiRoot(base) + "/models"
            else -> apiRoot(base) + "/models"
        }
        val connection = open(url, "GET")
        applyAuth(connection, provider, apiKey)
        val body = read(connection)
        val json = JSONObject(body)
        return when (provider) {
            LlmProvider.GEMINI -> {
                val array = json.optJSONArray("models")
                val parsed = buildList {
                    if (array != null) {
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
                parsed
            }
            LlmProvider.ROUTER_9_SMART, LlmProvider.ROUTER_9_COMBO -> {
                val array = json.optJSONArray("data")
                if (array != null && array.length() > 0) {
                    buildList {
                        for (i in 0 until array.length()) {
                            val model = array.optJSONObject(i) ?: continue
                            val id = model.optString("id")
                            if (id.isNotBlank()) add(ModelDescriptor(id, id))
                        }
                    }
                } else {
                    emptyList()
                }
            }
            else -> {
                val array = json.optJSONArray("data")
                if (array != null && array.length() > 0) {
                    buildList {
                        for (i in 0 until array.length()) {
                            val model = array.optJSONObject(i) ?: continue
                            val id = model.optString("id")
                            if (id.isNotBlank()) add(ModelDescriptor(id, id))
                        }
                    }
                } else {
                    emptyList()
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
            LlmProvider.GEMINI -> connection.setRequestProperty("x-goog-api-key", apiKey)
            LlmProvider.ROUTER_9_SMART -> {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey)

            }
            LlmProvider.ROUTER_9_COMBO -> {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey)

            }
            else -> connection.setRequestProperty("Authorization", "Bearer " + apiKey)
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MAXIMUS-AI/1.3")
        }

    private fun read(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            val message = runCatching {
                val root = JSONObject(text)
                root.optJSONObject("error")?.optString("message")
                    ?.ifBlank { root.optString("message") }
            }.getOrNull()
            throw ProviderRequestException.fromHttp(code)
        }
        return text
    }

    private fun providerFromUrl(url: String, apiKey: String): LlmProvider {
        val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
        return when {
            host == "generativelanguage.googleapis.com" -> LlmProvider.GEMINI
            host == "integrate.api.nvidia.com" -> LlmProvider.NVIDIA
            host.contains("9router") -> {
                if (url.contains("combo") || apiKey.contains("combo")) LlmProvider.ROUTER_9_COMBO else LlmProvider.ROUTER_9_SMART
            }
            host == "api.openai.com" -> LlmProvider.OPENAI
            host == "api.anthropic.com" -> LlmProvider.ANTHROPIC
            else -> LlmProvider.UNKNOWN
        }
    }

    private fun normalizeDetectedProvider(candidate: LlmProvider, hinted: LlmProvider, base: String): LlmProvider {
        if (candidate != LlmProvider.OPENAI_COMPATIBLE) return candidate
        if (hinted == LlmProvider.OPENAI) return LlmProvider.OPENAI
        if (hinted == LlmProvider.NVIDIA) return LlmProvider.NVIDIA
        if (hinted == LlmProvider.ROUTER_9_SMART) return LlmProvider.ROUTER_9_SMART
        if (hinted == LlmProvider.ROUTER_9_COMBO) return LlmProvider.ROUTER_9_COMBO
        val host = runCatching { URL(base).host.lowercase() }.getOrDefault("")
        return when {
            "api.nvidia.com" in host || "nvidia.com" in host -> LlmProvider.NVIDIA
            "9router" in host -> LlmProvider.ROUTER_9_SMART
            host == "api.openai.com" -> LlmProvider.OPENAI
            else -> LlmProvider.OPENAI_COMPATIBLE
        }
    }

    private fun providerRequiresKey(provider: LlmProvider): Boolean =
        provider == LlmProvider.NVIDIA || provider == LlmProvider.ANTHROPIC ||
            provider == LlmProvider.GEMINI || provider.isNineRouter

    companion object {
        const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
        const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"
        const val ROUTER_9_BASE_URL = ""

        fun resolveBaseUrl(rawBaseUrl: String, apiKey: String): String {
            val input = rawBaseUrl.trim()
            if (apiKey.startsWith("nvapi-") && (input.isBlank() || input.contains("api.openai.com", ignoreCase = true))) {
                return NVIDIA_BASE_URL
            }
            if (apiKey.startsWith("AIza") && (input.isBlank() || input.contains("api.openai.com", ignoreCase = true))) {
                return GEMINI_BASE_URL
            }
            if (input.isBlank()) {
                return when {
                    apiKey.startsWith("nvapi-") -> NVIDIA_BASE_URL
                    apiKey.startsWith("AIza") -> GEMINI_BASE_URL
                    apiKey.startsWith("9r-") -> ROUTER_9_BASE_URL
                    else -> GEMINI_BASE_URL
                }
            }
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

        /** Suggestions are deliberately not evidence of availability. */
        fun defaultCatalog(provider: LlmProvider): List<ModelDescriptor> = when (provider) {
            LlmProvider.GEMINI -> listOf(ModelDescriptor("gemini-2.5-flash", "Gemini 2.5 Flash (unverified suggestion)"))
            LlmProvider.NVIDIA -> listOf(ModelDescriptor("meta/llama-3.3-70b-instruct", "Llama 3.3 (unverified suggestion)"))
            LlmProvider.OPENAI -> listOf(ModelDescriptor("gpt-4o", "GPT-4o (unverified suggestion)"))
            else -> emptyList()
        }

        private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
    }
}
