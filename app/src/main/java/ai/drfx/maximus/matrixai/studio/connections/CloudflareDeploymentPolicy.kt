package ai.drfx.maximus.matrixai.studio.connections

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64

/** Immutable source/configuration evidence. Contains binding names, never binding values or credentials. */
internal data class CloudflareDeploymentReview(
    val accountId: String, val workerName: String, val sessionRevision: String,
    val deploymentId: String, val activeVersion: String, val configuration: String,
    val source: String, val sourceSha256: String, val inheritedBindings: List<String>
)
internal data class CloudflareDeploymentReceipt(
    val review: CloudflareDeploymentReview, val stagedVersion: String,
    val activatedDeployment: String? = null, val rolledBackDeployment: String? = null,
    val verified: Boolean = false
)

internal object CloudflareDeploymentPolicy {
    fun uuid(value: String): String {
        require(value.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")))
        return value
    }
    fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun active(deployments: JSONObject): Pair<String, String> {
        val items = deployments.getJSONArray("deployments")
        require(items.length() > 0) { "An existing deployment is required." }
        val deployment = items.getJSONObject(0)
        require(deployment.getString("strategy") == "percentage")
        val versions = deployment.getJSONArray("versions")
        require(versions.length() == 1 && versions.getJSONObject(0).getDouble("percentage") == 100.0) {
            "Multi-version traffic is not supported by this deployment flow."
        }
        return uuid(deployment.getString("id")) to uuid(versions.getJSONObject(0).getString("version_id"))
    }

    /** Fail closed on lifecycle, stateful, asset and unknown version configuration. */
    fun review(account: String, name: String, session: String, deployment: String,
        activeVersion: String, version: JSONObject, source: String): CloudflareDeploymentReview {
        DeveloperConnectionPolicy.cloudflareAccountId(account)
        DeveloperConnectionPolicy.workerName(name)
        uuid(deployment); uuid(activeVersion)
        require(version.getString("id") == activeVersion)
        require(source.toByteArray(Charsets.UTF_8).size in 1..192 * 1024)
        val allowed = setOf("id", "number", "created_on", "author_id", "author_email", "annotations",
            "source", "startup_time_ms", "urls", "modules", "main_module", "bindings",
            "compatibility_date", "compatibility_flags", "usage_model", "limits", "placement",
            "exports", "exports_reconciliation")
        version.keys().forEach { require(it in allowed || version.isNull(it)) {
            "Worker configuration requires a separate deployment workflow."
        } }
        val exports = version.optJSONObject("exports")
        if (exports != null) exports.keys().forEach { key ->
            val export = exports.getJSONObject(key)
            require(key == "default" && export.optString("type") == "worker" &&
                export.optString("state", "created") == "created") {
                "Stateful or named exports require a separate deployment workflow."
            }
            export.keys().forEach { require(it in setOf("type", "state", "cache")) }
        }
        val module = version.getString("main_module")
        require(module.matches(Regex("[A-Za-z0-9_./-]{1,180}")) && !module.contains(".."))
        val date = version.getString("compatibility_date")
        require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        val bindings = version.getJSONArray("bindings")
        require(bindings.length() <= 100)
        val names = mutableListOf<String>()
        val inherited = JSONArray()
        val allowedBindings = setOf("ai", "plain_text", "json", "secret_text", "kv_namespace", "r2_bucket",
            "d1", "service", "vectorize", "analytics_engine", "images", "version_metadata")
        for (i in 0 until bindings.length()) {
            val binding = bindings.getJSONObject(i)
            require(binding.getString("type") in allowedBindings) { "Unsupported Worker binding." }
            val bindingName = binding.getString("name")
            require(bindingName.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]{0,127}")) && bindingName !in names)
            names += bindingName
            inherited.put(JSONObject().put("name", bindingName).put("type", "inherit").put("version_id", activeVersion))
        }
        require("AI" in names && "MAXIMUS_AGENT_TOKEN" in names) {
            "This starter requires existing AI and MAXIMUS_AGENT_TOKEN bindings."
        }
        require((0 until bindings.length()).any { bindings.getJSONObject(it).let { b ->
            b.getString("name") == "AI" && b.getString("type") == "ai" } })
        require((0 until bindings.length()).any { bindings.getJSONObject(it).let { b ->
            b.getString("name") == "MAXIMUS_AGENT_TOKEN" && b.getString("type") == "secret_text" } })
        val config = JSONObject().put("main_module", module).put("compatibility_date", date).put("bindings", inherited)
        if (exports != null && exports.has("default")) {
            val original = exports.getJSONObject("default")
            val preserved = JSONObject().put("type", "worker")
            if (original.has("cache") && !original.isNull("cache")) preserved.put("cache", original.getJSONObject("cache"))
            config.put("exports", JSONObject().put("default", preserved))
        }
        listOf("compatibility_flags", "usage_model", "limits", "placement").forEach { key ->
            if (version.has(key) && !version.isNull(key)) config.put(key, version.get(key))
        }
        return CloudflareDeploymentReview(account, name, session, deployment, activeVersion,
            config.toString(), source, digest(source), names.toList())
    }

    fun upload(review: CloudflareDeploymentReview): JSONObject {
        require(digest(review.source) == review.sourceSha256)
        val body = JSONObject(review.configuration)
        val module = body.getString("main_module")
        body.put("modules", JSONArray().put(JSONObject().put("name", module)
            .put("content_type", "application/javascript+module")
            .put("content_base64", Base64.getEncoder().encodeToString(review.source.toByteArray(Charsets.UTF_8)))))
        return body
    }
    fun requireCurrent(review: CloudflareDeploymentReview, session: String, deployment: String, version: String) {
        check(review.sessionRevision == session && review.deploymentId == deployment && review.activeVersion == version) {
            "Account or deployment changed. Inspect and review again."
        }
    }
    fun activation(version: String): JSONObject = JSONObject().put("strategy", "percentage")
        .put("versions", JSONArray().put(JSONObject().put("version_id", uuid(version)).put("percentage", 100)))
}
