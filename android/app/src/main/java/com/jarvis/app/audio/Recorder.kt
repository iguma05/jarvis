package com.jarvis.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Records mono 16-bit PCM at 16 kHz and writes a WAV file on stop.
 */
class WavRecorder {
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private val recording = AtomicBoolean(false)
    private val pcmBuffer = ByteArrayOutputStream()

    fun start() {
        if (recording.get()) return

        val sampleRate = SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBuf.coerceAtLeast(sampleRate)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        pcmBuffer.reset()
        audioRecord = recorder
        recording.set(true)
        recorder.startRecording()

        worker = thread(name = "jarvis-wav-recorder", isDaemon = true) {
            val buf = ByteArray(bufferSize)
            while (recording.get()) {
                val read = recorder.read(buf, 0, buf.size)
                if (read > 0) {
                    synchronized(pcmBuffer) {
                        pcmBuffer.write(buf, 0, read)
                    }
                }
            }
        }
    }

    fun stop(outputFile: File): File {
        if (!recording.getAndSet(false)) {
            throw IllegalStateException("Recorder was not running")
        }
        worker?.join(2000)
        worker = null

        audioRecord?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        audioRecord = null

        val pcm = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
        writeWav(outputFile, pcm, SAMPLE_RATE, 1, 16)
        return outputFile
    }

    fun cancel() {
        if (!recording.getAndSet(false)) return
        worker?.join(1000)
        worker = null
        audioRecord?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        audioRecord = null
        pcmBuffer.reset()
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        fun writeWav(
            file: File,
            pcm: ByteArray,
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int,
        ) {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val dataSize = pcm.size
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1) // PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((channels * bitsPerSample / 8).toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)

            FileOutputStream(file).use { out ->
                out.write(header.array())
                out.write(pcm)
            }
        }
    }
}
