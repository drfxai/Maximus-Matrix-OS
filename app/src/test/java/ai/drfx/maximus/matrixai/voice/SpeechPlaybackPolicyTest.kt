package ai.drfx.maximus.matrixai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPlaybackPolicyTest {
    /** Simulates the same enqueue-success boundary used by the Android TTS adapter. */
    private class VoiceSession {
        val policy = SpeechPlaybackPolicy()
        val queued = mutableListOf<Long>()
        var resumed = true
        var microphoneInUse = false
        var engineAcceptsUtterance = true

        fun replyCompleted(id: Long) {
            if (policy.mayPlay(id, resumed, microphoneInUse) && engineAcceptsUtterance) {
                queued += id
                policy.markSpoken(id)
            }
        }
    }

    @Test fun replyFinishingWhileBackgroundedWaitsForReturnAndIsNotReplayed() {
        val session = VoiceSession()
        session.policy.skipExistingReply(100L)
        session.resumed = false
        session.replyCompleted(101L)
        session.replyCompleted(101L) // TTS initialization can also trigger the effect.
        assertTrue(session.queued.isEmpty())

        session.resumed = true
        session.replyCompleted(101L)
        session.resumed = false
        session.replyCompleted(101L)
        session.resumed = true
        session.replyCompleted(101L)
        assertEquals(listOf(101L), session.queued)
    }

    @Test fun activeRecognizerCannotHearNewAssistantReplyEvenIfActivityIsResumed() {
        val session = VoiceSession()
        session.microphoneInUse = true
        session.replyCompleted(200L)
        session.resumed = false
        session.replyCompleted(200L)
        session.resumed = true // Recognition activity can return before its result callback.
        session.replyCompleted(200L)
        assertTrue(session.queued.isEmpty())

        session.microphoneInUse = false // Success, cancellation and failed launch all release the gate.
        session.replyCompleted(200L)
        session.replyCompleted(200L)
        assertEquals(listOf(200L), session.queued)
    }

    @Test fun replyWaitsUntilVoiceNoteRecordingReleasesMicrophone() {
        val session = VoiceSession()
        session.microphoneInUse = true
        session.replyCompleted(250L)
        assertTrue(session.queued.isEmpty())
        session.microphoneInUse = false
        session.replyCompleted(250L)
        assertEquals(listOf(250L), session.queued)
    }

    @Test fun rejectedSpeechEnqueueDoesNotConsumeReplyOrPreventRetry() {
        val session = VoiceSession()
        session.engineAcceptsUtterance = false
        session.replyCompleted(300L)
        assertTrue(session.queued.isEmpty())
        session.engineAcceptsUtterance = true
        session.replyCompleted(300L)
        session.replyCompleted(300L)
        assertEquals(listOf(300L), session.queued)
    }

    @Test fun enablingRepliesSkipsHistoryButAllowsNextReplyWithoutTimestampOrderingAssumption() {
        val session = VoiceSession()
        session.policy.skipExistingReply(500L)
        session.replyCompleted(500L)
        assertTrue(session.queued.isEmpty())
        // Reply IDs identify turns, not a monotonically increasing system clock.
        session.replyCompleted(400L)
        assertEquals(listOf(400L), session.queued)
    }

    @Test fun noExistingHistoryDoesNotSuppressFirstReply() {
        val policy = SpeechPlaybackPolicy()
        policy.skipExistingReply(null)
        assertFalse(policy.mayPlay(0L, isResumed = false, microphoneInUse = false))
        assertTrue(policy.mayPlay(0L, isResumed = true, microphoneInUse = false))
    }
}
