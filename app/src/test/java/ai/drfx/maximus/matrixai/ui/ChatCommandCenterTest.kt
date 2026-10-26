package ai.drfx.maximus.matrixai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCommandCenterTest {
    @Test fun shortModelNameDropsVendorFolderAndDateSuffix() {
        assertEquals("llama-3.3-70b-instruct", shortModelName("meta/llama-3.3-70b-instruct"))
        assertEquals("claude-3-5-sonnet", shortModelName("claude-3-5-sonnet-20241022"))
        assertEquals("gpt-4o", shortModelName("gpt-4o-2024-08-06"))
        assertEquals("gemini-2.5-flash", shortModelName("models/gemini-2.5-flash"))
        assertEquals("", shortModelName(""))
    }

    @Test fun compactTokensUsesReadableUnits() {
        assertEquals("950", compactTokens(950))
        assertEquals("1.5k", compactTokens(1_500))
        assertEquals("2k", compactTokens(2_000))
        assertEquals("128k", compactTokens(128_000))
        assertEquals("1M", compactTokens(1_000_000))
        assertEquals("1M", compactTokens(1_048_576))
    }
}
