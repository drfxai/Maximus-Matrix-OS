package ai.drfx.maximus.matrixai.studio.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App-private user-owned Hindsight credentials and per-project bank mapping. */
internal class HindsightMemorySettings(context: Context) {
    private val prefs = context.getSharedPreferences("matrix_hindsight_memory", Context.MODE_PRIVATE)
    private val alias = "matrix_hindsight_aes_v1"

    fun hasKey(): Boolean = prefs.contains("encrypted_key") && key().isNotBlank()
    fun key(): String = prefs.getString("encrypted_key", "").orEmpty().takeIf { it.isNotBlank() }
        ?.let { runCatching { decrypt(it) }.getOrDefault("") }.orEmpty()

    fun saveKey(value: String) {
        require(HindsightClient.validKey(value)) { "Invalid user Hindsight API key." }
        check(prefs.edit().putString("encrypted_key", encrypt(value)).commit()) {
            "Hindsight credentials could not be stored securely."
        }
    }

    /** Does not erase memory bank data on the server. */
    fun removeKey() {
        check(prefs.edit().remove("encrypted_key").commit())
    }

    /** Each local project defaults to its own isolated memory bank. */
    fun bank(projectId: String): String {
        require(projectId.matches(Regex("[a-f0-9-]{36}")))
        return prefs.getString("bank_$projectId", null)
            ?.takeIf { HindsightClient.validBankId(it) }
            ?: "matrix_project_" + projectId.replace("-", "")
    }

    /** Existing shared memory can be mapped to an imported workspace on another device. */
    fun setBank(projectId: String, bankId: String) {
        require(projectId.matches(Regex("[a-f0-9-]{36}")))
        require(HindsightClient.validBankId(bankId))
        check(prefs.edit().putString("bank_$projectId", bankId).commit())
    }

    fun enabledFor(projectId: String): Boolean =
        hasKey() && prefs.getBoolean("share_$projectId", false)

    fun enable(projectId: String, value: Boolean) {
        require(projectId.matches(Regex("[a-f0-9-]{36}")))
        require(!value || hasKey())
        check(prefs.edit().putBoolean("share_$projectId", value).commit())
    }

    private fun aes(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return gen.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aes())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP)
    }

    private fun decrypt(data: String): String {
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aes(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
