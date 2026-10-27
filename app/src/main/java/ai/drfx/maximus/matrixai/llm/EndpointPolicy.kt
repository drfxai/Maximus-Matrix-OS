package ai.drfx.maximus.matrixai.llm

import java.net.URI

/** Credentials must never traverse cleartext transport or redirects to another origin. */
object EndpointPolicy {
    fun validate(endpoint: String, provider: LlmProvider = LlmProvider.OPENAI_COMPATIBLE) {
        val uri = runCatching { URI(endpoint) }.getOrElse { throw IllegalArgumentException("Invalid API endpoint.") }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "A valid HTTPS API endpoint is required." }
        require(uri.rawUserInfo == null && uri.rawFragment == null && uri.rawQuery == null) {
            "API endpoints cannot contain credentials, query parameters or fragments."
        }
        val expected = when (provider) {
            LlmProvider.GEMINI -> "generativelanguage.googleapis.com"
            LlmProvider.OPENAI -> "api.openai.com"
            LlmProvider.ANTHROPIC -> "api.anthropic.com"
            LlmProvider.NVIDIA -> "integrate.api.nvidia.com"
            else -> null
        }
        require(expected == null || uri.host.equals(expected, true)) {
            "Provider endpoint host does not match the selected provider. Use a custom provider for a separate deployment."
        }
    }
}

enum class ProviderFailure { AUTHENTICATION, FORBIDDEN, RATE_LIMIT_OR_QUOTA, MODEL_OR_ENDPOINT, SERVICE_OUTAGE, REDIRECT, REQUEST }

class ProviderRequestException(
    val failure: ProviderFailure,
    val httpStatus: Int,
    /** The provider's own short error text, redacted; helps tell "model not in plan" from "bad key". */
    val providerDetail: String? = null
) : IllegalStateException(
    when (failure) {
        ProviderFailure.AUTHENTICATION -> "Authentication failed (HTTP $httpStatus). Check this provider's credentials."
        ProviderFailure.FORBIDDEN -> "Provider access denied (HTTP $httpStatus). Check permissions and account status."
        ProviderFailure.RATE_LIMIT_OR_QUOTA -> "Provider rate limit or quota reached (HTTP $httpStatus). Retry later or check the provider account."
        ProviderFailure.MODEL_OR_ENDPOINT -> "Model or endpoint unavailable (HTTP $httpStatus). Discover models and verify the endpoint."
        ProviderFailure.SERVICE_OUTAGE -> "Provider unavailable (HTTP $httpStatus). Retry later."
        ProviderFailure.REDIRECT -> "API redirect rejected to protect credentials (HTTP $httpStatus). Use the final trusted HTTPS endpoint."
        ProviderFailure.REQUEST -> "Provider request failed (HTTP $httpStatus). Check request compatibility."
    } + (providerDetail?.let { " Provider says: $it" } ?: "")
) {
    companion object {
        fun fromHttp(code: Int, body: String? = null) = ProviderRequestException(when (code) {
            401 -> ProviderFailure.AUTHENTICATION
            403 -> ProviderFailure.FORBIDDEN
            429 -> ProviderFailure.RATE_LIMIT_OR_QUOTA
            404 -> ProviderFailure.MODEL_OR_ENDPOINT
            402 -> ProviderFailure.RATE_LIMIT_OR_QUOTA
            in 300..399 -> ProviderFailure.REDIRECT
            in 500..599 -> ProviderFailure.SERVICE_OUTAGE
            else -> ProviderFailure.REQUEST
        }, code, ProviderErrorDetail.extract(body))
    }
}

/** Pulls a short, credential-free message out of a provider error body. */
internal object ProviderErrorDetail {
    private val secretLike = Regex("""(sk-[A-Za-z0-9_\-]{8,}|nvapi-[A-Za-z0-9_\-]{8,}|AIza[0-9A-Za-z_\-]{16,}|Bearer\s+\S+|[A-Za-z0-9_]{32,})""")

    fun extract(body: String?): String? {
        val text = body?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val message = runCatching {
            val root = org.json.JSONObject(text)
            val error = root.opt("error")
            when (error) {
                is org.json.JSONObject -> error.optString("message").takeUnless { error.isNull("message") }
                    ?.ifBlank { null } ?: error.optString("type").ifBlank { null }
                is String -> error
                else -> null
            } ?: root.optString("detail").takeUnless { root.isNull("detail") }?.ifBlank { null }
                ?: root.optString("message").takeUnless { root.isNull("message") }?.ifBlank { null }
        }.getOrNull() ?: text.takeUnless { it.startsWith("<") || it.startsWith("{") || it.startsWith("[") }
        return message?.let(::clean)
    }

    fun clean(message: String): String? = message
        .replace(secretLike, "[redacted]")
        .filterNot { it.isISOControl() }
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(180)
        .ifBlank { null }
}
