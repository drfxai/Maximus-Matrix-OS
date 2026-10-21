package ai.drfx.maximus.matrixai.llm

/** A dated observation about one explicit test, never an entitlement or a routing approval. */
enum class ModelProbeOutcome { SUCCEEDED, FAILED, UNCERTAIN }

data class ModelProbeEvidence(val outcome: ModelProbeOutcome, val observedAtEpochMs: Long,
    val failure: ProviderFailure? = null) {
    fun isFresh(nowEpochMs: Long): Boolean =
        observedAtEpochMs >= 0 && nowEpochMs >= observedAtEpochMs && nowEpochMs - observedAtEpochMs <= ApiCatalogSession.CATALOG_TTL_MS
}
