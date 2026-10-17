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
internal enum class DevelopmentService { GITHUB, CLOUDFLARE, CLOUD_AGENT }

internal class DeveloperCredentialSnapshot(val revision: String, val token: String) {
    override fun toString(): String = "DeveloperCredentialSnapshot(redacted)"
}

internal class DeveloperCredentialVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "maximus_developer_connections", Context.MODE_PRIVATE
    )
    internal val plugins = ai.drfx.maximus.matrixai.plugins.NativePlugins.registry(context)
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

    /** Changes on every save, including replacing a token with the same plaintext. */
    internal fun revision(service: DevelopmentService): String = preferences
        .getString("secret_" + service.name, null)?.let(CloudflareDeploymentPolicy::digest).orEmpty()

    /** Resolve only immediately before a request to the exact designated service API. */
    internal fun resolve(service: DevelopmentService): String {
        val wrapped = preferences.getString("secret_" + service.name, null) ?: return ""
        return decrypt(wrapped)
    }

    /** The token and revision are derived from one captured encrypted value. */
    internal fun snapshot(service: DevelopmentService): DeveloperCredentialSnapshot {
        val wrapped = preferences.getString("secret_" + service.name, null).orEmpty()
        return DeveloperCredentialSnapshot(if (wrapped.isBlank()) "" else CloudflareDeploymentPolicy.digest(wrapped),
            if (wrapped.isBlank()) "" else decrypt(wrapped))
    }

    private fun decrypt(wrapped: String): String = runCatching {
            val data = Base64.decode(wrapped, Base64.NO_WRAP)
            check(data.size > 28)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
                doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
            }
        }.getOrDefault("")

    /** Non-secret write receipts survive screen dismissal and post-write validation failure. */
    internal fun recordCloudflareReceipt(receipt: CloudflareDeploymentReceipt) {
        val entry = org.json.JSONObject().put("account", receipt.review.accountId)
            .put("worker", receipt.review.workerName).put("previous_version", receipt.review.activeVersion)
            .put("staged_version", receipt.stagedVersion).put("source_sha256", receipt.review.sourceSha256)
            .put("deployment", receipt.activatedDeployment).put("rollback", receipt.rolledBackDeployment)
            .put("recorded_at", System.currentTimeMillis())
        check(preferences.edit().putString("cloudflare_last_deployment_receipt", entry.toString()).commit())
    }

    internal fun recordCloudflareCreation(receipt: CloudflareCreationReceipt) {
        val entry = org.json.JSONObject().put("account", receipt.accountId).put("worker", receipt.workerName)
            .put("worker_id", receipt.workerId).put("version", receipt.version)
            .put("verified", receipt.verified).put("recorded_at", System.currentTimeMillis())
        check(preferences.edit().putString("cloudflare_last_creation_receipt", entry.toString()).commit())
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
