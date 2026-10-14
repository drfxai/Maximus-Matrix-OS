package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject

/**
 * Never represent empty, malformed or inconsistent provider usage as verified zero tokens.
 * True accounting is only possible if upstream returns complete numeric usage fields.
 */
internal object ProviderUsageEvidence {
    fun parseOpenAi(usage: JSONObject?): ChatUsage? = parse(usage, "prompt_tokens", "completion_tokens", "total_tokens")
    fun parseResponses(usage: JSONObject?): ChatUsage? = parse(usage, "input_tokens", "output_tokens", "total_tokens")

    private fun parse(json: JSONObject?, input: String, output: String, total: String): ChatUsage? {
        if (json == null) return null
        fun exact(key: String): Int? {
            if (!json.has(key)) return null
            val raw = json.opt(key) as? Number ?: return null
            val value = raw.toDouble()
            if (!value.isFinite() || value < 0.0 || value > Int.MAX_VALUE || value != value.toLong().toDouble())
                return null
            return value.toInt()
        }
        val inTokens = exact(input) ?: return null
        val outTokens = exact(output) ?: return null
        val totalTokens = exact(total) ?: return null
        if (totalTokens.toLong() < inTokens.toLong() + outTokens.toLong()) return null
        return ChatUsage(inTokens, outTokens, totalTokens, estimated = false)
    }
}
