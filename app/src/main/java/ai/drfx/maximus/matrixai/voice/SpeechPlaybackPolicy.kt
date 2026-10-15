package ai.drfx.maximus.matrixai.voice

/**
 * Automatic replies must never start while the app is inactive or another
 * activity is recognizing the user or a voice note is recording. A blocked reply is still eligible after
 * returning, while successfully queued replies are never replayed on resume.
 * Manual replay is an explicit user action and does not use this policy.
 */
internal class SpeechPlaybackPolicy {
    private var spokenReplyId: Long? = null

    fun mayPlay(replyId: Long, isResumed: Boolean, microphoneInUse: Boolean): Boolean =
        isResumed && !microphoneInUse && replyId != spokenReplyId

    /** Call only after the speech engine accepts the utterance. */
    fun markSpoken(replyId: Long) {
        spokenReplyId = replyId
    }

    /** Enabling voice replies affects future replies, not the displayed history. */
    fun skipExistingReply(replyId: Long?) {
        spokenReplyId = replyId
    }
}
