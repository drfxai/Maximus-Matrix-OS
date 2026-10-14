package ai.drfx.maximus.matrixai.agent

import java.util.Locale

/**
 * Route only explicit device imperatives into the allowlisted, confirmation-gated
 * Android runtime. Everything else is an ordinary LLM conversation.
 * Never interpret model-generated text as an executable device command.
 */
internal object BrainRequestRouter {
    sealed interface Request {
        data class Conversation(val text: String) : Request
        data class DeviceAction(val canonicalObjective: String) : Request
    }

    fun route(input: String): Request {
        val text = input.trim().take(4000)
        if (text.isEmpty()) return Request.Conversation(text)
        val lower = text.lowercase(Locale.ROOT)
            .replace('ي', 'ی').replace('ك', 'ک').replace('‌', ' ')
            .replace(Regex("\\s+"), " ").trim()

        // "برنامه بنویس که تلگرام را باز کند" is a request for an explanation,
        // not an imperative. Do not match the "باز کن" prefix of "باز کند".
        val explicitOpen = (
            Regex("(^|\\s)(باز کن|بازش کن|اجرا کن|راه اندازی کن)(\\s|$)").containsMatchIn(lower) ||
                lower.startsWith("open ") || lower.startsWith("launch ") ||
                lower.startsWith("start ")
        ) && !lower.contains("بنویس") && !lower.startsWith("چگونه ") &&
            !lower.startsWith("how ") && !lower.startsWith("explain ")
        val photography = lower.startsWith("عکس بگیر") || lower.startsWith("عکس گرفتن") ||
            lower.startsWith("take a photo") || lower.startsWith("take photo") ||
            lower.startsWith("open camera") || lower.startsWith("camera open")
        if (photography || (explicitOpen && (lower.contains("دوربین") || lower.contains("camera")))) {
            return Request.DeviceAction("camera")
        }

        val network = lower.contains("وای فای") || lower.contains("وایفای") ||
            lower.contains("wifi") || lower.contains("wi-fi")
        if (network && (explicitOpen || lower.contains("روشن کن") ||
                    lower.contains("خاموش کن") || lower.startsWith("turn on") ||
                    lower.startsWith("turn off") || lower.contains("تنظیمات"))) {
            // An ordinary app cannot silently toggle Wi-Fi on modern Android.
            return Request.DeviceAction("settings wifi")
        }

        if (explicitOpen && (lower.contains("تلگرام") || lower.contains("telegram"))) {
            return Request.DeviceAction("app org.telegram.messenger")
        }
        if (explicitOpen && (lower.contains("گوگل کروم") || lower.contains("chrome") ||
                    lower.contains("مرورگر کروم"))) {
            return Request.DeviceAction("app com.android.chrome")
        }

        val englishSearch = Regex("^(?:search|google|search google for)\\s+(.+)$").find(lower)
        val persianSearch = Regex("^(?:در گوگل )?(?:جستجو کن|جست و جو کن|سرچ کن)\\s+(.+)$").find(lower)
            ?: Regex("^(.+?)\\s+(?:را )?(?:در گوگل )?(?:جستجو کن|سرچ کن)$").find(lower)
        val query = (englishSearch?.groupValues?.getOrNull(1)
            ?: persianSearch?.groupValues?.getOrNull(1))?.trim().orEmpty()
        if (query.isNotEmpty()) {
            return Request.DeviceAction("search ${query.take(350)}")
        }

        if ((lower.startsWith("settings") || lower.startsWith("open settings")) ||
            (explicitOpen && (lower.contains("تنظیمات گوشی") || lower == "تنظیمات را باز کن" ||
                lower == "تنظیمات باز کن"))) {
            return Request.DeviceAction("settings")
        }
        return Request.Conversation(text)
    }
}
