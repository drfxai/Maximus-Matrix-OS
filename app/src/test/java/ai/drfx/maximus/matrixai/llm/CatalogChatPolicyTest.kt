package ai.drfx.maximus.matrixai.llm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatalogChatPolicyTest {
    @Test fun excludesNonChatFamiliesFromMixedProviderCatalog() {
        listOf("text-embedding-3-small", "whisper-1", "tts-1", "dall-e-3", "omni-moderation-latest", "gpt-4o-realtime-preview", "gpt-image-1", "davinci-002", "gpt-3.5-turbo-instruct", "bge-reranker").forEach {
            assertFalse(it, CatalogChatPolicy.isCandidate(JSONObject().put("id", it)))
        }
    }
    @Test fun explicitEndpointAndTaskEvidenceOverrideChatLookingNames() {
        assertFalse(CatalogChatPolicy.isCandidate(JSONObject().put("id", "my-chat").put("supported_endpoints", JSONArray().put("embeddings"))))
        assertFalse(CatalogChatPolicy.isCandidate(JSONObject().put("id", "my-chat").put("task", "text-to-image")))
        assertFalse(CatalogChatPolicy.isCandidate(JSONObject().put("id", "my-chat").put("architecture", JSONObject().put("output_modalities", JSONArray().put("image")))))
    }
    @Test fun allowsUnknownChatCandidatesWithoutClaimingInferenceVerification() {
        assertTrue(CatalogChatPolicy.isCandidate(JSONObject().put("id", "vendor/custom-chat")))
        assertTrue(CatalogChatPolicy.isCandidate(JSONObject().put("id", "gpt-4o-audio-preview").put("supported_endpoints", JSONArray().put("/v1/chat/completions"))))
        assertFalse(CatalogChatPolicy.isCandidate(JSONObject().put("id", "")))
    }
}
