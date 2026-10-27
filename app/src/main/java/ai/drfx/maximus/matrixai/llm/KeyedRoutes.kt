package ai.drfx.maximus.matrixai.llm

/**
 * A chat model reachable with one of the user's own saved keys. Pricing is unknown or paid, so
 * these routes are used only when the user allows it: by Smart Combo after free routes, and by
 * Chat to take over from a model that failed mid-conversation.
 */
data class KeyedRoute(
    val provider: LlmProvider,
    val modelId: String,
    val endpoint: String,
    val capabilities: Set<ModelCapability>,
    val contextWindowTokens: Int,
    val quality: Int,
    /** Position in its provider's own preference order: 0 is the model the user last chose there. */
    val rank: Int = 0
) {
    val id: String get() = KeyedRoutes.id(provider, modelId)
}

internal object KeyedRoutes {
    /** Models per provider kept ready to take over; enough to skip a few broken ones. */
    const val PER_PROVIDER = 5

    fun id(provider: LlmProvider, modelId: String) = "key:" + provider.name + "::" + modelId

    /**
     * Orders one provider's catalog: the user's chosen model first, then models that answered a
     * test, then the strongest-looking ids. Models that failed a test are left out.
     */
    fun rank(
        provider: LlmProvider,
        endpoint: String,
        models: List<ModelDescriptor>,
        preferredModel: String,
        probes: Map<String, ModelProbeEvidence> = emptyMap(),
        limit: Int = PER_PROVIDER
    ): List<KeyedRoute> = models
        .filter { it.verified && ModelCapability.CHAT in it.capabilities }
        .filter { probes[it.id]?.outcome != ModelProbeOutcome.FAILED }
        .map { model ->
            val context = model.catalogContextTokens ?: ModelLimitsResolver.contextWindow(provider, model.id)
            model to SmartComboModelScorer.quality(model.id, context)
        }
        .sortedWith(compareBy<Pair<ModelDescriptor, Int>>(
            { it.first.id != preferredModel },
            { probes[it.first.id]?.outcome != ModelProbeOutcome.SUCCEEDED },
            { -it.second },
            { it.first.id }))
        .take(limit.coerceAtLeast(0))
        .mapIndexed { index, (model, quality) ->
            KeyedRoute(provider, model.id, endpoint,
                model.capabilities + ModelCapability.CHAT,
                model.catalogContextTokens ?: ModelLimitsResolver.contextWindow(provider, model.id),
                quality, index)
        }

    /**
     * Picks the next route to try.
     *
     * @param tried route ids (free or keyed) already used for this message.
     * @param failedProvider provider of the route that just failed, if any.
     * @param leaveProvider the failure affects every model of [failedProvider], so prefer another provider.
     * @param coolingDown routes whose recent failures put them on a short cooldown.
     */
    fun next(
        routes: List<KeyedRoute>,
        tried: Set<String>,
        requiredCapabilities: Set<ModelCapability> = emptySet(),
        failedProvider: LlmProvider? = null,
        leaveProvider: Boolean = false,
        coolingDown: (String) -> Boolean = { false },
        intent: SmartComboModelScorer.Intent = SmartComboModelScorer.Intent.GENERAL,
        requiredContextTokens: Int = 0
    ): KeyedRoute? {
        val open = routes.distinctBy { it.id }.filter { route ->
            route.id !in tried && route.capabilities.containsAll(requiredCapabilities) &&
                route.contextWindowTokens >= requiredContextTokens
        }
        val ready = open.filterNot { coolingDown(it.id) }.ifEmpty { open }
        // A model-level failure stays with the same account; a provider-wide one moves away from it.
        return ready.sortedWith(compareBy<KeyedRoute>(
            { failedProvider != null && (it.provider == failedProvider) == leaveProvider },
            { it.rank },
            { -(it.quality * 3.0 + SmartComboModelScorer.intentFit(it.modelId, intent, it.contextWindowTokens)) },
            { it.id })).firstOrNull()
    }
}
