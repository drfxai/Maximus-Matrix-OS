package ai.drfx.maximus.matrixai.llm

/**
 * Verified free-text model candidates from user-configured provider accounts.
 * A successful provider login is NOT proof of free pricing or remaining quota.
 * Never guess prices or model context from a provider label or model ID.
 *
 * The same scan also lists the chat models each saved key can reach ([ScanResult.keyed]); those
 * are never labelled free and are used only when the user allows routing to their own keys.
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
        val message: String,
        val kind: FreeCatalogCheckKind = FreeCatalogCheckKind.NO_ELIGIBLE_MODEL,
        val matchingCount: Int = 0
    )

    data class ScanResult(
        val offers: List<Offer>,
        val checks: List<ProviderStatus>,
        val configuredProviders: Int,
        /** Chat models on the user's own keys, best first per provider; pricing unknown or paid. */
        val keyed: List<KeyedRoute> = emptyList()
    ) {
        val diagnosis: String get() = when {
            offers.isNotEmpty() ->
                "${offers.size} verified zero-price catalog routes. Remaining account quota is unknown."
            configuredProviders == 0 ->
                "No configured account found. Add a provider key, or switch off free-only routing."
            keyed.isNotEmpty() ->
                "No catalog-confirmed zero-price text route. ${keyed.size} models on your own keys " +
                    "(${keyed.map { it.provider }.distinct().size} providers) can answer; provider charges may apply."
            else ->
                "No catalog-confirmed zero-price text route. An API key or promotional credits do not prove a model is free."
        }
    }

    /** Compatibility entry point for existing callers. */
    suspend fun refresh(): List<Offer> = refreshDetailed().offers

    suspend fun refreshDetailed(probes: (LlmProvider) -> Map<String, ModelProbeEvidence> = { emptyMap() }): ScanResult {
        val offers = mutableListOf<Offer>()
        val checks = mutableListOf<ProviderStatus>()
        val keyed = mutableListOf<KeyedRoute>()
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
                    "Catalog unavailable or authentication failed; no free-pricing claim.",
                    FreeCatalogCheckKind.CATALOG_UNAVAILABLE))
                continue
            }
            keyed += KeyedRoutes.rank(provider, catalog.baseUrl, catalog.models,
                store.loadModelForProvider(provider), probes(provider))
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
                if (price == null || !price.verified || !price.isFree) continue
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
                            // Keep the catalog's own capability evidence so personas needing tools,
                            // reasoning or long context can be routed instead of always failing.
                            capabilities = model.capabilities + ModelCapability.CHAT +
                                SmartComboModelScorer.contextCapabilities(context),
                            contextWindowTokens = context,
                            price = price,
                            p95TimeToFirstTokenMs = null,
                            quality = SmartComboModelScorer.quality(model.id, context)
                        ),
                        endpoint = catalog.baseUrl,
                        quotaScope = scope
                    )
                )
            }
            val (outcome, count) = when {
                qualified > 0 -> FreeCatalogCheckKind.VERIFIED_FREE to qualified
                chat == 0 -> FreeCatalogCheckKind.NO_CHAT_MODEL to 0
                free == 0 -> FreeCatalogCheckKind.NO_ZERO_PRICE to 0
                missingContext > 0 -> FreeCatalogCheckKind.MISSING_CONTEXT to free
                else -> FreeCatalogCheckKind.NO_ELIGIBLE_MODEL to 0
            }
            val description = when (outcome) {
                FreeCatalogCheckKind.VERIFIED_FREE ->
                    "$qualified verified free text route(s); live quota unknown."
                FreeCatalogCheckKind.NO_CHAT_MODEL ->
                    "Catalog contains no verified chat-capable model."
                FreeCatalogCheckKind.NO_ZERO_PRICE ->
                    "No published zero-price text pricing; free tier not proven."
                FreeCatalogCheckKind.MISSING_CONTEXT ->
                    "$free free-price model(s), but context length is not verified."
                else -> "No eligible free model."
            }
            checks.add(ProviderStatus(provider, description, outcome, count))
        }
        return ScanResult(offers.distinctBy { it.route.id }, checks, configured, keyed.distinctBy { it.id })
    }
}
