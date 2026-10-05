package ai.drfx.maximus.matrixai.llm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureApiConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences("maximus_secure_api", Context.MODE_PRIVATE)
    private val alias = "maximus_ai_api_key"

    fun save(
        baseUrl: String,
        apiKey: String,
        provider: LlmProvider,
        model: String,
        agentId: String = "general",
        subscriptionLabel: String = loadSubscriptionLabel(),
        monthlyBudgetUsd: Double = loadMonthlyBudgetUsd()
    ) {
        val editor = preferences.edit()
            .putString("base_url", baseUrl)
            .putString("provider", provider.name)
            .putString("model", model)
            .putString("agent_id", agentId)
            .putString("subscription_label", subscriptionLabel)
            .putLong("monthly_budget_bits", java.lang.Double.doubleToRawLongBits(monthlyBudgetUsd))
        if (apiKey.isNotBlank()) editor.putString("api_key", encrypt(apiKey))
        editor.apply()
    }

    fun loadBaseUrl(): String = preferences.getString("base_url", "").orEmpty()
    fun loadModel(): String = preferences.getString("model", "").orEmpty()
    fun loadAgentId(): String = preferences.getString("agent_id", "general").orEmpty().ifBlank { "general" }
    fun loadSubscriptionLabel(): String = preferences.getString("subscription_label", "").orEmpty()
    fun loadMonthlyBudgetUsd(): Double =
        java.lang.Double.longBitsToDouble(preferences.getLong("monthly_budget_bits", java.lang.Double.doubleToRawLongBits(0.0)))

    fun loadProvider(): LlmProvider = runCatching {
        LlmProvider.valueOf(preferences.getString("provider", LlmProvider.UNKNOWN.name).orEmpty())
    }.getOrDefault(LlmProvider.UNKNOWN)

    fun hasApiKey(): Boolean = !preferences.getString("api_key", null).isNullOrBlank()

    fun loadApiKey(): String {
        val encrypted = preferences.getString("api_key", null) ?: return ""
        return runCatching { decrypt(encrypted) }.getOrDefault("")
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
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
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > 12) { "Invalid encrypted credential." }
        val iv = payload.copyOfRange(0, 12)
        val data = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(data).toString(Charsets.UTF_8)
    }
}
