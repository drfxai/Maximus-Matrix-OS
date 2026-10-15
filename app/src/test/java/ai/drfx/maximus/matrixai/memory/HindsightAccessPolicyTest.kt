package ai.drfx.maximus.matrixai.memory

import org.junit.Assert.*
import org.junit.Test

class HindsightAccessPolicyTest {
    private val project = "123e4567-e89b-12d3-a456-426614174000"
    private fun account(id: String = "a"): Map<String, Any> = mapOf(
        "endpoint" to "https://memory-$id.example.org",
        "token" to "encrypted-test-$id",
        "active_project" to project,
        "enabled" to true,
        "auto_share" to true,
        HindsightAccessPolicy.REVISION_KEY to "revision-$id"
    )
    private fun capture(values: Map<String, *>) = HindsightAccessPolicy.capture(values) {
        "decoded-" + it.removePrefix("encrypted-")
    }

    @Test fun aConcurrentAccountSwitchCannotMixOriginAndCredential() {
        var current = account("a")
        // SharedPreferences.all returns this one captured map. An account switch
        // during slow keystore decryption must not change either half of the pair.
        val capturedMap = current
        val operation = HindsightAccessPolicy.capture(capturedMap) { encrypted ->
            current = account("b")
            "decoded-" + encrypted.removePrefix("encrypted-")
        }
        assertEquals("https://memory-a.example.org", operation.scope.endpoint)
        assertEquals("decoded-test-a", operation.token)
        assertFalse(HindsightAccessPolicy.isCurrent(operation, current))
        val next = capture(current)
        assertEquals("https://memory-b.example.org", next.scope.endpoint)
        assertEquals("decoded-test-b", next.token)
    }

    @Test fun accountProjectAndConsentChangesRejectQueuedOperationsAndLateResults() {
        val original = account()
        val queued = capture(original)
        val changes = listOf(
            account("b"),
            original + ("active_project" to "123e4567-e89b-12d3-a456-426614174001"),
            original + ("enabled" to false),
            original + ("auto_share" to false),
            original + ("token" to "different-encrypted-key"),
            original + (HindsightAccessPolicy.REVISION_KEY to "revoked-and-reenabled")
        )
        assertTrue(HindsightAccessPolicy.isCurrent(queued, original, automatic = true))
        for (current in changes) {
            var cacheWrites = 0
            if (HindsightAccessPolicy.isCurrent(queued, current, automatic = true)) cacheWrites++
            assertEquals("A late recall must not publish under a changed scope", 0, cacheWrites)
            assertFalse(HindsightAccessPolicy.isCurrent(queued, current, automatic = true))
        }
    }

    @Test fun disconnectAndReconnectCannotResurrectPreviouslyAuthorizedWork() {
        val first = account()
        val operation = capture(first)
        val disconnected = mapOf("enabled" to false, HindsightAccessPolicy.REVISION_KEY to "disconnect")
        assertFalse(HindsightAccessPolicy.isCurrent(operation, disconnected))
        val reconnected = first + (HindsightAccessPolicy.REVISION_KEY to "new-connection-same-account")
        assertFalse(HindsightAccessPolicy.isCurrent(operation, reconnected))
        assertTrue(HindsightAccessPolicy.isCurrent(capture(reconnected), reconnected, automatic = true))
    }

    @Test fun hostOrKeyChangesRequireFreshSharingConsent() {
        assertTrue(HindsightAccessPolicy.configurationChangesAccess("https://a.example.org", "https://b.example.org", false))
        assertTrue(HindsightAccessPolicy.configurationChangesAccess("https://a.example.org", "https://a.example.org", true))
        assertFalse(HindsightAccessPolicy.configurationChangesAccess("https://a.example.org", "https://a.example.org", false))
        val configuredWithoutSharing = account("b") + ("auto_share" to false)
        val newScope = capture(configuredWithoutSharing)
        assertTrue(HindsightAccessPolicy.allowed(newScope.scope, project))
        assertFalse(HindsightAccessPolicy.allowed(newScope.scope, project, automatic = true))
        assertFalse(HindsightAccessPolicy.allowed(newScope.scope, "another-project"))
    }

    @Test fun snapshotDiagnosticsRedactCredentialsAndScopeKeepsOnlyCiphertextFingerprint() {
        val values = account()
        val operation = capture(values)
        assertFalse(operation.toString().contains(operation.token))
        assertFalse(operation.scope.toString().contains(values["token"].toString()))
        assertEquals(64, operation.scope.credentialFingerprint.length)
        assertThrows(IllegalStateException::class.java) {
            HindsightAccessPolicy.capture(values) { error("keystore unavailable") }
        }
        assertThrows(IllegalStateException::class.java) {
            HindsightAccessPolicy.capture(values) { "" }
        }
        // An intentionally keyless private deployment remains a separate supported case.
        assertEquals("", HindsightAccessPolicy.capture(values - "token") { error("must not decrypt") }.token)
    }

    @Test fun oldChatCannotAdoptNewConsentAfterAccountSwitchOrSwitchBack() {
        val accountA = account("a")
        val requestStart = capture(accountA)
        val accountBReconsented = account("b") + (HindsightAccessPolicy.REVISION_KEY to "b-user-reconsented")
        val returnedToA = accountA + (HindsightAccessPolicy.REVISION_KEY to "a-user-reconsented-again")
        for (responseTimeAccess in listOf(accountBReconsented, returnedToA)) {
            assertFalse(HindsightAccessPolicy.isCurrent(requestStart, responseTimeAccess, automatic = true))
            assertFalse(HindsightAccessPolicy.isCurrent(requestStart, responseTimeAccess))
            assertTrue(HindsightAccessPolicy.isCurrent(capture(responseTimeAccess), responseTimeAccess, automatic = true))
        }
    }

    @Test fun wrongTypedConnectionFieldsCannotSubstituteDefaultHostOrAnonymousAccess() {
        for (invalid in listOf(account() + ("endpoint" to 42), account() + ("token" to true))) {
            assertThrows(IllegalStateException::class.java) { capture(invalid) }
        }
        val intentionallyAbsent = mapOf<String, Any?>("endpoint" to null, "token" to null)
        val anonymous = HindsightAccessPolicy.capture(intentionallyAbsent) { error("must not decrypt") }
        assertEquals(HindsightMemoryClient.DEFAULT_ENDPOINT, anonymous.scope.endpoint)
        assertEquals("", anonymous.token)
        assertFalse(anonymous.scope.enabled)
    }
}
