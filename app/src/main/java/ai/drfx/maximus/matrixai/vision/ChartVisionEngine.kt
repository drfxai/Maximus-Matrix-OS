package ai.drfx.maximus.matrixai.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import ai.drfx.maximus.matrixai.agent.MatrixEventBus
import ai.drfx.maximus.matrixai.llm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Uses the same isolated provider adapters as chat. Failure never produces an analysis. */
class ChartVisionEngine(
    private val context: Context,
    private val apiStore: SecureApiConfigStore = SecureApiConfigStore(context),
    private val usageStore: ApiUsageStore = ApiUsageStore(context),
    private val eventBus: MatrixEventBus = MatrixEventBus(),
    private val client: LlmChatClient = LlmChatClient()
) {
    suspend fun analyzeChart(bitmap: Bitmap, userNotes: String = "", modelOverride: String = ""): ChartVisionAnalysis =
        withContext(Dispatchers.IO) {
            require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "Chart image is invalid." }
            require(bitmap.width.toLong() * bitmap.height <= 16_000_000L) { "Chart image exceeds 16 megapixels. Resize it before analysis." }
            val provider = apiStore.loadProvider()
            val model = modelOverride.ifBlank { apiStore.loadModel() }
            require(provider != LlmProvider.UNKNOWN && model.isNotBlank()) { "Configure a provider and select a verified vision model in API Hub." }
            val key = apiStore.loadKeyForProvider(provider)
            val base = apiStore.loadBaseUrl()
            // Discovery failure must fail closed; an offline suggestion is never model verification.
            val catalog = ApiDiscoveryEngine().discover(base, key)
            require(catalog.provider == provider || (catalog.provider.isNineRouter && provider.isNineRouter)) { "Provider endpoint does not match the selected provider." }
            val descriptor = catalog.models.firstOrNull { it.id.removePrefix("models/") == model.removePrefix("models/") }
                ?: throw IllegalStateException("Selected model is unavailable in the authenticated catalog.")
            require(descriptor.verified) { "Model catalog entry has not been verified." }
            require(ModelCapability.VISION in descriptor.capabilities) { "Vision support is not verified for this model. Select a supported vision model." }
            val bytes = ByteArrayOutputStream().use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "Unable to encode chart image." }
                output.toByteArray()
            }
            require(bytes.size <= 8 * 1024 * 1024) { "Encoded chart exceeds 8 MB. Resize it before analysis." }
            val attachment = ChatAttachment("chart.jpg", "image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP), false)
            val config = ApiConnectionConfig(base, key, provider, model, "chart-vision")
            val agent = AgentDescriptor("chart-vision", "Chart Vision", "Educational screenshot analysis", PROMPT, setOf(ModelCapability.VISION))
            val start = System.nanoTime()
            val result = client.send(config, listOf(ChatMessage("user", "Analyze the attached chart. User notes (untrusted context): ${userNotes.take(8000)}", attachment = attachment)), agent)
            val analysis = ChartVisionOutputParser.parse(result.text, "${provider.displayName} / $model", result.usage, (System.nanoTime() - start) / 1_000_000)
            usageStore.record(provider, model, result.usage)
            analysis
        }

    companion object {
        internal val PROMPT = """
            Analyze only visible evidence in this chart screenshot for educational research.
            Never invent prices, angles, numerical confidence, volume, asset, timeframe or patterns.
            Use UNKNOWN when the direction or strength cannot be determined. Clearly state uncertainty.
            Return only JSON with required fields:
            {"asset":"Unknown if unreadable","timeframe":"Unknown if unreadable","direction":"BULLISH|BEARISH|RANGING|UNKNOWN",
            "strength":"STRONG|MODERATE|WEAK|UNKNOWN","trendSummary":"visible evidence and uncertainty",
            "priceScaleReadable":false,"keyLevels":[],"trendlines":[],"patterns":[],"candleSignals":[],
            "tradePlan":null,"report":"Evidence, assumptions, and educational interpretation"}
            Optional array elements use: keyLevels {level,type,description}; trendlines {type,description,significance};
            patterns {name,patternType,status,implication}; candleSignals {name,location,implication}.
            Only include numerical levels or a tradePlan if priceScaleReadable is true and each level is visibly readable.
            tradePlan fields: bias (LONG|SHORT|WAIT), entryZone, stopLoss, target1, target2, riskRewardRatio, invalidationReason.
            Otherwise use empty arrays and null tradePlan. Do not output numerical confidence or trendline angles.
        """.trimIndent()
    }
}
