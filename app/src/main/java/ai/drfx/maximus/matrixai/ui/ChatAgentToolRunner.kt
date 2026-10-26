package ai.drfx.maximus.matrixai.ui

import android.content.Context
import ai.drfx.maximus.matrixai.llm.AgentTools
import ai.drfx.maximus.matrixai.llm.AgentTools.Tool
import ai.drfx.maximus.matrixai.studio.LargeSource
import ai.drfx.maximus.matrixai.studio.PineAnalysis
import ai.drfx.maximus.matrixai.studio.PineMarketDataSource
import ai.drfx.maximus.matrixai.studio.PineScriptEngine
import ai.drfx.maximus.matrixai.studio.PineStudioStore
import ai.drfx.maximus.matrixai.studio.PineTimeframe
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Runs the read-only lookups a Chat reply asked for and returns their combined results. */
internal class ChatAgentToolRunner(context: Context) {
    private val appContext = context.applicationContext

    suspend fun run(calls: List<AgentTools.Call>): String = withContext(Dispatchers.IO) {
        val out = StringBuilder()
        for (call in calls) {
            val section = withTimeoutOrNull(25_000L) {
                runCatching {
                    when (call.tool) {
                        Tool.WEB_SEARCH -> search(call.argument)
                        Tool.FETCH_URL -> page(call.argument)
                        Tool.PINE_INSPECT -> pine(call.argument)
                    }
                }.getOrElse { "${call.tool.label} failed: " + (it.message ?: it.javaClass.simpleName) }
            } ?: "${call.tool.label} timed out after 25 seconds."
            out.append("### ").append(call.tool.label).append(" · ").append(call.title).append('\n')
                .append(section.trim()).append("\n\n")
            if (out.length > AgentTools.MAX_RESULT_CHARS) break
        }
        out.toString().take(AgentTools.MAX_RESULT_CHARS).trim()
    }

    private fun search(query: String): String {
        val html = fetch(AgentTools.searchUrl(query), maxBytes = 900_000).second
        return AgentTools.formatSearch(query, AgentTools.parseSearchResults(html))
    }

    private fun page(url: String): String {
        val (type, body) = fetch(url, maxBytes = 2_000_000)
        return when {
            type.contains("html") || type.isBlank() && body.trimStart().startsWith("<") -> {
                val page = AgentTools.pageText(body)
                "Title: " + page.title.ifBlank { "(none)" } + "\nURL: " + url + "\n\n" + page.text
            }
            type.startsWith("text/") || type.contains("json") || type.contains("xml") ->
                "URL: $url\n\n" + body.take(AgentTools.MAX_PAGE_CHARS)
            else -> "URL: $url\nThis page is $type, which cannot be read as text."
        }
    }

    /** GET with manual redirects, so every hop is checked against the public-URL rule. */
    private fun fetch(start: String, maxBytes: Int): Pair<String, String> {
        var url = start
        repeat(4) {
            require(AgentTools.isPublicWebUrl(url)) { "Only public http(s) pages can be read." }
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8")
                val code = connection.responseCode
                if (code in 300..399) {
                    val next = connection.getHeaderField("Location") ?: error("Redirect without a target.")
                    url = URL(URL(url), next).toString()
                    return@repeat
                }
                require(code in 200..299) { "HTTP $code from " + (AgentTools.host(url) ?: url) + "." }
                val type = connection.contentType.orEmpty().lowercase()
                val charset = Regex("charset=([\\w-]+)").find(type)?.groupValues?.get(1)
                    ?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
                val bytes = connection.inputStream.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (output.size() < maxBytes) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, minOf(count, maxBytes - output.size()))
                    }
                    output.toByteArray()
                }
                return type.substringBefore(';').trim() to String(bytes, charset)
            } finally { connection.disconnect() }
        }
        error("Too many redirects.")
    }

    private suspend fun pine(name: String): String {
        val drafts = PineStudioStore(appContext).list()
        require(drafts.isNotEmpty()) { "Pine Studio has no drafts yet." }
        val draft = drafts.firstOrNull { name.isNotBlank() && it.name.equals(name, ignoreCase = true) }
            ?: drafts.firstOrNull { name.isNotBlank() && it.name.contains(name, ignoreCase = true) }
            ?: drafts.maxBy { it.updatedAt }
        val source = draft.code
        val lines = source.count { it == '\n' } + 1
        val out = StringBuilder()
        out.append("Draft “").append(draft.name).append("” · ").append(lines).append(" lines · ")
            .append(LargeSource.sizeLabel(source)).append('\n')
        if (drafts.size > 1) out.append("Other drafts: ").append(drafts.filter { it.id != draft.id }.take(12)
            .joinToString(", ") { it.name }).append('\n')
        val syntax = PineScriptEngine.check(source).filter { it.severity != PineScriptEngine.Severity.INFO }
        out.append("\nLocal syntax check: ").append(if (syntax.isEmpty()) "no issues" else "${syntax.size} issue(s)").append('\n')
        syntax.take(12).forEach { out.append("- line ").append(it.line).append(": ").append(it.message).append('\n') }
        val chart = runCatching {
            val series = PineMarketDataSource().load("BTC-USD", PineTimeframe.ONE_HOUR)
            val output = PineScriptEngine.run(source, series.candles, "BTC-USD", PineTimeframe.ONE_HOUR.seconds)
            PineAnalysis.summary(output, series.candles, "BTC-USD", PineTimeframe.ONE_HOUR.label) +
                (if (output.skippedStatements > 0) "\nStatements the local engine skipped: ${output.skippedStatements} of ${output.statements}." else "")
        }.getOrElse { "Chart run unavailable: " + (it.message ?: "engine error") }
        out.append("\nChart run:\n").append(chart).append('\n')
        out.append("\nSource").append(if (source.length > 18_000) " (outline and opening; ask for a section by line numbers if needed)" else "")
            .append(":\n```pine\n")
        if (source.length <= 18_000) out.append(source)
        else {
            LargeSource.outline(source, 160).forEach { (line, text) -> out.append("// L").append(line).append(": ").append(text).append('\n') }
            out.append("\n").append(source.take(6_000))
        }
        out.append("\n```")
        return out.toString()
    }
}
