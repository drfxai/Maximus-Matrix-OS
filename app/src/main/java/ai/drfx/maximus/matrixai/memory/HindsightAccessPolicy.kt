package ai.drfx.maximus.matrixai.memory

import java.security.MessageDigest

/** One immutable scope decoded from one SharedPreferences.all snapshot. */
internal data class HindsightAccessScope(
    val revision: String,
    val endpoint: String,
    val credentialFingerprint: String,
    val project: String,
    val enabled: Boolean,
    val autoShare: Boolean
)

/** Credentials are held only in memory for one operation and never included in diagnostics. */
internal class HindsightAccessSnapshot(val scope: HindsightAccessScope, val token: String) {
    override fun toString(): String = "HindsightAccessSnapshot(credentials redacted)"
}

internal object HindsightAccessPolicy {
    const val REVISION_KEY = "access_revision"

    fun scope(values: Map<String, *>): HindsightAccessScope {
        check(values["endpoint"] == null || values["endpoint"] is String) {
            "Saved memory endpoint is invalid; reconnect the account."
        }
        check(values["token"] == null || values["token"] is String) {
            "Saved memory credential is invalid; reconnect the account."
        }
        val encrypted = values["token"] as? String ?: ""
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(encrypted.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val enabled = values["enabled"] as? Boolean ?: false
        return HindsightAccessScope(
            revision = values[REVISION_KEY] as? String ?: "",
            endpoint = (values["endpoint"] as? String).orEmpty()
                .ifBlank { HindsightMemoryClient.DEFAULT_ENDPOINT },
            credentialFingerprint = fingerprint,
            project = (values["active_project"] as? String).orEmpty()
                .takeIf { it.matches(Regex("[0-9a-f-]{36}")) }.orEmpty(),
            enabled = enabled,
            autoShare = enabled && (values["auto_share"] as? Boolean ?: false)
        )
    }

    /** Decryption may take time; both origin and encrypted key come from this SAME captured map. */
    fun capture(values: Map<String, *>, decrypt: (String) -> String): HindsightAccessSnapshot {
        val scope = scope(values)
        val encrypted = (values["token"] as? String).orEmpty()
        val key = if (encrypted.isBlank()) "" else {
            val decoded = runCatching { decrypt(encrypted) }.getOrNull()
            check(!decoded.isNullOrBlank()) { "Saved memory credential is unavailable; reconnect the account." }
            decoded
        }
        return HindsightAccessSnapshot(scope, key)
    }

    fun allowed(scope: HindsightAccessScope, project: String, automatic: Boolean = false): Boolean =
        scope.enabled && project.isNotBlank() && scope.project == project &&
            (!automatic || scope.autoShare)

    fun isCurrent(
        captured: HindsightAccessSnapshot,
        currentValues: Map<String, *>,
        automatic: Boolean = false
    ): Boolean = captured.scope == scope(currentValues) &&
        allowed(captured.scope, captured.scope.project, automatic)

    /** A supplied replacement key also revokes automatic sharing, even on the same host. */
    fun configurationChangesAccess(oldEndpoint: String, newEndpoint: String, replacingKey: Boolean): Boolean =
        oldEndpoint != newEndpoint || replacingKey
}
