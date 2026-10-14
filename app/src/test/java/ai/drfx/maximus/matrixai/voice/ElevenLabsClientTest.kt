package ai.drfx.maximus.matrixai.voice

import org.junit.Assert.*
import org.junit.Test

class ElevenLabsClientTest {
    @Test fun voiceIdsCannotInjectPathsOrQueryParameters() {
        assertTrue(ElevenLabsClient.validVoiceId("JBFqnCBsd6RMkjVDRZzb"))
        assertFalse(ElevenLabsClient.validVoiceId("../voices?key=abc"))
        assertFalse(ElevenLabsClient.validVoiceId("voice-with spaces"))
        assertFalse(ElevenLabsClient.validVoiceId("x"))
    }

    @Test fun modelAllowListAndCredentialFormatAreStrict() {
        assertTrue(ElevenLabsClient.validModel(ElevenLabsClient.DEFAULT_MODEL))
        assertTrue(ElevenLabsClient.validModel(ElevenLabsClient.LOW_LATENCY_MODEL))
        assertFalse(ElevenLabsClient.validModel("arbitrary-priced-model"))
        assertTrue(ElevenLabsClient.validKey("test-owned-api-key-abcdefgh"))
        assertFalse(ElevenLabsClient.validKey("token with spaces"))
        assertFalse(ElevenLabsClient.validKey("token-\r\ninjection"))
    }

    @Test fun subscriptionBalanceDoesNotBecomeModelTokenBalance() {
        val limit = ElevenQuota("free", used = 9500, limit = 10000)
        assertEquals(500L, limit.remaining)
        assertEquals(0L, ElevenQuota("free", 11000, 10000).remaining)
    }
}
