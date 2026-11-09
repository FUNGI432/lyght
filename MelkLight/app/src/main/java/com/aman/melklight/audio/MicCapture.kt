package com.aman.melklight.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Live RMS level (0..1) from the phone's own microphone. Just RECORD_AUDIO,
 * no system-audio loopback: Android has no equivalent without a per-session
 * MediaProjection consent dialog and a dedicated foreground-service type,
 * which isn't worth the complexity against the device's own onboard mic
 * already covering that case - see MelkLight/README.md.
 */
class MicCapture {
    @Volatile
    var level: Float = 0f
        private set

    private val running = AtomicBoolean(false)
    private var record: AudioRecord? = null

    fun isRunning() = running.get()

    fun start() {
        if (running.get()) return
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = if (minBuf > 0) minBuf else 2048
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2)
        } catch (_: SecurityException) {
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }
        record = rec
        running.set(true)
        rec.startRecording()
        thread(name = "mic-capture", isDaemon = true) {
            val buffer = ShortArray(bufSize)
            while (running.get()) {
                val read = rec.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val v = buffer[i] / 32768.0
                        sumSq += v * v
                    }
                    level = sqrt(sumSq / read).toFloat()
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        record?.apply {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        record = null
        level = 0f
    }
}
