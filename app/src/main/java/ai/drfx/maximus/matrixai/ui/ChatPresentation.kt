package ai.drfx.maximus.matrixai.ui

import android.content.Context
import java.security.MessageDigest

/** A model's text is never evidence that a tool executed successfully. */
data class ChatExport(val name: String, val mimeType: String, val bytes: ByteArray)
data class ToolDraft(val tool: String, val path: String?, val description: String)

object ChatPresentation {
    private val invoke = Regex("""<invoke\s+name=["']([a-zA-Z0-9_-]{1,60})["']""")
    private val path = Regex("""<parameter\s+name=["']path["']\s*>(.*?)</parameter>""", RegexOption.DOT_MATCHES_ALL)
    private val contents = Regex("""<parameter\s+name=["']contents["']\s*>(.*?)</parameter>""", RegexOption.DOT_MATCHES_ALL)

    fun draft(raw: String): ToolDraft? {
        if (!raw.contains("<function_calls>") && !raw.contains("<invoke ")) return null
        val action = invoke.find(raw)?.groupValues?.get(1) ?: return null
        val file = path.find(raw)?.groupValues?.get(1)?.trim()?.take(220)
        return ToolDraft(action, file, "Suggested tool operation — not executed or verified")
    }

    fun export(raw: String): ChatExport {
        val draft = draft(raw)
        val suggestedName = draft?.path?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_.-]{1,80}")) && !it.startsWith(".") }
        val payload = if (draft?.tool == "create_file") contents.find(raw)?.groupValues?.get(1) else null
        val name = if (payload != null && suggestedName != null) suggestedName else "maximus-response.md"
        val text = payload ?: raw
        require(text.toByteArray().size <= 3 * 1024 * 1024) { "Response is too large for direct export." }
        val mime = if (payload != null && name.endsWith(".html", true)) "text/html" else "text/plain"
        return ChatExport(name, mime, text.toByteArray(Charsets.UTF_8))
    }
}

/** Feedback stays private and never silently changes a provider's routing or sends analytics. */
class ChatFeedbackStore(context: Context) {
    private val preferences = context.getSharedPreferences("chat_reactions_v1", Context.MODE_PRIVATE)
    fun key(sessionId: String, timestamp: Long, content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("$sessionId:$timestamp:$content").toByteArray())
            .joinToString("") { "%02x".format(it) }
        return digest
    }
    fun get(key: String) = preferences.getString(key, "").orEmpty()
    fun set(key: String, reaction: String) {
        require(reaction in setOf("", "like", "dislike", "love"))
        preferences.edit().putString(key, reaction).apply()
    }
}
