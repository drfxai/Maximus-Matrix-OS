package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject

/**
 * Parses the supported single-data-line OpenAI SSE events without treating partial text as success.
 * Reasoning-only deltas are reported as an empty delta: the route is alive, but nothing is shown.
 */
internal class OpenAiStreamParser(private val onDelta: (String) -> Unit) {
    private val output = StringBuilder()
    var completed = false
        private set
    var usage: ChatUsage? = null
        private set
    /** The stream stopped at the output cap. */
    var stoppedAtLimit = false
        private set
    fun text(): String = output.toString()
    fun line(line: String): Boolean {
        if (!line.startsWith("data:")) return true
        val data = line.removePrefix("data:").trim()
        if (data == "[DONE]") { completed = true; return false }
        if (data.isBlank()) return true
        val json = JSONObject(data)
        require(!json.has("error") || json.isNull("error")) {
            "Provider reported an error during generation." +
                (ProviderErrorDetail.extract(data)?.let { " Provider says: $it" } ?: "") +
                " Partial text was not saved as a completed answer."
        }
        ProviderUsageEvidence.parseOpenAi(json.optJSONObject("usage"))?.let { usage = it }
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val finish = ReplyText.string(choice, "finish_reason")
        require(finish != "content_filter") { "Provider content filter stopped generation." }
        if (finish == "stop" || ReplyText.isLengthStop(finish)) completed = true
        if (ReplyText.isLengthStop(finish)) stoppedAtLimit = true
        val delta = choice?.optJSONObject("delta")
        val content = ReplyText.content(delta)
        if (content.isNotEmpty()) { output.append(content); onDelta(content) }
        else if (ReplyText.reasoning(delta).isNotEmpty()) onDelta("")
        return true
    }
}
