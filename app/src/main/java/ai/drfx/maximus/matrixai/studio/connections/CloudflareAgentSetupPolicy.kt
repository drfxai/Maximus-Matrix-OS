package ai.drfx.maximus.matrixai.studio.connections

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Cloudflare account-owned credentials are provisioned by the USER on
 * Cloudflare's own dashboard. This app never asks for their Cloudflare password
 * or claims to create an API token using an untrusted privileged bootstrap key.
 */
internal object CloudflareAgentSetupPolicy {
    const val SIGN_UP_URL = "https://dash.cloudflare.com/sign-up"
    const val TOKEN_DASHBOARD = "https://dash.cloudflare.com/profile/api-tokens"
    const val WORKER_MODULE = "worker.mjs"
    const val COMPATIBILITY_DATE = "2026-10-15"

    /**
     * Verified Cloudflare documented user-token template for Workers Scripts Edit.
     * The resource must be narrowed to the account by the user before creation.
     * Extra administrative permissions, DNS, R2 and token-provisioning are omitted.
     */
    fun tokenTemplateUrl(accountId: String = ""): String {
        val scope = if (accountId.isBlank()) "*" else
            DeveloperConnectionPolicy.cloudflareAccountId(accountId)
        val permissionJson = """[{"key":"workers_scripts","type":"edit"}]"""
        val encoder: (String) -> String = { URLEncoder.encode(it, "UTF-8") }
        return TOKEN_DASHBOARD +
            "?permissionGroupKeys=" + encoder(permissionJson) +
            "&accountId=" + encoder(scope) +
            "&zoneId=all&name=" + encoder("Maximus Scheduled Agent")
    }

    fun workerName(): String = "maximus-news-" +
        UUID.randomUUID().toString().substring(0, 12).lowercase(Locale.ROOT)

    fun cron(periodMinutes: Long): String = when (periodMinutes) {
        15L -> "*/15 * * * *"
        180L -> "0 */3 * * *"
        1440L -> "0 8 * * *"
        else -> throw IllegalArgumentException("Only 15 min, three hours and daily are supported.")
    }

    fun validatedWorkerName(name: String): String {
        val valid = DeveloperConnectionPolicy.workerName(name)
        require(valid.matches(Regex("maximus-news-[a-z0-9-]{8,40}"))) {
            "Only Maximus-managed Worker names are permitted."
        }
        return valid
    }

    fun fingerprint(js: String): String =
        MessageDigest.getInstance("SHA-256").digest(js.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun validateModule(js: String) {
        val data = js.toByteArray(Charsets.UTF_8)
        require(data.size in 100..48*1024)
        check(js.contains("async scheduled(") && js.contains("collector") &&
            js.contains("validator") && js.contains("reporter")) {
            "Expected a reviewed collector/validator/reporter scheduled Worker template."
        }
    }
}
