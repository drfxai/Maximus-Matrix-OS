package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject

/** Parses the supported single-data-line OpenAI SSE events without treating partial text as success. */
internal class OpenAiStreamParser(private val onDelta: (String) -> Unit) {
    private val output = StringBuilder()
    var completed = false
        private set
    var usage: ChatUsage? = null
        private set
    fun text(): String = output.toString()
    fun line(line: String): Boolean {
        if (!line.startsWith("data:")) return true
        val data = line.removePrefix("data:").trim()
        if (data == "[DONE]") { completed = true; return false }
        if (data.isBlank()) return true
        val json = JSONObject(data)
        require(!json.has("error")) { "Provider reported an error during generation. Partial text was not saved as a completed answer." }
        json.optJSONObject("usage")?.let { raw ->
            usage = ChatUsage(raw.optInt("prompt_tokens"), raw.optInt("completion_tokens"), raw.optInt("total_tokens"))
        }
        val finish = json.optJSONArray("choices")?.optJSONObject(0)?.optString("finish_reason").orEmpty()
        require(finish != "content_filter") { "Provider content filter stopped generation." }
        if (finish in setOf("stop", "length")) completed = true
        val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
        if (delta.isNotEmpty()) { output.append(delta); onDelta(delta) }
        return true
    }
}
