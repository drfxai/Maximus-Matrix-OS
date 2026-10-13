package ai.drfx.maximus.matrixai.llm

/**
 * A catalogue of verified free-text offers from user-configured provider accounts.
 * No credentials are cached here. No paid route or unverifiable context is eligible.
 * Provider model discovery is a preflight operation, never performed on the UI thread.
 */
internal class SmartComboFreeCatalog(
    private val discovery: ApiDiscoveryEngine,
    private val store: SecureApiConfigStore
) {
    data class Offer(
        val route: SmartComboPlanner.Route,
        val endpoint: String,
        val quotaScope: String
    )

    suspend fun refresh(): List<Offer> {
        val offers = mutableListOf<Offer>()
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
            val catalog = try { discovery.discover(endpoint, key, provider) } catch (_: Exception) { continue }
            val group = if (provider.isNineRouter) "nine-router" else provider.name
            val scope = group + "|" + catalog.baseUrl
            for (model in catalog.models) {
                val price = model.catalogFreeTextPrice ?: continue
                val context = model.catalogContextTokens ?: continue
                if (!model.verified || !price.verified || !price.isFree ||
                    ModelCapability.CHAT !in model.capabilities) continue
                offers.add(
                    Offer(
                        SmartComboPlanner.Route(
                            id = provider.name + "::" + model.id,
                            provider = provider,
                            modelId = model.id,
                            verified = true,
                            healthy = true,
                            capabilities = setOf(ModelCapability.CHAT), // Additional capabilities need provider-specific confirmation.
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
        }
        return offers.distinctBy { it.route.id }
    }
}
