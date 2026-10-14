package ai.drfx.maximus.matrixai.agent

import org.junit.Assert.*
import org.junit.Test

class BrainRequestRouterTest {
    private fun command(text: String): String =
        (BrainRequestRouter.route(text) as BrainRequestRouter.Request.DeviceAction).canonicalObjective

    @Test fun normalQuestionsAreModelConversations() {
        for (question in listOf("hi", "سلام، حالت چطوره؟",
            "How can I improve my camera?", "Explain Wi-Fi security",
            "آیا میتوانی برای تلگرام ربات بسازی؟",
            "یک برنامه بنویس که تلگرام را باز کند")) {
            assertTrue(question, BrainRequestRouter.route(question) is
                BrainRequestRouter.Request.Conversation)
        }
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
