package ai.drfx.maximus.matrixai.llm

/** In-memory authorization scope. Credentials and catalog suggestions are never retained here. */
class ApiCatalogSession(private val clock: () -> Long = System::currentTimeMillis) {
    private var revision = 0L
    private var binding: Binding? = null
    private data class Binding(val provider: LlmProvider, val endpoint: String, val credential: String,
        val ids: Set<String>, val checkedAt: Long)
    @Synchronized fun invalidate(): Long { binding = null; return ++revision }
    @Synchronized fun currentRevision(): Long = revision
    @Synchronized fun isCurrent(expected: Long): Boolean = expected == revision
    @Synchronized fun bind(expected: Long, provider: LlmProvider, endpoint: String, credential: String,
        models: List<ModelDescriptor>): Boolean {
        if (expected != revision || credential.isBlank()) return false
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
    companion object { const val CATALOG_TTL_MS = 5 * 60 * 1000L }
}
