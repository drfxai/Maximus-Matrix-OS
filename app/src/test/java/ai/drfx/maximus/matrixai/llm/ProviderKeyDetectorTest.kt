package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderKeyDetectorTest {
    @Test fun recognizesFirstPartyAndCompatibleKeyPrefixes() {
        assertEquals(LlmProvider.ANTHROPIC, ProviderKeyDetector.detect("sk-ant-api03-example")!!.provider)
        assertEquals(LlmProvider.GEMINI, ProviderKeyDetector.detect("AIzaSyExample123")!!.provider)
        assertEquals(LlmProvider.NVIDIA, ProviderKeyDetector.detect("nvapi-example-123")!!.provider)
        assertEquals(LlmProvider.OPENAI, ProviderKeyDetector.detect("sk-proj-example")!!.provider)
        val groq = ProviderKeyDetector.detect("gsk_example12345")!!
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, groq.provider)
        assertEquals("https://api.groq.com/openai/v1", groq.baseUrl)
        assertEquals("https://openrouter.ai/api/v1", ProviderKeyDetector.detect("sk-or-v1-example")!!.baseUrl)
        assertTrue(ProviderKeyDetector.detect("sk-legacy-1234567")!!.ambiguous)
        assertNull(ProviderKeyDetector.detect("unknownkey12345"))
        assertNull(ProviderKeyDetector.detect("short"))
    }

    @Test fun suggestedEndpointsPassTransportPolicy() {
        listOf("sk-ant-a12345678", "sk-or-a12345678", "AIza12345678", "nvapi-12345678", "gsk_12345678", "xai-12345678",
            "pplx-12345678", "csk-12345678", "fw_12345678", "hf_12345678", "sk-12345678").forEach { key ->
            val suggestion = ProviderKeyDetector.detect(key)!!
            EndpointPolicy.validate(suggestion.baseUrl, suggestion.provider)
            assertTrue(key, CredentialPolicy.compatible(suggestion.provider, key))
        }
    }

    @Test fun mismatchOnlyFlagsClearlyForeignKeys() {
        assertEquals(LlmProvider.GEMINI, ProviderKeyDetector.mismatch(LlmProvider.OPENAI, "AIza12345678")!!.provider)
        assertNull(ProviderKeyDetector.mismatch(LlmProvider.GEMINI, "AIza12345678"))
        assertNull(ProviderKeyDetector.mismatch(LlmProvider.OPENAI_COMPATIBLE, "gsk_12345678"))
        assertNull(ProviderKeyDetector.mismatch(LlmProvider.ANTHROPIC, "sk-12345678"))
    }

    @Test fun unknownKeyWithoutEndpointIsNotSentToGemini() {
        assertTrue(runCatching { ApiDiscoveryEngine.resolveBaseUrl("", "mystery-key-123") }.isFailure)
        assertEquals("https://api.groq.com/openai/v1", ApiDiscoveryEngine.resolveBaseUrl("", "gsk_12345678"))
    }

    @Test fun catalogDropsNonChatFamiliesWithoutEndpointMetadata() {
        listOf("gpt-5-codex", "o3-pro", "gpt-4o-search-preview", "gpt-4o-mini-tts", "gpt-4o-transcribe",
            "nvidia/nv-embedqa-e5-v5", "nvidia/llama-3.1-nemoguard-8b-content-safety", "nvidia/nemotron-4-340b-reward",
            "computer-use-preview", "o4-mini-deep-research", "sora-2").forEach {
            assertFalse(it, CatalogChatPolicy.isCandidate(JSONObject().put("id", it)))
        }
        listOf("gpt-4o", "gpt-4.1-mini", "o4-mini", "meta/llama-3.3-70b-instruct", "deepseek-chat", "llama-3.3-70b-versatile",
            "claude-sonnet-4-5", "openai/gpt-oss-120b", "qwen/qwen3-coder-480b-a35b-instruct").forEach {
            assertTrue(it, CatalogChatPolicy.isCandidate(JSONObject().put("id", it)))
        }
        assertFalse(CatalogChatPolicy.isGeminiChatCandidate("gemini-2.5-flash-preview-tts"))
        assertFalse(CatalogChatPolicy.isGeminiChatCandidate("imagen-4.0-generate-001"))
        assertTrue(CatalogChatPolicy.isGeminiChatCandidate("gemini-2.5-pro"))
        assertTrue(CatalogChatPolicy.isGeminiChatCandidate("gemma-3-27b-it"))
    }
}
