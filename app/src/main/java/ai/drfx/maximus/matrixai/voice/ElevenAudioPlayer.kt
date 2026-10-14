package ai.drfx.maximus.matrixai.voice

import android.content.Context
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream

/** One playback owner, temporary app-private MP3 bytes are deleted on stop/completion. */
internal class ElevenAudioPlayer(private val context: Context) {
    private var media: MediaPlayer? = null
    private var tempFile: File? = null

    fun stop() {
        media?.let { playing -> runCatching { playing.stop() }; playing.release() }
        media = null
        tempFile?.delete()
        tempFile = null
    }

    fun play(bytes: ByteArray) {
        require(bytes.size in 129..(8 * 1024 * 1024)) { "Audio is missing or oversized." }
        stop()
        val target = File.createTempFile("maximus_eleven_voice_", ".mp3", context.cacheDir)
        tempFile = target
        val player = MediaPlayer()
        media = player
        try {
            FileOutputStream(target).use { it.write(bytes); it.fd.sync() }
            player.setDataSource(target.absolutePath)
            player.setOnPreparedListener { if (media === it) it.start() }
            player.setOnCompletionListener { if (media === it) stop() }
            player.setOnErrorListener { failed, _, _ -> if (media === failed) stop(); true }
            player.prepareAsync()
        } catch (error: Exception) { stop(); throw error }
    }
}
