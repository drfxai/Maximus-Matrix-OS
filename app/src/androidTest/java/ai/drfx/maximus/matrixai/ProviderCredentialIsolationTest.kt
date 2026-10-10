package ai.drfx.maximus.matrixai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ai.drfx.maximus.matrixai.llm.LlmProvider
import ai.drfx.maximus.matrixai.llm.SecureApiConfigStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real Android Keystore checks; fixture values are not usable provider credentials. */
class ProviderCredentialIsolationTest {
    private lateinit var store: SecureApiConfigStore
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("maximus_secure_api", Context.MODE_PRIVATE).edit().clear().commit()
        store = SecureApiConfigStore(context)
    }
    @After fun cleanup() { store.clear() }

    @Test fun selectingAnotherProviderNeverInheritsEncryptedKey() {
        store.save(LlmProvider.GEMINI.defaultBaseUrl, "AIza-fixture-not-a-real-key", LlmProvider.GEMINI, "gemini-2.5-flash")
        store.save(LlmProvider.NVIDIA.defaultBaseUrl, "", LlmProvider.NVIDIA, "fixture-model")
        assertFalse(store.hasKeyForProvider(LlmProvider.NVIDIA))
        assertEquals("", store.loadApiKey())
        assertEquals("AIza-fixture-not-a-real-key", store.loadKeyForProvider(LlmProvider.GEMINI))
        store.save(LlmProvider.NVIDIA.defaultBaseUrl, "nvapi-fixture-not-a-real-key", LlmProvider.NVIDIA, "fixture-model")
        assertEquals("nvapi-fixture-not-a-real-key", store.loadKeyForProvider(LlmProvider.NVIDIA))
        assertEquals("AIza-fixture-not-a-real-key", store.loadKeyForProvider(LlmProvider.GEMINI))
    }

    @Test fun savedCustomKeyIsBoundToItsTrustedEndpoint() {
        store.save("https://fixture.example/v1", "fixture-not-a-real-key", LlmProvider.OPENAI_COMPATIBLE, "fixture-model")
        assertEquals("fixture-not-a-real-key", store.resolveCredential(LlmProvider.OPENAI_COMPATIBLE, "https://fixture.example/v1"))
        assertEquals("", store.resolveCredential(LlmProvider.OPENAI_COMPATIBLE, "https://other.example/v1"))
        assertEquals("", store.loadKeyForProvider(LlmProvider.ROUTER_9_SMART))
    }

    @Test fun preferencesNeverContainPlaintextCredential() {
        val fixture = "AIza-fixture-not-a-real-key"
        store.save(LlmProvider.GEMINI.defaultBaseUrl, fixture, LlmProvider.GEMINI, "gemini-2.5-flash")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val encrypted = context.getSharedPreferences("maximus_secure_api", Context.MODE_PRIVATE).getString("api_key_GEMINI", null)
        assertNotNull(encrypted)
        assertNotEquals(fixture, encrypted)
    }
}
