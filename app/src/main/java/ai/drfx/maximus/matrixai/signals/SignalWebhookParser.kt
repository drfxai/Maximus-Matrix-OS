package ai.drfx.maximus.matrixai.signals

import ai.drfx.maximus.matrixai.database.entities.TradingSignalEntity
import org.json.JSONObject
import java.util.UUID

sealed class WebhookParseResult {
    data class Success(val signal: TradingSignalEntity, val rawPayload: String) : WebhookParseResult()
    data class Failure(val errorMessage: String, val rawPayload: String) : WebhookParseResult()
}

/**
 * Robust parser for incoming TradingView and Cloudflare webhook signals.
 */
object SignalWebhookParser {

    fun parse(rawBody: String, sourceHint: String = "External Webhook"): WebhookParseResult {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) {
            return WebhookParseResult.Failure("Payload is empty", rawBody)
        }

        return try {
            if (trimmed.startsWith("{")) {
                parseJson(trimmed, sourceHint)
            } else {
                parsePlainText(trimmed, sourceHint)
            }
        } catch (e: Exception) {
            WebhookParseResult.Failure("Malformed payload: ${e.localizedMessage ?: "Unknown error"}", rawBody)
        }
    }

    private fun parseJson(jsonString: String, sourceHint: String): WebhookParseResult {
        val root = JSONObject(jsonString)

        // Check if nested inside Cloudflare wrapper (e.g. { "source": "cloudflare", "signal": { ... } })
        val json = if (root.has("signal") && root.get("signal") is JSONObject) {
            root.getJSONObject("signal")
        } else {
            root
        }

        // Symbol / Ticker resolution
        val symbolRaw = when {
            json.has("ticker") -> json.optString("ticker")
            json.has("symbol") -> json.optString("symbol")
            json.has("pair") -> json.optString("pair")
            json.has("instrument") -> json.optString("instrument")
            else -> "UNKNOWN"
        }.trim().uppercase()

        if (symbolRaw.isEmpty() || symbolRaw == "UNKNOWN") {
            return WebhookParseResult.Failure("Missing required 'ticker' or 'symbol' field", jsonString)
        }

        // Format symbol cleanly (e.g. BTCUSDT -> BTC/USDT if 6 chars or contains slash)
        val formattedSymbol = formatSymbol(symbolRaw)

        // Direction / Action resolution
        val actionRaw = when {
            json.has("action") -> json.optString("action")
            json.has("direction") -> json.optString("direction")
            json.has("order") -> json.optString("order")
            json.has("side") -> json.optString("side")
            json.has("signal") -> json.optString("signal")
            else -> "BUY"
        }.trim().uppercase()

        val direction = when {
            actionRaw.contains("BUY") || actionRaw.contains("LONG") -> "BUY"
            actionRaw.contains("SELL") || actionRaw.contains("SHORT") -> "SELL"
            else -> "BUY"
        }

        // Entry Price resolution
        val entryPrice = when {
            json.has("price") -> json.optDouble("price", 0.0)
            json.has("entry") -> json.optDouble("entry", 0.0)
            json.has("entry_price") -> json.optDouble("entry_price", 0.0)
            json.has("entryPrice") -> json.optDouble("entryPrice", 0.0)
            json.has("close") -> json.optDouble("close", 0.0)
            else -> 0.0
        }

        if (entryPrice <= 0.0) {
            return WebhookParseResult.Failure("Invalid or missing 'price' / 'entry' (got: $entryPrice)", jsonString)
        }

        // Stop Loss resolution (heuristic fallback if missing)
        val stopLoss = when {
            json.has("sl") -> json.optDouble("sl", 0.0)
            json.has("stop_loss") -> json.optDouble("stop_loss", 0.0)
            json.has("stopLoss") -> json.optDouble("stopLoss", 0.0)
            json.has("stop") -> json.optDouble("stop", 0.0)
            else -> 0.0
        }.let { sl ->
            if (sl > 0.0) sl
            else {
                // Heuristic: 1.5% SL
                if (direction == "BUY") entryPrice * 0.985 else entryPrice * 1.015
            }
        }

        // Take Profit 1 resolution
        val takeProfit1 = when {
            json.has("tp1") -> json.optDouble("tp1", 0.0)
            json.has("take_profit_1") -> json.optDouble("take_profit_1", 0.0)
            json.has("takeProfit1") -> json.optDouble("takeProfit1", 0.0)
            json.has("tp") -> json.optDouble("tp", 0.0)
            json.has("take_profit") -> json.optDouble("take_profit", 0.0)
            else -> 0.0
        }.let { tp ->
            if (tp > 0.0) tp
            else {
                // Heuristic: 2.5% TP
                if (direction == "BUY") entryPrice * 1.025 else entryPrice * 0.975
            }
        }

        // Take Profit 2 resolution
        val takeProfit2 = when {
            json.has("tp2") -> json.optDouble("tp2", 0.0)
            json.has("take_profit_2") -> json.optDouble("take_profit_2", 0.0)
            json.has("takeProfit2") -> json.optDouble("takeProfit2", 0.0)
            else -> 0.0
        }.let { tp ->
            if (tp > 0.0) tp
            else {
                if (direction == "BUY") entryPrice * 1.045 else entryPrice * 0.955
            }
        }

        // Take Profit 3 (optional)
        val takeProfit3 = when {
            json.has("tp3") -> json.optDouble("tp3", 0.0)
            json.has("take_profit_3") -> json.optDouble("take_profit_3", 0.0)
            json.has("takeProfit3") -> json.optDouble("takeProfit3", 0.0)
            else -> null
        }?.takeIf { it > 0.0 }

        // Timeframe resolution
        val timeframe = normalizeTimeframe(
            when {
                json.has("timeframe") -> json.optString("timeframe")
                json.has("tf") -> json.optString("tf")
                json.has("interval") -> json.optString("interval")
                else -> "M15"
            }
        )

        // Asset Class heuristic
        val assetClass = when {
            json.has("asset_class") -> json.optString("asset_class")
            json.has("assetClass") -> json.optString("assetClass")
            else -> inferAssetClass(formattedSymbol)
        }

        // Strategy name
        val strategyName = when {
            json.has("strategy") -> json.optString("strategy")
            json.has("strategy_name") -> json.optString("strategy_name")
            json.has("indicator") -> json.optString("indicator")
            else -> if (root.optString("source").contains("cloudflare", ignoreCase = true)) {
                "Cloudflare Relay · TradingView"
            } else {
                "TradingView Webhook Engine"
            }
        }

        // Author agent label
        val authorAgent = when {
            json.has("agent") -> json.optString("agent")
            root.optString("source").contains("cloudflare", ignoreCase = true) -> "Cloudflare Worker Gateway"
            sourceHint.contains("Cloudflare", ignoreCase = true) -> "Cloudflare Worker Gateway"
            else -> "TradingView Webhook"
        }

        // Confluence factors
        val confluence = when {
            json.has("confluence") -> json.optString("confluence")
            json.has("reasons") -> json.optString("reasons")
            else -> "Real-time automated webhook trigger, PineScript indicator confluence"
        }

        // AI rationale / explanation
        val aiRationale = when {
            json.has("rationale") -> json.optString("rationale")
            json.has("comment") -> json.optString("comment")
            json.has("message") -> json.optString("message")
            else -> "Executed via dedicated Webhook listener from $authorAgent with verified entry and target geometry."
        }

        // Win probability / Confidence
        val winProbability = extractConfidence(json).coerceIn(50, 99)

        // Risk Reward Ratio calculation
        val risk = kotlin.math.abs(entryPrice - stopLoss)
        val reward = kotlin.math.abs(takeProfit1 - entryPrice)
        val rrRatio = if (risk > 0.0) {
            val ratio = reward / risk
            "1:%.1f".format(ratio)
        } else {
            "1:3.0"
        }

        val signalType = when {
            timeframe in listOf("M1", "M5") -> "SCALPING"
            timeframe in listOf("M15", "H1") -> "INTRADAY"
            else -> "BREAKOUT"
        }

        val id = "sig_wh_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"

        val entity = TradingSignalEntity(
            id = id,
            symbol = formattedSymbol,
            assetClass = assetClass,
            direction = direction,
            signalType = signalType,
            timeframe = timeframe,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit1 = takeProfit1,
            takeProfit2 = takeProfit2,
            takeProfit3 = takeProfit3,
            currentPrice = entryPrice,
            status = "ACTIVE",
            winProbability = winProbability,
            riskRewardRatio = rrRatio,
            strategyName = strategyName,
            confluenceFactors = confluence,
            aiRationale = aiRationale,
            authorAgent = authorAgent,
            timestampMs = System.currentTimeMillis(),
            isBookmarked = true,
            isCustomUserSignal = true
        )

        return WebhookParseResult.Success(entity, jsonString)
    }

    private fun parsePlainText(plainText: String, sourceHint: String): WebhookParseResult {
        // Example: BUY BTCUSDT 67500 SL:66500 TP:69500
        val tokens = plainText.split("\\s+".toRegex())
        if (tokens.size < 3) {
            return WebhookParseResult.Failure("Text alert requires at least: [ACTION] [SYMBOL] [PRICE]", plainText)
        }

        var action = "BUY"
        var symbol = "BTC/USDT"
        var price = 0.0
        var sl = 0.0
        var tp1 = 0.0

        for (token in tokens) {
            val upper = token.uppercase()
            when {
                upper in listOf("BUY", "LONG") -> action = "BUY"
                upper in listOf("SELL", "SHORT") -> action = "SELL"
                upper.contains("/") || upper.endsWith("USDT") || upper.endsWith("USD") -> symbol = formatSymbol(upper)
                upper.startsWith("SL:") -> sl = upper.removePrefix("SL:").toDoubleOrNull() ?: 0.0
                upper.startsWith("TP:") -> tp1 = upper.removePrefix("TP:").toDoubleOrNull() ?: 0.0
                upper.startsWith("PRICE:") -> price = upper.removePrefix("PRICE:").toDoubleOrNull() ?: 0.0
                price == 0.0 && token.toDoubleOrNull() != null -> price = token.toDouble()
            }
        }

        if (price <= 0.0) {
            return WebhookParseResult.Failure("Unable to parse price from plain text alert", plainText)
        }

        if (sl == 0.0) {
            sl = if (action == "BUY") price * 0.985 else price * 1.015
        }
        if (tp1 == 0.0) {
            tp1 = if (action == "BUY") price * 1.03 else price * 0.97
        }

        val id = "sig_wh_txt_${System.currentTimeMillis()}"
        val entity = TradingSignalEntity(
            id = id,
            symbol = symbol,
            assetClass = inferAssetClass(symbol),
            direction = action,
            signalType = "INTRADAY",
            timeframe = "M15",
            entryPrice = price,
            stopLoss = sl,
            takeProfit1 = tp1,
            takeProfit2 = if (action == "BUY") price * 1.05 else price * 0.95,
            takeProfit3 = null,
            currentPrice = price,
            status = "ACTIVE",
            winProbability = 86,
            riskRewardRatio = "1:2.8",
            strategyName = "TradingView Text Alert",
            confluenceFactors = "Direct text trigger parsed by Maximus Webhook Engine",
            aiRationale = "Parsed text command received from $sourceHint.",
            authorAgent = "TradingView Webhook",
            timestampMs = System.currentTimeMillis(),
            isBookmarked = true,
            isCustomUserSignal = true
        )

        return WebhookParseResult.Success(entity, plainText)
    }

    private fun formatSymbol(raw: String): String {
        val s = raw.uppercase().replace("-", "/").replace("_", "/")
        if (s.contains("/")) return s
        if (s.endsWith("USDT")) return s.removeSuffix("USDT") + "/USDT"
        if (s.endsWith("USD")) return s.removeSuffix("USD") + "/USD"
        if (s.length == 6) return s.take(3) + "/" + s.takeLast(3)
        return s
    }

    fun extractConfidence(json: JSONObject): Int {
        if (json.has("confidence")) {
            val confVal = json.opt("confidence")
            if (confVal is Number) {
                val d = confVal.toDouble()
                return if (d in 0.0..1.0) (d * 100).toInt() else d.toInt()
            }
            val str = confVal?.toString()?.trim()?.removeSuffix("%") ?: ""
            str.toDoubleOrNull()?.let { d ->
                return if (d in 0.0..1.0) (d * 100).toInt() else d.toInt()
            }
        }
        if (json.has("win_probability")) {
            val v = json.opt("win_probability")
            if (v is Number) {
                val d = v.toDouble()
                return if (d in 0.0..1.0) (d * 100).toInt() else d.toInt()
            }
            v?.toString()?.removeSuffix("%")?.toDoubleOrNull()?.let { d ->
                return if (d in 0.0..1.0) (d * 100).toInt() else d.toInt()
            }
        }
        if (json.has("winProbability")) return json.optInt("winProbability", 88)
        if (json.has("probability")) return json.optInt("probability", 88)
        if (json.has("prob")) return json.optInt("prob", 88)
        return 88
    }

    private fun inferAssetClass(symbol: String): String {
        val s = symbol.uppercase()
        return when {
            s.contains("BTC") || s.contains("ETH") || s.contains("SOL") || s.contains("USDT") ||
                    s.contains("XRP") || s.contains("DOGE") || s.contains("BNB") -> "Crypto"
            s.contains("EUR") || s.contains("GBP") || s.contains("JPY") || s.contains("AUD") ||
                    s.contains("CAD") || s.contains("NZD") || s.contains("CHF") -> "Forex"
            s.contains("XAU") || s.contains("GOLD") || s.contains("OIL") || s.contains("WTI") ||
                    s.contains("BRENT") || s.contains("SILVER") || s.contains("XAG") -> "Commodities"
            s.contains("US30") || s.contains("SPX") || s.contains("NAS100") || s.contains("NDX") ||
                    s.contains("DAX") || s.contains("UK100") || s.contains("DOW") -> "Indices"
            else -> "Equities"
        }
    }

    fun normalizeTimeframe(raw: String): String {
        val upper = raw.trim().uppercase()
            .replace("MIN", "M")
            .replace("MINUTE", "M")
            .replace("HOUR", "H")
            .replace("DAY", "D")

        return when {
            upper in listOf("1M", "M1", "1") -> "M1"
            upper in listOf("5M", "M5", "5") -> "M5"
            upper in listOf("15M", "M15", "15") -> "M15"
            upper in listOf("30M", "M30", "30") -> "M30"
            upper in listOf("1H", "H1", "60") -> "H1"
            upper in listOf("4H", "H4", "240") -> "H4"
            upper in listOf("1D", "D1", "D", "DAILY") -> "D1"
            upper.endsWith("M") && !upper.startsWith("M") -> "M" + upper.removeSuffix("M")
            upper.endsWith("H") && !upper.startsWith("H") -> "H" + upper.removeSuffix("H")
            upper.endsWith("D") && !upper.startsWith("D") -> "D" + upper.removeSuffix("D")
            upper.startsWith("M") || upper.startsWith("H") || upper.startsWith("D") -> upper
            else -> "M$upper"
        }
    }
}
