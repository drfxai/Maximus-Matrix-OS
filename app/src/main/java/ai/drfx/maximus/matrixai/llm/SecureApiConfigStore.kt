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

    init {
        // Attribute legacy global credentials only to their recorded owner, never a new selection.
        if (!preferences.getBoolean("provider_isolation_v2", false)) {
            val owner = loadProvider()
            val legacy = preferences.getString("api_key", null)
            val editor = preferences.edit().remove("api_key").putBoolean("provider_isolation_v2", true)
            preferences.getString("base_url", null)?.takeIf { it.isNotBlank() }?.let { editor.putString("endpoint_" + owner.name, ApiDiscoveryEngine.normalizeBaseUrl(it)) }
            preferences.getString("model", null)?.let { editor.putString("model_" + owner.name, it) }
            if (!legacy.isNullOrBlank() && !preferences.contains("api_key_" + owner.name) && runCatching { CredentialPolicy.compatible(owner, decrypt(legacy)) }.getOrDefault(false)) {
                editor.putString("api_key_" + owner.name, legacy)
            }
            editor.commit()
        }
    }

    fun save(
        baseUrl: String,
        apiKey: String,
        provider: LlmProvider,
        model: String,
        agentId: String = "general",
        subscriptionLabel: String = loadSubscriptionLabel(),
        monthlyBudgetUsd: Double = loadMonthlyBudgetUsd(),
        monthlyTokenBudget: Long = loadMonthlyTokenBudget()
    ) {
        CredentialPolicy.validate(provider, apiKey)
        if (baseUrl.isNotBlank()) EndpointPolicy.validate(baseUrl, provider)
        else require(apiKey.isBlank()) { "Set a trusted HTTPS endpoint before saving credentials." }
        val previousEndpoint = preferences.getString("endpoint_" + provider.name, null)
        require(baseUrl.isBlank() || previousEndpoint == null || previousEndpoint == ApiDiscoveryEngine.normalizeBaseUrl(baseUrl) || apiKey.isNotBlank()) {
            "Endpoint changed. Re-enter credentials to authorize this provider endpoint."
        }
        val editor = preferences.edit()
            .putString("endpoint_" + provider.name, baseUrl.takeIf { it.isNotBlank() }?.let { ApiDiscoveryEngine.normalizeBaseUrl(it) } ?: previousEndpoint)
            .putString("model_" + provider.name, model)
            .putString("base_url", baseUrl)
            .putString("provider", provider.name)
            .putString("model", model)
            .putString("agent_id", agentId)
            .putString("subscription_label", subscriptionLabel)
            .putLong("monthly_budget_bits", java.lang.Double.doubleToRawLongBits(monthlyBudgetUsd))
            .putLong("monthly_token_budget", monthlyTokenBudget)
        if (apiKey.isNotBlank()) editor.putString("api_key_" + provider.name, encrypt(apiKey))
        editor.apply()
    }

    fun loadBaseUrlForProvider(provider: LlmProvider): String = preferences.getString("endpoint_" + provider.name, null) ?: provider.defaultBaseUrl
    fun loadModelForProvider(provider: LlmProvider): String = preferences.getString("model_" + provider.name, null) ?: provider.defaultModel

    /** Re-entering a key is required to authorize any custom endpoint change. */
    fun resolveCredential(provider: LlmProvider, endpoint: String): String {
        if (endpoint.isBlank()) return ""
        EndpointPolicy.validate(endpoint, provider)
        val trusted = preferences.getString("endpoint_" + provider.name, null) ?: return ""
        return if (ApiDiscoveryEngine.normalizeBaseUrl(endpoint) == ApiDiscoveryEngine.normalizeBaseUrl(trusted)) loadKeyForProvider(provider) else ""
    }

    fun loadBaseUrl(): String = preferences.getString("base_url", "").orEmpty()
    fun loadModel(): String = preferences.getString("model", "").orEmpty()
    fun loadAgentId(): String = preferences.getString("agent_id", "general").orEmpty().ifBlank { "general" }
    fun loadSubscriptionLabel(): String = preferences.getString("subscription_label", "").orEmpty()
    fun loadMonthlyBudgetUsd(): Double =
        java.lang.Double.longBitsToDouble(preferences.getLong("monthly_budget_bits", java.lang.Double.doubleToRawLongBits(0.0)))
    fun loadMonthlyTokenBudget(): Long = preferences.getLong("monthly_token_budget", 0L)

    fun loadProvider(): LlmProvider = runCatching {
        LlmProvider.valueOf(preferences.getString("provider", LlmProvider.GEMINI.name).orEmpty())
    }.getOrDefault(LlmProvider.GEMINI)

    fun hasApiKey(): Boolean = hasKeyForProvider(loadProvider())

    fun saveKeyForProvider(provider: LlmProvider, key: String) {
        CredentialPolicy.validate(provider, key)
        if (key.isNotBlank()) {
            preferences.edit()
                .putString("api_key_" + provider.name, encrypt(key))
                .apply()
        }
    }

    fun loadKeyForProvider(provider: LlmProvider): String {
        val specific = preferences.getString("api_key_" + provider.name, null)
        if (!specific.isNullOrBlank()) {
            val decrypted = runCatching { decrypt(specific) }.getOrDefault("")
            if (decrypted.isNotBlank()) return decrypted
        }
        return ""
    }

    fun hasKeyForProvider(provider: LlmProvider): Boolean {
        val specific = preferences.getString("api_key_" + provider.name, null)
        if (!specific.isNullOrBlank()) return true
        return false
    }

    fun loadApiKey(): String = loadKeyForProvider(loadProvider())

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
