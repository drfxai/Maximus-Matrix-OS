package ai.drfx.maximus.matrixai.llm

/** A provider/model ID does not identify a credential or gateway account. */
internal fun TokenMetrics.withoutProviderRateEvidence(): TokenMetrics = copy(
    verifiedRateRequestsRemaining = null,
    verifiedRateTokensRemaining = null,
    verifiedRequestRateKind = "",
    rateEvidenceAtEpochMs = null,
    rateProvider = LlmProvider.UNKNOWN,
    rateModelId = ""
)
