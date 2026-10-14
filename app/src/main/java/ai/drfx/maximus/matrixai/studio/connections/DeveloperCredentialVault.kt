package ai.drfx.maximus.matrixai.studio.connections

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate per-service Android Keystore-backed credentials, never embedded in APK assets. */
internal enum class DevelopmentService { GITHUB, CLOUDFLARE }

internal class DeveloperCredentialVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "maximus_developer_connections", Context.MODE_PRIVATE
    )
    private val alias = "maximus_developer_connection_credentials_v1"

    fun configured(service: DevelopmentService): Boolean =
        !preferences.getString("secret_" + service.name, null).isNullOrBlank()

    @Synchronized fun save(service: DevelopmentService, token: String) {
        val trimmed = token.trim()
        require(trimmed.length in 20..512 && trimmed.none { it.isWhitespace() }) {
            "Invalid scoped API token length or spacing."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val data = cipher.iv + cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val encrypted = Base64.encodeToString(data, Base64.NO_WRAP)
        check(preferences.edit().putString("secret_" + service.name, encrypted).commit())
    }

    @Synchronized fun clear(service: DevelopmentService) {
        check(preferences.edit().remove("secret_" + service.name).commit())
    }

    /** Resolve only immediately before a request to the exact designated service API. */
    internal fun resolve(service: DevelopmentService): String {
        val wrapped = preferences.getString("secret_" + service.name, null) ?: return ""
        return runCatching {
            val data = Base64.decode(wrapped, Base64.NO_WRAP)
            check(data.size > 28)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
                doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
}
