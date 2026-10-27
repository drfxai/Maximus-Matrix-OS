package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject

/**
 * A provider accepted the request and answered, but spent its whole output allowance before any
 * visible text (typical for thinking models on a tiny test cap). The model works; the cap was small.
 */
class TruncatedReplyException : IllegalStateException(
    "The model used its whole output allowance before writing visible text. Raise the limit or try again.")

/** Null-safe reply text helpers: org.json's optString turns JSON null into the string "null". */
internal object ReplyText {
    fun string(json: JSONObject?, name: String): String =
        if (json == null || !json.has(name) || json.isNull(name)) "" else json.optString(name)

    /** Visible content of an OpenAI-style message or delta, ignoring JSON null. */
    fun content(message: JSONObject?): String = string(message, "content")

    /** Reasoning text some OpenAI-compatible hosts send next to (or instead of) content. */
    fun reasoning(message: JSONObject?): String =
        string(message, "reasoning_content").ifEmpty { string(message, "reasoning") }

    /** Finish reasons that mean "stopped at the output cap" across the supported protocols. */
    fun isLengthStop(reason: String): Boolean =
        reason.equals("length", true) || reason.equals("max_tokens", true) || reason.equals("MAX_TOKENS", true)
}
