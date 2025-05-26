package com.lebaillyapp.ultrasonicfsk.data.service.tone

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*

class TonePlayer(
    private val sampleRate: Int = 44100
) {
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null

    fun playTone(freq: Double, durationMs: Long, scope: CoroutineScope) {
        try {
            stop()
        }catch (e: Exception){
            e.printStackTrace()
        }
        playJob = scope.launch(Dispatchers.Default) {
            val numSamples = (sampleRate * durationMs / 1000.0).toInt()
            val samples = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * i * freq / sampleRate
                samples[i] = (Math.sin(angle) * Short.MAX_VALUE).toInt().toShort()
            }

            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                samples.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack?.apply {
                write(samples, 0, samples.size)
                play()
                delay(durationMs)
                stop()
                release()
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}