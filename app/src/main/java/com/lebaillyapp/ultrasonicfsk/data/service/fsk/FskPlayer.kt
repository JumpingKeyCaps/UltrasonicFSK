package com.lebaillyapp.ultrasonicfsk.data.service.fsk

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * #  FskPlayer – Générateur Audio FSK (émission)
 *
 * Composant responsable de la **lecture audio FSK** sur une sortie mono. Ce service produit des signaux sinusoïdaux à des fréquences précises
 * pour encoder une séquence binaire à l'aide de deux tonalités (`freq0`, `freq1`). Il gère aussi les marqueurs **start/stop** et une courte
 * pause silencieuse pour fiabiliser la réception.
 *
 * ---
 *
 * ##  Paramètres principaux :
 * @param sampleRate Fréquence d’échantillonnage (ex. 44100 Hz, idéal pour l'audio haute fréquence)
 * @param startFreq Fréquence utilisée pour signaler le début d’un message (ex. 19000 Hz)
 * @param stopFreq Fréquence utilisée pour signaler la fin d’un message (ex. 19200 Hz)
 * @param bitDurationMs Durée en millisecondes d’un bit (ex. 100 ms pour du 10 bauds)
 *
 * ---
 *
 * ##  Fonctionnalités :
 * - Joue une séquence de fréquences avec AudioTrack (mode STREAM)
 * - Ajoute automatiquement les marqueurs **start** et **stop**
 * - Génère des pauses silencieuses entre les blocs (anti-collage)
 * - Gère l’annulation propre via CoroutineScope
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
     * ##  playMessage()
     * Lance la lecture d’un message FSK (avec début/fin et silence).
     *
     * @param frequencies Liste des paires `(fréquence, durée)` représentant les bits (f0 / f1)
     */
    fun playMessage(frequencies: List<Pair<Double, Long>>) {
        playJob?.cancel()
        playJob = CoroutineScope(Dispatchers.IO).launch {
            audioTrack.play()

            //  Marqueur de début
            val startTone = generateTone(startFreq, bitDurationMs)
            audioTrack.write(startTone, 0, startTone.size)

            //  Petite pause (20 ms)
            val silence = generateSilence(20)
            audioTrack.write(silence, 0, silence.size)

            //  Message binaire
            for ((freq, duration) in frequencies) {
                val tone = generateTone(freq, duration)
                audioTrack.write(tone, 0, tone.size)
            }

            //  Petite pause
            audioTrack.write(silence, 0, silence.size)

            //  Marqueur de fin
            val stopTone = generateTone(stopFreq, bitDurationMs)
            audioTrack.write(stopTone, 0, stopTone.size)

            //  Nettoyage
            audioTrack.stop()
            audioTrack.flush()
        }
    }

    /**
     * ##  stop()
     * Stoppe la lecture immédiatement (annule la coroutine et stoppe l'audio).
     */
    fun stop() {
        playJob?.cancel()
        audioTrack.stop()
        audioTrack.flush()
    }

    /**
     * ##  generateTone()
     * Génère un tableau PCM 16-bit mono pour une onde sinusoïdale donnée.
     *
     * @param freq Fréquence en Hz
     * @param durationMs Durée en millisecondes
     * @return ShortArray contenant les échantillons audio
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
     * ##  generateSilence()
     * Génère un silence (tableau de zéros PCM).
     *
     * @param durationMs Durée du silence en ms
     * @return ShortArray de silence
     */
    private fun generateSilence(durationMs: Long): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        return ShortArray(numSamples) { 0 }
    }
}
