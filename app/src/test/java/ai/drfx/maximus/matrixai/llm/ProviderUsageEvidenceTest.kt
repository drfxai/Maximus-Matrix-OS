package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderUsageEvidenceTest {
    @Test fun acceptsOnlyCompleteConsistentProviderCounts() {
        val evidence = JSONObject().put("prompt_tokens", 200)
            .put("completion_tokens", 40).put("total_tokens", 250)
        val result = ProviderUsageEvidence.parseOpenAi(evidence)
        assertNotNull(result)
        assertFalse(requireNotNull(result).estimated)
        assertEquals(250, result.totalTokens)
    }

    @Test fun emptyMissingNegativeAndInconsistentCountsAreUnknown() {
        assertNull(ProviderUsageEvidence.parseOpenAi(JSONObject()))
        assertNull(ProviderUsageEvidence.parseOpenAi(
            JSONObject().put("prompt_tokens", 10).put("completion_tokens", 20)))
        assertNull(ProviderUsageEvidence.parseOpenAi(
            JSONObject().put("prompt_tokens", 20).put("completion_tokens", 20).put("total_tokens", 10)))
        assertNull(ProviderUsageEvidence.parseOpenAi(
            JSONObject().put("prompt_tokens", -1).put("completion_tokens", 0).put("total_tokens", 0)))
        assertNull(ProviderUsageEvidence.parseOpenAi(
            JSONObject().put("prompt_tokens", "invalid").put("completion_tokens", 0).put("total_tokens", 0)))
    }

    @Test fun validatesResponsesApiFieldsIndependently() {
        val usage = JSONObject().put("input_tokens", 40).put("output_tokens", 60).put("total_tokens", 110)
        assertEquals(110, ProviderUsageEvidence.parseResponses(usage)?.totalTokens)
        assertNull(ProviderUsageEvidence.parseOpenAi(usage))
    }
}
