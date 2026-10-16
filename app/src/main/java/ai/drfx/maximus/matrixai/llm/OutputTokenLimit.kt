package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject

/** Protocol serialization shared by streaming and nonstreaming compatible requests. */
internal object OutputTokenLimit {
    fun apply(body: JSONObject, provider: LlmProvider, limit: Int?) {
        if (limit == null) return
        require(limit in 1..4096)
        body.put(if (provider == LlmProvider.OPENAI) "max_completion_tokens" else "max_tokens", limit)
    }
}
