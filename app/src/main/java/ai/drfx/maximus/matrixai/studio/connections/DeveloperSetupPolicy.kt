package ai.drfx.maximus.matrixai.studio.connections

import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

internal enum class CloudAgentPurpose { ASSISTANT, RESEARCH, PROJECT }

/** Guided setup accepts repository identities, never credentials or arbitrary origins. */
internal object DeveloperSetupPolicy {
    const val WORKER_MODEL = "@cf/meta/llama-3.1-8b-instruct-fp8"

    fun parseGithubRepository(input: String): Pair<String, String> {
        val text = input.trim()
        require(text.isNotEmpty() && text.length <= 300) { "Enter a GitHub repository URL or owner/repository." }
        val path = if (text.startsWith("https://", ignoreCase = true)) {
            val uri = URI(text)
            require(uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("github.com", ignoreCase = true) && uri.port == -1 &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
                "Use a repository URL on https://github.com."
            }
            uri.rawPath.removePrefix("/")
        } else text
        val parts = path.removeSuffix("/").split('/')
        require(parts.size == 2) { "Enter owner/repository without a file or branch path." }
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        require(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?").matches(owner) &&
            !owner.contains("--") && Regex("[A-Za-z0-9_.-]{1,100}").matches(repo) &&
            repo !in setOf(".", "..")) { "Invalid GitHub repository identity." }
        return owner to repo
    }

    // Official user-token template: account-scoped Workers scripts only.
    // https://developers.cloudflare.com/fundamentals/api/how-to/account-owned-token-template/
    fun cloudflareTokenSetupUrl(accountId: String = ""): String {
        val scope = accountId.trim().ifEmpty { "*" }
        require(scope == "*" || Regex("[a-fA-F0-9]{32}").matches(scope)) { "Invalid Cloudflare account ID." }
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        val permissions = "[{\"key\":\"workers_scripts\",\"type\":\"edit\"}]"
        return "https://dash.cloudflare.com/profile/api-tokens?permissionGroupKeys=" +
            encode(permissions) + "&accountId=" + encode(scope) +
            "&zoneId=all&name=MAXIMUS%20Worker%20Setup"
    }

    fun suggestedWorkerName(purpose: CloudAgentPurpose): String =
        "maximus-" + purpose.name.lowercase(Locale.ROOT) + "-" +
            UUID.randomUUID().toString().replace("-", "").take(12)

    /** Stateless chat starter. It has no scheduling, browsing, repository or device tools. */
    fun workerSource(purpose: CloudAgentPurpose): String {
        val systemPrompt = when (purpose) {
            CloudAgentPurpose.ASSISTANT -> "You are a helpful MAXIMUS conversational assistant. Reply clearly in the user's language."
            CloudAgentPurpose.RESEARCH -> "You are a MAXIMUS research assistant. Analyze supplied information, distinguish evidence from assumptions, and never invent sources or claim live browsing."
            CloudAgentPurpose.PROJECT -> "You are a MAXIMUS project planning assistant. Explain code and propose reviewable steps. You cannot access repositories, run code, or deploy changes."
        } + " You are stateless, have no external tools, and cannot perform actions or work in the background."
        return """
            // MAXIMUS authenticated stateless Workers AI starter.
            // Configure AI binding and MAXIMUS_AGENT_TOKEN as a separate secret binding.
            const MODEL = "__MODEL__";
            const SYSTEM_PROMPT = "__SYSTEM_PROMPT__";
            const MAX_BODY_BYTES = 16384;
            const MAX_MESSAGE_CHARS = 8000;

            function json(value, status = 200) {
              return Response.json(value, { status, headers: {
                "cache-control": "no-store", "x-content-type-options": "nosniff"
              }});
            }
            function error(code, status) { return json({ error: { code } }, status); }

            async function authorized(request, secret) {
              const header = request.headers.get("authorization") || "";
              if (!header.startsWith("Bearer ") || header.length > 4096) return false;
              const encoder = new TextEncoder();
              const hashes = await Promise.all([
                crypto.subtle.digest("SHA-256", encoder.encode(header.slice(7))),
                crypto.subtle.digest("SHA-256", encoder.encode(secret))
              ]);
              return crypto.subtle.timingSafeEqual(hashes[0], hashes[1]);
            }

            async function readBody(request) {
              const declared = request.headers.get("content-length");
              if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) > MAX_BODY_BYTES)) {
                return { failure: error("body_too_large", 413) };
              }
              if (!request.body) return { failure: error("invalid_body", 400) };
              const reader = request.body.getReader();
              const chunks = [];
              let total = 0;
              try {
                while (true) {
                  const { done, value } = await reader.read();
                  if (done) break;
                  total += value.byteLength;
                  if (total > MAX_BODY_BYTES) {
                    await reader.cancel();
                    return { failure: error("body_too_large", 413) };
                  }
                  chunks.push(value);
                }
                const bytes = new Uint8Array(total);
                let offset = 0;
                for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
                return { value: JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)) };
              } catch (_) {
                return { failure: error("invalid_body", 400) };
              } finally { reader.releaseLock(); }
            }

            export default {
              async fetch(request, env) {
                try {
                  if (typeof env.MAXIMUS_AGENT_TOKEN !== "string" || env.MAXIMUS_AGENT_TOKEN.length < 32) {
                    return error("setup_required", 503);
                  }
                  if (!await authorized(request, env.MAXIMUS_AGENT_TOKEN)) return error("unauthorized", 401);
                  const path = new URL(request.url).pathname;
                  if (path !== "/chat" && path !== "/health") return error("not_found", 404);
                  if ((path === "/chat" && request.method !== "POST") ||
                      (path === "/health" && request.method !== "GET")) return error("method_not_allowed", 405);
                  if (!env.AI || typeof env.AI.run !== "function") return error("setup_required", 503);
                  if (path === "/health") return json({ status: "configured", mode: "stateless_chat", model: MODEL });
                  if ((request.headers.get("content-type") || "").split(";")[0].trim().toLowerCase() !== "application/json") {
                    return error("unsupported_media_type", 415);
                  }
                  const encoding = request.headers.get("content-encoding");
                  if (encoding && encoding.toLowerCase() !== "identity") return error("unsupported_media_type", 415);
                  const body = await readBody(request);
                  if (body.failure) return body.failure;
                  const input = body.value;
                  if (!input || typeof input !== "object" || Array.isArray(input) ||
                      typeof input.message !== "string" || input.message.length > MAX_MESSAGE_CHARS ||
                      !input.message.trim()) return error("invalid_message", 400);
                  try {
                    const result = await env.AI.run(MODEL, {
                      messages: [ { role: "system", content: SYSTEM_PROMPT },
                                  { role: "user", content: input.message } ],
                      max_tokens: 256, stream: false
                    });
                    if (!result || typeof result.response !== "string" || !result.response.trim()) {
                      return error("ai_unavailable", 502);
                    }
                    return json({ response: result.response });
                  } catch (_) { return error("ai_unavailable", 502); }
                } catch (_) { return error("internal_error", 500); }
              }
            };
        """.trimIndent().replace("__MODEL__", WORKER_MODEL)
            .replace("__SYSTEM_PROMPT__", systemPrompt)
    }
}
