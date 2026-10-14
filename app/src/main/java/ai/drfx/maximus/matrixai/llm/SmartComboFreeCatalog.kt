package ai.drfx.maximus.matrixai.llm

/**
 * Verified free-text model candidates from user-configured provider accounts.
 * A successful provider login is NOT proof of free pricing or remaining quota.
 * Never guess prices or model context from a provider label or model ID.
 */
internal class SmartComboFreeCatalog(
    private val discovery: ApiDiscoveryEngine,
    private val store: SecureApiConfigStore
) {
    data class Offer(
        val route: SmartComboPlanner.Route,
        val endpoint: String,
        val quotaScope: String,
        val checkedAtEpochMs: Long = System.currentTimeMillis()
    )

    data class ProviderStatus(
        val provider: LlmProvider,
        /** Intentionally excludes key material, custom URLs and response bodies. */
        val message: String
    )

    data class ScanResult(
        val offers: List<Offer>,
        val checks: List<ProviderStatus>,
        val configuredProviders: Int
    ) {
        val diagnosis: String get() = when {
            offers.isNotEmpty() ->
                "${offers.size} verified zero-price catalog routes. Remaining account quota is unknown."
            configuredProviders == 0 ->
                "No configured account found. Add a provider key, or switch off free-only routing."
            else ->
                "No catalog-confirmed zero-price text route. An API key or promotional credits do not prove a model is free."
        }
    }

    /** Compatibility entry point for existing callers. */
    suspend fun refresh(): List<Offer> = refreshDetailed().offers

    suspend fun refreshDetailed(): ScanResult {
        val offers = mutableListOf<Offer>()
        val checks = mutableListOf<ProviderStatus>()
        var configured = 0
        val supported = listOf(
            LlmProvider.ROUTER_9_COMBO,
            LlmProvider.ROUTER_9_SMART,
            LlmProvider.OPENAI_COMPATIBLE,
            LlmProvider.GEMINI,
            LlmProvider.NVIDIA,
            LlmProvider.OPENAI,
            LlmProvider.ANTHROPIC
        )
        for (provider in supported) {
            val endpoint = store.loadBaseUrlForProvider(provider)
            val key = runCatching { store.resolveCredential(provider, endpoint) }.getOrDefault("")
            if (key.isBlank()) continue
            configured++
            val catalog = try {
                discovery.discover(endpoint, key, provider)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                checks.add(ProviderStatus(provider,
                    "Catalog unavailable or authentication failed; no free-pricing claim."))
                continue
            }
            val scope = (if (provider.isNineRouter) "nine-router" else provider.name) +
                "|" + catalog.baseUrl
            var chat = 0
            var free = 0
            var missingContext = 0
            var qualified = 0
            for (model in catalog.models) {
                if (!model.verified || ModelCapability.CHAT !in model.capabilities) continue
                chat++
                val price = model.catalogFreeTextPrice
                if (price?.verified != true || !price.isFree) continue
                free++
                val context = model.catalogContextTokens
                if (context == null) {
                    missingContext++
                    continue
                }
                qualified++
                offers.add(
                    Offer(
                        SmartComboPlanner.Route(
                            id = provider.name + "::" + model.id,
                            provider = provider,
                            modelId = model.id,
                            verified = true,
                            healthy = true,
                            capabilities = setOf(ModelCapability.CHAT),
                            contextWindowTokens = context,
                            price = price,
                            p95TimeToFirstTokenMs = null,
                            quality = 75
                        ),
                        endpoint = catalog.baseUrl,
                        quotaScope = scope
                    )
                )
            }
            checks.add(ProviderStatus(provider, when {
                qualified > 0 -> "$qualified verified free text route(s); live quota unknown."
                chat == 0 -> "Catalog contains no verified chat-capable model."
                free == 0 -> "No published zero-price text pricing; free tier not proven."
                missingContext > 0 -> "$free free-price model(s), but context length is not verified."
                else -> "No eligible free model."
            }))
        }
        return ScanResult(offers.distinctBy { it.route.id }, checks, configured)
    }
}
