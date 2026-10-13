package ai.drfx.maximus.matrixai.studio.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteWorkerProfileStoreTest {
    @Test fun credentialsAreEncryptedEndpointBoundAndRevocable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = RemoteWorkerProfileStore(context)
        val endpoint = "https://worker.example.com"
        val secret = "test-only-credential-0123456789abcdef"
        store.forget()
        try {
            store.save(endpoint, secret)
            assertEquals(endpoint, store.endpoint())
            assertTrue(store.hasCredential())
            assertEquals(secret, RemoteWorkerProfileStore(context).credentialFor(endpoint))
            assertEquals("", store.credentialFor("https://different.example.com"))
            val prefs = context.getSharedPreferences("maximus_worker_profile", android.content.Context.MODE_PRIVATE)
            assertFalse(prefs.getString("token", "").orEmpty().contains(secret))
        } finally { store.forget() }
        assertFalse(store.hasCredential())
        assertEquals("", store.endpoint())
    }
}
