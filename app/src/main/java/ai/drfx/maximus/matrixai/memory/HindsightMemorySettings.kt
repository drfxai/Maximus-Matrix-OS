package ai.drfx.maximus.matrixai.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * User-owned cloud/self-hosted Hindsight integration, disabled by default. Project IDs are
 * per-workspace; shared model identities NEVER receive the Hindsight credential.
 */
internal class HindsightMemorySettings(context: Context) {
    private val prefs = context.getSharedPreferences("maximus_shared_memory_v1", Context.MODE_PRIVATE)
    private val alias = "maximus_hindsight_memory_aes_v1"

    companion object {
        // Shared across panel, chat and worker settings instances in this process.
        // This lock only protects local preference edits/cache publication, never HTTP.
        private val accessLock = Any()
    }

    fun accessScope(): HindsightAccessScope = HindsightAccessPolicy.scope(prefs.all)

    fun accessSnapshot(): HindsightAccessSnapshot =
        HindsightAccessPolicy.capture(prefs.all, ::decrypt)

    fun requireCurrentAccess(snapshot: HindsightAccessSnapshot, automatic: Boolean = false) {
        withCurrentAccess(snapshot, automatic) { }
    }

    fun <T> withCurrentAccess(
        snapshot: HindsightAccessSnapshot,
        automatic: Boolean = false,
        block: () -> T
    ): T = synchronized(accessLock) {
        check(HindsightAccessPolicy.isCurrent(snapshot, prefs.all, automatic)) {
            "Memory account, project or sharing consent changed; operation stopped."
        }
        block()
    }

    fun endpoint(): String = prefs.getString("endpoint", HindsightMemoryClient.DEFAULT_ENDPOINT)
        .orEmpty().ifBlank { HindsightMemoryClient.DEFAULT_ENDPOINT }

    fun token(): String = prefs.getString("token", "").orEmpty()
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { decrypt(it) }.getOrDefault("") }.orEmpty()

    fun hasKey(): Boolean = prefs.getString("token", "").orEmpty().isNotBlank()
    fun enabled(): Boolean = prefs.getBoolean("enabled", false)
    fun autoShare(): Boolean = enabled() && prefs.getBoolean("auto_share", false)
    fun activeProject(): String = prefs.getString("active_project", "").orEmpty()
        .takeIf { it.matches(Regex("[0-9a-f-]{36}")) }.orEmpty()

    fun setActiveProject(id: String) {
        require(id.isEmpty() || id.matches(Regex("[0-9a-f-]{36}")))
        synchronized(accessLock) {
            if (prefs.getString("active_project", "").orEmpty() == id) return
            check(prefs.edit().putString("active_project", id)
                .putString(HindsightAccessPolicy.REVISION_KEY, UUID.randomUUID().toString()).commit())
        }
    }

    fun configure(url: String, apiToken: String?) {
        val normalized = HindsightMemoryClient.normalizedEndpoint(url)
        val encrypted = apiToken?.takeIf { it.isNotBlank() }?.let {
            require(it.length in 12..512 && it.none { ch -> ch.isWhitespace() || ch.code < 32 || ch.code == 127 })
            encrypt(it)
        }
        synchronized(accessLock) {
            val values = prefs.all
            val oldEndpoint = HindsightAccessPolicy.scope(values).endpoint
            require(normalized == oldEndpoint || encrypted != null || (values["token"] as? String).isNullOrBlank()) {
                "To change Hindsight host, enter a new key or disconnect the old account first."
            }
            val edit = prefs.edit().putString("endpoint", normalized)
            if (encrypted != null) edit.putString("token", encrypted)
            if (HindsightAccessPolicy.configurationChangesAccess(oldEndpoint, normalized, encrypted != null)) {
                edit.putBoolean("auto_share", false)
                    .putString(HindsightAccessPolicy.REVISION_KEY, UUID.randomUUID().toString())
            }
            check(edit.commit()) { "Unable to securely save memory configuration." }
        }
    }

    fun setEnabled(value: Boolean) {
        // Revoking consent must remain possible even if a saved credential or
        // endpoint is unreadable. Disabling never needs to decrypt that key.
        if (!value) {
            synchronized(accessLock) {
                if (!prefs.getBoolean("enabled", false) && !prefs.getBoolean("auto_share", false)) return
                check(prefs.edit().putBoolean("enabled", false).putBoolean("auto_share", false)
                    .putString(HindsightAccessPolicy.REVISION_KEY, UUID.randomUUID().toString()).commit())
            }
            return
        }
        val captured = accessSnapshot()
        HindsightMemoryClient.normalizedEndpoint(captured.scope.endpoint)
        if (captured.scope.endpoint == HindsightMemoryClient.DEFAULT_ENDPOINT) {
            require(captured.token.isNotBlank()) { "Hindsight Cloud API key required." }
        }
        synchronized(accessLock) {
            check(captured.scope == HindsightAccessPolicy.scope(prefs.all)) {
                "Memory configuration changed; review it before enabling."
            }
            if (captured.scope.enabled) return
            check(prefs.edit().putBoolean("enabled", true)
                .putString(HindsightAccessPolicy.REVISION_KEY, UUID.randomUUID().toString()).commit())
        }
    }

    fun setAutoShare(value: Boolean) {
        synchronized(accessLock) {
            require(!value || prefs.getBoolean("enabled", false)) { "Enable memory before sharing progress." }
            if (prefs.getBoolean("auto_share", false) == value) return
            check(prefs.edit().putBoolean("auto_share", value)
                .putString(HindsightAccessPolicy.REVISION_KEY, UUID.randomUUID().toString()).commit())
        }
    }

    /** Small on-device encrypted progress journal shared by all models of the same project.
     * A successful chat may contribute a short excerpt, not source files or attachments. */
    @Synchronized
    fun rememberProgress(projectId: String, model: String, task: String, outcome: String) {
        HindsightMemoryClient.bankFor(projectId) // validates stable project identity
        val text = "Model: ${model.take(70)}. Task: ${task.take(320)}. " +
            "Proposed result: ${outcome.take(720)}. Unverified until files/tests are checked."
        if (!HindsightMemoryClient.shareable(text)) return
        val key = "journal_" + projectId
        val existing = prefs.getString(key, "").orEmpty()
        val list = runCatching {
            if (existing.isBlank()) JSONArray() else JSONArray(decrypt(existing))
        }.getOrDefault(JSONArray())
        list.put(JSONObject().put("time", System.currentTimeMillis()).put("text", text))
        val trimmed = JSONArray()
        for (i in (list.length() - 4).coerceAtLeast(0) until list.length()) {
            trimmed.put(list.getJSONObject(i))
        }
        check(prefs.edit().putString(key, encrypt(trimmed.toString())).commit()) {
            "Local project memory could not be saved."
        }
    }

    @Synchronized
    fun recentProgress(projectId: String): String {
        HindsightMemoryClient.bankFor(projectId)
        val value = prefs.getString("journal_" + projectId, "").orEmpty()
        if (value.isBlank()) return ""
        return runCatching {
            val array = JSONArray(decrypt(value))
            val nowMs = System.currentTimeMillis()
            val eligible = buildList {
                for (i in (array.length() - 4).coerceAtLeast(0) until array.length()) {
                    val item = array.getJSONObject(i)
                    val timestamp = item.optLong("time", 0)
                    val text = item.optString("text")
                    if (ProjectProgressContextPolicy.isRecentTimestamp(timestamp, nowMs) &&
                        HindsightMemoryClient.shareable(text)) add(text.take(1200))
                }
            }
            ProjectProgressContextPolicy.newestFirst(eligible)
        }.getOrDefault("")
    }

    /** Disconnect cloud access without destroying durable local project history. */
    fun disconnect() {
        synchronized(accessLock) {
            check(prefs.edit().remove("token").remove("endpoint")
                .putBoolean("enabled", false).putBoolean("auto_share", false)
                .putString(HindsightAccessPolicy.REVISION_KEY, UUID.randomUUID().toString()).commit())
        }
    }

    fun clear() {
        synchronized(accessLock) {
            check(prefs.edit().clear()
                .putString(HindsightAccessPolicy.REVISION_KEY, UUID.randomUUID().toString()).commit())
        }
    }

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return gen.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        return Base64.encodeToString(
            cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}
