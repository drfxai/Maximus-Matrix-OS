package ai.drfx.maximus.matrixai.llm

/** A dated observation about one explicit test, never an entitlement or a routing approval. */
enum class ModelProbeOutcome { SUCCEEDED, FAILED, UNCERTAIN }

data class ModelProbeEvidence(val outcome: ModelProbeOutcome, val observedAtEpochMs: Long,
    val failure: ProviderFailure? = null,
    /** Wall time of the test request when it answered; shown as responsiveness, never a quota. */
    val latencyMs: Long? = null) {
    fun isFresh(nowEpochMs: Long): Boolean =
        observedAtEpochMs >= 0 && nowEpochMs >= observedAtEpochMs && nowEpochMs - observedAtEpochMs <= ApiCatalogSession.CATALOG_TTL_MS
}

/** Orders and filters a catalog by test evidence, so pickers lead with models that really answered. */
object ModelCompatibilityFilter {
    fun hasPassing(models: List<ModelDescriptor>, evidence: Map<String, ModelProbeEvidence>): Boolean =
        models.any { evidence[it.id]?.outcome == ModelProbeOutcome.SUCCEEDED }

    /**
     * Once at least one model has answered, only answering models are shown unless [showAll];
     * the [selected] model is always kept so the current choice never disappears.
     */
    fun visible(models: List<ModelDescriptor>, evidence: Map<String, ModelProbeEvidence>,
        showAll: Boolean, selected: String = ""): List<ModelDescriptor> {
        val rank = { model: ModelDescriptor ->
            when (evidence[model.id]?.outcome) {
                ModelProbeOutcome.SUCCEEDED -> 0
                null -> 1
                ModelProbeOutcome.UNCERTAIN -> 2
                ModelProbeOutcome.FAILED -> 3
            }
        }
        val ordered = models.sortedBy(rank)
        if (showAll || !hasPassing(models, evidence)) return ordered
        return ordered.filter { evidence[it.id]?.outcome == ModelProbeOutcome.SUCCEEDED || it.id == selected }
    }
}
