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
        fun normalize(value: String) = value.lowercase(Locale.ROOT)
            .replace('ي', 'ی').replace('ك', 'ک').replace('‌', ' ')
            .replace(Regex("\\s+"), " ").trim()
        val lower = normalize(text)
        // Explicit localized imperatives share the SAME confirmation-gated
        // canonical actions. Do not infer commands from words in explanations.
        val localized = mapOf(
            "افتح الكاميرا" to "camera", "التقط صورة" to "camera",
            "kamerayı aç" to "camera", "fotoğraf çek" to "camera",
            "открой камеру" to "camera", "сделай фото" to "camera",
            "افتح تلغرام" to "app org.telegram.messenger",
            "افتح تيليجرام" to "app org.telegram.messenger",
            "telegram aç" to "app org.telegram.messenger",
            "telegram uygulamasını aç" to "app org.telegram.messenger",
            "открой телеграм" to "app org.telegram.messenger",
            "открой telegram" to "app org.telegram.messenger",
            "افتح كروم" to "app com.android.chrome",
            "chrome aç" to "app com.android.chrome",
            "chrome uygulamasını aç" to "app com.android.chrome",
            "открой chrome" to "app com.android.chrome",
            "открой хром" to "app com.android.chrome",
            "افتح إعدادات الواي فاي" to "settings wifi",
            "شغل الواي فاي" to "settings wifi", "أوقف الواي فاي" to "settings wifi",
            "wi-fi ayarlarını aç" to "settings wifi", "wifi aç" to "settings wifi",
            "wifi kapat" to "settings wifi", "открой настройки wi-fi" to "settings wifi",
            "включи wi-fi" to "settings wifi", "выключи wi-fi" to "settings wifi",
            "افتح الإعدادات" to "settings", "ayarları aç" to "settings",
            "открой настройки" to "settings"
        ).entries.firstOrNull { normalize(it.key) == lower }?.value
        if (localized != null) return Request.DeviceAction(localized)
        val localizedQuery = Regex("^(?:ابحث عن|ara|найди|ищи)\\s+(.+)$").find(lower)
            ?.groupValues?.getOrNull(1)?.trim()
        if (!localizedQuery.isNullOrEmpty())
            return Request.DeviceAction("search ${localizedQuery.take(350)}")

        if (listOf("how ", "explain ", "چگونه", "آیا ", "چطور ").any { lower.startsWith(it) })
            return Request.Conversation(text)

        // "برنامه بنویس که تلگرام را باز کند" is a request for an explanation,
        // not an imperative. Do not match the "باز کن" prefix of "باز کند".
        val explicitOpen = (
            Regex("(^|\\s)(باز کن|بازش کن|اجرا کن|راه اندازی کن)(\\s|$)").containsMatchIn(lower) ||
                lower.startsWith("open ") || lower.startsWith("launch ") ||
                lower.startsWith("start ")
        ) && !lower.contains("بنویس") && !lower.startsWith("چگونه ") &&
            !lower.startsWith("how ") && !lower.startsWith("explain ")
        val photography = lower.startsWith("عکس بگیر") ||
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

        val englishSearch = Regex("^(?:search google for|search|google)\\s+(.+)$").find(lower)
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
