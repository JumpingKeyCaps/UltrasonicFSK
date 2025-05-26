package com.lebaillyapp.ultrasonicfsk.data.service

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * ### FskPlayer
 * Service de lecture FSK responsable de jouer des sons sinusoïdaux
 * correspondant à une séquence binaire FSK (freq0 / freq1),
 * avec marqueurs de début/fin et gestion fluide du buffer AudioTrack.
 *
 * @param sampleRate Fréquence d’échantillonnage (44100 Hz par défaut)
 * @param startFreq Fréquence utilisée comme marqueur de début (ex: 19000 Hz)
 * @param stopFreq Fréquence utilisée comme marqueur de fin (ex: 19200 Hz)
 * @param bitDurationMs Durée standard d’un bit (en ms)
 */
class FskPlayer(
    private val sampleRate: Int = 44100,
    private val startFreq: Double = 19000.0,
    private val stopFreq: Double = 19200.0,
    private val bitDurationMs: Long = 100L
) {
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize,
        AudioTrack.MODE_STREAM
    )

    private var playJob: Job? = null

    /**
     * ### PlayMessage
     * Joue un message complet en FSK avec marqueurs début/fin.
     *
     * @param frequencies Liste des paires (fréquence, durée) représentant les bits
     */
    fun playMessage(frequencies: List<Pair<Double, Long>>) {
        playJob?.cancel()
        playJob = CoroutineScope(Dispatchers.IO).launch {
            audioTrack.play()

            // Marqueur de début (1 bit)
            val startTone = generateTone(startFreq, bitDurationMs)
            audioTrack.write(startTone, 0, startTone.size)

            // Pause courte entre marqueur et données
            val silence = generateSilence(20)
            audioTrack.write(silence, 0, silence.size)

            // Données
            for ((freq, duration) in frequencies) {
                val tone = generateTone(freq, duration)
                audioTrack.write(tone, 0, tone.size)
            }

            // Pause courte avant fin
            audioTrack.write(silence, 0, silence.size)

            // Marqueur de fin (1 bit)
            val stopTone = generateTone(stopFreq, bitDurationMs)
            audioTrack.write(stopTone, 0, stopTone.size)

            audioTrack.stop()
            audioTrack.flush()
        }
    }

    /**
     * ### Stop
     * Stoppe la lecture immédiatement.
     */
    fun stop() {
        playJob?.cancel()
        audioTrack.stop()
        audioTrack.flush()
    }

    /**
     * ### GenerateTone
     * Génère un tableau PCM 16-bit pour une onde sinusoïdale.
     *
     * @param freq Fréquence (Hz)
     * @param durationMs Durée en millisecondes
     * @return Samples audio
     */
    private fun generateTone(freq: Double, durationMs: Long): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * i * freq / sampleRate
            samples[i] = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /**
     * ### GenerateSilence
     * Génère un tableau de silence (samples à zéro).
     *
     * @param durationMs Durée en ms
     * @return Samples silence
     */
    private fun generateSilence(durationMs: Long): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        return ShortArray(numSamples) { 0 }
    }
}