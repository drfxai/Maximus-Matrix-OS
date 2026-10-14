package ai.drfx.maximus.matrixai.voice

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
 * User-supplied ElevenLabs key is encrypted at rest with a dedicated Android Keystore key.
 * Defaults to offline/device TTS. This setting is independent of chat-model API credentials.
 */
internal class ElevenVoiceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("maximus_eleven_voice", Context.MODE_PRIVATE)
    private val alias = "maximus_eleven_voice_aes_v1"

    fun hasKey(): Boolean = !prefs.getString("encrypted_key", null).isNullOrBlank()
    fun key(): String {
        val encrypted = prefs.getString("encrypted_key", "").orEmpty()
        return if (encrypted.isBlank()) "" else runCatching { decrypt(encrypted) }.getOrDefault("")
    }

    fun saveKey(value: String) {
        require(ElevenLabsClient.validKey(value)) { "Enter a valid ElevenLabs API key." }
        check(prefs.edit().putString("encrypted_key", encrypt(value)).commit()) {
            "Could not securely store the ElevenLabs key."
        }
    }

    fun clear() {
        check(prefs.edit().clear().commit()) { "Could not remove ElevenLabs settings." }
    }

    fun elevenSelected(): Boolean = prefs.getBoolean("eleven_selected", false) && hasKey()
    fun selectEleven(enabled: Boolean) {
        require(!enabled || hasKey()) { "Save a key before selecting ElevenLabs." }
        check(prefs.edit().putBoolean("eleven_selected", enabled).commit())
    }

    fun selectedVoice(): String = prefs.getString("voice_id", "").orEmpty()
    fun selectedModel(): String = prefs.getString("model", ElevenLabsClient.DEFAULT_MODEL)
        .orEmpty().takeIf(ElevenLabsClient::validModel) ?: ElevenLabsClient.DEFAULT_MODEL

    fun saveVoice(id: String) {
        require(ElevenLabsClient.validVoiceId(id))
        check(prefs.edit().putString("voice_id", id).commit())
    }

    fun saveModel(id: String) {
        require(ElevenLabsClient.validModel(id))
        check(prefs.edit().putString("model", id).commit())
    }

    private fun keyMaterial(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyMaterial())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP)
    }

    private fun decrypt(encrypted: String): String {
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyMaterial(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
