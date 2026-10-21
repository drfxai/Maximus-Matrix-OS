package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject

/** Catalog eligibility is not proof of successful inference. Explicit contrary evidence wins. */
object CatalogChatPolicy {
    fun isCandidate(model: JSONObject): Boolean {
        val id = model.optString("id").trim().lowercase()
        if (id.isBlank() || id.length > 512 || id.any { it.isISOControl() }) return false
        val endpoints = model.optJSONArray("supported_endpoints")
        if (endpoints != null && endpoints.length() > 0) {
            val chat = (0 until endpoints.length()).any {
                endpoints.optString(it).trim('/').lowercase() in setOf("chat/completions", "v1/chat/completions", "messages", "v1/messages")
            }
            if (!chat) return false
        }
        val task = model.optString("task").ifBlank { model.optString("pipeline_tag") }.lowercase()
        if (task in setOf("feature-extraction", "embedding", "embeddings", "rerank", "text-to-image", "image-to-image", "text-to-speech", "automatic-speech-recognition", "moderation")) return false
        val output = model.optJSONObject("architecture")?.optJSONArray("output_modalities")
        if (output != null && output.length() > 0 && (0 until output.length()).none { output.optString(it) == "text" }) return false
        // Common catalogs omit task metadata. Exclude known endpoint families; unknown
        // text models remain candidates for an explicit, separately authorized test.
        if (listOf("embed", "rerank", "whisper", "tts-", "dall-e", "moderation", "transcrib", "realtime", "gpt-image", "stable-diffusion", "flux.").any { it in id }) return false
        if (id in setOf("babbage-002", "davinci-002", "gpt-3.5-turbo-instruct") || id.startsWith("text-davinci") || id.startsWith("text-curie")) return false
        return true
    }
}
