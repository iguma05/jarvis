package com.jarvis.app.audio

import android.media.MediaPlayer
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class AudioPlayer {
    private val playerRef = AtomicReference<MediaPlayer?>(null)

    fun playFile(file: File, onComplete: (() -> Unit)? = null) {
        stop()
        val player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                stop()
                onComplete?.invoke()
            }
            setOnErrorListener { _, _, _ ->
                stop()
                onComplete?.invoke()
                true
            }
            prepare()
            start()
        }
        playerRef.set(player)
    }

    fun stop() {
        playerRef.getAndSet(null)?.run {
            try {
                if (isPlaying) stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
    }
}
