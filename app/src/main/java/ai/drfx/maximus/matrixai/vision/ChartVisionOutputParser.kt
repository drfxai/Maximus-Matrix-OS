package ai.drfx.maximus.matrixai.vision

import ai.drfx.maximus.matrixai.llm.ChatUsage
import org.json.JSONObject

/** No prose inference or fabricated fallback when the structured output is incomplete. */
object ChartVisionOutputParser {
    fun parse(raw: String, model: String, usage: ChatUsage, elapsedMs: Long): ChartVisionAnalysis {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = try { JSONObject(text) } catch (_: Exception) {
            throw IllegalArgumentException("Chart model returned invalid JSON. Retry with a supported model.")
        }
        fun required(name: String): String {
            require(obj.has(name) && obj.opt(name) is String && obj.getString(name).isNotBlank()) { "Chart response missing $name." }
            return obj.getString(name)
        }
        val direction = try { TrendDirection.valueOf(required("direction")) } catch (_: Exception) {
            throw IllegalArgumentException("Chart response has an invalid direction.")
        }
        val strength = try { TrendStrength.valueOf(required("strength")) } catch (_: Exception) {
            throw IllegalArgumentException("Chart response has an invalid strength.")
        }
        require(obj.has("priceScaleReadable") && obj.opt("priceScaleReadable") is Boolean) { "Chart response must declare price readability." }
        val readable = obj.getBoolean("priceScaleReadable")
        fun rows(name: String): List<JSONObject> {
            val array = obj.optJSONArray(name) ?: throw IllegalArgumentException("Chart response missing $name array.")
            require(array.length() <= 30) { "Chart response contains too many $name." }
            return (0 until array.length()).map { array.optJSONObject(it) ?: throw IllegalArgumentException("Invalid $name item.") }
        }
        fun JSONObject.field(name: String): String = optString(name).takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Chart item missing $name.")
        val levels = rows("keyLevels")
        val planObject = obj.optJSONObject("tradePlan")
        require(readable || (levels.isEmpty() && planObject == null)) { "Trading prices require readable chart evidence." }
        val plan = planObject?.let {
            val bias = it.field("bias")
            require(bias in setOf("LONG", "SHORT", "WAIT")) { "Invalid trade bias." }
            TradePlan(bias, it.field("entryZone"), it.field("stopLoss"), it.field("target1"), it.optString("target2"), it.field("riskRewardRatio"), it.field("invalidationReason"))
        } ?: TradePlan("WAIT", "Unavailable", "Unavailable", "Unavailable", "", "Unavailable", "Insufficient readable evidence for trading levels.")
        return ChartVisionAnalysis(
            modelUsed = model, assetIdentifier = required("asset"), timeframeEstimate = required("timeframe"),
            direction = direction, trendStrength = strength, trendSummary = required("trendSummary"),
            keyLevels = levels.map { PriceZone(it.field("level"), it.field("type"), it.optString("description")) },
            trendlines = rows("trendlines").map { ChartTrendline(it.field("type"), it.field("description"), "", it.optString("significance")) },
            patterns = rows("patterns").map { ChartPatternItem(it.field("name"), it.field("patternType"), 0, it.field("status"), it.field("implication")) },
            candleSignals = rows("candleSignals").map { CandleSignal(it.field("name"), it.field("location"), it.field("implication")) },
            tradePlan = plan, comprehensiveReport = required("report"),
            inputTokens = usage.inputTokens, outputTokens = usage.outputTokens, totalTokens = usage.totalTokens,
            usageEstimated = usage.estimated, processingMs = elapsedMs.coerceAtLeast(0)
        )
    }
}
