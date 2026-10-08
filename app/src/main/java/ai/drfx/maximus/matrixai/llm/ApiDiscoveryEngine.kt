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
            LlmProvider.GEMINI -> listOf(LlmProvider.GEMINI)
            LlmProvider.NVIDIA -> listOf(LlmProvider.NVIDIA)
            LlmProvider.ROUTER_9_SMART -> listOf(LlmProvider.ROUTER_9_SMART, LlmProvider.ROUTER_9_COMBO)
            LlmProvider.ROUTER_9_COMBO -> listOf(LlmProvider.ROUTER_9_COMBO, LlmProvider.ROUTER_9_SMART)
            LlmProvider.OPENAI -> listOf(LlmProvider.OPENAI, LlmProvider.OPENAI_COMPATIBLE)
            LlmProvider.ANTHROPIC -> listOf(LlmProvider.ANTHROPIC)
            else -> listOf(
                LlmProvider.GEMINI,
                LlmProvider.NVIDIA,
                LlmProvider.ROUTER_9_SMART,
                LlmProvider.OPENAI_COMPATIBLE,
                LlmProvider.ANTHROPIC
            )
        }

        val errors = mutableListOf<String>()
        for (provider in order) {
            try {
                if (providerRequiresKey(provider) && apiKey.isBlank()) {
                    // If no key is provided, provide the default offline catalog for user preview
                    val defaults = defaultCatalog(provider)
                    val detected = normalizeDetectedProvider(provider, hinted, base)
                    return@withContext ApiDiscoveryResult(
                        provider = detected,
                        baseUrl = base,
                        models = defaults,
                        message = "${detected.displayName}: configured with default model catalog. Add API key to chat."
                    )
                }
                val models = try {
                    fetchModels(base, apiKey, provider)
                } catch (netErr: Throwable) {
                    // Fallback to rich curated catalog if network endpoint is unreachable
                    val catalog = defaultCatalog(provider)
                    if (catalog.isNotEmpty()) catalog else throw netErr
                }
                if (models.isNotEmpty()) {
                    val detected = normalizeDetectedProvider(provider, hinted, base)
                    val profiled = models.map { model ->
                        val contextTokens = ModelLimitsResolver.contextWindow(detected, model.id)
                        model.copy(
                            capabilities = ModelCapabilityResolver.resolve(detected, model.id),
                            contextWindowTokens = contextTokens
                        )
                    }
                    return@withContext ApiDiscoveryResult(
                        provider = detected,
                        baseUrl = base,
                        models = profiled,
                        message = "Detected ${detected.displayName} with ${profiled.size} model(s)."
                    )
                }
            } catch (error: Throwable) {
                errors += provider.displayName + ": " + (error.message ?: "request failed")
            }
        }

        // Graceful fallback: return curated catalog based on hint
        val fallbackProvider = if (hinted != LlmProvider.UNKNOWN) hinted else LlmProvider.GEMINI
        val fallbackCatalog = defaultCatalog(fallbackProvider).map { model ->
            model.copy(
                capabilities = ModelCapabilityResolver.resolve(fallbackProvider, model.id),
                contextWindowTokens = ModelLimitsResolver.contextWindow(fallbackProvider, model.id)
            )
        }
        ApiDiscoveryResult(
            provider = fallbackProvider,
            baseUrl = base,
            models = fallbackCatalog,
            message = "Configured ${fallbackProvider.displayName} (${fallbackCatalog.size} models loaded)."
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
                // Ensure Gemini 3.8 Flash is prominently included at the top
                val defaultGemini = defaultCatalog(LlmProvider.GEMINI)
                val combined = mutableListOf<ModelDescriptor>()
                combined.addAll(defaultGemini)
                parsed.forEach { p ->
                    if (combined.none { it.id == p.id }) combined.add(p)
                }
                combined
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
                    defaultCatalog(provider)
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
                    defaultCatalog(provider)
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
            LlmProvider.ROUTER_9_SMART -> {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey)
                connection.setRequestProperty("X-Router-Mode", "smart")
            }
            LlmProvider.ROUTER_9_COMBO -> {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey)
                connection.setRequestProperty("X-Router-Mode", "combo")
            }
            else -> connection.setRequestProperty("Authorization", "Bearer " + apiKey)
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
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
            apiKey.startsWith("AIza", ignoreCase = true) || "googleapis.com" in host || "generativelanguage" in host -> LlmProvider.GEMINI
            apiKey.startsWith("nvapi-") || "api.nvidia.com" in host || "nvidia.com" in host -> LlmProvider.NVIDIA
            "9router" in host || url.contains("9router") || apiKey.startsWith("9r-") -> {
                if (url.contains("combo") || apiKey.contains("combo")) LlmProvider.ROUTER_9_COMBO else LlmProvider.ROUTER_9_SMART
            }
            "openai.com" in host -> LlmProvider.OPENAI
            "anthropic.com" in host -> LlmProvider.ANTHROPIC
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
            "openai.com" in host -> LlmProvider.OPENAI
            else -> LlmProvider.OPENAI_COMPATIBLE
        }
    }

    private fun providerRequiresKey(provider: LlmProvider): Boolean =
        provider == LlmProvider.NVIDIA || provider == LlmProvider.ANTHROPIC ||
            provider == LlmProvider.GEMINI || provider.isNineRouter

    companion object {
        const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
        const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"
        const val ROUTER_9_BASE_URL = "https://api.9router.com/v1"

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

        fun defaultCatalog(provider: LlmProvider): List<ModelDescriptor> {
            return when (provider) {
                LlmProvider.GEMINI -> listOf(
                    ModelDescriptor("gemini-3.8-flash", "Gemini 3.8 Flash (Primary Multi-Modal & Quant)", ModelCapabilityResolver.resolve(LlmProvider.GEMINI, "gemini-3.8-flash"), 1_048_576),
                    ModelDescriptor("gemini-3.5-flash", "Gemini 3.5 Flash", ModelCapabilityResolver.resolve(LlmProvider.GEMINI, "gemini-3.5-flash"), 1_048_576),
                    ModelDescriptor("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview (Complex Reasoning)", ModelCapabilityResolver.resolve(LlmProvider.GEMINI, "gemini-3.1-pro-preview"), 2_097_152),
                    ModelDescriptor("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite", ModelCapabilityResolver.resolve(LlmProvider.GEMINI, "gemini-3.1-flash-lite-preview"), 1_048_576),
                    ModelDescriptor("gemini-2.5-flash", "Gemini 2.5 Flash", ModelCapabilityResolver.resolve(LlmProvider.GEMINI, "gemini-2.5-flash"), 1_048_576),
                    ModelDescriptor("gemini-2.5-flash-image", "Gemini 2.5 Flash Image", ModelCapabilityResolver.resolve(LlmProvider.GEMINI, "gemini-2.5-flash-image"), 1_048_576),
                    ModelDescriptor("gemini-2.5-pro", "Gemini 2.5 Pro", ModelCapabilityResolver.resolve(LlmProvider.GEMINI, "gemini-2.5-pro"), 2_097_152)
                )
                LlmProvider.NVIDIA -> listOf(
                    ModelDescriptor("meta/llama-3.3-70b-instruct", "Meta Llama 3.3 70B Instruct (NVIDIA NIM)", ModelCapabilityResolver.resolve(LlmProvider.NVIDIA, "meta/llama-3.3-70b-instruct"), 131_072),
                    ModelDescriptor("deepseek-ai/deepseek-r1", "DeepSeek R1 Reasoning (NVIDIA NIM)", ModelCapabilityResolver.resolve(LlmProvider.NVIDIA, "deepseek-ai/deepseek-r1"), 131_072),
                    ModelDescriptor("nvidia/nemotron-4-340b-instruct", "NVIDIA Nemotron-4 340B (NVIDIA NIM)", ModelCapabilityResolver.resolve(LlmProvider.NVIDIA, "nvidia/nemotron-4-340b-instruct"), 131_072),
                    ModelDescriptor("mistralai/mistral-large-2-instruct", "Mistral Large 2 (NVIDIA NIM)", ModelCapabilityResolver.resolve(LlmProvider.NVIDIA, "mistralai/mistral-large-2-instruct"), 131_072),
                    ModelDescriptor("qwen/qwen2.5-72b-instruct", "Qwen 2.5 72B Instruct (NVIDIA NIM)", ModelCapabilityResolver.resolve(LlmProvider.NVIDIA, "qwen/qwen2.5-72b-instruct"), 131_072),
                    ModelDescriptor("nvidia/llama-3.1-nemotron-70b-instruct", "Llama 3.1 Nemotron 70B (NVIDIA NIM)", ModelCapabilityResolver.resolve(LlmProvider.NVIDIA, "nvidia/llama-3.1-nemotron-70b-instruct"), 131_072),
                    ModelDescriptor("meta/llama-3.1-8b-instruct", "Meta Llama 3.1 8B Instruct (NVIDIA NIM)", ModelCapabilityResolver.resolve(LlmProvider.NVIDIA, "meta/llama-3.1-8b-instruct"), 131_072),
                    ModelDescriptor("nvidia/neva-22b", "NVIDIA NeVA 22B Vision (NVIDIA NIM)", ModelCapabilityResolver.resolve(LlmProvider.NVIDIA, "nvidia/neva-22b"), 131_072)
                )
                LlmProvider.ROUTER_9_SMART -> listOf(
                    ModelDescriptor("9router-smart-auto", "9Router Smart (Autonomous Task Router)", ModelCapabilityResolver.resolve(LlmProvider.ROUTER_9_SMART, "9router-smart-auto"), 1_048_576),
                    ModelDescriptor("9router/smart-code", "9Router Smart Code", ModelCapabilityResolver.resolve(LlmProvider.ROUTER_9_SMART, "9router/smart-code"), 1_048_576),
                    ModelDescriptor("9router/smart-vision", "9Router Smart Vision", ModelCapabilityResolver.resolve(LlmProvider.ROUTER_9_SMART, "9router/smart-vision"), 1_048_576),
                    ModelDescriptor("9router/smart-reasoning", "9Router Smart Reasoning", ModelCapabilityResolver.resolve(LlmProvider.ROUTER_9_SMART, "9router/smart-reasoning"), 1_048_576),
                    ModelDescriptor("9router/smart-fast", "9Router Smart Fast", ModelCapabilityResolver.resolve(LlmProvider.ROUTER_9_SMART, "9router/smart-fast"), 131_072)
                )
                LlmProvider.ROUTER_9_COMBO -> listOf(
                    ModelDescriptor("9router-combo-synthesis", "9Router Combo (Multi-Model Synthesis)", ModelCapabilityResolver.resolve(LlmProvider.ROUTER_9_COMBO, "9router-combo-synthesis"), 262_144),
                    ModelDescriptor("9router/combo-consensus", "9Router Combo Consensus", ModelCapabilityResolver.resolve(LlmProvider.ROUTER_9_COMBO, "9router/combo-consensus"), 262_144),
                    ModelDescriptor("9router/combo-validator", "9Router Combo Validator", ModelCapabilityResolver.resolve(LlmProvider.ROUTER_9_COMBO, "9router/combo-validator"), 262_144)
                )
                LlmProvider.OPENAI -> listOf(
                    ModelDescriptor("gpt-4o", "GPT-4o (Omni)", ModelCapabilityResolver.resolve(LlmProvider.OPENAI, "gpt-4o"), 128_000),
                    ModelDescriptor("gpt-4o-mini", "GPT-4o Mini", ModelCapabilityResolver.resolve(LlmProvider.OPENAI, "gpt-4o-mini"), 128_000),
                    ModelDescriptor("o3-mini", "o3-mini Reasoning", ModelCapabilityResolver.resolve(LlmProvider.OPENAI, "o3-mini"), 128_000)
                )
                LlmProvider.ANTHROPIC -> listOf(
                    ModelDescriptor("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", ModelCapabilityResolver.resolve(LlmProvider.ANTHROPIC, "claude-3-5-sonnet-20241022"), 200_000),
                    ModelDescriptor("claude-3-7-sonnet", "Claude 3.7 Sonnet Hybrid", ModelCapabilityResolver.resolve(LlmProvider.ANTHROPIC, "claude-3-7-sonnet"), 200_000),
                    ModelDescriptor("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", ModelCapabilityResolver.resolve(LlmProvider.ANTHROPIC, "claude-3-5-haiku-20241022"), 200_000)
                )
                LlmProvider.OPENAI_COMPATIBLE, LlmProvider.UNKNOWN -> listOf(
                    ModelDescriptor("default-chat", "Default Chat Model", setOf(ModelCapability.CHAT), 128_000)
                )
            }
        }

        private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
    }
}
