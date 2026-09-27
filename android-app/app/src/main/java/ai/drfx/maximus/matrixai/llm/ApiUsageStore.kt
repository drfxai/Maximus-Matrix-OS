package ai.drfx.maximus.matrixai.llm

import android.content.Context

data class ApiUsageSummary(
    val requests: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val estimatedResponses: Long = 0
) {
    val totalTokens: Long get() = inputTokens + outputTokens
}

class ApiUsageStore(context: Context) {
    private val prefs = context.getSharedPreferences("maximus_api_usage", Context.MODE_PRIVATE)

    fun record(provider: LlmProvider, model: String, usage: ChatUsage) {
        val key = provider.name + "::" + model
        prefs.edit()
            .putLong("requests::$key", prefs.getLong("requests::$key", 0) + 1)
            .putLong("input::$key", prefs.getLong("input::$key", 0) + usage.inputTokens)
            .putLong("output::$key", prefs.getLong("output::$key", 0) + usage.outputTokens)
            .putLong("estimated::$key", prefs.getLong("estimated::$key", 0) + if (usage.estimated) 1 else 0)
            .apply()
    }

    fun summary(): ApiUsageSummary {
        var requests = 0L
        var input = 0L
        var output = 0L
        var estimated = 0L
        prefs.all.forEach { (key, value) ->
            val number = value as? Long ?: return@forEach
            when {
                key.startsWith("requests::") -> requests += number
                key.startsWith("input::") -> input += number
                key.startsWith("output::") -> output += number
                key.startsWith("estimated::") -> estimated += number
            }
        }
        return ApiUsageSummary(requests, input, output, estimated)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
