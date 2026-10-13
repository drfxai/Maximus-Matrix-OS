package ai.drfx.maximus.matrixai.studio.remote

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
 * An independent credential scope for the remote Worker.
 * Never reuse API provider tokens or store the token in plaintext preferences.
 */
class RemoteWorkerProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("maximus_worker_profile", Context.MODE_PRIVATE)
    private val alias = "maximus_runtime_worker_token_v1"

    fun endpoint(): String = prefs.getString("endpoint", "").orEmpty()
    fun hasCredential(): Boolean = !prefs.getString("token", null).isNullOrBlank()
    fun credentialFor(endpoint: String): String {
        val normalized = RemoteWorkerEndpointPolicy.normalize(endpoint)
        if (normalized != this.endpoint()) return ""
        return runCatching { decrypt(prefs.getString("token", "").orEmpty()) }.getOrDefault("")
    }

    fun save(endpoint: String, token: String) {
        val normalized = RemoteWorkerEndpointPolicy.normalize(endpoint)
        require(token.length in 32..4096 && !token.contains('\n') && !token.contains('\r')) {
            "Invalid Worker credential."
        }
        check(prefs.edit()
            .putString("endpoint", normalized)
            .putString("token", encrypt(token))
            .commit()) { "Worker settings could not be saved." }
    }

    fun forget() {
        check(prefs.edit().clear().commit())
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val encrypted = Base64.decode(blob, Base64.NO_WRAP)
        require(encrypted.size > 28) { "Worker credential is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
        return cipher.doFinal(encrypted.copyOfRange(12, encrypted.size)).toString(Charsets.UTF_8)
    }
}
