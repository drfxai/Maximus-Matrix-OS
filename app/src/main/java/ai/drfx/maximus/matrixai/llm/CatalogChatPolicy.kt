package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject

/** Catalog eligibility is not proof of successful inference. Explicit contrary evidence wins. */
object CatalogChatPolicy {
    fun isCandidate(model: JSONObject): Boolean {
        val id = model.optString("id").trim().lowercase()
        if (id.isBlank() || id.length > 512 || id.any { it.isISOControl() }) return false
        val endpoints = model.optJSONArray("supported_endpoints")
        val declaresEndpoints = endpoints != null && endpoints.length() > 0
        if (declaresEndpoints) {
            val chat = (0 until endpoints!!.length()).any {
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
        // Without endpoint metadata, also drop families that only work on other APIs
        // (Responses-only, audio, image/video generation, safety classifiers, retrievers).
        if (!declaresEndpoints && nonChatFamilies.any { it.containsMatchIn(id) }) return false
        return true
    }

    private val nonChatFamilies = listOf(
        Regex("""(^|[/-])(codex|computer-use|deep-research)"""),
        Regex("""^(o1|o3|o4)-pro\b|gpt-[\d.]+-pro\b"""),
        Regex("""search-preview|-search-api|audio-preview|-audio-|-transcribe|-tts\b|^tts|text-to-speech"""),
        Regex("""imagen|image-generation|-image-preview|sora|veo-|dall-e|stable-?diffusion|sdxl|playground-v"""),
        Regex("""(^|[/-])(nv-?embed|embedqa|embedding|e5-|bge-|gte-|arctic-embed|retriever|rerank|reward|guard|shieldgemma|nemoguard|safety|parse|ocdr|ocr|paddleocr|deplot|kosmos|neva-|vila|cosmos)"""),
        Regex("""(^|[/-])(babbage|davinci|ada-)""")
    )

    /** Gemini lists generateContent for some non-chat models; keep only text chat families. */
    fun isGeminiChatCandidate(id: String): Boolean {
        val clean = id.lowercase()
        return listOf("tts", "image-generation", "imagen", "veo", "native-audio", "embedding", "aqa", "learnlm-1.5")
            .none { it in clean } && !clean.startsWith("gemini-live")
    }
}
