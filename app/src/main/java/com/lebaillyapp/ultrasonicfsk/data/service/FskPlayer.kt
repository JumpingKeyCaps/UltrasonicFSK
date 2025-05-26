package com.lebaillyapp.ultrasonicfsk.data.service

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * ### FskPlayer
 * Service de lecture FSK responsable de générer et de jouer des sons sinusoïdaux
 * correspondant à une séquence binaire FSK (fréquence 0 / fréquence 1).
 *
 * Utilise AudioTrack pour jouer des samples PCM 16-bit mono.
 *
 * @param sampleRate La fréquence d’échantillonnage audio (par défaut 44100 Hz).
 */
class FskPlayer(
    private val sampleRate: Int = 44100
) {
    private val sampleBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val audioTrack: AudioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        sampleBufferSize,
        AudioTrack.MODE_STREAM
    )

    private var playJob: Job? = null

    /**
     * ### playFrequencies
     * Joue une séquence de paires (fréquence, durée) via le haut-parleur.
     * La lecture est bloquante (synchrone).
     *
     * @param frequencies Liste de paires (fréquence en Hz, durée en millisecondes).
     */
    fun playFrequencies(frequencies: List<Pair<Double, Long>>) {
        audioTrack.play()
        for ((frequency, durationMs) in frequencies) {
            val buffer = generateTone(frequency, durationMs)
            audioTrack.write(buffer, 0, buffer.size)
        }
        audioTrack.stop()
        audioTrack.flush()
    }

    /**
     * ### playAsync
     * Joue la séquence FSK en tâche de fond (asynchrone via coroutine).
     *
     * @param frequencies Liste de paires (fréquence, durée).
     * @param addSilenceBetweenBits Ajouter un petit silence entre les sons ?
     * @param silenceDurationMs Durée du silence inséré (si activé).
     */
    fun playAsync(
        frequencies: List<Pair<Double, Long>>,
        addSilenceBetweenBits: Boolean = false,
        silenceDurationMs: Long = 5
    ) {
        playJob?.cancel()
        playJob = CoroutineScope(Dispatchers.IO).launch {
            audioTrack.play()
            for ((frequency, durationMs) in frequencies) {
                val tone = generateTone(frequency, durationMs)
                audioTrack.write(tone, 0, tone.size)

                if (addSilenceBetweenBits) {
                    val silence = generateSilence(silenceDurationMs)
                    audioTrack.write(silence, 0, silence.size)
                }
            }
            audioTrack.stop()
            audioTrack.flush()
        }
    }

    /**
     * ### stop
     * Coupe immédiatement l’émission sonore en cours.
     */
    fun stop() {
        playJob?.cancel()
        audioTrack.stop()
        audioTrack.flush()
    }

    /**
     * ### generateTone
     * Génère un tableau PCM 16-bit correspondant à une onde sinusoïdale donnée.
     *
     * @param freq Fréquence de l’onde (en Hz).
     * @param durationMs Durée du son (en millisecondes).
     * @return Tableau de samples audio.
     */
    private fun generateTone(freq: Double, durationMs: Long): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * i * freq / sampleRate
            samples[i] = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /**
     * ### generateSilence
     * Génère un tableau de silence (échantillons à 0) pour une durée donnée.
     *
     * @param durationMs Durée du silence (en millisecondes).
     * @return Tableau de samples audio à 0.
     */
    private fun generateSilence(durationMs: Long): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        return ShortArray(numSamples) { 0 }
    }
}