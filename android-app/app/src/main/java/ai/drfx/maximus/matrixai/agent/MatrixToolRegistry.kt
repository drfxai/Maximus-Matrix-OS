package ai.drfx.maximus.matrixai.agent

import android.content.Context
import ai.drfx.maximus.matrixai.capabilities.AndroidCapabilities

class MatrixToolRegistry(context: Context) {
    private val capabilities = AndroidCapabilities(context)
    private val notes = mutableListOf<String>()

    suspend fun execute(action: AgentAction): ToolOutcome = when (action.tool) {
        "system_status", "device_info" -> capabilities.deviceInfo()
        "web_search" -> capabilities.webSearch(action.arguments["query"].orEmpty())
        "open_url" -> capabilities.openUrl(action.arguments["url"].orEmpty())
        "open_settings" -> capabilities.openSettings(action.arguments["section"].orEmpty())
        "open_camera" -> capabilities.openCamera()
        "set_alarm" -> capabilities.setAlarm(
            action.arguments["hour"]?.toIntOrNull() ?: 9,
            action.arguments["minute"]?.toIntOrNull() ?: 0,
            action.arguments["label"].orEmpty().ifBlank { "MAXIMUS AI" }
        )
        "create_calendar_event" -> capabilities.createCalendarEvent(action.arguments["title"].orEmpty())
        "open_dialer" -> capabilities.openDialer(action.arguments["number"].orEmpty())
        "compose_sms" -> capabilities.composeSms(action.arguments["number"].orEmpty(), action.arguments["message"].orEmpty())
        "share_text" -> capabilities.shareText(action.arguments["text"].orEmpty())
        "copy_clipboard" -> capabilities.copyToClipboard(action.arguments["text"].orEmpty())
        "open_app" -> capabilities.launchPackage(action.arguments["package"].orEmpty())
        "knowledge_lookup" -> ToolOutcome(
            true,
            "Local knowledge request accepted for: ${action.arguments["query"].orEmpty()}",
            mapOf("source" to "device-runtime", "mode" to "local")
        )
        "create_note" -> {
            val text = action.arguments["text"].orEmpty().trim()
            if (text.isBlank()) ToolOutcome(false, "A note requires non-empty text.")
            else {
                notes += text
                ToolOutcome(true, "Note stored in mission memory.", mapOf("noteCount" to notes.size.toString()))
            }
        }
        "validate_strategy" -> ToolOutcome(
            true,
            "Static validation request registered. TradingView runtime execution was not claimed.",
            mapOf("validation" to "static", "authoritativeRuntime" to "false")
        )
        else -> ToolOutcome(false, "Unknown tool: ${action.tool}")
    }
}
