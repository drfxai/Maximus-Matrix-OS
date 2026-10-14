package ai.drfx.maximus.matrixai.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
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
        check(prefs.edit().putString("active_project", id).commit())
    }

    fun configure(url: String, apiToken: String?) {
        val normalized = HindsightMemoryClient.normalizedEndpoint(url)
        require(normalized == endpoint() || apiToken?.isNotBlank() == true || !hasKey()) {
            "To change Hindsight host, enter a new key or disconnect the old account first."
        }
        val encrypted = apiToken?.takeIf { it.isNotBlank() }?.let {
            require(it.length in 12..512 && it.none { ch -> ch.isWhitespace() || ch.code < 32 || ch.code == 127 })
            encrypt(it)
        }
        val edit = prefs.edit().putString("endpoint", normalized)
        if (encrypted != null) edit.putString("token", encrypted)
        check(edit.commit()) { "Unable to securely save memory configuration." }
    }

    fun setEnabled(value: Boolean) {
        // Hindsight Cloud requires a user-owned API token; private HTTPS deployments may
        // authenticate at a separate edge. Account/bank permissions must be enforced there.
        HindsightMemoryClient.normalizedEndpoint(endpoint())
        if (value && endpoint() == HindsightMemoryClient.DEFAULT_ENDPOINT) {
            require(token().isNotBlank()) { "Hindsight Cloud API key required." }
        }
        check(prefs.edit().putBoolean("enabled", value).commit())
    }

    fun setAutoShare(value: Boolean) {
        require(!value || enabled()) { "Enable memory before sharing progress." }
        check(prefs.edit().putBoolean("auto_share", value).commit())
    }

    fun clear() {
        check(prefs.edit().clear().commit())
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
