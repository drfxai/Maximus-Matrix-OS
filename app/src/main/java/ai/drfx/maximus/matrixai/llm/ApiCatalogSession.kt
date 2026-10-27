package ai.drfx.maximus.matrixai.llm

/** In-memory authorization scope. Credentials and catalog suggestions are never retained here. */
class ApiCatalogSession(private val clock: () -> Long = System::currentTimeMillis) {
    private var revision = 0L
    private var binding: Binding? = null
    private val probes = mutableMapOf<String, ModelProbeEvidence>()
    private data class Binding(val provider: LlmProvider, val endpoint: String, val credential: String,
        val ids: Set<String>, val checkedAt: Long)
    @Synchronized fun invalidate(): Long { binding = null; probes.clear(); return ++revision }
    @Synchronized fun currentRevision(): Long = revision
    @Synchronized fun isCurrent(expected: Long): Boolean = expected == revision
    @Synchronized fun bind(expected: Long, provider: LlmProvider, endpoint: String, credential: String,
        models: List<ModelDescriptor>): Boolean {
        if (expected != revision || credential.isBlank()) return false
        probes.clear()
        binding = Binding(provider, ApiDiscoveryEngine.normalizeBaseUrl(endpoint), credential,
            models.filter { it.verified && ModelCapability.CHAT in it.capabilities }.map { it.id }.toSet(), clock())
        return true
    }
    /** A model change revokes old probe approval without discarding this catalog. */
    @Synchronized fun changeSelection(): Long = ++revision
    @Synchronized fun permits(expected: Long, provider: LlmProvider, endpoint: String, credential: String, model: String): Boolean {
        val b = binding ?: return false
        val age = clock() - b.checkedAt
        return expected == revision && age in 0..CATALOG_TTL_MS && b.provider == provider &&
            b.endpoint == ApiDiscoveryEngine.normalizeBaseUrl(endpoint) && b.credential == credential && model in b.ids
    }
    /** Evidence can only be recorded for the currently authorized account/catalog/selection revision. */
    @Synchronized fun recordProbe(expected: Long, provider: LlmProvider, endpoint: String,
        credential: String, model: String, outcome: ModelProbeOutcome, failure: ProviderFailure? = null,
        latencyMs: Long? = null): Boolean {
        if (!permits(expected, provider, endpoint, credential, model)) return false
        probes[model] = ModelProbeEvidence(outcome, clock(), failure, latencyMs?.takeIf { it in 1L..120_000L })
        return true
    }
    /** Immutable projection; contains no credentials, prompts, response bodies or raw errors. */
    @Synchronized fun probeEvidence(): Map<String, ModelProbeEvidence> = probes.toMap()

    companion object {
        /**
         * How long an authenticated catalog (and its test results) stays valid. The credential
         * fingerprint already revokes it on any key or endpoint change; five minutes used to expire
         * the catalog mid-conversation and every send failed with "Model catalog is missing, stale…".
         */
        const val CATALOG_TTL_MS = 12 * 60 * 60 * 1000L
    }
}
