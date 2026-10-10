package ai.drfx.maximus.matrixai.ui

import android.content.Context
import android.media.MediaRecorder
import android.util.Base64
import ai.drfx.maximus.matrixai.llm.ChatAttachment
import java.io.File
import android.os.SystemClock

/** Captures a bounded voice note in the app cache. The temporary file is removed on stop or cancel. */
internal class VoiceMessageRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var startedAtMs: Long = 0

    fun start() {
        check(recorder == null) { "A recording is already active." }
        val file = File.createTempFile("maximus-voice-", ".m4a", context.cacheDir)
        val mediaRecorder = MediaRecorder()
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(64_000)
            mediaRecorder.setAudioSamplingRate(16_000)
            mediaRecorder.setMaxDuration(60_000)
            mediaRecorder.setMaxFileSize(3L * 1024 * 1024)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            startedAtMs = SystemClock.elapsedRealtime()
            recorder = mediaRecorder
            recordingFile = file
        } catch (error: Exception) {
            mediaRecorder.release()
            file.delete()
            throw error
        }
    }

    fun stop(): ChatAttachment {
        val active = recorder ?: error("No voice message is being recorded.")
        val file = recordingFile ?: error("Recording file is missing.")
        recorder = null
        recordingFile = null
        return try {
            active.stop()
            active.release()
            require(file.length() in 1..3L * 1024 * 1024) { "The voice message is empty or over 3 MB." }
            val bytes = file.readBytes()
            require(bytes.isNotEmpty() && bytes.size <= 3 * 1024 * 1024) {
                "The voice message is empty or over 3 MB."
            }
            ChatAttachment(
                name = "Voice message.m4a",
                mimeType = "audio/mp4",
                data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                isText = false,
                durationMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0)
            )
        } catch (error: Exception) {
            runCatching { active.release() }
            throw error
        } finally {
            file.delete()
        }
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        recordingFile?.delete()
        recordingFile = null
    }
}
