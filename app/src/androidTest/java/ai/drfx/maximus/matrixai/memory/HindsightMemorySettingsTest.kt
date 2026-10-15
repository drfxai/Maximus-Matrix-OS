package ai.drfx.maximus.matrixai.memory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HindsightMemorySettingsTest {
    @Test fun userKeyEncryptedAndStatePersistsForCrossModelHandoff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = HindsightMemorySettings(context)
        settings.clear()
        try {
            assertFalse(settings.enabled())
            assertFalse(settings.autoShare())
            settings.configure("https://api.hindsight.vectorize.io",
                "hsk_owned_by_user_1234567890abcdef")
            val raw = context.getSharedPreferences(
                "maximus_shared_memory_v1", Context.MODE_PRIVATE)
                .getString("token", "").orEmpty()
            assertFalse(raw.contains("hsk_owned_by_user_1234567890abcdef"))
            assertEquals("hsk_owned_by_user_1234567890abcdef",
                HindsightMemorySettings(context).token())
            assertThrows(IllegalArgumentException::class.java) {
                settings.configure("https://another-memory.example.org", null)
            }
            assertEquals("https://api.hindsight.vectorize.io", settings.endpoint())
            settings.setActiveProject("123e4567-e89b-12d3-a456-426614174000")
            settings.setEnabled(true)
            assertTrue(settings.enabled())
            assertFalse(settings.autoShare())
            settings.setAutoShare(true)
            assertTrue(HindsightMemorySettings(context).autoShare())
            settings.rememberProgress("123e4567-e89b-12d3-a456-426614174000",
                "test-model", "Fix parser regression", "Patch proposed; tests not yet run.")
            val progress = HindsightMemorySettings(context)
                .recentProgress("123e4567-e89b-12d3-a456-426614174000")
            assertTrue(progress.contains("Fix parser regression"))
            val journalBytes = prefsValue(context, "journal_123e4567-e89b-12d3-a456-426614174000")
            assertFalse(journalBytes.contains("Fix parser regression"))
            settings.rememberProgress("123e4567-e89b-12d3-a456-426614174000",
                "test-model", "password = unsafe-secret", "Should not persist")
            assertFalse(settings.recentProgress("123e4567-e89b-12d3-a456-426614174000")
                .contains("unsafe-secret"))
            assertEquals("123e4567-e89b-12d3-a456-426614174000",
                HindsightMemorySettings(context).activeProject())
            settings.disconnect()
            assertFalse(settings.hasKey())
            assertFalse(settings.autoShare())
            assertTrue(settings.recentProgress("123e4567-e89b-12d3-a456-426614174000")
                .contains("Fix parser regression"))
            settings.clear()
            assertFalse(settings.hasKey())
            assertFalse(settings.enabled())
            assertFalse(settings.autoShare())
        } finally { settings.clear() }
    }

    @Test fun accountSwitchAcrossSettingsInstancesRevokesPendingAccessAndSharing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = HindsightMemorySettings(context)
        val second = HindsightMemorySettings(context)
        first.clear()
        try {
            val project = "123e4567-e89b-12d3-a456-426614174000"
            first.configure("https://memory-a.example.org", "test-account-a-owned-key-123456")
            first.setActiveProject(project)
            first.setEnabled(true)
            first.setAutoShare(true)
            val requestA = first.accessSnapshot()
            second.configure("https://memory-b.example.org", "test-account-b-owned-key-123456")
            assertFalse(first.autoShare())
            assertFalse(second.autoShare())
            val requestB = first.accessSnapshot()
            assertEquals("https://memory-b.example.org", requestB.scope.endpoint)
            assertEquals("test-account-b-owned-key-123456", requestB.token)
            assertNotEquals(requestA.scope.revision, requestB.scope.revision)
            assertThrows(IllegalStateException::class.java) { first.requireCurrentAccess(requestA) }
            second.setAutoShare(true)
            assertThrows(IllegalStateException::class.java) { first.requireCurrentAccess(requestA, automatic = true) }

            second.configure("https://memory-a.example.org", "test-account-a-owned-key-123456")
            second.setAutoShare(true)
            assertThrows(IllegalStateException::class.java) { first.requireCurrentAccess(requestA) }
            val returnedToA = first.accessSnapshot()
            assertEquals(requestA.scope.endpoint, returnedToA.scope.endpoint)
            assertEquals(requestA.token, returnedToA.token)
            assertNotEquals(requestA.scope.revision, returnedToA.scope.revision)
            first.requireCurrentAccess(returnedToA, automatic = true)
            second.setActiveProject("123e4567-e89b-12d3-a456-426614174001")
            assertThrows(IllegalStateException::class.java) { first.requireCurrentAccess(returnedToA) }
        } finally { first.clear() }
    }

    @Test fun corruptSavedKeyCannotBecomeAnAnonymousPrivateEndpointRequest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = HindsightMemorySettings(context)
        settings.clear()
        try {
            settings.configure("https://memory.example.org", "test-account-owned-key-123456")
            settings.setActiveProject("123e4567-e89b-12d3-a456-426614174000")
            settings.setEnabled(true)
            val beforeCorruption = settings.accessSnapshot()
            val prefs = context.getSharedPreferences("maximus_shared_memory_v1", Context.MODE_PRIVATE)
            assertTrue(prefs.edit().putString("token", "not-valid-encrypted-key-data").commit())
            assertThrows(IllegalStateException::class.java) { settings.accessSnapshot() }
            assertThrows(IllegalStateException::class.java) { settings.requireCurrentAccess(beforeCorruption) }
        } finally { settings.clear() }
    }

    @Test fun persistedJournalHandsOffNewestModelProgressWithinActualBudgets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = HindsightMemorySettings(context)
        val project = "123e4567-e89b-12d3-a456-426614174000"
        settings.clear()
        try {
            for (revision in 1..4) {
                settings.rememberProgress(project, "model-$revision",
                    "Task $revision " + "requirements ".repeat(40),
                    "Result $revision " + "proposed implementation ".repeat(45))
            }
            val handoff = HindsightMemorySettings(context).recentProgress(project)
            assertTrue(handoff.startsWith("Model: model-4. Task: Task 4"))
            assertTrue(handoff.length <= 2100)
            assertTrue(handoff.take(950).startsWith("Model: model-4. Task: Task 4"))
            assertFalse(handoff.take(950).contains("Model: model-3"))
            assertFalse(handoff.contains("Model: model-1"))
        } finally { settings.clear() }
    }

    @Test fun explicitlyKeylessPrivateAccessStillWorksAndDisconnectInvalidatesSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = HindsightMemorySettings(context)
        settings.clear()
        try {
            settings.configure("https://private-memory.example.org", null)
            settings.setActiveProject("123e4567-e89b-12d3-a456-426614174000")
            settings.setEnabled(true)
            val access = settings.accessSnapshot()
            assertEquals("", access.token)
            settings.requireCurrentAccess(access)
            settings.disconnect()
            assertThrows(IllegalStateException::class.java) { settings.requireCurrentAccess(access) }
        } finally { settings.clear() }
    }

    @Test fun optionalPromptMemoryNeverAdoptsNewAccessOrBreaksChatOnUnreadableKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = HindsightMemorySettings(context)
        val bridge = SharedProjectMemory(context)
        val project = "123e4567-e89b-12d3-a456-426614174000"
        settings.clear()
        try {
            settings.configure("https://memory-a.example.org", "test-account-a-owned-key-123456")
            settings.setActiveProject(project)
            settings.setEnabled(true)
            settings.rememberProgress(project, "model-a", "A-only recent task", "A-only proposal")
            bridge.warmLocalCheckpoint()
            val requestA = bridge.captureAccess()
            assertTrue(bridge.cachedContext(requestA).contains("A-only recent task"))
            assertEquals("", bridge.cachedContext(null))
            settings.configure("https://memory-b.example.org", "test-account-b-owned-key-123456")
            assertEquals("", bridge.cachedContext(requestA))
            val prefs = context.getSharedPreferences("maximus_shared_memory_v1", Context.MODE_PRIVATE)
            assertTrue(prefs.edit().putString("token", "not-valid-encrypted-key-data").commit())
            val unavailable = runCatching { bridge.captureAccess() }.getOrNull()
            assertNull(unavailable)
            assertEquals("", bridge.cachedContext(unavailable))
            assertEquals("", bridge.cachedContext(requestA))
            settings.setEnabled(false)
            assertEquals("", bridge.cachedContext(unavailable))
        } finally { bridge.forgetCache(); settings.clear() }
    }

    private fun prefsValue(context: Context, key: String): String =
        context.getSharedPreferences("maximus_shared_memory_v1", Context.MODE_PRIVATE)
            .getString(key, "").orEmpty()
}
