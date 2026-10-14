package ai.drfx.maximus.matrixai.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechTranscriptGuardTest {
    @Test fun persianRecognizerOutputInEnglishIsReviewOnly() {
        val candidate = SpeechTranscriptGuard.choose(
            listOf("Open Telegram", "Turn on Wi Fi"), SpeechLanguage.PERSIAN
        )
        assertEquals("Open Telegram", candidate.text)
        assertFalse(candidate.scriptMatchesLanguage)
        assertFalse(candidate.mayAutomaticallySend)
    }

    @Test fun persianScriptWinsOverIncorrectEnglishFirstCandidate() {
        val choice = SpeechTranscriptGuard.choose(
            listOf("salam chetori", "سلام چطوری", "hi"), SpeechLanguage.PERSIAN
        )
        assertEquals("سلام چطوری", choice.text)
        assertTrue(choice.mayAutomaticallySend)
    }

    @Test fun arabicAndRussianSpeechHaveIndependentScriptGates() {
        assertTrue(SpeechTranscriptGuard.choose(listOf("مرحبا"), SpeechLanguage.ARABIC)
            .mayAutomaticallySend)
        assertFalse(SpeechTranscriptGuard.choose(listOf("privet"), SpeechLanguage.RUSSIAN)
            .mayAutomaticallySend)
        assertTrue(SpeechTranscriptGuard.choose(listOf("Привет!"), SpeechLanguage.RUSSIAN)
            .mayAutomaticallySend)
    }

    @Test fun noSpeechNeverProducesAnAutomaticTurn() {
        assertNull(SpeechTranscriptGuard.choose(emptyList(), SpeechLanguage.PERSIAN).text)
        assertFalse(SpeechTranscriptGuard.choose(listOf("  "), SpeechLanguage.ENGLISH)
            .mayAutomaticallySend)
    }

    @Test fun englishAndTurkishShareLatinScriptWithoutUnsupportedLanguageGuessing() {
        assertTrue(SpeechTranscriptGuard.choose(listOf("Merhaba"), SpeechLanguage.TURKISH)
            .mayAutomaticallySend)
        assertTrue(SpeechTranscriptGuard.choose(listOf("hello"), SpeechLanguage.ENGLISH)
            .mayAutomaticallySend)
    }

    @Test fun digitsWithoutPersianLettersNeedReviewNotAutoSend() {
        val result = SpeechTranscriptGuard.choose(listOf("12345"), SpeechLanguage.PERSIAN)
        assertEquals("12345", result.text)
        assertFalse(result.mayAutomaticallySend)
    }

    @Test fun mixedPersianAndEnglishNamesAreAllowed() {
        assertTrue(SpeechTranscriptGuard.choose(
            listOf("تلگرام رو باز کن Telegram"), SpeechLanguage.PERSIAN
        ).mayAutomaticallySend)
    }
}
