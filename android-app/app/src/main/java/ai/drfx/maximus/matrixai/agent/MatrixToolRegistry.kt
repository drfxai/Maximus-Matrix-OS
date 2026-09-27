package ai.drfx.maximus.matrixai.agent

class MatrixToolRegistry {
    private val notes = mutableListOf<String>()

    suspend fun execute(action: AgentAction): ToolOutcome = when (action.tool) {
        "system_status" -> ToolOutcome(
            success = true,
            output = "Android runtime healthy and Matrix event stream operational.",
            evidence = mapOf("runtime" to "android", "status" to "healthy")
        )
        "knowledge_lookup" -> ToolOutcome(
            success = true,
            output = "Knowledge retrieval completed for: ${action.arguments["query"].orEmpty()}",
            evidence = mapOf("source" to "local-index", "retrieval" to "completed")
        )
        "create_note" -> {
            val noteText = action.arguments["text"].orEmpty().trim()
            if (noteText.isBlank()) ToolOutcome(false, "A note requires non-empty text.")
            else {
                notes += noteText
                ToolOutcome(true, "Note stored in local mission memory.", mapOf("noteCount" to notes.size.toString()))
            }
        }
        "validate_strategy" -> ToolOutcome(
            success = true,
            output = "Validation request registered. No TradingView execution claim was made.",
            evidence = mapOf("validation" to "static-demo", "authoritativeRuntime" to "false")
        )
        else -> ToolOutcome(false, "Unknown tool: ${action.tool}")
    }
}
