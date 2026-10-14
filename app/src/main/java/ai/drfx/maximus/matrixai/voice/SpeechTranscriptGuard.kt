package ai.drfx.maximus.matrixai.voice

/**
 * The external Android speech-recognition activity can ignore a requested locale.
 *
 * This is a conservative safety check, NOT a transcription accuracy verifier:
 * the transcript may still be wrong even when its script matches. When a
 * Persian/Arabic/Russian voice turn arrives in an unrelated script, keep its
 * editable text but never silently send it as a conversation turn.
 */
object SpeechTranscriptGuard {
    data class Selection(val text: String?, val scriptMatchesLanguage: Boolean) {
        val mayAutomaticallySend: Boolean get() = !text.isNullOrBlank() && scriptMatchesLanguage
    }

    fun choose(results: List<String>?, language: SpeechLanguage): Selection {
        val candidates = results.orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
        if (candidates.isEmpty()) return Selection(null, false)
        val matching = candidates.firstOrNull { scriptMatches(it, language) }
        return if (matching != null) Selection(matching, true)
        else Selection(candidates.first(), false)
    }

    fun scriptMatches(text: String, language: SpeechLanguage): Boolean {
        return when (language) {
            SpeechLanguage.PERSIAN, SpeechLanguage.ARABIC ->
                text.any { Character.isLetter(it) && (
                    it in '\u0600'..'\u06ff' || it in '\u0750'..'\u077f' ||
                    it in '\u08a0'..'\u08ff' || it in '\ufb50'..'\ufdff' ||
                    it in '\ufe70'..'\ufeff') }
            SpeechLanguage.RUSSIAN ->
                text.any { it in '\u0400'..'\u052f' }
            // Latin script is shared by English and Turkish. No reliable
            // language classification is possible from script alone.
            SpeechLanguage.ENGLISH, SpeechLanguage.TURKISH -> true
        }
    }
}
