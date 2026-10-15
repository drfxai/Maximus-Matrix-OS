package ai.drfx.maximus.matrixai.agent

import org.junit.Assert.*
import org.junit.Test

class BrainRequestRouterTest {
    private fun command(text: String): String =
        (BrainRequestRouter.route(text) as BrainRequestRouter.Request.DeviceAction).canonicalObjective

    @Test fun normalQuestionsAreModelConversations() {
        for (question in listOf("hi", "سلام، حالت چطوره؟",
            "How can I improve my camera?", "Explain Wi-Fi security",
            "چگونه تنظیمات وای فای را تغییر بدهم؟", "عکس گرفتن با دوربین چگونه است؟",
            "Как открыть камеру?", "Kamera nasıl açılır?", "كيف أفتح الكاميرا؟",
            "آیا میتوانی برای تلگرام ربات بسازی؟",
            "یک برنامه بنویس که تلگرام را باز کند")) {
            assertTrue(question, BrainRequestRouter.route(question) is
                BrainRequestRouter.Request.Conversation)
        }
    }

    @Test fun explicitActionsWorkInTheOtherThreeUiLanguages() {
        listOf("افتح الكاميرا", "kamerayı aç", "открой камеру").forEach {
            assertEquals("camera", command(it))
        }
        listOf("افتح تلغرام", "telegram aç", "открой телеграм").forEach {
            assertEquals("app org.telegram.messenger", command(it))
        }
        listOf("افتح كروم", "chrome aç", "открой хром").forEach {
            assertEquals("app com.android.chrome", command(it))
        }
        listOf("شغل الواي فاي", "wifi kapat", "включи wi-fi").forEach {
            assertEquals("settings wifi", command(it))
        }
        listOf("افتح الإعدادات", "ayarları aç", "открой настройки").forEach {
            assertEquals("settings", command(it))
        }
        assertEquals("search kotlin", command("search google for Kotlin"))
        assertEquals("search kotlin", command("ابحث عن Kotlin"))
        assertEquals("search kotlin", command("ara Kotlin"))
        assertEquals("search kotlin", command("найди Kotlin"))
    }

    @Test fun explicitPersianAndEnglishPhoneActionsAreAllowlisted() {
        assertEquals("camera", command("دوربین را باز کن"))
        assertEquals("camera", command("عکس بگیر"))
        assertEquals("camera", command("Open camera"))
        assertEquals("settings wifi", command("وای فای را روشن کن"))
        assertEquals("settings wifi", command("turn off wifi"))
        assertEquals("app org.telegram.messenger", command("تلگرام رو باز کن"))
        assertEquals("app com.android.chrome", command("open Google Chrome"))
        assertEquals("settings", command("تنظیمات را باز کن"))
        assertEquals("search بهترین مدل محلی", command("سرچ کن بهترین مدل محلی"))
        assertEquals("search kotlin coroutine", command("search Kotlin coroutine"))
    }

    @Test fun noDirectSettingsToggleAndNoArbitraryCommands() {
        assertTrue(BrainRequestRouter.route("disable security checks") is
            BrainRequestRouter.Request.Conversation)
        assertTrue(BrainRequestRouter.route("delete all messages") is
            BrainRequestRouter.Request.Conversation)
        assertEquals("settings wifi", command("وایفای را خاموش کن"))
    }
}
