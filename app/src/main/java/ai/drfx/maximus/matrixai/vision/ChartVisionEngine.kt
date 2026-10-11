package ai.drfx.maximus.matrixai.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import ai.drfx.maximus.matrixai.agent.MatrixEvent
import ai.drfx.maximus.matrixai.agent.MatrixEventBus
import ai.drfx.maximus.matrixai.agent.MatrixEventType
import ai.drfx.maximus.matrixai.llm.ApiDiscoveryEngine
import ai.drfx.maximus.matrixai.llm.ApiUsageStore
import ai.drfx.maximus.matrixai.llm.ChatUsage
import ai.drfx.maximus.matrixai.llm.LlmProvider
import ai.drfx.maximus.matrixai.llm.SecureApiConfigStore
import ai.drfx.maximus.matrixai.logging.AppLogStore

class ChartVisionEngine(
    private val context: Context,
    private val apiStore: SecureApiConfigStore = SecureApiConfigStore(context),
    private val usageStore: ApiUsageStore = ApiUsageStore(context),
    private val eventBus: MatrixEventBus = MatrixEventBus()
) {

    suspend fun analyzeChart(
        bitmap: Bitmap,
        userNotes: String = "",
        modelOverride: String = "gemini-3.8-flash"
    ): ChartVisionAnalysis = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val missionId = UUID.randomUUID().toString()

        eventBus.emit(
            MatrixEvent(
                missionId = missionId,
                type = MatrixEventType.TOOL_STARTED,
                sourceNode = "vision:chart_analyzer",
                targetNode = "llm:gemini",
                message = "Starting AI Chart Vision scan on image using $modelOverride"
            )
        )

        require(bitmap.width > 0 && bitmap.height > 0) { "Chart image is empty or invalid." }
        require(apiStore.loadProvider() == LlmProvider.GEMINI) {
            "Chart Vision currently supports Gemini only. Select Gemini and a verified vision model."
        }
        require(modelOverride.startsWith("gemini-", ignoreCase = true)) {
            "The selected model is not a Gemini vision model. Select a verified Gemini vision model."
        }
        // Compress and prepare image
        val base64Image = encodeBitmapToBase64(bitmap)
        val resolvedKey = apiStore.loadApiKey().ifBlank { "" }
        val baseUrl = apiStore.loadBaseUrl()

        if (resolvedKey.isBlank()) {
            throw IllegalStateException("Gemini API key is missing. Configure it in API Hub and retry.")
        }

        try {
            val root = ApiDiscoveryEngine.geminiRoot(baseUrl)
            val modelName = modelOverride.removePrefix("models/").ifBlank { "gemini-3.8-flash" }
            val endpoint = root + "/models/" + modelName + ":generateContent?key=" +
                java.net.URLEncoder.encode(resolvedKey, "UTF-8")

            val prompt = buildTechnicalPrompt(userNotes)

            // Construct Gemini Multimodal REST request
            val contents = JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", prompt))
                            .put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject()
                                        .put("mimeType", "image/jpeg")
                                        .put("data", base64Image)
                                )
                            )
                    )
            )

            val systemInstruction = JSONObject().put(
                "parts",
                JSONArray().put(
                    JSONObject().put(
                        "text",
                        "You are MAXIMUS MATRIX AI Chart Vision, a quantitative market technician and multi-modal financial chart analyst. " +
                            "Analyze the provided chart screenshot with surgical precision. Identify prevailing trends, key trendlines, " +
                            "chart patterns (continuation/reversal), critical support & resistance zones, candlestick formations, " +
                            "and an actionable risk-managed trade setup. Always provide both the requested JSON block and markdown commentary."
                    )
                )
            )

            val requestBody = JSONObject()
                .put("contents", contents)
                .put("systemInstruction", systemInstruction)
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.2)
                        .put("maxOutputTokens", 4096)
                )

            val responseJson = post(endpoint, requestBody.toString())
            val rawText = parseGeminiText(responseJson)
            val usageMeta = responseJson.optJSONObject("usageMetadata")
            val inTokens = usageMeta?.optInt("promptTokenCount", 0) ?: 0
            val outTokens = usageMeta?.optInt("candidatesTokenCount", 0) ?: 0
            val totalTokens = usageMeta?.optInt("totalTokenCount", inTokens + outTokens) ?: (inTokens + outTokens)

            // Record usage
            if (usageMeta != null) {
                usageStore.record(LlmProvider.GEMINI, modelName, ChatUsage(inTokens, outTokens, totalTokens, estimated = false))
            }

            val parsedAnalysis = parseAnalysisOutput(
                rawText = rawText,
                modelUsed = modelName,
                base64Image = base64Image,
                inTokens = inTokens,
                outTokens = outTokens,
                totalTokens = totalTokens,
                elapsedMs = System.currentTimeMillis() - startTime
            )

            eventBus.emit(
                MatrixEvent(
                    missionId = missionId,
                    type = MatrixEventType.TOOL_COMPLETED,
                    sourceNode = "vision:chart_analyzer",
                    targetNode = "ui:chart_vision",
                    message = "AI Chart Vision completed successfully: ${parsedAnalysis.direction.displayName} on ${parsedAnalysis.assetIdentifier}"
                )
            )

            return@withContext parsedAnalysis
        } catch (e: Exception) {
            AppLogStore.error("VISION", "Gemini multimodal chart vision failed: " + (e.message ?: "unknown error"))
            throw e
        }
    }

    private fun buildTechnicalPrompt(userNotes: String): String {
        val notesSegment = if (userNotes.isNotBlank()) "User context / notes: $userNotes\n\n" else ""
        return """
            ${notesSegment}Perform a comprehensive technical analysis of this trading chart screenshot.
            Identify:
            1. Asset symbol and timeframe (if discernible).
            2. Primary trend (Bullish, Bearish, Sideways/Ranging) and trend strength (Strong, Moderate, Weak).
            3. All visible trendlines (e.g. Upper Resistance line, Ascending Support line, Dynamic Moving Averages).
            4. Chart patterns (e.g. Bull Flag, Ascending Triangle, Double Bottom, Head and Shoulders, Cup & Handle, Wedge, Channel).
            5. Key horizontal support and resistance price zones.
            6. Significant candlestick patterns (e.g. Hammer, Bullish Engulfing, Morning Star, Pin bar).
            7. Trade plan only where readable chart prices support it. Otherwise use WAIT and "Unavailable" for unsupported levels.

            Ground every finding in visible image evidence. Use "Unclear" for unreadable asset/timeframe and avoid invented numbers, angles, prices, confidence percentages, and risk/reward ratios. If the image is not a readable trading chart, explain that clearly.

            IMPORTANT: Output your structured findings FIRST inside a ```json ``` block with this exact schema:
            ```json
            {
              "asset": "Unclear",
              "timeframe": "Unclear",
              "direction": "RANGING",
              "strength": "WEAK",
              "trendSummary": "Describe only what is visible in the image.",
              "trendlines": [],
              "patterns": [],
              "keyLevels": [],
              "candleSignals": [],
              "tradePlan": {
                "bias": "WAIT",
                "entryZone": "Unavailable",
                "stopLoss": "Unavailable",
                "target1": "Unavailable",
                "target2": "Unavailable",
                "riskRewardRatio": "Unavailable",
                "invalidationReason": "Insufficient readable evidence."
              }
            }
            ```

            After the JSON block, provide a clear, professional technical breakdown explaining the market dynamics, volume behavior, and strategic trade execution.
        """.trimIndent()
    }

    private fun parseGeminiText(responseJson: JSONObject): String {
        val candidates = responseJson.optJSONArray("candidates") ?: return ""
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val text = part.optString("text")
            if (text.isNotBlank()) sb.append(text).append("\n")
        }
        val out = sb.toString().trim()
        if (out.isBlank()) throw IllegalStateException("Gemini returned no response text.")
        return out
    }

    private fun parseAnalysisOutput(
        rawText: String,
        modelUsed: String,
        base64Image: String,
        inTokens: Int,
        outTokens: Int,
        totalTokens: Int,
        elapsedMs: Long
    ): ChartVisionAnalysis {
        // Look for JSON block
        val jsonPattern = Regex("```json\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        val match = jsonPattern.find(rawText)
        val jsonString = match?.groups?.get(1)?.value

        if (!jsonString.isNullOrBlank()) {
            try {
                val obj = JSONObject(jsonString)
                val asset = obj.optString("asset", "Unclear")
                val timeframe = obj.optString("timeframe", "Unclear")
                val dirStr = obj.optString("direction", "").uppercase()
                val strengthStr = obj.optString("strength", "").uppercase()
                val summary = obj.optString("trendSummary", "")
                require(summary.isNotBlank() && dirStr.isNotBlank() && strengthStr.isNotBlank()) {
                    "Missing chart analysis fields"
                }
                require(dirStr.contains("BEAR") || dirStr.contains("BULL") || dirStr.contains("RANGE") || dirStr.contains("SIDE")) {
                    "Invalid chart direction"
                }

                val dir = when {
                    dirStr.contains("BEAR") -> TrendDirection.BEARISH
                    dirStr.contains("RANGE") || dirStr.contains("SIDE") -> TrendDirection.RANGING
                    else -> TrendDirection.BULLISH
                }

                val strength = when {
                    strengthStr.contains("MOD") -> TrendStrength.MODERATE
                    strengthStr.contains("WEAK") -> TrendStrength.WEAK
                    else -> TrendStrength.STRONG
                }

                val trendlines = mutableListOf<ChartTrendline>()
                val tArray = obj.optJSONArray("trendlines")
                if (tArray != null) {
                    for (i in 0 until tArray.length()) {
                        val item = tArray.optJSONObject(i) ?: continue
                        trendlines.add(
                            ChartTrendline(
                                type = item.optString("type", "Trendline"),
                                description = item.optString("description", ""),
                                angle = item.optString("angle", ""),
                                significance = item.optString("significance", "Major")
                            )
                        )
                    }
                }

                val patterns = mutableListOf<ChartPatternItem>()
                val pArray = obj.optJSONArray("patterns")
                if (pArray != null) {
                    for (i in 0 until pArray.length()) {
                        val item = pArray.optJSONObject(i) ?: continue
                        patterns.add(
                            ChartPatternItem(
                                name = item.optString("name", "Detected Pattern"),
                                patternType = item.optString("patternType", "Continuation"),
                                confidencePercent = 0,
                                status = item.optString("status", "Confirmed"),
                                implication = item.optString("implication", "Favorable bias")
                            )
                        )
                    }
                }

                val keyLevels = mutableListOf<PriceZone>()
                val lArray = obj.optJSONArray("keyLevels")
                if (lArray != null) {
                    for (i in 0 until lArray.length()) {
                        val item = lArray.optJSONObject(i) ?: continue
                        keyLevels.add(
                            PriceZone(
                                level = item.optString("level", "-"),
                                type = item.optString("type", "Key Zone"),
                                description = item.optString("description", "")
                            )
                        )
                    }
                }

                val candleSignals = mutableListOf<CandleSignal>()
                val cArray = obj.optJSONArray("candleSignals")
                if (cArray != null) {
                    for (i in 0 until cArray.length()) {
                        val item = cArray.optJSONObject(i) ?: continue
                        candleSignals.add(
                            CandleSignal(
                                name = item.optString("name", "Candle Pattern"),
                                location = item.optString("location", "Recent bar"),
                                implication = item.optString("implication", "")
                            )
                        )
                    }
                }

                val tpObj = obj.optJSONObject("tradePlan")
                val tradePlan = if (tpObj != null) {
                    TradePlan(
                        bias = tpObj.optString("bias", "WAIT"),
                        entryZone = tpObj.optString("entryZone", "-"),
                        stopLoss = tpObj.optString("stopLoss", "-"),
                        target1 = tpObj.optString("target1", "-"),
                        target2 = tpObj.optString("target2", "-"),
                        riskRewardRatio = tpObj.optString("riskRewardRatio", "Unavailable"),
                        invalidationReason = tpObj.optString("invalidationReason", "")
                    )
                } else TradePlan(bias = "WAIT", entryZone = "Unavailable", stopLoss = "Unavailable", target1 = "Unavailable", riskRewardRatio = "Unavailable")

                // Extract remaining markdown report
                val reportClean = rawText.replace(match.value, "").trim()

                return ChartVisionAnalysis(
                    modelUsed = modelUsed,
                    assetIdentifier = asset,
                    timeframeEstimate = timeframe,
                    direction = dir,
                    trendStrength = strength,
                    trendSummary = summary,
                    trendlines = trendlines,
                    patterns = patterns,
                    keyLevels = keyLevels,
                    candleSignals = candleSignals,
                    tradePlan = tradePlan,
                    comprehensiveReport = reportClean.ifBlank { rawText },
                    inputTokens = inTokens,
                    outputTokens = outTokens,
                    totalTokens = totalTokens,
                    processingMs = elapsedMs,
                    imageBase64 = base64Image
                )
            } catch (e: Exception) {
                AppLogStore.warn("VISION", "JSON parse issue: ${e.message}, falling back to text parsing")
            }
        }

        throw IllegalStateException("Gemini returned invalid structured chart analysis. Retry with a clearer chart image.")
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        // Scale down if image is huge to optimize token bandwidth and latency
        val maxDimension = 1280
        val scaled = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else {
            bitmap
        }
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun post(url: String, body: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MAXIMUS-AI/1.3")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val errorMsg = runCatching {
                JSONObject(text).optJSONObject("error")?.optString("message")
            }.getOrNull()
            throw IllegalStateException(errorMsg?.takeIf { it.isNotBlank() } ?: "HTTP $code: $text")
        }
        return JSONObject(text)
    }
}
